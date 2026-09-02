-- 로컬 개발용 시드 데이터.
-- 운영/공유 DB에서 절대 실행하지 말 것 - 맨 앞에서 기존 데이터를 전부 지운다.
-- 실행: docker exec -i wellbuying-postgres psql -U postgres -d wellbuying < scripts/local-seed.sql
--
-- 비밀번호는 세 계정 모두 testpass1234 (BCrypt 해시는 애플리케이션의 BCryptPasswordEncoder 기본 강도로 생성)

-- 시각 기준: 컨테이너 Postgres는 UTC로 뜨지만 애플리케이션은 LocalDateTime(KST 벽시계)으로 비교한다.
-- now()를 그대로 쓰면 9시간 어긋나 '마감 임박' 공구가 시더 직후 FAILED로 확정된다.
-- 그래서 KST 벽시계를 뜻하는 now() AT TIME ZONE 'Asia/Seoul' 을 기준으로 삼는다.

BEGIN;

-- 재실행 가능하도록 FK 역순으로 비운다
DELETE FROM payment_failure_log;
DELETE FROM orders;
DELETE FROM payment;
DELETE FROM payment_consumed_event;
DELETE FROM group_buy_event_outbox;
DELETE FROM group_buy_suspension_request;
DELETE FROM group_buy_part;
DELETE FROM group_buy_price;
DELETE FROM group_buy;
DELETE FROM product_count;
DELETE FROM product;
DELETE FROM product_category;
DELETE FROM buyer_address;
DELETE FROM seller_info;
DELETE FROM notification;
DELETE FROM social_account;
DELETE FROM members;

-- ── 회원 ─────────────────────────────────────────────
INSERT INTO members (email, name, password, role, status, phone_number) VALUES
    ('admin@wellbuying.local',  '관리자',   '$2a$10$IyaOotRZ3fqux4aSbWIrnedUK34HmWBhXbNsBR3SlebjgecccX07m', 'ADMIN',  'ACTIVE', '010-0000-0001'),
    ('seller@wellbuying.local', '김생산',   '$2a$10$psUZEQON2qijqWGM2hOvwOQb6fChl8HG0bUIPpjmGUuhKt3nqyFPe', 'SELLER', 'ACTIVE', '010-0000-0002'),
    ('buyer@wellbuying.local',  '이구매',   '$2a$10$OUFf10W6pb9Mgw7dvR7PReBPojfyMinqwoMQHECiGOkHCF6db9uu.', 'BUYER',  'ACTIVE', '010-0000-0003');

-- ── 판매자 정보 (승인 완료 상태) ──────────────────────
INSERT INTO seller_info (member_id, bank_code, bank_name, account_number, account_holder, company_name, status, rank, settlement_cycle, approved_at)
SELECT id, '004', '국민은행', '12345678901234', '김생산', '웰바잉농장', 'APPROVED', 'GOLD', 'MONTHLY', (now() AT TIME ZONE 'Asia/Seoul') - INTERVAL '30 days'
FROM members WHERE email = 'seller@wellbuying.local';

-- ── 구매자 배송지 주소록 ──────────────────────────────
INSERT INTO buyer_address (member_id, address, address_detail, zipcode)
SELECT id, '서울특별시 강남구 테헤란로 123', '4층 401호', '06234'
FROM members WHERE email = 'buyer@wellbuying.local';

-- ── 카테고리 ─────────────────────────────────────────
INSERT INTO product_category (category_name) VALUES ('농산물'), ('수산물'), ('가공식품');

