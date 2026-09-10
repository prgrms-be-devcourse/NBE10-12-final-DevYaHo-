-- 같은 부모 아래에서 카테고리 이름 중복을 방지하는 유니크 인덱스
-- parent_id가 NULL인 최상위(1뎁스) 끼리도 이름이 유일해야 하므로 두 개의 부분 인덱스로 구성
-- (PostgreSQL의 일반 UNIQUE 인덱스는 NULL을 서로 다른 값으로 취급하므로, NULL 끼리의 중복을 막으려면 부분 인덱스가 필요)

-- 최상위(1뎁스) 카테고리: parent_id IS NULL인 행들 내에서 category_name 유일
CREATE UNIQUE INDEX uq_category_name_top_level
    ON product_category (category_name)
    WHERE parent_id IS NULL;

-- 하위(2뎁스) 카테고리: 같은 parent_id 내에서 category_name 유일
CREATE UNIQUE INDEX uq_category_name_per_parent
    ON product_category (parent_id, category_name)
    WHERE parent_id IS NOT NULL;
