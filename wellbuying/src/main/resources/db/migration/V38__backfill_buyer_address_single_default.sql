-- buyer_address.is_default가 지금까지 실제로 쓰인 적이 없어서, 회원당 여러 행이
-- is_default=true인 채로 남아있다. 회원당 가장 오래된(최소 id) 배송지 하나만 기본으로
-- 남기고 나머지는 해제한다.
UPDATE buyer_address
SET is_default = false
WHERE id NOT IN (
    SELECT MIN(id)
    FROM buyer_address
    GROUP BY member_id
);
