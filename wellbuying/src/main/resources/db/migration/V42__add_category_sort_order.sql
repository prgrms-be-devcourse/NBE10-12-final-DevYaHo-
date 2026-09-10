-- 카테고리 노출 순서 컬럼 추가 (기본값 0)
ALTER TABLE product_category ADD COLUMN sort_order INT NOT NULL DEFAULT 0;
