-- 애플리케이션의 existsByGroupBuyIdAndStatus 체크만으로는 동시 요청(더블 클릭 등)에서 PENDING 요청이
-- 중복 생성될 수 있다(TOCTOU) - 부분 유니크 인덱스로 DB 레벨에서 확실히 막는다. 위반 시 발생하는
-- DataIntegrityViolationException은 GlobalExceptionHandler가 이미 409로 변환해 응답한다
CREATE UNIQUE INDEX uq_group_buy_suspension_pending
    ON group_buy_suspension_request (group_buy_id)
    WHERE status = 'PENDING';
