-- KEYS[1] = 공동구매 카운터 키 (gb:cnt:{groupBuyId})
-- ARGV[1] = 참여 신청 수량
-- ARGV[2] = 최대(재고) 수량
-- 반환값: 증가 후 누적 수량(성공) 또는 -1(재고 초과)
local current = tonumber(redis.call('GET', KEYS[1]) or '0')
local requested = tonumber(ARGV[1])
local max = tonumber(ARGV[2])

if current + requested > max then
    return -1
end

return redis.call('INCRBY', KEYS[1], requested)
