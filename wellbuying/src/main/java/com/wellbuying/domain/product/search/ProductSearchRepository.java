package com.wellbuying.domain.product.search;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ProductSearchRepository extends ElasticsearchRepository<ProductSearchDocument, Long>, ProductSearchRepositoryCustom {
}
