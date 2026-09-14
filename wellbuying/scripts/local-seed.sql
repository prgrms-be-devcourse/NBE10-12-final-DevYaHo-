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
DELETE FROM settlement_item;
DELETE FROM settlement;
DELETE FROM group_buy_part;
DELETE FROM group_buy_price;
DELETE FROM group_buy;
DELETE FROM product_count;
DELETE FROM product_image;
DELETE FROM product;
DELETE FROM product_category;
DELETE FROM buyer_address;
DELETE FROM seller_info;
DELETE FROM notification;
DELETE FROM social_account;
DELETE FROM admin_action_log;
DELETE FROM members;

-- ── 회원 ─────────────────────────────────────────────
INSERT INTO members (email, name, password, role, status, phone_number) VALUES
    ('admin@wellbuying.xyz',    '관리자',   '$2a$10$IyaOotRZ3fqux4aSbWIrnedUK34HmWBhXbNsBR3SlebjgecccX07m', 'ADMIN',  'ACTIVE', '010-0000-0001'),
    ('seller@wellbuying.local', '김생산',   '$2a$10$psUZEQON2qijqWGM2hOvwOQb6fChl8HG0bUIPpjmGUuhKt3nqyFPe', 'SELLER', 'ACTIVE', '010-0000-0002'),
    ('buyer@wellbuying.local',  '이구매',   '$2a$10$OUFf10W6pb9Mgw7dvR7PReBPojfyMinqwoMQHECiGOkHCF6db9uu.', 'BUYER',  'ACTIVE', '010-0000-0003');

-- 정산 대시보드 시연용 추가 구매자(buyer2~buyer8) - 한 공동구매에 여러 참여자가 있어야
-- settlement.item_count/total_sales가 1건짜리보다 그럴듯해진다. 비밀번호는 buyer@wellbuying.local과
-- 동일한 해시(testpass1234)를 재사용한다
INSERT INTO members (email, name, password, role, status, phone_number)
SELECT 'buyer' || n || '@wellbuying.local', '구매자' || n,
       '$2a$10$OUFf10W6pb9Mgw7dvR7PReBPojfyMinqwoMQHECiGOkHCF6db9uu.', 'BUYER', 'ACTIVE',
       '010-0000-10' || lpad(n::text, 2, '0')
FROM generate_series(2, 8) AS n;

-- ── 판매자 정보 (승인 완료 상태) ──────────────────────
INSERT INTO seller_info (member_id, bank_code, bank_name, account_number, account_holder, company_name, status, rank, settlement_cycle, approved_at)
SELECT id, '004', '국민은행', '12345678901234', '김생산', '웰바잉농장', 'APPROVED', 'GOLD', 'MONTHLY', (now() AT TIME ZONE 'Asia/Seoul') - INTERVAL '30 days'
FROM members WHERE email = 'seller@wellbuying.local';

-- ── 구매자 배송지 주소록 ──────────────────────────────
INSERT INTO buyer_address (member_id, address, address_detail, zipcode)
SELECT id, '서울특별시 강남구 테헤란로 123', '4층 401호', '06234'
FROM members WHERE email = 'buyer@wellbuying.local';

