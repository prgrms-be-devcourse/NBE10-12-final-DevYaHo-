-- 관리자 화면(상품 심사/판매자 심사/공동구매 판매정지/회원 관리/처리 이력) 검증용 추가 시드.
-- scripts/local-seed.sql을 먼저 실행한 뒤에 실행할 것 (거기서 만든 admin/seller/buyer 계정과
-- 카테고리에 의존한다). 재실행 가능하도록 이 스크립트가 만든 데이터만 이름/이메일 접두사로 걷어내고 다시 심는다.
--
-- 실행:
--   docker exec -i wellbuying-postgres psql -U postgres -d wellbuying < scripts/local-seed.sql
--   docker exec -i wellbuying-postgres psql -U postgres -d wellbuying < scripts/local-seed-admin-review-demo.sql
--
-- 비밀번호는 전부 testpass1234 (local-seed.sql과 동일한 BCrypt 해시)

BEGIN;

-- ── 재실행 대비 정리 (FK 역순) ─────────────────────────
DELETE FROM admin_action_log WHERE reason LIKE '[demo]%';
DELETE FROM group_buy_suspension_request WHERE reason LIKE '[demo]%';
DELETE FROM settlement WHERE group_buy_id IN (SELECT id FROM group_buy WHERE title LIKE '정산 페이지네이션 데모 %');
DELETE FROM settlement_item WHERE group_buy_id IN (SELECT id FROM group_buy WHERE title LIKE '정산 페이지네이션 데모 %');
DELETE FROM group_buy_part WHERE group_buy_id IN (SELECT id FROM group_buy WHERE title LIKE '정산 페이지네이션 데모 %');
DELETE FROM group_buy WHERE title LIKE '판매정지 데모 공동구매 %' OR title LIKE '정산 페이지네이션 데모 %';
DELETE FROM product WHERE product_name LIKE '검토대기 데모 상품 %'
    OR product_name LIKE '반려 데모 상품 %'
    OR product_name LIKE '판매정지 데모용 상품 %'
    OR product_name LIKE '정산 데모 상품 %';
DELETE FROM seller_info WHERE member_id IN (
    SELECT id FROM members WHERE email LIKE 'seller-pending%@wellbuying.local'
        OR email LIKE 'seller-rejected%@wellbuying.local'
        OR email LIKE 'seller-suspended%@wellbuying.local'
        OR email LIKE 'seller-approved-extra%@wellbuying.local'
);
DELETE FROM members WHERE email LIKE 'seller-pending%@wellbuying.local'
    OR email LIKE 'seller-rejected%@wellbuying.local'
    OR email LIKE 'seller-suspended%@wellbuying.local'
    OR email LIKE 'seller-approved-extra%@wellbuying.local'
    OR email ~ '^buyer(9|1[0-9]|2[0-4])@wellbuying\.local$';

-- ── 회원 목록 페이지네이션 확인용 구매자 16명 (buyer9~buyer24) ──
INSERT INTO members (email, name, password, role, status, phone_number)
SELECT 'buyer' || n || '@wellbuying.local', '구매자' || n,
       '$2a$10$OUFf10W6pb9Mgw7dvR7PReBPojfyMinqwoMQHECiGOkHCF6db9uu.', 'BUYER', 'ACTIVE',
       '010-0000-20' || lpad(n::text, 2, '0')
FROM generate_series(9, 24) AS n;

-- ── 판매자 전환 심사: 승인 대기 12명 (역할은 아직 BUYER) ──
INSERT INTO members (email, name, password, role, status, phone_number)
SELECT 'seller-pending' || n || '@wellbuying.local', '심사대기판매자' || n,
       '$2a$10$OUFf10W6pb9Mgw7dvR7PReBPojfyMinqwoMQHECiGOkHCF6db9uu.', 'BUYER', 'ACTIVE',
       '010-0001-00' || lpad(n::text, 2, '0')
FROM generate_series(1, 12) AS n;

INSERT INTO seller_info (member_id, bank_code, bank_name, account_number, account_holder, company_name, status, rank, settlement_cycle)
SELECT m.id, '088', '신한은행', '110' || lpad(n::text, 10, '0'), '심사대기판매자' || n,
       '데모심사대기상회' || n, 'PENDING', 'SILVER', 'MONTHLY'
FROM generate_series(1, 12) AS n
JOIN members m ON m.email = 'seller-pending' || n || '@wellbuying.local';

