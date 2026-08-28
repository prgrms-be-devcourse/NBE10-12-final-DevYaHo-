-- V15에서 추가한 PENDING이 이제 별도 트랜잭션에서 커밋된 상태이므로 안전하게 DEFAULT로 지정 가능
-- PostgreSQL은 같은 트랜잭션 안에서 방금 ADD VALUE한 enum 값을 바로 사용하지 못하므로 파일을 분리
ALTER TABLE product ALTER COLUMN status SET DEFAULT 'PENDING';