-- ── 상품 (전부 승인 완료) ─────────────────────────────
INSERT INTO product (seller_id, category_id, product_name, description, start_price, thumbnail_url, status)
SELECT m.id, c.id, p.name, p.descr, p.price, p.thumb, 'APPROVED'
FROM members m
CROSS JOIN LATERAL (VALUES
    ('농산물',   '해남 꿀고구마 5kg',      '수확 직후 저온 숙성한 해남산 꿀고구마입니다.',      18000, 'https://picsum.photos/seed/sweetpotato/600/400'),
    ('농산물',   '제주 노지 감귤 10kg',    '노지에서 자연 그대로 키운 제주 감귤.',              25000, 'https://picsum.photos/seed/tangerine/600/400'),
    ('수산물',   '완도 활전복 1kg',        '당일 조업한 완도 활전복을 산 채로 보냅니다.',        45000, 'https://picsum.photos/seed/abalone/600/400'),
    ('가공식품', '전통 방식 조청 500g',    '가마솥에서 8시간 고아낸 조청.',                     12000, 'https://picsum.photos/seed/syrup/600/400')
) AS p(cat, name, descr, price, thumb)
JOIN product_category c ON c.category_name = p.cat
WHERE m.email = 'seller@wellbuying.local';

INSERT INTO product_count (product_id, view_count, like_count, groupbuy_participant_count)
SELECT id, 120, 14, 0 FROM product;

-- ── 공동구매 ─────────────────────────────────────────
-- 상태별로 하나씩 깔아 화면에서 진행중/예정/성사를 모두 볼 수 있게 한다
INSERT INTO group_buy (product_id, producer_id, title, status, start_at, end_at, min_quantity, max_quantity, current_quantity)
SELECT p.id, p.seller_id, g.title, g.status::group_buy_status, g.start_at, g.end_at, g.min_q, g.max_q, g.cur_q
FROM product p
JOIN (VALUES
    ('해남 꿀고구마 5kg',   '해남 꿀고구마 공동구매 3차', 'ONGOING', (now() AT TIME ZONE 'Asia/Seoul') - INTERVAL '2 days',  (now() AT TIME ZONE 'Asia/Seoul') + INTERVAL '7 days',  10, 100, 6),
    ('제주 노지 감귤 10kg', '제주 감귤 마감 임박 공구',   'ONGOING', (now() AT TIME ZONE 'Asia/Seoul') - INTERVAL '5 days',  (now() AT TIME ZONE 'Asia/Seoul') + INTERVAL '2 hours',  5,  50, 4),
    ('완도 활전복 1kg',     '완도 활전복 오픈 예정',      'READY',   (now() AT TIME ZONE 'Asia/Seoul') + INTERVAL '1 day',   (now() AT TIME ZONE 'Asia/Seoul') + INTERVAL '10 days',  8,  40, 0),
    ('전통 방식 조청 500g', '조청 공동구매 (성사 완료)',  'SUCCESS', (now() AT TIME ZONE 'Asia/Seoul') - INTERVAL '20 days', (now() AT TIME ZONE 'Asia/Seoul') - INTERVAL '3 days',  10,  60, 32)
) AS g(product_name, title, status, start_at, end_at, min_q, max_q, cur_q)
  ON g.product_name = p.product_name;

-- ── 수량 구간별 가격 (많이 모일수록 싸진다) ────────────
INSERT INTO group_buy_price (group_buy_id, tier_order, threshold_quantity, unit_price)
SELECT gb.id, t.tier_order, t.threshold, (SELECT start_price FROM product WHERE id = gb.product_id) - t.discount
FROM group_buy gb
CROSS JOIN (VALUES (1, 0, 0), (2, 20, 2000), (3, 50, 4000)) AS t(tier_order, threshold, discount);

-- ── 구매자의 참여 1건 (진행중 공구에 참여한 상태) ──────
INSERT INTO group_buy_part (group_buy_id, member_id, quantity, applied_price, status, buyer_address_id)
SELECT gb.id, m.id, 2, 18000, 'PENDING', ba.id
FROM group_buy gb, members m, buyer_address ba
WHERE gb.title = '해남 꿀고구마 공동구매 3차' AND m.email = 'buyer@wellbuying.local' AND ba.member_id = m.id;

COMMIT;

SELECT 'members' AS t, count(*) FROM members
UNION ALL SELECT 'product', count(*) FROM product
UNION ALL SELECT 'group_buy', count(*) FROM group_buy
UNION ALL SELECT 'group_buy_price', count(*) FROM group_buy_price
UNION ALL SELECT 'group_buy_part', count(*) FROM group_buy_part;
