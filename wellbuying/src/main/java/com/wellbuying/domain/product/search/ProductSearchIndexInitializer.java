package com.wellbuying.domain.product.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Component;

// 기존 인덱스에 새 필드 매핑 추가용. 인덱스가 없으면 Spring Data가 생성하므로 건너뜀.
@Component
public class ProductSearchIndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductSearchIndexInitializer.class);

    private final ElasticsearchOperations operations;

    public ProductSearchIndexInitializer(ElasticsearchOperations operations) {
        this.operations = operations;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            IndexOperations indexOps = operations.indexOps(ProductSearchDocument.class);
            if (indexOps.exists()) {
                indexOps.putMapping(indexOps.createMapping());
            }
        } catch (Exception e) {
            log.error("검색 인덱스 매핑 업데이트 실패 (기동 중단 없이 진행)", e);
        }
    }
}
