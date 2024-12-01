--参数列表
local voucherId=ARGV[1]
local userId=ARGV[2]
local orderId=ARGV[3]

--数据key
local stockKey='secKill:stock:'..voucherId
local voucherKey='secKill:order'..voucherId

--判断Redis中储存的库存是否充足，即取出库存值
if tonumber(redis.call('GET', stockKey)) <= 0 then

    --库存不足，返回数字1
    return 1
end

--判断Redis中存储的订单信息表中是否存在当前用户
if redis.call('SISMEMBER', voucherKey, userId) == 1 then

    --存在，说明用户重复下单
    return 2
end

--可以下单，进行库存扣减
redis.call('INCRBY',stockKey,-1)

--在Redis的集合中添加订单信息，完成下单业务
redis.call('SADD',voucherKey,userId)

--判断有资格后，发送消息到stream消息队列
--redis.call('XADD','stream.orders','*','voucherId',voucherId, 'userId',userId,'id',orderId)

--判断有购买资格，将订单ID，用户ID,商品ID返回给后台
 return 3