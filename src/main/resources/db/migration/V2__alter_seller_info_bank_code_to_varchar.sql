-- bank_code가 INT였던 탓에 "088"(신한은행)처럼 앞자리 0이 있는 은행 코드가 88로 잘려 저장되는 문제를 막기 위해 VARCHAR로 변경
ALTER TABLE seller_info
    ALTER COLUMN bank_code TYPE VARCHAR(10) USING bank_code::text;
