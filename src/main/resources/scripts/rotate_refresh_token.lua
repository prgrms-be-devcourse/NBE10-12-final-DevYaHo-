-- rotate_refresh_token.lua
-- KEYS[1] = ReT:{member_id}
-- ARGV[1] = device_id
-- ARGV[2] = 클라이언트가 보낸 토큰의 SHA-256 해시
-- ARGV[3] = 새로 발급한 토큰의 SHA-256 해시
-- ARGV[4] = TTL(초)
-- ARGV[5] = grace 기간(초) — 직전 토큰을 정상으로 허용할 유예 시간
-- ARGV[6] = 현재 시각(epoch seconds, 애플리케이션 서버 시각 기준)

local current = redis.call('HGET', KEYS[1], ARGV[1])
if not current then return 0 end                 -- 세션 없음 (만료/로그아웃)

local data = cjson.decode(current)
local now = tonumber(ARGV[6])

local isCurrent = (data.tokenHash == ARGV[2])
local isGracedPrevious = data.previousTokenHash
        and data.previousTokenHash == ARGV[2]
        and data.graceUntil
        and now <= tonumber(data.graceUntil)

if not (isCurrent or isGracedPrevious) then
    redis.call('DEL', KEYS[1])                    -- 진짜 탈취 의심: 해당 계정의 모든 기기 세션 즉시 삭제
    return -1
end

-- 정상 rotate이거나, grace 기간 내 벤치어스(정상) 경쟁 요청인 경우 — 둘 다 새 토큰 발급 허용
-- 직전 current였던 해시를 previousTokenHash로 넘기고, 새 grace 기간을 부여
local newValue = cjson.encode({
    tokenHash = ARGV[3],
    previousTokenHash = data.tokenHash,
    graceUntil = now + tonumber(ARGV[5]),
    issuedAt = now,
    lastUsedAt = now
})

redis.call('HSETEX', KEYS[1], 'EX', ARGV[4], 'FIELDS', 1, ARGV[1], newValue)
return 1
