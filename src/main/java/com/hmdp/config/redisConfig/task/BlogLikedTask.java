package com.hmdp.config.redisConfig.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hmdp.config.redisConfig.RedisConnectionFactoryManagement;
import com.hmdp.config.redisConfig.redisLog.RedisLogUtils;
import com.hmdp.entity.Blog;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.reactive.TransactionalOperator;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

@Component
public class BlogLikedTask {

    @Autowired
    private RedisConnectionFactoryManagement management;

    @Autowired
    private BlogMapper blogMapper;

    @Autowired
    private IBlogService blogService;

    private static final Logger log = Logger.getLogger(RedisLogUtils.class.getName());

    private String pattern="blog:like*";



    public void scanLikedZset(){

        //扫描所有以"blog:like"为前缀的zset，即臊面所有博客的点赞表
        //统计点赞表中元素个数，即每一篇博客的点赞数
        //将点赞数连同博客在zset中的key放入到likedNumsMap中
        //并且通过string的方法获取博客在MySQL中的id以key的形式放入map中
        log.info("初始化 map");
        Map<Long,Integer> likedNumsMap=new HashMap();
        log.info("start writing blog liked from redis to mysql...");
        ScanOptions.ScanOptionsBuilder option = ScanOptions.scanOptions().match(pattern);
        Cursor<byte[]> scanResult = management.getReadRedisTemplate()
                .getConnectionFactory()
                .getConnection()
                .scan(option.build());

        while (scanResult.hasNext()) {
            byte[] currentEle = scanResult.next();
            String redisSortedSetKey=new String(currentEle);
            Long likedCount = management.getReadRedisTemplate().opsForZSet().size(redisSortedSetKey);
            String blogId = redisSortedSetKey.substring(pattern.length()-1);
            likedNumsMap.put(Long.valueOf(blogId),Integer.valueOf(likedCount.toString()));
        }

        //从map中获取点赞数
        //通过id对数据库中博客的点赞字段进行更新操作
        //mybatis-plus自定义sql，对map中的数据同时更新到数据中
        likedNumsMap.forEach((k,v)->{
            LambdaUpdateWrapper<Blog> updateWrapper=new LambdaUpdateWrapper<>();
            updateWrapper.eq(Blog::getId,k).set(Blog::getLiked,v);
            blogService.update(updateWrapper);
        });
        log.info("write blog liked from redis to mysql end ");

    }


}
