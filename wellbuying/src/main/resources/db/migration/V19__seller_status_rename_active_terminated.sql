-- SellerStatus 네이밍 명확화 (Phase 13 §2-2): ACTIVE -> APPROVED, TERMINATED -> REJECTED
-- 기존 라벨 이름만 바뀌는 것이라 컬럼 DEFAULT와 기존 데이터가 자동으로 새 이름을 따라감
ALTER TYPE seller_status RENAME VALUE 'ACTIVE' TO 'APPROVED';
ALTER TYPE seller_status RENAME VALUE 'TERMINATED' TO 'REJECTED';
