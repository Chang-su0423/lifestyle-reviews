package com.hmdp.service.impl;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.config.redisConfig.RedisConnectionFactoryManagement;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.RedisLogicExpireTime;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.dao.DataAccessException;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.domain.geo.BoundingBox;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.print.DocFlavor;
import javax.security.auth.callback.Callback;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private RedisConnectionFactoryManagement management;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    private static final Long LOGIC_EXPIRE_TIME=200000000L;

    private static final String REDIS_KEY_PREFIX = "cache:shop:";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Qualifier("redisTemplate")
    @Autowired
    private RedisTemplate redisTemplate;


    @Override
    public Result queryById(Long id) {

        //解决缓存穿透问题
        //Shop shop = queryWithPassThrough(id);

        //使用互斥锁解决缓存击穿问题
        //Shop shop = queryWithMutex(id);

        //使用逻辑过期解决缓存击穿问题
        Shop shop = this.queryWithLogicExpire(id);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }

        return Result.ok(shop);
    }


    private Shop queryWithLogicExpire(Long id) {
        String lockKey = "shop:lock:" + id;
        String cacheKey = RedisConstants.CACHE_SHOP_KEY + id;
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
        Shop shop = JSONUtil.toBean((JSONObject) expireTimeBean.getData(), Shop.class);
        //逻辑删除时间若未超出
        if (expireTimeBean.getExpireTime().isAfter(LocalDateTime.now())) {
            return shop;
        }
        //若超出，获取锁
        //开启缓存重建策略
        boolean isLock = this.tryLock(lockKey);
        if (!isLock) {
            return shop;
        }
        //成功获取锁
        //获取锁之后再次检测key是否过期
        //未过期返回Redis缓存中信息
        String resultJSONNew = redis.opsForValue().get(cacheKey);
        RedisLogicExpireTime expireTimeBeanNew = JSONUtil.toBean(resultJSONNew, RedisLogicExpireTime.class);
        Shop shopNew = JSONUtil.toBean((JSONObject) expireTimeBeanNew.getData(), Shop.class);
        if (expireTimeBeanNew.getExpireTime().isAfter(LocalDateTime.now())) {
            return shopNew;
        }
        //过期，缓存重建
        //开启新线程，查询数据库，并写入缓存
        //使用线程池

        CACHE_REBUILD_EXECUTOR.submit(() ->
        {
            try {
                saveShopToRedis(id, 2000000L);
            } finally {
                this.unLock(lockKey);
            }

        });

        //释放锁
        //直接返回缓存中查询的结果
        //直接返回缓存中查询数据

        //获取锁失败
        return shop;


    }

    @Override
    public Result updateRedisAndMysql(Shop shop) {
        //更新数据库
        this.updateById(shop);
        //采取逻辑过期时间的策略，不进行缓存的删除
        return Result.ok(shop);
    }

    @Override
    public Result searchByXAndY(Integer typeId, Integer current, Double x, Double y) {
        //判断客户端是否发送经纬度坐标，若没有发送，直接进行数据库的查询
        if (x == null || y == null) {
            // 根据类型分页查询
            Page<Shop> page = this.query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page);
        }

        //计算分页查询参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        //从Redis中查询数据
        //拼接当前查询的Redis的key
        //根据key,经纬度坐标，半径，规定结果包含距离，添加end限制，从Redis中查询附近商户
        String redisKey = "shop:geo:" + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> searchResult = redis.opsForGeo().search(
                redisKey,
                GeoReference.fromCoordinate(x, y),
                new Distance(500000),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end));

        //对Redis中查询的数据进行判空处理
        if (searchResult == null) {
            return Result.ok();
        }

        //解析Redis中的数据
        //判断是否还有下一页
        if (from >= searchResult.getContent().size()) {
            return Result.ok();
        }
        ArrayList<Long> idList = new ArrayList<>();
        Map<Long, Distance> resultMap = new HashMap<>();
        //对Redis查询到的数据进行截断处理
        searchResult.getContent().stream().skip(from).forEach(result -> {
            Long test = Long.valueOf(result.getContent().getName());
            System.out.println(test);
            idList.add(Long.valueOf(result.getContent().getName()));
            resultMap.put(Long.valueOf(result.getContent().getName()), result.getDistance());
        });

        //根据ID列表从数据库中查询详细信息
        //拼接字符串
        String sql = StrUtil.join(",", idList);
        List<Shop> shopList = this.query().in("id", idList).last("order by field(id," + sql + ")").list();
        shopList.forEach(shop -> {
            Distance distance = resultMap.get(Long.valueOf(shop.getId()));
            shop.setDistance(distance.getValue());
        });

        //向客户端返回结果
        return Result.ok(shopList);

    }

    @Override
    public void writeIntoRedisWithLogicExpire(Shop shop) {
        RedisTemplate<String, String> redisTemplate = management.getWriteRedisTemplate();
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(LOGIC_EXPIRE_TIME));
        String redisKey = RedisConstants.CACHE_SHOP_KEY + shop.getId();
        redisTemplate.opsForValue().set(redisKey,JSONUtil.toJsonStr(redisData));
    }


    //获取互斥锁
    private boolean tryLock(String key) {
        Boolean isFlag = redis.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(isFlag);
    }

    //释放互斥锁
    private void unLock(String key) {
        redis.delete(key);
    }

    public void saveShopToRedis(Long id, Long expireSeconds) {
        RedisLogicExpireTime redisLogicExpireTime = new RedisLogicExpireTime();
        Shop shop = this.getById(id);
        //模拟长耗时的缓存重建操作
        try {
            Thread.sleep(20);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        redisLogicExpireTime.setData(shop);
        redisLogicExpireTime.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        redis.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisLogicExpireTime));
    }
    /*public Shop queryWithPassThrough(Long id) {
        String cacheKey = RedisConstants.CACHE_SHOP_KEY + id;
        //查询Redis缓存
        String resultJSON = redis.opsForValue().get(cacheKey);
        //存在直接返回
        *//*isNotBlank()方法对于空字符串""返回值为false*//*
        if (StrUtil.isNotBlank(resultJSON)) {
            Shop shop = JSONUtil.toBean(resultJSON, Shop.class);
            return shop;
        }
        //判断redis查询的值是否为空字符串""
        if (resultJSON != null) {
            return null;
        }
        //是，无需继续查询数据库
        //不存在，查询MySQL
        Shop shop = this.getById(id);
        //MySQL不存在，缓存空对象，将null值写入Redis，并设置一个较短的过期时间
        if (shop == null) {
            redis.opsForValue().set(cacheKey, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //MySQL存在，返回前端并写入Redis,并设置超时时间
        redis.opsForValue().set(cacheKey, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }*/

   /* public Shop queryWithMutex(Long id) {
        String cacheKey = RedisConstants.CACHE_SHOP_KEY + id;
        //查询Redis缓存
        String resultJSON = redis.opsForValue().get(cacheKey);
        //存在直接返回
        *//*isNotBlank()方法对于空字符串""返回值为false*//*
        if (StrUtil.isNotBlank(resultJSON)) {
            Shop shop = JSONUtil.toBean(resultJSON, Shop.class);
            return shop;
        }
        //判断redis查询的值是否为空字符串""
        if (resultJSON != null) {
            //是，无需继续查询数据库
            return null;
        }

        //不存在实行缓存重建策略
        //获取互斥锁
        String lockKey = "shop:lock:" + id;
        boolean isLock = this.tryLock(lockKey);
        //获取互斥锁失败
        //进入休眠状态，稍后重试
        try {
            if (!isLock) {
                Thread.sleep(500);
                this.queryWithMutex(id);
            }
            //获取互斥锁成功
            //检查Redis缓存中是否存在
            String resultJsonNow = redis.opsForValue().get(cacheKey);
            //存在
            //验证取到的值是否为缓存的空对象
            //不是空对象，直接返回给客户端,释放锁
            if (StrUtil.isNotBlank(resultJsonNow)) {
                Shop shop = JSONUtil.toBean(resultJSON, Shop.class);
                this.unLock(lockKey);
                return shop;
            }
            //是空对象，返回错误结论
            if (resultJsonNow != null) {
                //是，无需继续查询数据库,释放锁
                this.unLock(lockKey);
                return null;
            }
            //不存在，根据id查询MySQL数据库
            //若MySQL数据库中查询值为null
            //在缓存中缓存空对象
            //释放互斥锁
            //返回数据给客户端
            Shop shop = this.getById(id);
            //模拟查询数据库的延迟
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            if (shop == null) {
                redis.opsForValue().set(cacheKey, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                this.unLock(lockKey);
                return null;
            }
            //若MySQL数据库中查询值不为null
            //将数据写入Redis
            //释放互斥锁
            //将数据返回给客户端
            redis.opsForValue().set(cacheKey, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
            return shop;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            this.unLock(lockKey);
        }


    }*/
}
