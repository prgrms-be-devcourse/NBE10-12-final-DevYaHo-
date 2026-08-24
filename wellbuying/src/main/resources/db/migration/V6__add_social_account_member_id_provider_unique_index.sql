-- 연동 목록/해제 조회(member_id 기준)에 인덱스가 없어 조회가 풀스캔에 의존하던 문제 해결
-- (member_id, provider) UNIQUE로 걸어 한 회원이 같은 provider를 두 번 연동하는 것을 DB 차원에서도 방지
CREATE UNIQUE INDEX uk_social_account_member_id_provider ON social_account (member_id, provider);
