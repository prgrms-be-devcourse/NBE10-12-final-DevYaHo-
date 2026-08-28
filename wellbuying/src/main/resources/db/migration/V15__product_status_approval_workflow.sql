-- Product.status를 승인 워크플로우 개념으로 전환
-- ON_SALE -> APPROVED로 이름 변경 (기존 라벨 이름만 바뀌는 것이라 컬럼 DEFAULT와 기존 데이터가 자동으로 새 이름을 따라감)
ALTER TYPE product_status RENAME VALUE 'ON_SALE' TO 'APPROVED';

-- 승인 대기/거절 상태 추가
ALTER TYPE product_status ADD VALUE 'PENDING';
ALTER TYPE product_status ADD VALUE 'REJECTED';

-- SOLD_OUT 값은 PostgreSQL enum에서 완전히 제거하려면 타입 재생성이 필요해서(컬럼 재정의 필요) 지금은 그냥
-- 안 쓰는 채로 남겨둬. 실제 저장된 데이터가 없어서 문제없어.
