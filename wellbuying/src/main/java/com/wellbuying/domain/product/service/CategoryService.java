package com.wellbuying.domain.product.service;

import com.wellbuying.domain.product.dto.CategoryCreateRequest;
import com.wellbuying.domain.product.dto.CategoryResponse;
import com.wellbuying.domain.product.dto.CategoryTreeResponse;
import com.wellbuying.domain.product.dto.CategoryUpdateRequest;
import com.wellbuying.domain.product.entity.ProductCategory;
import com.wellbuying.domain.product.repository.ProductCategoryRepository;
import com.wellbuying.domain.product.repository.ProductRepository;
import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private static final long ROOT = 0L;

    private final ProductCategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CategoryService(ProductCategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    // 등록 후 거의 안 바뀌는 데이터라 캐싱 - 카테고리 생성/수정 API가 생기면 그때 캐시 무효화(@CacheEvict)도 같이 추가해야 함
    // 전체 카테고리를 조회해 부모-자식 관계 기준으로 그룹핑한 뒤, 최상위부터 트리 구조로 조립
    @Cacheable("categoryTree")
    @Transactional(readOnly = true)
    public List<CategoryTreeResponse> getCategoryTree() {
        List<ProductCategory> all = categoryRepository.findAll();
        Map<Long, List<ProductCategory>> byParent = all.stream()
                .collect(Collectors.groupingBy(c -> c.getParentId() == null ? ROOT : c.getParentId()));
        return buildTree(ROOT, byParent, new HashSet<>());
    }

    // parentId가 null이면 최상위(1뎁스), 있으면 해당 카테고리의 하위(2뎁스)로 생성
    // 2뎁스 제한: parentId가 가리키는 카테고리가 이미 하위(parentId != null)이면 3뎁스가 되므로 차단
    @Transactional
    @CacheEvict(value = "categoryTree", allEntries = true)
    public CategoryResponse create(CategoryCreateRequest request) {
        if (request.parentId() != null) {
            ProductCategory parent = categoryRepository.findById(request.parentId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.PARENT_CATEGORY_NOT_FOUND));
            if (parent.getParentId() != null) {
                throw new BusinessException(ErrorCode.CATEGORY_DEPTH_EXCEEDED);
            }
            if (categoryRepository.existsByParentIdAndCategoryName(request.parentId(), request.categoryName())) {
                throw new BusinessException(ErrorCode.CATEGORY_NAME_DUPLICATE);
            }
        } else {
            if (categoryRepository.existsByParentIdIsNullAndCategoryName(request.categoryName())) {
                throw new BusinessException(ErrorCode.CATEGORY_NAME_DUPLICATE);
            }
        }
        
        ProductCategory category = ProductCategory.create(request.parentId(), request.categoryName(), request.sortOrder());
        insertAndReorderSiblings(request.parentId(), category, request.sortOrder());
        return CategoryResponse.from(category);
    }

    // 카테고리명 변경 - 같은 부모 아래 동일 이름 중복 차단, parentId 변경(이동)은 순환 참조 이슈가 있어 이번 범위 제외
    @Transactional
    @CacheEvict(value = "categoryTree", allEntries = true)
    public CategoryResponse update(Long categoryId, CategoryUpdateRequest request) {
        ProductCategory category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
        // 자기 자신을 제외하고 같은 부모 아래 같은 이름이 이미 있는지 확인
        boolean duplicate = category.getParentId() == null
                ? categoryRepository.existsByParentIdIsNullAndCategoryName(request.categoryName())
                : categoryRepository.existsByParentIdAndCategoryName(category.getParentId(), request.categoryName());
        // 이름이 같아도 자기 자신이라면 허용 (이름 그대로 저장하는 경우)
        if (duplicate && !category.getCategoryName().equals(request.categoryName())) {
            throw new BusinessException(ErrorCode.CATEGORY_NAME_DUPLICATE);
        }

        category.update(request.categoryName(), request.sortOrder());
        insertAndReorderSiblings(category.getParentId(), category, request.sortOrder());
        return CategoryResponse.from(category);
    }

    // 드래그 앤 드롭 일괄 저장 API용 메서드
    @Transactional
    @CacheEvict(value = "categoryTree", allEntries = true)
    public void reorder(java.util.List<com.wellbuying.domain.product.dto.CategoryReorderRequest> requests) {
        for (com.wellbuying.domain.product.dto.CategoryReorderRequest req : requests) {
            ProductCategory category = categoryRepository.findById(req.id())
                    .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
            category.update(category.getCategoryName(), req.sortOrder());
        }
        // DB flush 시점에서 유니크 제약이나 꼬임 방지를 위해 순차 재정렬은 호출하지 않음 
        // (프론트에서 이미 1, 2, 3.. 으로 완벽히 세팅해서 넘겼다고 가정)
    }

    // 형제 카테고리 리스트를 불러와서 원하는 위치에 삽입하고, 1부터 N까지 번호를 자동 재부여 (Auto-shifting)
    private void insertAndReorderSiblings(Long parentId, ProductCategory target, Integer targetSortOrder) {
        List<ProductCategory> siblings = parentId == null 
                ? categoryRepository.findAllByParentIdIsNullOrderBySortOrderAscIdAsc()
                : categoryRepository.findAllByParentIdOrderBySortOrderAscIdAsc(parentId);
        
        // 메모리 리스트 편집을 위해 ArrayList 변환 및 타겟 카테고리 기존 내역 제거
        siblings = new java.util.ArrayList<>(siblings);
        siblings.removeIf(c -> c.getId() != null && c.getId().equals(target.getId()));
        
        // 0 미만이나 배열 크기를 초과하지 않도록 안전한 인덱스 계산 (sortOrder는 1부터 시작하므로 -1)
        int insertIndex = targetSortOrder != null ? targetSortOrder - 1 : siblings.size();
        insertIndex = Math.max(0, Math.min(insertIndex, siblings.size()));
        
        siblings.add(insertIndex, target);
        
        // 다시 1부터 N까지 순차 부여
        for (int i = 0; i < siblings.size(); i++) {
            siblings.get(i).update(siblings.get(i).getCategoryName(), i + 1);
        }
        categoryRepository.saveAll(siblings);
    }

    // 삭제 전 자식 카테고리·참조 상품 존재 여부를 선제 차단 (DB FK 오류보다 명확한 메시지 제공)
    @Transactional
    @CacheEvict(value = "categoryTree", allEntries = true)
    public void delete(Long categoryId) {
        ProductCategory category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
        if (categoryRepository.existsByParentId(categoryId)) {
            throw new BusinessException(ErrorCode.CATEGORY_HAS_CHILDREN);
        }
        if (productRepository.existsByCategoryIdAndDeletedAtIsNull(categoryId)) {
            throw new BusinessException(ErrorCode.CATEGORY_HAS_PRODUCTS);
        }
        
        categoryRepository.delete(category);
        categoryRepository.flush(); // DB에서 삭제 처리를 먼저 확정
        
        // 삭제 후 이빨 빠진 형제 목록 재정렬
        List<ProductCategory> siblings = category.getParentId() == null 
                ? categoryRepository.findAllByParentIdIsNullOrderBySortOrderAscIdAsc()
                : categoryRepository.findAllByParentIdOrderBySortOrderAscIdAsc(category.getParentId());
        
        for (int i = 0; i < siblings.size(); i++) {
            siblings.get(i).update(siblings.get(i).getCategoryName(), i + 1);
        }
        categoryRepository.saveAll(siblings);
    }

    // 주어진 부모 ID의 자식 카테고리들을 조회하고, 각 자식에 대해 재귀 호출하여 하위 트리까지 조립
    // visited로 이미 방문한 카테고리를 걸러내 데이터 오류로 인한 순환 참조가 있어도 무한 재귀에 빠지지 않게 함
    // 노출 순서 정렬: sortOrder 오름차순, 동일 시 id 오름차순
    private List<CategoryTreeResponse> buildTree(Long parentId, Map<Long, List<ProductCategory>> byParent,
                                                 Set<Long> visited) {
        List<ProductCategory> children = byParent.getOrDefault(parentId, List.of()).stream()
                .sorted(java.util.Comparator.comparing(ProductCategory::getSortOrder)
                        .thenComparing(ProductCategory::getId))
                .toList();

        List<CategoryTreeResponse> result = new ArrayList<>();
        for (ProductCategory child : children) {
            if (visited.add(child.getId())) {
                result.add(new CategoryTreeResponse(child.getId(), child.getCategoryName(), child.getSortOrder(),
                        buildTree(child.getId(), byParent, visited)));
            }
        }
        return Collections.unmodifiableList(result);
    }
}