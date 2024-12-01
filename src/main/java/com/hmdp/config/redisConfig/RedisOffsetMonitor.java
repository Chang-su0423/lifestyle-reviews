package com.hmdp.config.redisConfig;


import com.hmdp.config.redisConfig.redisLog.RedisLogUtils;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;

@Component
public class RedisOffsetMonitor {

    @Autowired
    private RedisProperties redisProperties;

    @Autowired
    private RedisConnectionFactoryManagement redisConnectionFactoryManagement;

    @Autowired
    private RedisLogUtils log;

    private String redisUrlPrefix = "redis://";

    private String masterOffsetPrefix="master_repl_offset";

    private String slaveOffsetPrefix="slave_repl_offset";

    private RedisClient redisClient;

    @PostConstruct
    public void initRedisClient() {
        this.redisClient = RedisClient.create(); // 初始化客户端
    }

    @PreDestroy
    public void shutdownRedisClient() {
        if (this.redisClient != null) {
            this.redisClient.shutdown(); // 关闭客户端
        }
    }


    public void scanAvailableSlaveRedisNodes(){
        log.scanAvailableSlaveRedisNodes();
        RedisNode masterNode = redisProperties.getMaster();
        List<RedisNode> ableSlaveRedisNodes = this.getAbleSlaveRedisNodes();
        if (ableSlaveRedisNodes==null||ableSlaveRedisNodes.isEmpty()) {
            return;
        }
        for (RedisNode ableSlaveRedisNode : ableSlaveRedisNodes) {
            long masterOffset = getOffset(masterNode, true);
            long currentSlaveOffset = getOffset(ableSlaveRedisNode, false);
            if (masterOffset-currentSlaveOffset > redisProperties.getMaxOffsetMinus()) {
                redisConnectionFactoryManagement.disable(ableSlaveRedisNode);
                log.occurRedisNodesUnAvailable(ableSlaveRedisNode);
            }
        }
    }

    public  void  scanUnAvailableSlaveRedisNodes(){
        log.scanUnAvailableSlaveRedisNodes();
        RedisNode masterNode = redisProperties.getMaster();
        List<RedisNode> disAbleSlaveRedisNodes = this.getDisableSlaveRedisNodes();
        if (disAbleSlaveRedisNodes==null||disAbleSlaveRedisNodes.isEmpty()) {
            return;
        }
        for (RedisNode disAbleSlaveRedisNode : disAbleSlaveRedisNodes) {
            long masterOffset = getOffset(masterNode, true);
            long currentSlaveOffset = getOffset(disAbleSlaveRedisNode, false);
            if (masterOffset-currentSlaveOffset < redisProperties.getMaxOffsetMinus()) {
                redisConnectionFactoryManagement.reAble(disAbleSlaveRedisNode);
                log.occurRedisNodesAvailable(disAbleSlaveRedisNode);
            }
        }
    }

    private List<RedisNode> getDisableSlaveRedisNodes(){
        List<RedisConnectionFactory> unavailableConnectionFactories = redisConnectionFactoryManagement.getUnavailableConnectionFactories();
        if (unavailableConnectionFactories==null||unavailableConnectionFactories.isEmpty()) {
            return null;
        }
        List<RedisNode> unavailableRedisNodes = new ArrayList<RedisNode>();
        for (RedisConnectionFactory unavailableConnectionFactory : unavailableConnectionFactories) {
            LettuceConnectionFactory lettuceConnectionFactory = (LettuceConnectionFactory) unavailableConnectionFactory;
            unavailableRedisNodes.add(new RedisNode(
                    lettuceConnectionFactory.getHostName(),
                    lettuceConnectionFactory.getPort()));
        }
        return unavailableRedisNodes;
    }

    private List<RedisNode> getAbleSlaveRedisNodes(){
        List<RedisConnectionFactory> availableConnectionFactories = redisConnectionFactoryManagement.getAvailableConnectionFactories();
        if (availableConnectionFactories==null||availableConnectionFactories.isEmpty()) {
            return null;
        }
        List<RedisNode> availableRedisNodes =new ArrayList<>();
        for (RedisConnectionFactory availableConnectionFactory : availableConnectionFactories) {
            LettuceConnectionFactory lettuceConnectionFactory = (LettuceConnectionFactory) availableConnectionFactory;
            availableRedisNodes.add(new RedisNode(
                    lettuceConnectionFactory.getHostName(),
                    lettuceConnectionFactory.getPort()
            ));
        }
        return availableRedisNodes;
    }

    private long getOffset(RedisNode redisNode, boolean isMaster) {
        String offsetPrefix = isMaster ? masterOffsetPrefix : slaveOffsetPrefix;
        String redisUrl = redisUrlPrefix + redisNode.getHost() + ":" + redisNode.getPort();
        StatefulRedisConnection<String, String> connection = null;
        try {
            connection = redisClient.connect(RedisURI.create(redisUrl)); // 动态连接
            RedisCommands<String, String> syncCommands = connection.sync();
            String replication = syncCommands.info("replication");
            String[] splitReplication = replication.split("\r\n");
            for (String split : splitReplication) {
                if (split.startsWith(offsetPrefix)) {
                    String[] targetLine = split.split(":");
                    long offSet = Long.parseLong(targetLine[1]);
                    log.redisOffsetLog(redisNode, offSet);
                    return offSet;
                }
            }
        } catch (Exception e) {
        } finally {
            if (connection != null) {
                connection.close(); // 释放连接
            }
        }
        return -1L;
    }




}
