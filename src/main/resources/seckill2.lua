local voucherId = KEYS[1];
local userId = ARGV[1];
local orderId = ARGV[2];
local stockKey = "seckill:stock:" .. voucherId;
local voucherSetKey = "voucher:set:" .. voucherId

-- 秒杀券
if(redis.call("exists", stockKey) == 0) then
    return 1;
end
-- 库存
if(tonumber(redis.call("get", stockKey)) <= 0) then
    return 2;
end
-- 一人一单
if(redis.call('sismember', voucherSetKey, userId) == 1) then
    return 3;
end

-- 校验通过，可以创建订单
redis.call('incrby', stockKey, -1);         -- 扣减库存
redis.call('sadd', voucherSetKey, userId);  -- 标记该用户已购买该秒杀券

return 0;  -- 购买成功，返回