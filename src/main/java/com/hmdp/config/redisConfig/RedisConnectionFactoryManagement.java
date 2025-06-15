package com.hmdp.config.redisConfig;

import com.hmdp.config.redisConfig.redisLog.RedisLogUtils;
import lombok.Data;
import org.apache.logging.log4j.spi.CopyOnWrite;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Data
@Component
 public  class  RedisConnectionFactoryManagement {

    @Autowired
    @Qualifier("redisReadFactoryNode1")
    private  RedisConnectionFactory redisReadFactoryNode1;

    @Autowired
    @Qualifier("redisReadFactoryNode2")
    private RedisConnectionFactory redisReadFactoryNode2;

    @Autowired
    @Qualifier("redisReadFactoryNode3")
    private RedisConnectionFactory redisReadFactoryNode3;

    @Autowired
    @Qualifier("redisWriteFactoryNode")
    private RedisConnectionFactory redisWriteFactoryNode;

    @Autowired
    private RedisLogUtils log;

    private CopyOnWriteArrayList<RedisConnectionFactory> availableConnectionFactories;

    private CopyOnWriteArrayList<RedisConnectionFactory> unavailableConnectionFactories;

    private static RedisTemplate<String,String> redisTemplate = new RedisTemplate<>();

    private Long version=0L;


    /*在该redisConnectionFactory管理类属性初始化完毕之后
    * 对两个copyOnWrite数组进行赋值操作
    * 两个数组对所有的Redis节点进行管理
    * availableConnectionFactories数组管理所有标记为可以使用的Redis节点
    * unavailableConnectionFactories数组管理所有标记为不可用的Redis节点
    * 使用copyOnWrite数组的作用是避免获取节点方法获取指定index元素时恰好改节点被从可用数组移除
    * 从而带来线程安全问题带来的空指针异常*/
    @PostConstruct
    public void init()
    {
        unavailableConnectionFactories=new CopyOnWriteArrayList<>();
        availableConnectionFactories = new CopyOnWriteArrayList<>() ;
        availableConnectionFactories.add(redisReadFactoryNode1);
        availableConnectionFactories.add(redisReadFactoryNode2);
        availableConnectionFactories.add(redisReadFactoryNode3);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new StringRedisSerializer());
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashValueSerializer(new StringRedisSerializer());
    }

    private RedisConnectionFactoryManagement()
    {

    }
    //随机从可用节点获取redisConnectionFactory,实现Redis读操作的随机负载均衡
    public  RedisTemplate<String,String> getReadRedisTemplate() {
        setReadConnectionFactory(redisTemplate);
        redisTemplate.afterPropertiesSet();
        log.redisTemplateInUseLog(redisTemplate);
        return redisTemplate;
    }

    //返回Redis主节点redisConnectionFactory
    public RedisTemplate<String,String> getWriteRedisTemplate() {
        setWriteConnectionFactory(redisTemplate);
        redisTemplate.afterPropertiesSet();
        log.redisTemplateInUseLog(redisTemplate);
        return redisTemplate;
    }

    private void incrVersion(){
        version++;
    }
    //该方法将一个读节点从availableConnectionFactories数组中移除
    //并将节点放入unavailableConnectionFactories
    //将该节点归为不可用节点
    public  void disable(RedisNode redisNode){
        int index=0;
        for (RedisConnectionFactory availableConnectionFactory : availableConnectionFactories) {
            LettuceConnectionFactory lettuceConnectionFactory = (LettuceConnectionFactory) availableConnectionFactory;
            int port = lettuceConnectionFactory.getPort();
            String hostName = lettuceConnectionFactory.getHostName();
            if (redisNode.getHost().equals(hostName)&&redisNode.getPort()==port) {
                incrVersion();
                unavailableConnectionFactories.add(availableConnectionFactories.get(index));
                availableConnectionFactories.remove(index);
                break;
            }
            index++;
        }


    }

    //该方法将一个指定节点从unavailableConnectionFactories数组移除
    //并将节点放入availableConnectionFactories
    //重新将该节点标记为可用节点
    public  void reAble(RedisNode redisNode){
        int index=0;
        for (RedisConnectionFactory unavailableConnectionFactory : unavailableConnectionFactories) {
            LettuceConnectionFactory lettuceConnectionFactory = (LettuceConnectionFactory) unavailableConnectionFactory;
            int port = lettuceConnectionFactory.getPort();
            String hostName = lettuceConnectionFactory.getHostName();
            if (redisNode.getHost().equals(hostName)&&redisNode.getPort()==port) {
                incrVersion();
                availableConnectionFactories.add(unavailableConnectionFactories.get(index));
                unavailableConnectionFactories.remove(index);
                log.reAbleLog(redisNode);
                break;
            }
        }

    }


    private  void setReadConnectionFactory (RedisTemplate<String,String> redisTemplate)
    {
        long preVersion=this.version;
        int length = availableConnectionFactories.size();
        if (length==0) {
            redisTemplate.setConnectionFactory(redisWriteFactoryNode);
        }
        int randomNumber = (int) (Math.random() * length);
        long currentVersion=this.version;
        if (preVersion!=currentVersion) {
            setWriteConnectionFactory(redisTemplate);
        }
        RedisConnectionFactory currentConnectionFactory = availableConnectionFactories.get(randomNumber);
        redisTemplate.setConnectionFactory(currentConnectionFactory);
    }

    private void setWriteConnectionFactory(RedisTemplate<String,String> redisTemplate){
        redisTemplate.setConnectionFactory(redisWriteFactoryNode);
    }
}
