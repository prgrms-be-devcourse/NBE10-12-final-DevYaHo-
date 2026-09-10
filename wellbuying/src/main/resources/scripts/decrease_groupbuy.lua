-- KEYS[1] = 공동구매 카운터 키 (gb:cnt:{groupBuyId})
-- ARGV[1] = 되돌릴 수량
-- 참여 취소 또는 DB 반영 실패 보상 처리에 쓰인다. 단순 DECRBY는 키가 이미 지워진 뒤(성사 확정 시
-- delete()됨) 호출되면 음수 값으로 키를 새로 만들어버리고, 그 음수를 participate_groupbuy.lua가
-- 그대로 신뢰해 재고 초과 허용으로 이어질 수 있다 - 키가 없으면 아무것도 하지 않고, 있어도 0 밑으로는
-- 내려가지 않게 막는다
if redis.call('EXISTS', KEYS[1]) == 0 then
    return nil
end

local newValue = redis.call('DECRBY', KEYS[1], ARGV[1])
if newValue < 0 then
    redis.call('SET', KEYS[1], 0, 'KEEPTTL')
    return 0
end

return newValue
