-- product.status 컬럼을 BOOLEAN에서 PostgreSQL 네이티브 ENUM 타입으로 전환
CREATE TYPE product_status AS ENUM ('ON_SALE', 'SOLD_OUT');

-- DEFAULT 값이 BOOLEAN 리터럴(true)이라 TYPE 변환 시 자동 캐스팅 불가 → 먼저 DROP 후 변환, 이후 새 DEFAULT 세팅
ALTER TABLE product ALTER COLUMN status DROP DEFAULT;

ALTER TABLE product
    ALTER COLUMN status TYPE product_status
    USING CASE WHEN status = true THEN 'ON_SALE'::product_status
               ELSE 'SOLD_OUT'::product_status
          END;

ALTER TABLE product ALTER COLUMN status SET DEFAULT 'ON_SALE';

CREATE INDEX idx_product_start_price ON product (start_price);

-- (category_id, status) 필터 단독 및 start_price 정렬을 하나의 인덱스로 커버
-- PRICE_ASC / PRICE_DESC 정렬 시 filesort를 제거하고, category+status 필터만 쓰는 경우도 leftmost prefix로 그대로 활용
CREATE INDEX idx_product_category_status_price ON product (category_id, status, start_price);
