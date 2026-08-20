-- rotate_refresh_token.lua
-- KEYS[1] = ReT:{member_id}
-- ARGV[1] = device_id
-- ARGV[2] = 클라이언트 토큰의 SHA-256 해시
-- ARGV[3] = 새 JSON value
-- ARGV[4] = TTL(초)

local current = redis.call('HGET', KEYS[1], ARGV[1])
if not current then return 0 end                 -- 세션 없음 (만료/로그아웃)

local data = cjson.decode(current)
if data.tokenHash ~= ARGV[2] then
    redis.call('DEL', KEYS[1])                    -- 탈취 의심: 해당 계정의 모든 기기 세션 즉시 삭제
    return -1
end

redis.call('HSETEX', KEYS[1], 'EX', ARGV[4], 'FIELDS', 1, ARGV[1], ARGV[3])
return 1