-- ── 판매자 전환 심사: 거절됨 12명 (역할은 BUYER 유지, 거절 이력 로그 포함) ──
INSERT INTO members (email, name, password, role, status, phone_number)
SELECT 'seller-rejected' || n || '@wellbuying.local', '거절판매자' || n,
       '$2a$10$OUFf10W6pb9Mgw7dvR7PReBPojfyMinqwoMQHECiGOkHCF6db9uu.', 'BUYER', 'ACTIVE',
       '010-0002-00' || lpad(n::text, 2, '0')
FROM generate_series(1, 12) AS n;

INSERT INTO seller_info (member_id, bank_code, bank_name, account_number, account_holder, company_name, status, rank, settlement_cycle)
SELECT m.id, '088', '신한은행', '110' || lpad((100 + n)::text, 10, '0'), '거절판매자' || n,
       '데모거절상회' || n, 'REJECTED', 'SILVER', 'MONTHLY'
FROM generate_series(1, 12) AS n
JOIN members m ON m.email = 'seller-rejected' || n || '@wellbuying.local';

INSERT INTO admin_action_log (target_type, target_id, admin_id, action, reason, occurred_at)
SELECT 'SELLER_INFO', si.id, admin.id, 'REJECT', '[demo] 서류 미비로 반려', (now() AT TIME ZONE 'Asia/Seoul') - (n || ' hours')::interval
FROM generate_series(1, 12) AS n
JOIN members m ON m.email = 'seller-rejected' || n || '@wellbuying.local'
JOIN seller_info si ON si.member_id = m.id
CROSS JOIN (SELECT id FROM members WHERE email = 'admin@wellbuying.xyz') admin;

-- ── 판매자 관리: 정지됨 12명 (한때 승인 -> 이후 정지, 승인/정지 이력 둘 다 남김) ──
INSERT INTO members (email, name, password, role, status, phone_number)
SELECT 'seller-suspended' || n || '@wellbuying.local', '정지판매자' || n,
       '$2a$10$OUFf10W6pb9Mgw7dvR7PReBPojfyMinqwoMQHECiGOkHCF6db9uu.', 'SELLER', 'ACTIVE',
       '010-0003-00' || lpad(n::text, 2, '0')
FROM generate_series(1, 12) AS n;

INSERT INTO seller_info (member_id, bank_code, bank_name, account_number, account_holder, company_name, status, rank, settlement_cycle, approved_at)
SELECT m.id, '004', '국민은행', '110' || lpad((200 + n)::text, 10, '0'), '정지판매자' || n,
       '데모정지상회' || n, 'SUSPENDED', 'GOLD', 'MONTHLY', (now() AT TIME ZONE 'Asia/Seoul') - INTERVAL '20 days'
FROM generate_series(1, 12) AS n
JOIN members m ON m.email = 'seller-suspended' || n || '@wellbuying.local';

INSERT INTO admin_action_log (target_type, target_id, admin_id, action, reason, occurred_at)
SELECT 'SELLER_INFO', si.id, admin.id, 'APPROVE', '[demo] 서류 확인 완료로 승인', (now() AT TIME ZONE 'Asia/Seoul') - INTERVAL '20 days'
FROM members m
JOIN seller_info si ON si.member_id = m.id
CROSS JOIN (SELECT id FROM members WHERE email = 'admin@wellbuying.xyz') admin
WHERE m.email LIKE 'seller-suspended%@wellbuying.local';

INSERT INTO admin_action_log (target_type, target_id, admin_id, action, reason, occurred_at)
SELECT 'SELLER_INFO', si.id, admin.id, 'SUSPEND', '[demo] 반복된 배송 지연 신고로 정지', (now() AT TIME ZONE 'Asia/Seoul') - (n || ' hours')::interval
FROM generate_series(1, 12) AS n
JOIN members m ON m.email = 'seller-suspended' || n || '@wellbuying.local'
JOIN seller_info si ON si.member_id = m.id
CROSS JOIN (SELECT id FROM members WHERE email = 'admin@wellbuying.xyz') admin;

-- ── 판매자 관리: 승인됨 추가 12명 (기존 seller@wellbuying.local 1명 + 이 12명 = 13명) ──
INSERT INTO members (email, name, password, role, status, phone_number)
SELECT 'seller-approved-extra' || n || '@wellbuying.local', '승인판매자' || n,
       '$2a$10$OUFf10W6pb9Mgw7dvR7PReBPojfyMinqwoMQHECiGOkHCF6db9uu.', 'SELLER', 'ACTIVE',
       '010-0004-00' || lpad(n::text, 2, '0')
FROM generate_series(1, 12) AS n;

