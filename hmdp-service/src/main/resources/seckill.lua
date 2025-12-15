-- KEYS
-- 1 stockKey
-- 2 orderKey
-- 3 userCountKey
-- 4 outboxKey
-- 5 requestKey
-- 6 statusKey

-- ARGV
-- 1 voucherId
-- 2 userId
-- 3 orderId
-- 4 limitType
-- 5 userLimit
-- 6 count
-- 7 payload

local stockKey = KEYS[1]
local orderKey = KEYS[2]
local userCountKey = KEYS[3]
local outboxKey = KEYS[4]
local requestKey = KEYS[5]
local statusKey = KEYS[6]

local voucherId = ARGV[1]
local userId = ARGV[2]
local orderId = ARGV[3]
local limitType = tonumber(ARGV[4])
local userLimit = tonumber(ARGV[5])
local count = tonumber(ARGV[6])
local payload = ARGV[7]

if count == nil or count < 1 then
    count = 1
end

-- 幂等：同一个 req_id 多次调用直接视为成功
if(redis.call('exists', requestKey) == 1) then
    return 0
end

-- 3.脚本业务
-- 3.1判断库存是否充足
local stock = redis.call('get', stockKey)
if(not stock or tonumber(stock) < count)then
    local fail = cjson.encode({status="FAILED", reason="STOCK", voucherId=voucherId, userId=userId, count=count})
    redis.call('set', statusKey, fail, 'EX', 1800)
    redis.call('setex', requestKey, 1800, 'FAIL')
    return 1
end
-- 3.2根据限购类型判断用户是否可下单
if(limitType == 1) then
    if(count > 1) then
        local fail = cjson.encode({status="FAILED", reason="LIMIT", voucherId=voucherId, userId=userId, count=count})
        redis.call('set', statusKey, fail, 'EX', 1800)
        redis.call('setex', requestKey, 1800, 'FAIL')
        return 2
    end
    if(redis.call('sismember',orderKey,userId) == 1) then
        local fail = cjson.encode({status="FAILED", reason="LIMIT", voucherId=voucherId, userId=userId, count=count})
        redis.call('set', statusKey, fail, 'EX', 1800)
        redis.call('setex', requestKey, 1800, 'FAIL')
        return 2
    end
elseif(limitType == 3) then
    local used = redis.call('hget', userCountKey, userId)
    if(used == false) then
        used = 0
    end
    if((tonumber(used) + count) > userLimit) then
        local fail = cjson.encode({status="FAILED", reason="LIMIT", voucherId=voucherId, userId=userId, count=count})
        redis.call('set', statusKey, fail, 'EX', 1800)
        redis.call('setex', requestKey, 1800, 'FAIL')
        return 2
    end
end
-- 3.4扣库存
redis.call('incrby',stockKey,-count)
-- 3.5下单并保存用户限购信息
if(limitType == 1) then
    redis.call('sadd',orderKey,userId)
elseif(limitType == 3) then
    redis.call('hincrby', userCountKey, userId, count)
end
-- 3.6 记录请求ID，防止重试重复扣减
redis.call('set', requestKey, orderId, 'EX', 1800)
-- 3.7 预写入状态，供前端轮询（由消费者覆盖）
local pending = cjson.encode({status="PENDING", orderId=orderId, voucherId=voucherId, userId=userId, count=count})
redis.call('set', statusKey, pending, 'EX', 1800)
-- 3.6写入一级缓冲队列
redis.call('lpush', outboxKey, payload)
return 0
