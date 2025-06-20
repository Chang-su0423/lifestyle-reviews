package com.hmdp.mapper;

import ch.qos.logback.core.rolling.helper.IntegerTokenConverter;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface BlogMapper extends BaseMapper<Blog> {

    void updateBatchByLikedNumsMap( Map<Long,Integer> likedNumsMap);
}
