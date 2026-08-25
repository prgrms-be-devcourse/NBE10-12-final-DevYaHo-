-- 회원당 셀러 정보는 최대 1건만 존재해야 하는데(재신청 시 기존 행을 갱신, 신규 INSERT 아님) DB 제약이 없어
-- 동시에 apply()가 두 번 호출되면 애플리케이션 레벨 체크를 뚫고 중복 INSERT될 수 있는 문제 해결
CREATE UNIQUE INDEX uk_seller_info_member_id ON seller_info (member_id);
