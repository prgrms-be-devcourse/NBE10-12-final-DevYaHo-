-- accountNumber/accountHolder를 AES-256-GCM으로 암호화해 저장하면서 Base64 인코딩(IV+암호문)으로 인해
-- 평문보다 길어져 기존 VARCHAR(255)를 초과할 수 있음 - 여유 있게 VARCHAR(500)으로 확장
ALTER TABLE seller_info
    ALTER COLUMN account_number TYPE VARCHAR(500),
    ALTER COLUMN account_holder TYPE VARCHAR(500);
