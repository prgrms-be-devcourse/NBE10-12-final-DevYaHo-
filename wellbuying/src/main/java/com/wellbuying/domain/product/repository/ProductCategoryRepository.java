package com.wellbuying.domain.product.repository;

import com.wellbuying.domain.product.entity.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {

    // 해당 카테고리를 부모로 가지는 자식 카테고리 존재 여부 확인 (삭제 차단용)
    boolean existsByParentId(Long parentId);

    // 최상위(1뎁스) 카테고리 중 같은 이름 존재 여부 - 생성/수정 시 중복 차단용
    boolean existsByParentIdIsNullAndCategoryName(String categoryName);

    // 같은 부모 아래 같은 이름 존재 여부 - 생성/수정 시 중복 차단용
    boolean existsByParentIdAndCategoryName(Long parentId, String categoryName);

    // 최상위 카테고리 형제 목록 조회 (정렬)
    List<ProductCategory> findAllByParentIdIsNullOrderBySortOrderAscIdAsc();

    // 하위 카테고리 형제 목록 조회 (정렬)
    List<ProductCategory> findAllByParentIdOrderBySortOrderAscIdAsc(Long parentId);
}