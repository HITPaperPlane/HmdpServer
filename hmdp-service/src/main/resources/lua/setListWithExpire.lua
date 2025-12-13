-- 原子地设置List并添加过期时间
-- KEYS[1]: Redis key
-- ARGV[1]: TTL (秒)
-- ARGV[2..n]: List元素

redis.call('DEL', KEYS[1])  -- 先删除旧数据
for i = 2, #ARGV do
    redis.call('RPUSH', KEYS[1], ARGV[i])
end
redis.call('EXPIRE', KEYS[1], ARGV[1])
return 'OK'
