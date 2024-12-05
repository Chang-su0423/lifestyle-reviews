package com.hmdp.service.impl;
import com.hmdp.config.redisConfig.redisLog.RedisLogUtils;
import com.hmdp.dto.Result;
import com.hmdp.dto.SecKillOrderDTO;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIDWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.concurrent.ListenableFutureCallback;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */

@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService service;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    //注入自身，使用带有事务的方法用该对象调用，防止事务失效
    @Autowired
    private IVoucherOrderService voucherOrderService;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private ISeckillVoucherService seckillVoucherService;


    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    private static final Logger logger = Logger.getLogger(RedisLogUtils.class.getName());

//废弃方案------------------------------------------------------------------------------------------------------
   /* @Autowired                                                                                              |
    private RedissonClient redisson;*/
    //private static final ExecutorService SECKILL_EXECUTOR = Executors.newSingleThreadExecutor();
    //private BlockingQueue<VoucherOrder> voucherOrderBlockingQueue=new ArrayBlockingQueue<>(1024*1024);

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setResultType(Long.class);
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
    }

    @Transactional
    @RabbitListener(queues = "sekill_queue")
    public void processMessage(SecKillOrderDTO orderDTO) {
        Long orderId = orderDTO.getOrderId();
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderDTO.getOrderId());
        voucherOrder.setUserId(orderDTO.getUserId());
        voucherOrder.setVoucherId(orderDTO.getVoucherId());
        voucherOrderService.createVoucherOrder(voucherOrder);
        seckillVoucherService.update().eq("voucher_id",orderDTO.getVoucherId()).setSql("set stock = stock-1");
    }


    public void sendMessageToRabbitMQ(List<Long> result, int reTryCount) {
        //设置最大重试次数
        int maxRetryCount = 5;
        if (reTryCount > maxRetryCount) {
            logger.info("retry times beyond max retry times");
            return;
        }
        //发送前配置回调函数，进行发送消息业务确认
        CorrelationData cd = new CorrelationData();
        cd.getFuture().addCallback(new ListenableFutureCallback<CorrelationData.Confirm>() {
            @Override
            public void onFailure(Throwable throwable) {
                //SpringAMQP在处理future时发生异常，一般不会出现
                logger.info("send message fail" + throwable.getMessage());
            }

            @Override
            public void onSuccess(CorrelationData.Confirm confirm) {
                if (confirm.isAck()) {
                    //mq发送了ack确认
                    logger.info("send message ack success");
                } else {
                    //日志中打印NACK原因
                    logger.info("send message ack fail" + confirm.getReason());
                    //mq发送了NACK
                    //重新进行发送业务
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    sendMessageToRabbitMQ(result, reTryCount + 1);
                }
            }
        });
        SecKillOrderDTO orderDTO = new SecKillOrderDTO();
        orderDTO.setVoucherId(result.get(0));
        orderDTO.setUserId(result.get(1));
        orderDTO.setOrderId(result.get(2));
        rabbitTemplate.convertAndSend("sekill", null, orderDTO, cd);
    }


    @Override
    public Result secKillVoucher(Long voucherId) {

        //生成订单ID
        RedisIDWorker worker = new RedisIDWorker(redis);
        long id = worker.nextId("order:");

        //获取用户ID
        Long userId = UserHolder.getUser().getId();

        //获取LUA脚本执行结果
        Object LUAResult = redis.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(id)
        );

        //根据LUA脚本执行的不同结果，进行不同的操作
        if (LUAResult instanceof Long) {
            Long result = (Long) LUAResult;
            if (result == 1) {
                return Result.fail("stock not enough");
            }
            if (result == 2) {
                return Result.fail("do not order again");
            } else {
                List<Long> orderIds = new ArrayList<>();
                orderIds.add(voucherId);
                orderIds.add(userId);
                orderIds.add(id);


                //将消息放进rabbitmq中去
                sendMessageToRabbitMQ(orderIds, 0);
            }
        }


        //直接向客户端返回订单ID
        return Result.ok(id);

        //TODO 将执行的下单结果放入队列中
        //TODO 将消息放入rabbitmq中，
        //TODO 每次发送前配置回调函数，进行消息确认
        //TODO ACK ->false  进行重新发送业务
        //TODO 限制重新发送次数，保证性能
        //TODO 保证扣减库存业务具有幂等性->方案一：自动生成唯一ID   方案二：
        //TODO 生产者重试机制
        //TODO 消费者重试机制->放入另一个queue并进行邮件通知业务
        //TODO 配置lazyqueue
        //TODO springAMQP自动配置ack确认

