package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.config.redisConfig.RedisConnectionFactoryManagement;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.service.RedisPreheatService;
import com.hmdp.utils.RedisData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;

@Service
public class RedisPreheatServiceImpl implements RedisPreheatService {

    private static final Logger log = LoggerFactory.getLogger(RedisPreheatServiceImpl.class);

    @Autowired
    private ShopMapper shopMapper;

    @Autowired
    private RedisConnectionFactoryManagement management;

    private static final BlockingQueue<Shop> blockingQueue = new ArrayBlockingQueue<>(1000);

    private static final ExecutorService EXECUTOR_SERVICE = Executors.newSingleThreadExecutor();

    private static final int PAGE_SIZE = 100;

    private static final Long TIME_OUT = 1000L;

    private static final Long SLEEP_TIME = 100L;

    private static final int BATCH_SIZE = 16;

    private static final Long EXPIRE_TIMES = 2000000L;

    private static final String redisPrefix = "cache:shop:";

    private volatile Thread workerThread; // 保存写入线程的引用,方便在调用unpPark()方法时传入参数

    private volatile boolean isEnd = false;

    @PostConstruct
    public void init() {
        EXECUTOR_SERVICE.submit(new WriteIntoRedisByPipeline());
    }

    public class WriteIntoRedisByPipeline implements Runnable {

        @Override
        public void run() {
            workerThread = Thread.currentThread(); // 保存当前线程的引用
            LockSupport.park(); // 阻塞当前线程，等待唤醒
            //死循环，不断尝试从阻塞队列中获取数据
            while (true) {
                Long preTimeMillis=System.currentTimeMillis();
                List<Shop> batch = new ArrayList<>();
                while (System.currentTimeMillis() - preTimeMillis < TIME_OUT){
                    blockingQueue.drainTo(batch, BATCH_SIZE); // 批量获取数据
                    if (batch.size()>BATCH_SIZE) {
                        break;
                    }
                    try {
                        Thread.sleep(SLEEP_TIME);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                if (!batch.isEmpty()) {
                    log.info("redis data preheating: writing to redis with data in blocking queue");
                    writeToRedis(batch);
                }
                //拖过isEnd标识为true，并且阻塞队列中没有待消费的数据，跳出外层循环，线程最终死亡
                if (isEnd&&blockingQueue.isEmpty()) {
                    log.info("preheat data end...");
                    log.error(System.currentTimeMillis()+"");
                    break;
                }
            }
        }
    }

    /*调用loadDataFromMysqlToQueue()方法，将数据库中数据写入阻塞队列
    * 同时使用unPark()方法，将写入Redis的线程唤醒*/
    @Override
    public void preHeatRedisPipeline() {
        log.error(System.currentTimeMillis()+"");
        // 唤醒写入线程
        if (workerThread != null) {
            log.info("Unparking the worker thread");
            LockSupport.unpark(workerThread); // 唤醒线程
        }
        loadDataFromMysqlToQueue();
    }
    /*从数据库中分页查询数据并写入阻塞队列中*/
    private void loadDataFromMysqlToQueue() {
        int page = 0;
        while (true) {
            //对数据库中数据进行分页查询，防止一次查询数量过多造成数据库阻塞
            int offset = page * PAGE_SIZE;
            List<Shop> shopList = shopMapper.selectShopListByPage(offset, PAGE_SIZE);
            if (shopList == null || shopList.isEmpty()) {
                log.info("No more data to load from MySQL");
                isEnd=true;//将isEnd标识更改为true
                break;
            }
            for (Shop shop : shopList) {
                try {
                    blockingQueue.put(shop); // 阻塞放入队列，防止队列满
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Thread interrupted while adding to queue", e);
                }
            }
            log.info("Loaded {} shops into the queue", shopList.size());

            page++;
        }
    }

    private void writeToRedis(List<Shop> shopBatch) {
        List<RedisData> redisDataList = shopBatch.stream().map(shop -> {
            RedisData redisData = new RedisData();
            redisData.setData(shop);
            redisData.setExpireTime(LocalDateTime.now().plusSeconds(EXPIRE_TIMES));
            return redisData;
        }).collect(Collectors.toList());

        try {
            List<Object> objects = management.getWriteRedisTemplate().executePipelined(new SessionCallback<Object>() {
                @Override
                public <K, V> Object execute(RedisOperations<K, V> operations) {
                    RedisOperations<String, String> ops = (RedisOperations<String, String>) operations;
                    for (RedisData redisData : redisDataList) {
                        Shop shop = (Shop) redisData.getData();
                        String redisKey = redisPrefix + shop.getId();
                        ops.opsForValue().set(redisKey, JSONUtil.toJsonStr(redisData));
                    }
                    return null;
                }
            });
        } catch (Exception e) {
            log.error("Error writing to Redis", e);
            throw new RuntimeException(e);
        }
    }
}