INSERT INTO seller_info (member_id, bank_code, bank_name, account_number, account_holder, company_name, status, rank, settlement_cycle, approved_at)
SELECT m.id, '004', '국민은행', '110' || lpad((300 + n)::text, 10, '0'), '승인판매자' || n,
       '데모승인상회' || n, 'APPROVED', 'SILVER', 'MONTHLY', (now() AT TIME ZONE 'Asia/Seoul') - INTERVAL '5 days'
FROM generate_series(1, 12) AS n
JOIN members m ON m.email = 'seller-approved-extra' || n || '@wellbuying.local';

INSERT INTO admin_action_log (target_type, target_id, admin_id, action, reason, occurred_at)
SELECT 'SELLER_INFO', si.id, admin.id, 'APPROVE', '[demo] 서류 확인 완료로 승인', (now() AT TIME ZONE 'Asia/Seoul') - INTERVAL '5 days'
FROM members m
JOIN seller_info si ON si.member_id = m.id
CROSS JOIN (SELECT id FROM members WHERE email = 'admin@wellbuying.xyz') admin
WHERE m.email LIKE 'seller-approved-extra%@wellbuying.local';

-- ── 상품 심사: 검토대기 12건 (전체 상품목록 탭에서는 안 보여야 함 - 등록 심사 탭에서만) ──
INSERT INTO product (seller_id, category_id, product_name, description, start_price, thumbnail_url, status)
SELECT seller.id, c.id, '검토대기 데모 상품 ' || n, '관리자 검토 대기 상태 확인용 데모 상품입니다.', 10000 + n * 500,
       'https://picsum.photos/seed/pending' || n || '/600/400', 'PENDING'
FROM generate_series(1, 12) AS n
JOIN (SELECT id FROM members WHERE email = 'seller@wellbuying.local') seller ON true
JOIN LATERAL (SELECT id FROM product_category ORDER BY id LIMIT 1 OFFSET (n % 6)) c ON true;

-- ── 상품 심사: 반려 12건 (반려 사유 이력 포함) ──
INSERT INTO product (seller_id, category_id, product_name, description, start_price, thumbnail_url, status)
SELECT seller.id, c.id, '반려 데모 상품 ' || n, '관리자 반려 처리 확인용 데모 상품입니다.', 8000 + n * 400,
       'https://picsum.photos/seed/rejected' || n || '/600/400', 'REJECTED'
FROM generate_series(1, 12) AS n
JOIN (SELECT id FROM members WHERE email = 'seller@wellbuying.local') seller ON true
JOIN LATERAL (SELECT id FROM product_category ORDER BY id LIMIT 1 OFFSET (n % 6)) c ON true;

INSERT INTO admin_action_log (target_type, target_id, admin_id, action, reason, occurred_at)
SELECT 'PRODUCT', p.id, admin.id, 'REJECT', '[demo] 상품 설명 부실로 반려', (now() AT TIME ZONE 'Asia/Seoul') - (n || ' hours')::interval
FROM generate_series(1, 12) AS n
JOIN product p ON p.product_name = '반려 데모 상품 ' || n
CROSS JOIN (SELECT id FROM members WHERE email = 'admin@wellbuying.xyz') admin;

-- 처리 이력 탭이 승인/반려를 함께 보여주므로(액션 타입 필터 없음), 기존에 이미 APPROVED로
-- 심어진 상품 12개에 대해서도 승인 이력을 소급해서 남겨 목록이 20건을 넘도록(페이지네이션 확인용) 채운다
INSERT INTO admin_action_log (target_type, target_id, admin_id, action, reason, occurred_at)
SELECT 'PRODUCT', p.id, admin.id, 'APPROVE', '[demo] 상품 정보 확인 완료로 승인', p.created_at + INTERVAL '1 hour'
FROM (SELECT id, created_at FROM product WHERE status = 'APPROVED' ORDER BY id LIMIT 12) p
CROSS JOIN (SELECT id FROM members WHERE email = 'admin@wellbuying.xyz') admin;

-- ── 공동구매 판매정지 심사용 상품/공동구매 12쌍 ──
INSERT INTO product (seller_id, category_id, product_name, description, start_price, thumbnail_url, status)
SELECT seller.id, c.id, '판매정지 데모용 상품 ' || n, '판매정지 요청 데모용 공동구매에 연결된 상품입니다.', 15000 + n * 300,
       'https://picsum.photos/seed/suspenddemo' || n || '/600/400', 'APPROVED'
FROM generate_series(1, 12) AS n
JOIN (SELECT id FROM members WHERE email = 'seller@wellbuying.local') seller ON true
JOIN LATERAL (SELECT id FROM product_category ORDER BY id LIMIT 1 OFFSET (n % 6)) c ON true;

