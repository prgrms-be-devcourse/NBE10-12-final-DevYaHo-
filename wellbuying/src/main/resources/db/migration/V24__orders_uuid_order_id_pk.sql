-- ORDERS의 PK를 시퀀스 기반 bigint에서, 토스로 보내는 orderId와 같은 값(UUID 기반 문자열)으로 바꾼다.
--
-- 계산식으로 orderId를 만들면(예: 'gb-' || group_buy_participant_id) 두 가지가 동시에 깨진다.
--   1) 토스 orderId는 6~64자여야 하는데 참여 id가 한 자리면 규격에 미달한다
--   2) 승인에 성공한 orderId는 상점 단위로 재사용할 수 없는데, 로컬 DB를 초기화하면
--      같은 참여 id가 다시 나와 같은 orderId를 또 보내게 된다
-- 주문을 만들 때 한 번 생성해 저장해 두면 재시도 때는 저장된 값을 그대로 읽으므로 요청 본문이
-- 동일하게 유지되고(멱등키와 짝이 맞는다), 값 자체는 DB 초기화·환경과 무관하게 유일하다.
--
-- ORDERS를 참조하는 테이블이 없어 PK 교체의 파급이 이 테이블 안에서 끝난다.

ALTER TABLE orders ADD COLUMN order_id VARCHAR(64);

-- 기존 행(로컬 개발 데이터뿐)에도 같은 형식의 값을 채워 넣는다
UPDATE orders SET order_id = 'gb-' || gen_random_uuid() WHERE order_id IS NULL;

ALTER TABLE orders ALTER COLUMN order_id SET NOT NULL;

-- id 컬럼을 지우면 소유 시퀀스(orders_id_seq)도 함께 사라진다
ALTER TABLE orders DROP CONSTRAINT orders_pkey;
ALTER TABLE orders DROP COLUMN id;
ALTER TABLE orders ADD CONSTRAINT orders_pkey PRIMARY KEY (order_id);
