-- Sliding-window rate limiter (EDD §11.3C 限流鍵設計 — 滑動視窗 Lua，原子)
-- KEYS[1] = rate key ({env}:rate:{action}:{identifier})
-- ARGV[1] = now (ms)   ARGV[2] = window (ms)   ARGV[3] = limit
-- Returns 1 if the request is allowed (and records it), 0 if the limit is exceeded.
local key = KEYS[1]
local now = tonumber(ARGV[1]); local window = tonumber(ARGV[2]); local limit = tonumber(ARGV[3])
redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
local count = redis.call('ZCARD', key)
if count < limit then
  redis.call('ZADD', key, now, now); redis.call('PEXPIRE', key, window); return 1
end
return 0