INSERT INTO group_buy (product_id, producer_id, title, status, start_at, end_at, min_quantity, max_quantity, current_quantity)
SELECT p.id, p.seller_id, '판매정지 데모 공동구매 ' || n, 'ONGOING',
       (now() AT TIME ZONE 'Asia/Seoul') - INTERVAL '3 days', (now() AT TIME ZONE 'Asia/Seoul') + INTERVAL '5 days',
       5, 50, 3 + n
FROM generate_series(1, 12) AS n
JOIN product p ON p.product_name = '판매정지 데모용 상품 ' || n;

-- 공동구매당 3건씩(대기/승인됨/반려됨) 심어서 판매정지 심사 탭 3개 상태를 전부 12건 이상으로 채운다
INSERT INTO group_buy_suspension_request (group_buy_id, requester_id, reason, status, requested_at, decided_at)
SELECT gb.id, gb.producer_id, '[demo] 재고 부족으로 판매정지 요청', 'PENDING',
       (now() AT TIME ZONE 'Asia/Seoul') - (n || ' hours')::interval, NULL
FROM generate_series(1, 12) AS n
JOIN group_buy gb ON gb.title = '판매정지 데모 공동구매 ' || n;

INSERT INTO group_buy_suspension_request (group_buy_id, requester_id, reason, status, requested_at, decided_at)
SELECT gb.id, gb.producer_id, '[demo] 배송 지연으로 판매정지 요청 (승인 이력용)', 'APPROVED',
       (now() AT TIME ZONE 'Asia/Seoul') - INTERVAL '10 days', (now() AT TIME ZONE 'Asia/Seoul') - INTERVAL '9 days'
FROM generate_series(1, 12) AS n
JOIN group_buy gb ON gb.title = '판매정지 데모 공동구매 ' || n;

INSERT INTO group_buy_suspension_request (group_buy_id, requester_id, reason, status, requested_at, decided_at)
SELECT gb.id, gb.producer_id, '[demo] 단순 변심으로 판매정지 요청 (반려 이력용)', 'REJECTED',
       (now() AT TIME ZONE 'Asia/Seoul') - INTERVAL '15 days', (now() AT TIME ZONE 'Asia/Seoul') - INTERVAL '14 days'
FROM generate_series(1, 12) AS n
JOIN group_buy gb ON gb.title = '판매정지 데모 공동구매 ' || n;

INSERT INTO admin_action_log (target_type, target_id, admin_id, action, reason, occurred_at)
SELECT 'GROUP_BUY_SUSPENSION_REQUEST', sr.id, admin.id, 'APPROVE', '[demo] 배송 지연 확인되어 승인', sr.decided_at
FROM group_buy_suspension_request sr
CROSS JOIN (SELECT id FROM members WHERE email = 'admin@wellbuying.xyz') admin
WHERE sr.status = 'APPROVED' AND sr.reason LIKE '[demo]%';

INSERT INTO admin_action_log (target_type, target_id, admin_id, action, reason, occurred_at)
SELECT 'GROUP_BUY_SUSPENSION_REQUEST', sr.id, admin.id, 'REJECT', '[demo] 단순 변심은 정지 사유 아님으로 반려', sr.decided_at
FROM group_buy_suspension_request sr
CROSS JOIN (SELECT id FROM members WHERE email = 'admin@wellbuying.xyz') admin
WHERE sr.status = 'REJECTED' AND sr.reason LIKE '[demo]%';

-- ── /producer/settlements 페이지네이션(페이지당 10건) 확인용: 이번 달 확정 정산 11건 ──
-- local-seed.sql의 확정 정산 3건은 서로 다른 달에 흩어져 있어 특정 월 조회 시 항상 10건 미만이라
-- 페이지네이션이 보이지 않는다. 이번 달에만 11건을 몰아 넣어 2페이지 이상 나오게 한다.
INSERT INTO product (seller_id, category_id, product_name, description, start_price, thumbnail_url, status)
SELECT seller.id, c.id, '정산 데모 상품 ' || n, '정산 목록 페이지네이션 데모용 상품입니다.', 9000 + n * 200,
       'https://picsum.photos/seed/settledemo' || n || '/600/400', 'APPROVED'
FROM generate_series(1, 11) AS n
JOIN (SELECT id FROM members WHERE email = 'seller@wellbuying.local') seller ON true
JOIN LATERAL (SELECT id FROM product_category ORDER BY id LIMIT 1 OFFSET (n % 6)) c ON true;

