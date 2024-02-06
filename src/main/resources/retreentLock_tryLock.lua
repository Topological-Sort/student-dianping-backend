local key = KEYS[1];
local threadId = ARGV[1];
local releaseTime = ARGV[2];
-- 判断锁是否存在
if (redis.call('exists', key) == 0) then
	-- 不存在，插入锁
	redis.call('hset', key, threadId, '1');  -- 1: state计数值
	-- 设置有效期限
	redis.call('expire', key, releaseTime);
	return 1;  -- 返回获取成功
end;
if (redis.call('hexists', key) == 1) then
    -- 已存在，获取锁，重入次数加1
    redis.call('hincrby', key, threadId, '1');
    -- 刷新有效期
    redis.call('expire', key, releaseTime);
    return 1;
end
return 0; --