-- Redis key for the token bucket -> KEYS[1] = rate:{user_id}:{method}:{path}
local key = KEYS[1]

-- units: capacity (tokens), refill_interval (ms), now (ms)
local capacity = tonumber(ARGV[1])
local refill_interval = tonumber(ARGV[2])
local now = tonumber(ARGV[3])

local data = redis.call("HMGET", key, "tokens", "last_refill")

local tokens = tonumber(data[1])
local last_refill = tonumber(data[2])

-- If the token bucket does not exist, initialize it with full capacity and set the last refill time to now
if tokens == nil then
    tokens = capacity
    last_refill = now
end

-- Calculate the time elapsed since the last refill and determine how many new tokens to add
local elapsed = math.max(0, now - last_refill)
local new_tokens = math.floor(elapsed / refill_interval)

-- Refill the token bucket with new tokens, ensuring it does not exceed the capacity
-- Update the last refill time based on the number of new tokens added
if new_tokens > 0 then
    tokens = math.min(capacity, tokens + new_tokens)
    last_refill = last_refill + (new_tokens * refill_interval)
end

-- Check if there are enough tokens available to allow the request
-- 0 = request denied, 1 = request allowed
local allowed = 0

if tokens >= 1 then
    tokens = tokens - 1
    allowed = 1
end

-- Update the token bucket in Redis with the new token count and last refill time
redis.call("HSET", key,
    "tokens", tokens,
    "last_refill", last_refill
)

-- Set the expiration time for the token bucket
redis.call("EXPIRE", key,
    math.ceil(capacity * refill_interval / 1000 * 2)
)

return { allowed, tokens }