/*
        //将订单信息封装为VoucherOrder类的对象
        RedisIDWorker worker=new RedisIDWorker();
        VoucherOrder voucherOrder=new VoucherOrder();
        voucherOrder.setVoucherId(voucherId);
        Long id=worker.nextId("secKill");
        voucherOrder.setId(id);
        voucherOrder.setUserId(UserHolder.getUser().getId());

        //将VoucherOrder对象放入阻塞队列中去
        voucherOrderBlockingQueue.add(voucherOrder);*/


    }

    @Transactional
    @Override
    public Result createVoucherOrder(VoucherOrder voucherOrder) {
        Integer userVoucherCount = this.query().eq("voucher_id", voucherOrder.getVoucherId()).eq("user_id", voucherOrder.getUserId()).count();
        if (userVoucherCount > 0) {
            return Result.ok();
        }
        //操作1：扣减库存
        boolean SUCCESS = service.update().setSql("stock = stock - 1").gt("stock", 0).eq("voucher_id", voucherOrder.getVoucherId()).update();
        if (!SUCCESS) {
            return Result.fail("no stock");
        }
        //操作2：保存订单信息
        //@transactional注解保证操作1和操作2的原子性
        this.save(voucherOrder);
        return Result.ok();
    }


    ///废弃方案--------------------------------------------------------------------------------------
    /*@PostConstruct
    private void init() {
        SECKILL_EXECUTOR.submit(new VoucherOrderHandler());
    }*/

    /*private class VoucherOrderHandler implements Runnable {

        @Override
        public void run() {
            while (true) {
                try {
                    //获取消息队列中的消息
                    List<MapRecord<String, Object, Object>> message = redis.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create("stream.orders", ReadOffset.lastConsumed())
                    );

                    //判断消息获取是否成功
                    if (message == null || message.isEmpty()) {

                        //没有消息，进入下一次循环
                        continue;
                    }

                    //有消息，调用createVoucherOrder方法创建订单
                    //解析消息中的订单信息
                    MapRecord<String, Object, Object> record = message.get(0);
                    Map<Object, Object> valueMap = record.getValue();

                    //将取出的消息封装为VoucherOrder类
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(valueMap, new VoucherOrder(), true);

                    //创建订单，保存到数据库
                    createVoucherOrder(voucherOrder);

                    //进行ACK确认
                    redis.opsForStream().acknowledge("streams.order", "g1", record.getId());
                } catch (Exception e) {

                    //调用处理异常方法
                    handleExceptionByPendingList();

                    //捕获异常
                    throw new RuntimeException(e);

                }


                //从pendingList中获取订单信息，进行ACK确认
                //若继续捕获异常，循环（从pendingList中获取订单信息，进行ACK确认）

            }
        }
    }*/



  /*  private void handleExceptionByPendingList() {
        while (true) {
            try {
                //获取消息队列中的消息
                List<MapRecord<String, Object, Object>> message = redis.opsForStream().read(
                        Consumer.from("g1", "c1"),
                        StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                        StreamOffset.create("stream.orders", ReadOffset.from("0"))
                );

                //判断消息获取是否成功
                if (message == null || message.isEmpty()) {

                    //没有消息，说明pendingList中没有数据，直接跳出当前处理异常数据循环
                    break;
                }

                //有消息，调用createVoucherOrder方法创建订单
                //解析消息中的订单信息
                MapRecord<String, Object, Object> record = message.get(0);
                Map<Object, Object> valueMap = record.getValue();

                //将取出的消息封装为VoucherOrder类
                VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(valueMap, new VoucherOrder(), true);

                //创建订单，保存到数据库
                createVoucherOrder(voucherOrder);

                //进行ACK确认
                redis.opsForStream().acknowledge("streams.order", "g1", record.getId());
            } catch (Exception e) {

                //捕获异常
                throw new RuntimeException(e);

                //再次出现异常，不用处理，继续进入当前处理异常数据while循环

            }
        }
    }*/


    //@Transactional
    //当外部方法事务未提交，内部事务方法是无法生效的
   /* @Override
    public Result secKillVoucher(Long voucherId) {
        //从数据库中查询优惠券信息
         SeckillVoucher Seckillvoucher = service.getById(voucherId);
        //判断秒杀时间是否开始和结束
        //未开始，或者已经结束，向客户端返回错误码
        if (!(Seckillvoucher.getBeginTime().isBefore(LocalDateTime.now())&&Seckillvoucher.getEndTime().isAfter(LocalDateTime.now()))) {
            return Result.fail("time incorrect");
        }
        //开始且未结束
        //判断商品库存是否为零
        //为零
        if (Seckillvoucher.getStock()<=0) {
            return Result.fail("stock incorrect");
        }
        //向客户端返回相关信息
        //不为零
        //进行购买业务
        //扣减商品库存
        //生成商品订单
        //再次检查商品库存并扣减
       *//* Integer userCount = this.query().eq("voucher_id", voucherId).eq("user_id", UserHolder.getUser().getId()).count();
        if (userCount>0) {
            return Result.fail("do not repeat order!");
        }
        boolean SUCCESS= service.update()
                .setSql("stock= stock -1")
                .eq("voucher_id", voucherId).gt("stock",0).update(); //where id = ? and stock > 0
        long id = worker.nextId("order:");
        if (!SUCCESS) {
            return  Result.fail("stock incorrect");
        }
        else {
        VoucherOrder voucherOrder=new VoucherOrder();
        voucherOrder.setVoucherId(Seckillvoucher.getVoucherId());
        voucherOrder.setId(id);
        voucherOrder.setUserId(UserHolder.getUser().getId());
        voucherOrderService.save(voucherOrder);
        }
        //向客户端返回商品订单编号
        return Result.ok(id);*//*


        //spring事物是通过代理实现的
        //调用createVoucherOder()方法是通过this.createVoucherOrder()实现的，不能够获取代理对象，事物失效
        //通过api获取当前接口的代理对象
        //加锁，保证相同userid的线程只能进入一个

        //实例化ILock对象,并且给分布式锁的key加上userid，让同id的用户的锁相同，从而防止同一用户多次下单
        Long userId = UserHolder.getUser().getId();
        //ILock lock=new SimpleRedisLock(redis,"secKill"+userId);
        RLock lock = redisson.getLock("lock:secKill:" + userId);
        //使用redisson的api提供的默认值，不在tryLock()方法中传入参数
        boolean success = lock.tryLock();
        if (!success) {
            return Result.fail("do not order again");
        }
        try {
            IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId, Seckillvoucher);
        } finally {
            lock.unlock();
        }

    }*/



    //将创建订单抽象到一个方法中去
    /*@Transactional
    public Result createVoucherOrder(Long voucherId, SeckillVoucher seckillVoucher) {

            Integer userCount = this.query().eq("voucher_id", voucherId).eq("user_id", UserHolder.getUser().getId()).count();
            if (userCount > 0) {
                return Result.fail("do not repeat order!");
        }

            //乐观锁再次查验数据库中的库存是否符合要求
            boolean SUCCESS = service.update().setSql("stock=stock-1").eq("voucher_id", voucherId).gt("stock", 0).update();
            if (!SUCCESS) {
                return Result.fail("stock incorrect");
            }
            long id = worker.nextId("order:");
            VoucherOrder order = new VoucherOrder();
            order.setVoucherId(seckillVoucher.getVoucherId());
            order.setId(id);
            order.setUserId(UserHolder.getUser().getId());
            voucherOrderService.save(order);

            return Result.ok(id);
    }*/
}
