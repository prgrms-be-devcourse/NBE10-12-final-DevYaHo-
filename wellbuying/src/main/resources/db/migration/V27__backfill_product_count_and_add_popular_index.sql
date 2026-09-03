-- product_count가 없는 기존 상품에 대해 (view_count=0, like_count=0, groupbuy_participant_count=0)으로 초기화
-- 신규 상품은 ProductService.createProduct()에서 항상 생성하므로 이미 존재하는 행은 건드리지 않음
INSERT INTO product_count (product_id, view_count, like_count, groupbuy_participant_count, updated_at)
SELECT p.id, 0, 0, 0, now()
FROM product p
WHERE NOT EXISTS (
    SELECT 1 FROM product_count pc WHERE pc.product_id = p.id
);

-- POPULAR 정렬(view_count DESC, product_id DESC) 커버링 인덱스
-- coalesce 없이 product_count.view_count를 직접 참조하므로 이 인덱스를 정렬에 활용 가능
CREATE INDEX idx_product_count_view_count_product_id ON product_count (view_count DESC, product_id DESC);
