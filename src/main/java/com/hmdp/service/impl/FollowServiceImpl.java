package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Autowired
    StringRedisTemplate  redis;

    @Autowired
    IUserService userService;
    @Override
    public Result follow(Long id, boolean isFollow) {
        String key="follows"+UserHolder.getUser().getId();
        if (!isFollow) {
            LambdaQueryWrapper<Follow> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Follow::getUserId, UserHolder.getUser().getId()).eq(Follow::getFollowUserId,id);
            boolean success = this.remove(wrapper);
            if (success) {
                redis.opsForSet().remove(key,id.toString());
            }
        }else {
            Follow follow = new Follow();
            follow.setUserId(UserHolder.getUser().getId());
            follow.setFollowUserId(id);
            boolean success = this.save(follow);
            if (success) {
                redis.opsForSet().add(key,id.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long id) {
        Integer count = this.query().eq("follow_user_id", id).eq("user_id", UserHolder.getUser().getId()).count();
        return Result.ok(count>0);
    }

    @Override
    public Result commonFollow(Long id) {
        //获取当前用户
        Long currentUserId = UserHolder.getUser().getId();
        String key1="follows"+currentUserId;
        String key2="follows"+id;

        //求当前用户与目标用户交集
        Set<String> intersect = redis.opsForSet().intersect(key1, key2);
        //判断无交集情况
        if (intersect.isEmpty()||intersect==null) {
            return Result.ok();
        }
        List<Long> intersectList = intersect
                .stream()
                .map((value) -> Long.valueOf(value))
                .collect(Collectors.toList());


        //查询用户详细信息
        List<UserDTO> userDTOList = userService.listByIds(intersectList)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(userDTOList);

    }
}
