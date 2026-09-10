package com.wellbuying.domain.product.service;

import com.wellbuying.domain.product.dto.CategoryCreateRequest;
import com.wellbuying.domain.product.dto.CategoryReorderRequest;
import com.wellbuying.domain.product.dto.CategoryResponse;
import com.wellbuying.domain.product.dto.CategoryTreeResponse;
import com.wellbuying.domain.product.dto.CategoryUpdateRequest;
import com.wellbuying.domain.product.entity.ProductCategory;
import com.wellbuying.domain.product.repository.ProductCategoryRepository;
import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CategoryService {

    private static final Long ROOT = -1L;

    private final ProductCategoryRepository categoryRepository;
    // 상품 서비스 등과의 결합도를 낮추기 위해 상품 존재 여부를 확인하는 별도의 컴포넌트나 레포지토리를 참조할 수 있음
    // (현재는 임시로 항상 false를 반환하는 스텁 메서드 사용)

    public CategoryService(ProductCategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Cacheable(value = "categoryTree")
    public List<CategoryTreeResponse> getCategoryTree() {
        List<ProductCategory> all = categoryRepository.findAll();
        
        // 전체 리스트를 단 한 번만 정렬하여 트리를 만들 때 불필요한 중복 정렬 방지 (성능 최적화)
        all.sort(Comparator.comparing(ProductCategory::getSortOrder)
                .thenComparing(ProductCategory::getId));
                
        Map<Long, List<ProductCategory>> byParent = all.stream()
                .collect(Collectors.groupingBy(c -> c.getParentId() == null ? ROOT : c.getParentId()));
                
        return buildTree(ROOT, byParent, new HashSet<>());
    }

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
        // 비영속 상태인 새 엔티티를 먼저 영속화하여 Dirty Checking의 이점을 살릴 수 있도록 함
        category = categoryRepository.save(category);
        
        insertAndReorderSiblings(request.parentId(), category, request.sortOrder());
        return CategoryResponse.from(category);
    }

    @Transactional
    @CacheEvict(value = "categoryTree", allEntries = true)
    public CategoryResponse update(Long categoryId, CategoryUpdateRequest request) {
        ProductCategory category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
        
        boolean duplicate = category.getParentId() == null
                ? categoryRepository.existsByParentIdIsNullAndCategoryName(request.categoryName())
                : categoryRepository.existsByParentIdAndCategoryName(category.getParentId(), request.categoryName());
                
        if (duplicate && !category.getCategoryName().equals(request.categoryName())) {
            throw new BusinessException(ErrorCode.CATEGORY_NAME_DUPLICATE);
        }

        category.update(request.categoryName(), request.sortOrder());
        insertAndReorderSiblings(category.getParentId(), category, request.sortOrder());
        return CategoryResponse.from(category);
    }

    @Transactional
    @CacheEvict(value = "categoryTree", allEntries = true)
    public void reorder(List<CategoryReorderRequest> requests) {
        for (CategoryReorderRequest req : requests) {
            ProductCategory category = categoryRepository.findById(req.id())
                    .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
            category.update(category.getCategoryName(), req.sortOrder());
        }
    }

    @Transactional
    @CacheEvict(value = "categoryTree", allEntries = true)
    public void delete(Long categoryId) {
        ProductCategory category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        if (categoryRepository.existsByParentId(categoryId)) {
            throw new BusinessException(ErrorCode.CATEGORY_HAS_CHILDREN);
        }
        if (hasProducts(categoryId)) {
            throw new BusinessException(ErrorCode.CATEGORY_HAS_PRODUCTS);
        }

        Long parentId = category.getParentId();
        categoryRepository.delete(category);

        List<ProductCategory> siblings = parentId == null 
                ? categoryRepository.findAllByParentIdIsNullOrderBySortOrderAscIdAsc()
                : categoryRepository.findAllByParentIdOrderBySortOrderAscIdAsc(parentId);

        IntStream.range(0, siblings.size())
                 .forEach(i -> siblings.get(i).update(siblings.get(i).getCategoryName(), i + 1));
                 
        // saveAll 제거: 영속성 컨텍스트의 Dirty Checking 활용
    }

    private boolean hasProducts(Long categoryId) {
        return false;
    }

    private void insertAndReorderSiblings(Long parentId, ProductCategory target, Integer targetSortOrder) {
        List<ProductCategory> siblings = parentId == null 
                ? categoryRepository.findAllByParentIdIsNullOrderBySortOrderAscIdAsc()
                : categoryRepository.findAllByParentIdOrderBySortOrderAscIdAsc(parentId);
        
        siblings = new ArrayList<>(siblings);
        // Objects.equals 를 활용하여 NPE 방지
        siblings.removeIf(c -> Objects.equals(c.getId(), target.getId()));
        
        int insertIndex = targetSortOrder != null ? targetSortOrder - 1 : siblings.size();
        insertIndex = Math.max(0, Math.min(insertIndex, siblings.size()));
        
        siblings.add(insertIndex, target);
        
        // IntStream을 활용하여 간결하게 1부터 N까지 순차 부여
        List<ProductCategory> finalSiblings = siblings;
        IntStream.range(0, finalSiblings.size())
                 .forEach(i -> finalSiblings.get(i).update(finalSiblings.get(i).getCategoryName(), i + 1));
                 
        // saveAll 제거: 영속성 컨텍스트의 Dirty Checking 활용
    }

    private List<CategoryTreeResponse> buildTree(Long parentId, Map<Long, List<ProductCategory>> byParent,
                                                 Set<Long> visited) {
        // 이미 상단에서 정렬된 채로 Map에 들어왔으므로 별도의 정렬 연산 제거
        List<ProductCategory> children = byParent.getOrDefault(parentId, List.of());

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