-- ── 카테고리 ─────────────────────────────────────────
-- 정육/뷰티/생활용품은 오픈소스 더미 데이터(DummyJSON, https://dummyjson.com)로 상품을 대량 채우기 위해 추가
INSERT INTO product_category (category_name) VALUES ('농산물'), ('수산물'), ('가공식품'), ('정육'), ('뷰티'), ('생활용품');

-- ── 상품 (전부 승인 완료) ─────────────────────────────
INSERT INTO product (seller_id, category_id, product_name, description, start_price, thumbnail_url, status)
SELECT m.id, c.id, p.name, p.descr, p.price, p.thumb, 'APPROVED'
FROM members m
CROSS JOIN LATERAL (VALUES
    ('농산물',   '해남 꿀고구마 5kg',      '수확 직후 저온 숙성한 해남산 꿀고구마입니다.',      18000, 'https://picsum.photos/seed/sweetpotato/600/400'),
    ('농산물',   '제주 노지 감귤 10kg',    '노지에서 자연 그대로 키운 제주 감귤.',              25000, 'https://picsum.photos/seed/tangerine/600/400'),
    ('수산물',   '완도 활전복 1kg',        '당일 조업한 완도 활전복을 산 채로 보냅니다.',        45000, 'https://picsum.photos/seed/abalone/600/400'),
    ('가공식품', '전통 방식 조청 500g',    '가마솥에서 8시간 고아낸 조청.',                     12000, 'https://picsum.photos/seed/syrup/600/400'),
    -- 아래부터는 DummyJSON(오픈소스 더미 API) 상품을 국문으로 각색해 대량 시딩용으로 추가한 것.
    -- 가격은 USD 가격에 대략 1300원을 곱해 반올림한 값으로, 실제 시세와 무관하다.
    ('농산물',   '아삭 사과 1kg',            '아삭하고 신선한 사과, 간식이나 다양한 요리에 잘 어울립니다.', 2600,    'https://cdn.dummyjson.com/product-images/groceries/apple/thumbnail.webp'),
    ('농산물',   '아삭 오이 5입',            '아삭하고 수분 가득한 오이, 샐러드나 간식으로 좋습니다.',      1900,    'https://cdn.dummyjson.com/product-images/groceries/cucumber/thumbnail.webp'),
    ('가공식품', '요리용 식용유 900ml',      '튀김, 볶음 등 다양한 요리에 쓰기 좋은 식용유.',              6500,    'https://cdn.dummyjson.com/product-images/groceries/cooking-oil/thumbnail.webp'),
    ('가공식품', '신선 달걀 한 판(30구)',    '베이킹, 요리, 아침식사에 두루 쓰이는 신선한 달걀.',          3900,    'https://cdn.dummyjson.com/product-images/groceries/eggs/thumbnail.webp'),
    ('정육',     '소고기 스테이크용 400g',   '그릴이나 구이에 좋은 고급 소고기 스테이크.',                16900,   'https://cdn.dummyjson.com/product-images/groceries/beef-steak/thumbnail.webp'),
    ('정육',     '냉장 닭가슴살 1kg',        '다양한 요리에 활용하기 좋은 신선하고 부드러운 닭고기.',      13000,   'https://cdn.dummyjson.com/product-images/groceries/chicken-meat/thumbnail.webp'),
    ('뷰티',     '래쉬 프린세스 마스카라',   '풍성하고 길어 보이는 속눈썹을 만들어주는 인기 마스카라.',    13000,   'https://cdn.dummyjson.com/product-images/beauty/essence-mascara-lash-princess/thumbnail.webp'),
    ('뷰티',     '거울 내장 아이섀도 팔레트', '다양한 색상의 아이섀도로 눈매 연출, 휴대용 거울 내장.',      26000,   'https://cdn.dummyjson.com/product-images/beauty/eyeshadow-palette-with-mirror/thumbnail.webp'),
    ('뷰티',     '피지 컨트롤 세팅 파우더', '피지를 잡아주는 미세한 입자의 세팅 파우더.',                19500,   'https://cdn.dummyjson.com/product-images/beauty/powder-canister/thumbnail.webp'),
    ('뷰티',     '레드 립스틱',              '선명한 컬러감의 클래식 레드 립스틱, 크리미한 발림성.',        16900,   'https://cdn.dummyjson.com/product-images/beauty/red-lipstick/thumbnail.webp'),
    ('뷰티',     '레드 네일 폴리시',         '빠르게 건조되는 살롱급 발색의 레드 네일 폴리시.',            11700,   'https://cdn.dummyjson.com/product-images/beauty/red-nail-polish/thumbnail.webp'),
    ('뷰티',     '슈퍼리브스 핸드솝',        '슈퍼리브스 성분으로 촉촉하게 씻어내는 핸드솝.',              11700,   'https://cdn.dummyjson.com/product-images/skin-care/attitude-super-leaves-hand-soap/thumbnail.webp'),
    ('뷰티',     '시어버터 모이스처 바디워시', '시어버터 성분으로 촉촉함을 더하는 바디워시.',              16900,   'https://cdn.dummyjson.com/product-images/skin-care/olay-ultra-moisture-shea-butter-body-wash/thumbnail.webp'),
    ('뷰티',     '맨즈 바디앤페이스 로션',   '남성 피부에 오래가는 보습을 주는 로션.',                    13000,   'https://cdn.dummyjson.com/product-images/skin-care/vaseline-men-body-and-face-lotion/thumbnail.webp'),
    ('생활용품', '침대 프레임(퀸)',          '고급스러운 소재로 제작된 침실용 침대 프레임.',              2470000, 'https://cdn.dummyjson.com/product-images/furniture/annibale-colombo-bed/thumbnail.webp'),
    ('생활용품', '업홀스터리 3인 소파',      '고급 업홀스터리로 마감된 거실용 소파.',                      3250000, 'https://cdn.dummyjson.com/product-images/furniture/annibale-colombo-sofa/thumbnail.webp'),
    ('생활용품', '체리목 협탁',              '침실에 포인트를 더하는 수납형 협탁.',                        390000,  'https://cdn.dummyjson.com/product-images/furniture/bedside-table-african-cherry/thumbnail.webp'),
    ('생활용품', '이그제큐티브 오피스 체어', '인체공학적인 디자인의 모던 오피스 체어.',                    650000,  'https://cdn.dummyjson.com/product-images/furniture/knoll-saarinen-executive-conference-chair/thumbnail.webp'),
    ('생활용품', '원목 세면대 & 거울 세트',  '원목 세면대와 매칭 거울로 구성된 욕실 세트.',                1040000, 'https://cdn.dummyjson.com/product-images/furniture/wooden-bathroom-sink-with-mirror/thumbnail.webp'),
    ('생활용품', '장식용 그네 소품',         '정교한 디테일로 공간에 포인트를 더하는 장식 그네.',          78000,   'https://cdn.dummyjson.com/product-images/home-decoration/decoration-swing/thumbnail.webp'),
    ('생활용품', '패밀리 포토 프레임',       '여러 장의 사진을 담을 수 있는 감성적인 가족 액자.',          39000,   'https://cdn.dummyjson.com/product-images/home-decoration/family-tree-photo-frame/thumbnail.webp'),
    ('생활용품', '조화 인테리어 화분',       '관리 부담 없이 그린 인테리어를 완성하는 조화 식물.',          52000,   'https://cdn.dummyjson.com/product-images/home-decoration/house-showpiece-plant/thumbnail.webp'),
    ('생활용품', '토분 화분',                '실내외 어디에나 어울리는 세련된 디자인의 화분.',              19500,   'https://cdn.dummyjson.com/product-images/home-decoration/plant-pot/thumbnail.webp'),
    ('생활용품', '테이블 스탠드 조명',       '은은한 분위기를 더하는 모던 테이블 조명.',                    65000,   'https://cdn.dummyjson.com/product-images/home-decoration/table-lamp/thumbnail.webp'),
    ('생활용품', '대나무 뒤집개',            '친환경 대나무 소재의 다용도 뒤집개.',                        10400,   'https://cdn.dummyjson.com/product-images/kitchen-accessories/bamboo-spatula/thumbnail.webp'),
    ('생활용품', '블랙 알루미늄 컵',         '냉온 음료 모두 어울리는 세련된 블랙 컵.',                    7800,    'https://cdn.dummyjson.com/product-images/kitchen-accessories/black-aluminium-cup/thumbnail.webp'),
    ('생활용품', '블랙 거품기',              '인체공학 손잡이의 실용적인 거품기.',                          13000,   'https://cdn.dummyjson.com/product-images/kitchen-accessories/black-whisk/thumbnail.webp'),
    ('생활용품', '박스형 블렌더',            '스무디, 쉐이크에 좋은 강력한 컴팩트 블렌더.',                52000,   'https://cdn.dummyjson.com/product-images/kitchen-accessories/boxed-blender/thumbnail.webp'),
    ('생활용품', '탄소강 웍',                '고르게 열을 전달하는 튼튼한 탄소강 웍.',                      39000,   'https://cdn.dummyjson.com/product-images/kitchen-accessories/carbon-steel-wok/thumbnail.webp'),
    ('생활용품', '원목 도마',                '위생적인 조리를 위한 튼튼한 도마.',                          16900,   'https://cdn.dummyjson.com/product-images/kitchen-accessories/chopping-board/thumbnail.webp')
) AS p(cat, name, descr, price, thumb)
JOIN product_category c ON c.category_name = p.cat
WHERE m.email = 'seller@wellbuying.local';

-- ── 상품 갤러리 이미지 (DummyJSON 원본 이미지를 그대로 사용) ────
INSERT INTO product_image (product_id, image_url, sort_order, image_type)
SELECT p.id, img.image_url, img.sort_order, 'GALLERY'
FROM product p
JOIN (VALUES
    ('아삭 사과 1kg', 'https://cdn.dummyjson.com/product-images/groceries/apple/1.webp', 0),
    ('아삭 오이 5입', 'https://cdn.dummyjson.com/product-images/groceries/cucumber/1.webp', 0),
    ('요리용 식용유 900ml', 'https://cdn.dummyjson.com/product-images/groceries/cooking-oil/1.webp', 0),
    ('신선 달걀 한 판(30구)', 'https://cdn.dummyjson.com/product-images/groceries/eggs/1.webp', 0),
    ('소고기 스테이크용 400g', 'https://cdn.dummyjson.com/product-images/groceries/beef-steak/1.webp', 0),
    ('냉장 닭가슴살 1kg', 'https://cdn.dummyjson.com/product-images/groceries/chicken-meat/1.webp', 0),
    ('냉장 닭가슴살 1kg', 'https://cdn.dummyjson.com/product-images/groceries/chicken-meat/2.webp', 1),
    ('래쉬 프린세스 마스카라', 'https://cdn.dummyjson.com/product-images/beauty/essence-mascara-lash-princess/1.webp', 0),
    ('거울 내장 아이섀도 팔레트', 'https://cdn.dummyjson.com/product-images/beauty/eyeshadow-palette-with-mirror/1.webp', 0),
    ('피지 컨트롤 세팅 파우더', 'https://cdn.dummyjson.com/product-images/beauty/powder-canister/1.webp', 0),
    ('레드 립스틱', 'https://cdn.dummyjson.com/product-images/beauty/red-lipstick/1.webp', 0),
    ('레드 네일 폴리시', 'https://cdn.dummyjson.com/product-images/beauty/red-nail-polish/1.webp', 0),
    ('슈퍼리브스 핸드솝', 'https://cdn.dummyjson.com/product-images/skin-care/attitude-super-leaves-hand-soap/1.webp', 0),
    ('슈퍼리브스 핸드솝', 'https://cdn.dummyjson.com/product-images/skin-care/attitude-super-leaves-hand-soap/2.webp', 1),
    ('시어버터 모이스처 바디워시', 'https://cdn.dummyjson.com/product-images/skin-care/olay-ultra-moisture-shea-butter-body-wash/1.webp', 0),
    ('맨즈 바디앤페이스 로션', 'https://cdn.dummyjson.com/product-images/skin-care/vaseline-men-body-and-face-lotion/1.webp', 0),
    ('침대 프레임(퀸)', 'https://cdn.dummyjson.com/product-images/furniture/annibale-colombo-bed/1.webp', 0),
    ('침대 프레임(퀸)', 'https://cdn.dummyjson.com/product-images/furniture/annibale-colombo-bed/2.webp', 1),
    ('업홀스터리 3인 소파', 'https://cdn.dummyjson.com/product-images/furniture/annibale-colombo-sofa/1.webp', 0),
    ('체리목 협탁', 'https://cdn.dummyjson.com/product-images/furniture/bedside-table-african-cherry/1.webp', 0),
    ('이그제큐티브 오피스 체어', 'https://cdn.dummyjson.com/product-images/furniture/knoll-saarinen-executive-conference-chair/1.webp', 0),
    ('원목 세면대 & 거울 세트', 'https://cdn.dummyjson.com/product-images/furniture/wooden-bathroom-sink-with-mirror/1.webp', 0),
    ('장식용 그네 소품', 'https://cdn.dummyjson.com/product-images/home-decoration/decoration-swing/1.webp', 0),
    ('패밀리 포토 프레임', 'https://cdn.dummyjson.com/product-images/home-decoration/family-tree-photo-frame/1.webp', 0),
    ('조화 인테리어 화분', 'https://cdn.dummyjson.com/product-images/home-decoration/house-showpiece-plant/1.webp', 0),
    ('토분 화분', 'https://cdn.dummyjson.com/product-images/home-decoration/plant-pot/1.webp', 0),
    ('테이블 스탠드 조명', 'https://cdn.dummyjson.com/product-images/home-decoration/table-lamp/1.webp', 0),
    ('대나무 뒤집개', 'https://cdn.dummyjson.com/product-images/kitchen-accessories/bamboo-spatula/1.webp', 0),
    ('블랙 알루미늄 컵', 'https://cdn.dummyjson.com/product-images/kitchen-accessories/black-aluminium-cup/1.webp', 0),
    ('블랙 거품기', 'https://cdn.dummyjson.com/product-images/kitchen-accessories/black-whisk/1.webp', 0),
    ('박스형 블렌더', 'https://cdn.dummyjson.com/product-images/kitchen-accessories/boxed-blender/1.webp', 0),
    ('탄소강 웍', 'https://cdn.dummyjson.com/product-images/kitchen-accessories/carbon-steel-wok/1.webp', 0),
    ('원목 도마', 'https://cdn.dummyjson.com/product-images/kitchen-accessories/chopping-board/1.webp', 0)
) AS img(product_name, image_url, sort_order)
ON img.product_name = p.product_name;

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

-- ── 정산 대시보드 로컬 확인용: 이미 확정 정산까지 끝난 공동구매 3건 ────────────
-- SettlementConfirmationWorker(@Scheduled 배치)를 거치지 않고 확정 결과(settlement/settlement_item)를
-- 직접 심는다 - Kafka 성사 이벤트 → PG 승인 → 배치 확정까지 전체 파이프라인을 로컬에서 돌리지 않고도
-- /producer/settlements 대시보드를 바로 확인하기 위함. finalized_at은 전부 유예기간(기본 3일)보다
-- 오래전이라, 실제로 배치를 돌려도 이미 확정된 것으로 간주된다(NOT EXISTS(settlement)라 재확정 안 됨)
INSERT INTO group_buy (product_id, producer_id, title, status, start_at, end_at, min_quantity, max_quantity, current_quantity, finalized_at)
SELECT p.id, p.seller_id, g.title, 'SUCCESS'::group_buy_status, g.start_at, g.end_at, g.min_q, g.max_q, g.cur_q, g.finalized_at
FROM product p
JOIN (VALUES
    ('제주 노지 감귤 10kg', '제주 감귤 정산완료 1차', (now() AT TIME ZONE 'Asia/Seoul') - INTERVAL '20 days', (now() AT TIME ZONE 'Asia/Seoul') - INTERVAL '10 days', 5, 50, 5, (now() AT TIME ZONE 'Asia/Seoul') - INTERVAL '10 days'),
    ('해남 꿀고구마 5kg',   '꿀고구마 정산완료 2차',   (now() AT TIME ZONE 'Asia/Seoul') - INTERVAL '25 days', (now() AT TIME ZONE 'Asia/Seoul') - INTERVAL '12 days', 5, 50, 8, (now() AT TIME ZONE 'Asia/Seoul') - INTERVAL '12 days'),
    ('완도 활전복 1kg',     '활전복 정산완료 3차',     (now() AT TIME ZONE 'Asia/Seoul') - INTERVAL '15 days', (now() AT TIME ZONE 'Asia/Seoul') - INTERVAL '8 days',  3, 30, 3, (now() AT TIME ZONE 'Asia/Seoul') - INTERVAL '8 days')
) AS g(product_name, title, start_at, end_at, min_q, max_q, cur_q, finalized_at)
  ON g.product_name = p.product_name;

-- 참여자 - 그룹바이당 buyer@wellbuying.local부터 순서대로 몇 명씩 채운다 (5 / 8 / 3명)
INSERT INTO group_buy_part (group_buy_id, member_id, quantity, applied_price, status)
SELECT gb.id, m.id, 1, gp.applied_price, 'CONFIRMED'::group_buy_part_status
FROM (VALUES
    ('제주 감귤 정산완료 1차', 'buyer@wellbuying.local',  20000),
    ('제주 감귤 정산완료 1차', 'buyer2@wellbuying.local', 20000),
    ('제주 감귤 정산완료 1차', 'buyer3@wellbuying.local', 20000),
    ('제주 감귤 정산완료 1차', 'buyer4@wellbuying.local', 20000),
    ('제주 감귤 정산완료 1차', 'buyer5@wellbuying.local', 20000),
    ('꿀고구마 정산완료 2차',   'buyer@wellbuying.local',  16000),
    ('꿀고구마 정산완료 2차',   'buyer2@wellbuying.local', 16000),
    ('꿀고구마 정산완료 2차',   'buyer3@wellbuying.local', 16000),
    ('꿀고구마 정산완료 2차',   'buyer4@wellbuying.local', 16000),
    ('꿀고구마 정산완료 2차',   'buyer5@wellbuying.local', 16000),
    ('꿀고구마 정산완료 2차',   'buyer6@wellbuying.local', 16000),
    ('꿀고구마 정산완료 2차',   'buyer7@wellbuying.local', 16000),
    ('꿀고구마 정산완료 2차',   'buyer8@wellbuying.local', 16000),
    ('활전복 정산완료 3차',     'buyer@wellbuying.local',  40000),
    ('활전복 정산완료 3차',     'buyer2@wellbuying.local', 40000),
    ('활전복 정산완료 3차',     'buyer3@wellbuying.local', 40000)
) AS gp(gb_title, buyer_email, applied_price)
JOIN group_buy gb ON gb.title = gp.gb_title
JOIN members m ON m.email = gp.buyer_email;

-- settlement_item - 참여자별 정산 대상(이미 CONFIRMED, 배치를 거친 것처럼)
INSERT INTO settlement_item (group_buy_id, group_buy_participant_id, producer_id, member_id, amount, paid_at, status)
SELECT gbp.group_buy_id, gbp.id, gb.producer_id, gbp.member_id, gbp.applied_price,
       gb.finalized_at + INTERVAL '10 minutes', 'CONFIRMED'::settlement_item_status
FROM group_buy_part gbp
JOIN group_buy gb ON gb.id = gbp.group_buy_id
WHERE gb.title IN ('제주 감귤 정산완료 1차', '꿀고구마 정산완료 2차', '활전복 정산완료 3차');

-- settlement - 공동구매당 집계행 (수수료 5% 버림, SettlementConfirmationService와 동일 계산)
INSERT INTO settlement (group_buy_id, producer_id, item_count, total_sales, platform_fee, payout, status, confirmed_at)
SELECT gb.id, gb.producer_id, count(*), sum(si.amount),
       floor(sum(si.amount) * 0.05)::bigint, sum(si.amount) - floor(sum(si.amount) * 0.05)::bigint,
       'CONFIRMED'::settlement_status, gb.finalized_at + INTERVAL '1 hour'
FROM settlement_item si
JOIN group_buy gb ON gb.id = si.group_buy_id
WHERE gb.title IN ('제주 감귤 정산완료 1차', '꿀고구마 정산완료 2차', '활전복 정산완료 3차')
GROUP BY gb.id, gb.producer_id, gb.finalized_at;

-- ── 정산 대기중(PENDING) 데모용: 확정 참여자 6명 중 4명만 결제를 끝낸 공동구매 1건 ──────
-- settlement 행을 만들지 않고 settlement_item만 ACCRUED로 남겨둬 "정산 대기중" 카드/목록/진행도
-- (06-settlement-detail.md의 "몇 명 중 몇 명")를 확인할 수 있게 한다. finalized_at을 최근으로 둬서
-- 재결제 유예기간(기본 3일, repayment.grace-period-days)이 지나기 전까지는 배치가 확정하지 않는다 -
-- 유예기간이 실제로 지나면 다음 배치 실행 때 자연스럽게 CONFIRMED로 넘어간다(그때는 재시딩해서 확인)
INSERT INTO group_buy (product_id, producer_id, title, status, start_at, end_at, min_quantity, max_quantity, current_quantity, finalized_at)
SELECT p.id, p.seller_id, '제주 감귤 정산대기중 시연', 'SUCCESS'::group_buy_status,
       (now() AT TIME ZONE 'Asia/Seoul') - INTERVAL '4 days', (now() AT TIME ZONE 'Asia/Seoul') - INTERVAL '1 day',
       5, 50, 6, (now() AT TIME ZONE 'Asia/Seoul') - INTERVAL '1 day'
FROM product p WHERE p.product_name = '제주 노지 감귤 10kg';

-- 확정 참여자 6명 (공동구매 성사 시 전원 최종가 25000원 확정 완료 상태)
INSERT INTO group_buy_part (group_buy_id, member_id, quantity, applied_price, status)
SELECT gb.id, m.id, 1, 25000, 'CONFIRMED'::group_buy_part_status
FROM group_buy gb, members m
WHERE gb.title = '제주 감귤 정산대기중 시연'
  AND m.email IN ('buyer@wellbuying.local', 'buyer2@wellbuying.local', 'buyer3@wellbuying.local',
                   'buyer4@wellbuying.local', 'buyer5@wellbuying.local', 'buyer6@wellbuying.local');

-- 그중 4명만 결제 완료(settlement_item 적립) - 나머지 2명(buyer5, buyer6)은 아직 결제 전이라
-- 진행도 상세에서 "4명 / 6명"으로 미결제 인원이 남아있는 상태를 보여준다
INSERT INTO settlement_item (group_buy_id, group_buy_participant_id, producer_id, member_id, amount, paid_at, status)
SELECT gbp.group_buy_id, gbp.id, gb.producer_id, gbp.member_id, gbp.applied_price,
       gb.finalized_at + INTERVAL '10 minutes', 'ACCRUED'::settlement_item_status
FROM group_buy_part gbp
JOIN group_buy gb ON gb.id = gbp.group_buy_id
JOIN members m ON m.id = gbp.member_id
WHERE gb.title = '제주 감귤 정산대기중 시연'
  AND m.email IN ('buyer@wellbuying.local', 'buyer2@wellbuying.local', 'buyer3@wellbuying.local', 'buyer4@wellbuying.local');

-- ── 매출 추이 그래프 시연용: 위 3건(이번 달·전달) 외에 그 이전 10개월치 확정 정산을 채운다 ──
-- SettlementStatsService.getTrend는 이번 달 포함 롤링 12개월이라, 이 범위가 다 채워져 있어야
-- /producer/settlements 그래프가 1년치 막대로 그럴듯하게 보인다(hs-docs/settlement/04-dashboard-stats.md).
-- ON COMMIT DROP 임시테이블에 (제목/상품명/월초/참여자수) 계획을 먼저 만들어두고, 이후 각 INSERT에서
-- group_buy.title로 다시 조인해 참조한다 - 위 3건 블록과 같은 "title로 조인" 방식이라 스크립트
-- 안에서 두 방식이 섞이지 않는다
CREATE TEMP TABLE settlement_trend_seed (
    title text PRIMARY KEY,
    product_name text NOT NULL,
    month_start date NOT NULL,
    participant_count int NOT NULL
) ON COMMIT DROP;

-- 참여자 수를 월마다 들쭉날쭉하게 줘서(3~7명) 막대 높이에 변화를 준다. 오래된 달일수록 적고
-- 최근 달일수록 많아지는 완만한 상승 추세 + 계절감 있는 굴곡을 섞었다
INSERT INTO settlement_trend_seed (title, product_name, month_start, participant_count)
SELECT product_name || ' 정산완료 (' || to_char(month_start, 'YYYY.MM') || ')', product_name, month_start, participant_count
FROM (
    SELECT product_name, participant_count,
           (date_trunc('month', now() AT TIME ZONE 'Asia/Seoul') - (months_ago || ' months')::interval)::date AS month_start
    FROM (VALUES
        (11, '전통 방식 조청 500g',    4),
        (10, '해남 꿀고구마 5kg',      4),
        (9,  '제주 노지 감귤 10kg',    4),
        (8,  '해남 꿀고구마 5kg',      6),
        (7,  '제주 노지 감귤 10kg',    5),
        (6,  '완도 활전복 1kg',        3),
        (5,  '제주 노지 감귤 10kg',    6),
        (4,  '완도 활전복 1kg',        4),
        (3,  '제주 노지 감귤 10kg',    7),
        (2,  '완도 활전복 1kg',        5)
    ) AS v(months_ago, product_name, participant_count)
) AS raw;

INSERT INTO group_buy (product_id, producer_id, title, status, start_at, end_at, min_quantity, max_quantity, current_quantity, finalized_at)
SELECT p.id, p.seller_id, s.title, 'SUCCESS'::group_buy_status,
       s.month_start + INTERVAL '2 days', s.month_start + INTERVAL '12 days',
       3, 60, s.participant_count, s.month_start + INTERVAL '15 days'
FROM settlement_trend_seed s
JOIN product p ON p.product_name = s.product_name;

-- 참여자 - 그룹바이당 participant_count명을 buyer@~buyer8@(8명) 중 id 순서대로 앞에서부터 채운다.
-- 한 그룹바이 안의 확정 참여자는 전부 같은 최종가를 낸다(applyFinalPrice와 동일한 전제) - 상품의
-- start_price를 그대로 최종가로 쓴다
INSERT INTO group_buy_part (group_buy_id, member_id, quantity, applied_price, status)
SELECT gb.id, m.id, 1, (SELECT start_price FROM product WHERE product_name = s.product_name),
       'CONFIRMED'::group_buy_part_status
FROM settlement_trend_seed s
JOIN group_buy gb ON gb.title = s.title
JOIN LATERAL (
    SELECT id FROM members WHERE email ~ '^buyer[0-9]*@wellbuying\.local$' ORDER BY id LIMIT s.participant_count
) m ON true;

-- settlement_item - 위 3건 블록과 동일한 계산식
INSERT INTO settlement_item (group_buy_id, group_buy_participant_id, producer_id, member_id, amount, paid_at, status)
SELECT gbp.group_buy_id, gbp.id, gb.producer_id, gbp.member_id, gbp.applied_price,
       gb.finalized_at + INTERVAL '10 minutes', 'CONFIRMED'::settlement_item_status
FROM group_buy_part gbp
JOIN group_buy gb ON gb.id = gbp.group_buy_id
JOIN settlement_trend_seed s ON s.title = gb.title;

-- settlement - 공동구매당 집계행 (수수료 5% 버림, SettlementConfirmationService와 동일 계산)
INSERT INTO settlement (group_buy_id, producer_id, item_count, total_sales, platform_fee, payout, status, confirmed_at)
SELECT gb.id, gb.producer_id, count(*), sum(si.amount),
       floor(sum(si.amount) * 0.05)::bigint, sum(si.amount) - floor(sum(si.amount) * 0.05)::bigint,
       'CONFIRMED'::settlement_status, gb.finalized_at + INTERVAL '1 hour'
FROM settlement_item si
JOIN group_buy gb ON gb.id = si.group_buy_id
JOIN settlement_trend_seed s ON s.title = gb.title
GROUP BY gb.id, gb.producer_id, gb.finalized_at;

COMMIT;

SELECT 'members' AS t, count(*) FROM members
UNION ALL SELECT 'product', count(*) FROM product
UNION ALL SELECT 'product_image', count(*) FROM product_image
UNION ALL SELECT 'group_buy', count(*) FROM group_buy
UNION ALL SELECT 'group_buy_price', count(*) FROM group_buy_price
UNION ALL SELECT 'group_buy_part', count(*) FROM group_buy_part
UNION ALL SELECT 'settlement_item', count(*) FROM settlement_item
UNION ALL SELECT 'settlement', count(*) FROM settlement;
