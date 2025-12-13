-- KEYS
-- 1 stockKey
-- 2 orderKey
-- 3 userCountKey
-- 4 outboxKey

-- ARGV
-- 1 voucherId
-- 2 userId
-- 3 orderId
-- 4 limitType
-- 5 userLimit

local stockKey = KEYS[1]
local orderKey = KEYS[2]
local userCountKey = KEYS[3]
local outboxKey = KEYS[4]

local voucherId = ARGV[1]
local userId = ARGV[2]
local orderId = ARGV[3]
local limitType = tonumber(ARGV[4])
local userLimit = tonumber(ARGV[5])

-- 3.脚本业务
-- 3.1判断库存是否充足
local stock = redis.call('get', stockKey)
if(not stock or tonumber(stock) <= 0)then
    -- 3.2 库存不足 返回1
    return 1
end
-- 3.2根据限购类型判断用户是否可下单
if(limitType == 1) then
    if(redis.call('sismember',orderKey,userId) == 1) then
        return 2
    end
elseif(limitType == 3) then
    local used = redis.call('hget', userCountKey, userId)
    if(used ~= false and tonumber(used) >= userLimit) then
        return 2
    end
end
-- 3.4扣库存
redis.call('incrby',stockKey,-1)
-- 3.5下单并保存用户限购信息
if(limitType == 1) then
    redis.call('sadd',orderKey,userId)
elseif(limitType == 3) then
    redis.call('hincrby', userCountKey, userId, 1)
end
-- 3.6写入一级缓冲队列
local data = cjson.encode({orderId=orderId,voucherId=voucherId,userId=userId,limitType=limitType,userLimit=userLimit})
redis.call('lpush', outboxKey, data)
return 0
