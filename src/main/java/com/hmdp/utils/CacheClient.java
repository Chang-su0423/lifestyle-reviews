package com.hmdp.utils;


import cn.hutool.core.date.DateTime;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import io.lettuce.core.GeoArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;


/*
* 该类为Redis缓存工具类
* 对外暴露queryWithPassThrough(),queryWithMutex(),queryWithLogicExpire()三个方法
* 第一个方法以缓存空对象的方式解决缓存穿透带给数据库的压力
* 第二个方法以互斥锁的方案，保证数据一致性，解决热点key带来的缓存击穿问题
* 第三个方法以逻辑过期的方案，不在Redis中设置过期时间，由开发者自己维护过期时间，保证程序的可用性，解决热点key带来的缓存击穿问题
* */
@Component
@Slf4j
public class CacheClient {
    @Autowired
    private StringRedisTemplate redis;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(1);


   private void set(String key, Object value, Long time, TimeUnit timeUnit)
    {
        redis.opsForValue().set(key, JSONUtil.toJsonStr(value), time,timeUnit);
    }
    private  void  logicExpireTimeSet(String key, Object value, Long time, TimeUnit timeUnit)
    {
        RedisLogicExpireTime logic=new RedisLogicExpireTime();
        logic.setData(value);
        logic.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        redis.opsForValue().set(key, JSONUtil.toJsonStr(logic));
    }
    public<T,E> T queryWithPassThrough(String prefix, E id, Class<T> classType ,Function<E,T> dbFallBack,Long time,TimeUnit timeUnit)
    {

        String cacheKey= prefix+id;
        //查询Redis缓存
        String resultJSON = redis.opsForValue().get(cacheKey);
        //存在直接返回
        /*isNotBlank()方法对于空字符串""返回值为false*/
        if (StrUtil.isNotBlank(resultJSON)) {
            T t = JSONUtil.toBean(resultJSON,classType);
            return t;
        }
        //判断redis查询的值是否为空字符串""
        if (resultJSON!=null) {
            return null;
        }
        //是，无需继续查询数据库
        //不存在，查询MySQL
        T t =dbFallBack.apply(id);
        //MySQL不存在，缓存空对象，将null值写入Redis，并设置一个较短的过期时间
        if (t==null) {
            redis.opsForValue().set(cacheKey,"",time,timeUnit);
            return null;
        }
        //MySQL存在，返回前端并写入Redis,并设置超时时间
        redis.opsForValue().set(cacheKey,JSONUtil.toJsonStr(t), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return t;
    }

    public <T,E> T queryWithMutex(String prefix,String mutexLockPrefix,E id,Class<T> classType,Function<E,T> dbFallBack,Long time,TimeUnit timeUnit)
    {
        String cacheKey= prefix+id;
        //查询Redis缓存
        String resultJSON = redis.opsForValue().get(cacheKey);
        //存在直接返回
        /*isNotBlank()方法对于空字符串""返回值为false*/
        if (StrUtil.isNotBlank(resultJSON)) {
            T t = JSONUtil.toBean(resultJSON, classType);
            return t;
        }
        //判断redis查询的值是否为空字符串""
        if (resultJSON!=null) {
            //是，无需继续查询数据库
            return null;
        }

        //不存在实行缓存重建策略
        //获取互斥锁
        String lockKey=mutexLockPrefix+id;
        boolean isLock = this.tryLock(lockKey);
        //获取互斥锁失败
        //进入休眠状态，稍后重试
        try {
            if(!isLock)
            {
                Thread.sleep(500);
                this.queryWithMutex(prefix,mutexLockPrefix,id,classType,dbFallBack,time,timeUnit);
            }
            //获取互斥锁成功
            //检查Redis缓存中是否存在
            String resultJsonNow= redis.opsForValue().get(cacheKey);
            //存在
            //验证取到的值是否为缓存的空对象
            //不是空对象，直接返回给客户端,释放锁
            if (StrUtil.isNotBlank(resultJsonNow)) {
                T T = JSONUtil.toBean(resultJSON,classType);
                this.unLock(lockKey);
                return T;
            }
            //是空对象，返回错误结论
            if (resultJsonNow!=null) {
                //是，无需继续查询数据库,释放锁
                this.unLock(lockKey);
                return null;
            }
            //不存在，根据id查询MySQL数据库
            //若MySQL数据库中查询值为null
            //在缓存中缓存空对象
            //释放互斥锁
            //返回数据给客户端
            T t= dbFallBack.apply(id);
            //模拟查询数据库的延迟
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            if (t == null) {
               set(cacheKey,JSONUtil.toJsonStr(t),time,timeUnit);
                this.unLock(lockKey);
                return null;
            }
            //若MySQL数据库中查询值不为null
            //将数据写入Redis
            //释放互斥锁
            //将数据返回给客户端
           this.set(cacheKey,JSONUtil.toJsonStr(t),time,timeUnit);
            return t;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        finally {
            this.unLock(lockKey);
        }
    }

    public <T,E> T queryWithLogicExpire(String prefix,String mutexLockPrefix,E id,Class<T> classType,Function<E,T> dbFallBack,Long time,TimeUnit timeUnit)
    {
        String lockKey=mutexLockPrefix+id;
        String cacheKey= prefix+id;
        //查询Redis缓存
        String resultJSON = redis.opsForValue().get(cacheKey);
        //已进行缓存预热，直接返回，查询结果为空说明id不存在
        //结果为空，直接返回空值
        if (StrUtil.isBlank(resultJSON)) {
            return null;
        }
        //查询结果不为空
        //比对逻辑过期时间是否超出
        RedisLogicExpireTime expireTimeBean = JSONUtil.toBean(resultJSON, RedisLogicExpireTime.class);
       T t =JSONUtil.toBean((JSONObject)expireTimeBean.getData(), classType );
        //逻辑删除时间若未超出
        if (expireTimeBean.getExpireTime().isAfter(LocalDateTime.now())) {
            return t;
        }
        //若超出，获取锁
        //开启缓存重建策略
        boolean isLock = this.tryLock(lockKey);
        if (!isLock) {
            return t;
        }
        //成功获取锁
        //获取锁之后再次检测key是否过期
        //未过期返回Redis缓存中信息
        String resultJSONNew = redis.opsForValue().get(cacheKey);
        RedisLogicExpireTime expireTimeBeanNew = JSONUtil.toBean(resultJSONNew, RedisLogicExpireTime.class);
        T tNew = JSONUtil.toBean((JSONObject) expireTimeBeanNew.getData(), classType );
        if (expireTimeBeanNew.getExpireTime().isAfter(LocalDateTime.now())) {
            return tNew;
        }
        //过期，缓存重建
        //开启新线程，查询数据库，并写入缓存
        //使用线程池

        CACHE_REBUILD_EXECUTOR.submit(()->
        {
            try {
                saveShopToRedis(id,200L,dbFallBack,prefix);
            } finally {
                this.unLock(lockKey);
            }

        });

        //释放锁
        //直接返回缓存中查询的结果
        //直接返回缓存中查询数据

        //获取锁失败
        return t;

    }
    //获取互斥锁
    private boolean tryLock(String key)
    {
        Boolean isFlag = redis.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(isFlag);
    }

    //释放互斥锁
    private void unLock(String key)
    {
        redis.delete(key);
    }

    private <T,E>  void  saveShopToRedis(E id,Long expireSeconds,Function<E,T> dbFallBack,String prefix)
    {
        RedisLogicExpireTime redisLogicExpireTime= new RedisLogicExpireTime();
        T t = dbFallBack.apply(id);
        //模拟长耗时的缓存重建操作
        try {
            Thread.sleep(20);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        redisLogicExpireTime.setData(t);
        redisLogicExpireTime.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        redis.opsForValue().set(prefix+id,JSONUtil.toJsonStr(redisLogicExpireTime));
    }
}
