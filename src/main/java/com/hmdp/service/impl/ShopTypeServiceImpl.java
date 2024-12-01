package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate redis;
    @Override
    public Result queryTypeByRedis() {
        //从Redis中查询
        String cacheKey= RedisConstants.CACHE_SHOP_TYPE_KEY;
        String resultJSON = redis.opsForValue().get(cacheKey);
        //若存在，直接返回结果
        if (resultJSON!=null) {
            ShopType[] shopTypeList = JSONUtil.parseArray(resultJSON).toArray(new ShopType[0]);
            List<ShopType> list = Arrays.asList(shopTypeList);
            return Result.ok(list);

        }
        //若不存在，从MySQL中查询
        List<ShopType> shopTypeList = query().orderByAsc("sort").list();
        //若MySQL中不存在，返回错误信息
        if (shopTypeList.isEmpty()) {
            return Result.fail("no such data");
        }
        //若存在，直接返回给客户端，并将查询结果写入Redis缓存中
        redis.opsForValue().set(cacheKey, JSONUtil.toJsonStr(shopTypeList));
        return Result.ok(shopTypeList);
    }
}
