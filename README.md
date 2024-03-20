# student-dianping-back
## V3.0
### 增加使用 RabbitMQ 实现优惠券秒杀（RabbitMQVoucherOrderServiceImpl）
## V1.0
### 实现功能：
1.防止缓存穿透（缓存短时间空记录）
2.防止缓存雪崩（加随机过期时间）
3.防止缓存击穿，互斥锁/逻辑过期店铺查询
4.防止库存超卖问题：乐观锁
5.秒杀券一人一单实现：悲观锁、分布式锁