INSERT INTO group_buy (product_id, producer_id, title, status, start_at, end_at, min_quantity, max_quantity, current_quantity, finalized_at)
SELECT p.id, p.seller_id, '정산 페이지네이션 데모 ' || n, 'SUCCESS',
       (now() AT TIME ZONE 'Asia/Seoul') - (n + 3 || ' days')::interval,
       (now() AT TIME ZONE 'Asia/Seoul') - (n + 1 || ' days')::interval,
       1, 10, 1, (now() AT TIME ZONE 'Asia/Seoul') - (n || ' days')::interval
FROM generate_series(1, 11) AS n
JOIN product p ON p.product_name = '정산 데모 상품 ' || n;

INSERT INTO group_buy_part (group_buy_id, member_id, quantity, applied_price, status)
SELECT gb.id, buyer.id, 1, (SELECT start_price FROM product WHERE id = gb.product_id), 'CONFIRMED'
FROM generate_series(1, 11) AS n
JOIN group_buy gb ON gb.title = '정산 페이지네이션 데모 ' || n
CROSS JOIN (SELECT id FROM members WHERE email = 'buyer@wellbuying.local') buyer;

INSERT INTO settlement_item (group_buy_id, group_buy_participant_id, producer_id, member_id, amount, paid_at, status)
SELECT gbp.group_buy_id, gbp.id, gb.producer_id, gbp.member_id, gbp.applied_price,
       gb.finalized_at + INTERVAL '10 minutes', 'CONFIRMED'
FROM group_buy_part gbp
JOIN group_buy gb ON gb.id = gbp.group_buy_id
WHERE gb.title LIKE '정산 페이지네이션 데모 %';

INSERT INTO settlement (group_buy_id, producer_id, item_count, total_sales, platform_fee, payout, status, confirmed_at)
SELECT gb.id, gb.producer_id, count(*), sum(si.amount),
       floor(sum(si.amount) * 0.05)::bigint, sum(si.amount) - floor(sum(si.amount) * 0.05)::bigint,
       'CONFIRMED', gb.finalized_at + INTERVAL '1 hour'
FROM settlement_item si
JOIN group_buy gb ON gb.id = si.group_buy_id
WHERE gb.title LIKE '정산 페이지네이션 데모 %'
GROUP BY gb.id, gb.producer_id, gb.finalized_at;

COMMIT;

SELECT 'members(total)' AS t, count(*) FROM members
UNION ALL SELECT 'product(PENDING)', count(*) FROM product WHERE status = 'PENDING'
UNION ALL SELECT 'product(REJECTED)', count(*) FROM product WHERE status = 'REJECTED'
UNION ALL SELECT 'product(APPROVED)', count(*) FROM product WHERE status = 'APPROVED'
UNION ALL SELECT 'seller_info(PENDING)', count(*) FROM seller_info WHERE status = 'PENDING'
UNION ALL SELECT 'seller_info(REJECTED)', count(*) FROM seller_info WHERE status = 'REJECTED'
UNION ALL SELECT 'seller_info(SUSPENDED)', count(*) FROM seller_info WHERE status = 'SUSPENDED'
UNION ALL SELECT 'seller_info(APPROVED)', count(*) FROM seller_info WHERE status = 'APPROVED'
UNION ALL SELECT 'group_buy(total)', count(*) FROM group_buy
UNION ALL SELECT 'group_buy_suspension_request(PENDING)', count(*) FROM group_buy_suspension_request WHERE status = 'PENDING'
UNION ALL SELECT 'group_buy_suspension_request(APPROVED)', count(*) FROM group_buy_suspension_request WHERE status = 'APPROVED'
UNION ALL SELECT 'group_buy_suspension_request(REJECTED)', count(*) FROM group_buy_suspension_request WHERE status = 'REJECTED'
UNION ALL SELECT 'admin_action_log(PRODUCT)', count(*) FROM admin_action_log WHERE target_type = 'PRODUCT'
UNION ALL SELECT 'admin_action_log(SELLER_INFO)', count(*) FROM admin_action_log WHERE target_type = 'SELLER_INFO'
UNION ALL SELECT 'admin_action_log(GROUP_BUY_SUSPENSION_REQUEST)', count(*) FROM admin_action_log WHERE target_type = 'GROUP_BUY_SUSPENSION_REQUEST'
UNION ALL SELECT 'settlement(this-month-demo)', count(*) FROM settlement WHERE group_buy_id IN (SELECT id FROM group_buy WHERE title LIKE '정산 페이지네이션 데모 %');
