local lockKey = KEYS[1]
local threadId = ARGV[1]

if (redis.call('get', lockKey) == threadId) then
    return redis.call('del', lockKey)
end
return 0