package com.wellbuying.domain.product.service;

import com.wellbuying.domain.product.entity.ProductCategory;
import com.wellbuying.domain.product.dto.CategoryTreeResponse;
import com.wellbuying.domain.product.repository.ProductCategoryRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private static final long ROOT = 0L;

    private final ProductCategoryRepository categoryRepository;

    public CategoryService(ProductCategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
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

    // 주어진 부모 ID의 자식 카테고리들을 조회하고, 각 자식에 대해 재귀 호출하여 하위 트리까지 조립
    // visited로 이미 방문한 카테고리를 걸러내 데이터 오류로 인한 순환 참조가 있어도 무한 재귀에 빠지지 않게 함
    private List<CategoryTreeResponse> buildTree(Long parentId, Map<Long, List<ProductCategory>> byParent,
                                                 Set<Long> visited) {
        List<ProductCategory> children = byParent.getOrDefault(parentId, List.of());
        List<CategoryTreeResponse> result = new ArrayList<>();
        for (ProductCategory child : children) {
            if (visited.add(child.getId())) {
                result.add(new CategoryTreeResponse(child.getId(), child.getCategoryName(),
                        buildTree(child.getId(), byParent, visited)));
            }
        }
        return Collections.unmodifiableList(result);
    }
}