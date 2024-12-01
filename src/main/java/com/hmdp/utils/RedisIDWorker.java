package com.hmdp.utils;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIDWorker
{

    private StringRedisTemplate redis;

    //2022年一月一日零分零秒时间戳
    private static final long BEGIN_TIMESTAMP = 1640995200L;

    public RedisIDWorker(StringRedisTemplate redis) {
        this.redis = redis;
    }

    //生成优惠段订单唯一序列号
    public long nextId(String prefixName)
    {
        //获取当前时间戳
        LocalDateTime now = LocalDateTime.now();
        long timeStampNow = now.toEpochSecond(ZoneOffset.UTC);
        //使用时间戳减去特定时间获取最终前缀
        long finalPrefix=timeStampNow-BEGIN_TIMESTAMP;
        //拼接Redis中自增长value的key
        String nowDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        //使用日期拼接，精确到日
        //获取最终的自增长的序列号的后32位
        Long increment = redis.opsForValue().increment("icr:" + prefixName + ":" + nowDate);
        //拼接并返回最终的序列号
        return finalPrefix<<32|increment;

    }

}
