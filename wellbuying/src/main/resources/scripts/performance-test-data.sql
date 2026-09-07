-- 성능 테스트 데이터 생성 스크립트
-- 실행 전 확인: v_count 변수로 삽입 건수 조절 (테스트: 1000, 본 실행: 1000000)
-- 실행: docker exec -i wellbuying-postgres psql -U postgres -d wellbuying < performance-test-data.sql

\timing on

-- 1. 테스트 판매자 삽입
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM members WHERE email = 'perf-test-seller@test.com') THEN
        INSERT INTO members (email, name, role, status, created_at, updated_at)
        VALUES ('perf-test-seller@test.com', '성능테스트셀러', 'SELLER', 'ACTIVE', NOW(), NOW());
        RAISE NOTICE '테스트 판매자 삽입 완료';
    ELSE
        RAISE NOTICE '테스트 판매자 이미 존재';
    END IF;
END; $$;

-- 2. 테스트 카테고리 10개 삽입
DO $$
DECLARE
    cat TEXT;
    cat_names TEXT[] := ARRAY[
        '건강식품', '생활용품', '뷰티/화장품', '스포츠', '식품',
        '의류', '전자제품', '여행', '반려동물', '완구'
    ];
BEGIN
    FOREACH cat IN ARRAY cat_names LOOP
        INSERT INTO product_category (category_name)
        SELECT cat
        WHERE NOT EXISTS (SELECT 1 FROM product_category WHERE category_name = cat);
    END LOOP;
    RAISE NOTICE '카테고리 삽입 완료: % 개', (SELECT COUNT(*) FROM product_category);
END; $$;

-- 3. 상품 대량 삽입 + product_count
-- v_count 값을 바꿔서 삽입 건수 조절
DO $$
DECLARE
    v_seller_id  BIGINT;
    v_cat_ids    BIGINT[];
    v_cat_len    INT;
    v_count      INT := 1000000;  -- ← 본 실행 시 1000000으로 변경
    v_id_base    BIGINT := 800000000;

    adj    TEXT[] := ARRAY['유기농','프리미엄','베스트','신선한','천연','국내산','수입','고품질','특가','한정'];
    nouns  TEXT[] := ARRAY['비타민','마스크','텀블러','에코백','쿠션','영양제','셔츠','신발','가방','모자'];
    descs  TEXT[] := ARRAY[
        '건강에 좋은 제품입니다.',
        '인기 많은 베스트셀러입니다.',
        '품질이 뛰어납니다.',
        '가성비 최고 제품입니다.',
        '믿을 수 있는 브랜드입니다.'
    ];
BEGIN
    SELECT id INTO v_seller_id FROM members WHERE email = 'perf-test-seller@test.com';
    SELECT ARRAY_AGG(id ORDER BY id) INTO v_cat_ids FROM product_category;
    v_cat_len := array_length(v_cat_ids, 1);

    -- 상품 삽입 (status 분포: APPROVED 90%, PENDING 5%, REJECTED 5%)
    INSERT INTO product (id, seller_id, category_id, product_name, description,
                         start_price, thumbnail_url, status, created_at)
    SELECT
        v_id_base + i,
        v_seller_id,
        v_cat_ids[1 + (i % v_cat_len)],
        adj[1 + (i % 10)] || ' ' || nouns[1 + ((i / 10) % 10)],
        descs[1 + (i % 5)],
        1000 + (random() * 99000)::INT,
        NULL,
        CASE
            WHEN (i % 20) < 18 THEN 'APPROVED'::product_status
            WHEN (i % 20) = 18 THEN 'PENDING'::product_status
            ELSE                    'REJECTED'::product_status
        END,
        NOW() - ((random() * 365)::INT || ' days')::INTERVAL
    FROM generate_series(1, v_count) t(i)
    ON CONFLICT (id) DO NOTHING;

    RAISE NOTICE '상품 삽입 완료: % 건', v_count;

    -- product_count 삽입 (전체 상품, viewCount 랜덤)
    INSERT INTO product_count (product_id, view_count, like_count, groupbuy_participant_count, updated_at)
    SELECT
        v_id_base + i,
        (random() * 10000)::BIGINT,
        (random() * 1000)::BIGINT,
        (random() * 500)::BIGINT,
        NOW()
    FROM generate_series(1, v_count) t(i)
    ON CONFLICT (product_id) DO NOTHING;

    RAISE NOTICE 'product_count 삽입 완료';
END; $$;

-- 4. 결과 확인
SELECT
    status,
    COUNT(*) AS cnt
FROM product
WHERE id >= 800000001
GROUP BY status
ORDER BY status;

SELECT COUNT(*) AS product_count_rows FROM product_count WHERE product_id >= 800000001;
