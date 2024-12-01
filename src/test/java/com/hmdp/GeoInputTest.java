package com.hmdp;


import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SpringBootTest
public class GeoInputTest
{
    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private IShopService shopService;
    @Test
    public void test()
    {
        //从数据库中查询店铺信息
        List<Shop> shopList = shopService.list();

        //按照Type对店铺信息进行分组
        Map<Long,List<Shop>> map=shopList.stream().collect(Collectors.groupingBy(Shop::getTypeId));

        //分批写入Redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            Long key = entry.getKey();
            String redisKey="shop:geo:"+key;
            List<RedisGeoCommands.GeoLocation<String>> geoLocationList=new ArrayList<>(entry.getValue().size());
            for (Shop shop : entry.getValue()) {
                RedisGeoCommands.GeoLocation<String> location=new RedisGeoCommands
                        .GeoLocation<>(shop.getId().toString(),new Point(shop.getX(),shop.getY()));
                geoLocationList.add(location);
            }
            redis.opsForGeo().add(redisKey,geoLocationList);
        }

    }

}
