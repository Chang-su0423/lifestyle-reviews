package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.xml.bind.annotation.XmlType;
import java.util.ArrayList;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Autowired
    private IUserService userService;

    @Autowired
    private IFollowService followService;

    @Autowired
    private StringRedisTemplate redis;

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = this.getById(id);
        if (blog==null) {
            return Result.fail("blog不存在");
        }
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());

        Long currentUserId = UserHolder.getUser().getId();
        String key = "blog:like"+id;
        Long likedNums = redis.opsForZSet().size(key);
        blog.setLiked(Integer.valueOf(likedNums.toString()));
        Double score = redis.opsForZSet().score(key, currentUserId + "");
        if (score!=null) {
            blog.setIsLike(true);
        }
        else {
            blog.setIsLike(false);
        }
        return Result.ok(blog);
    }

    @Override
    public Result likeBlog(Long id) {
        //获取当前登录用户
        Long userId = UserHolder.getUser().getId();

        //判断用户是否已经点赞
        String key="blog:like"+id;
        Double score = redis.opsForZSet().score(key, userId + "");

        //若已经点赞
        if (score!=null) {
            //更新Redis，将当前用户从Redis的set集合移除
            redis.opsForZSet().remove(key,userId+"");
        }else {
            //若未点赞
            //更新Redis，将当前用户放入Redis的set集合
            redis.opsForZSet().add(key,userId+"",System.currentTimeMillis());
        }
        return Result.ok();
    }

    @Override
    public Result queryHotBlog(Integer current) {
        //主页未登录时currentUserId为null
        if (UserHolder.getUser()==null) {
            // 根据用户查询
            Page<Blog> page = query()
                    .orderByDesc("liked")
                    .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
            // 获取当前页数据
            List<Blog> records = page.getRecords();
            records.forEach(blog ->{
                Long userId = blog.getUserId();
                User user = userService.getById(userId);
                blog.setName(user.getNickName());
                blog.setIcon(user.getIcon());
                String key = "blog:like"+blog.getId();
                Long likedNums = redis.opsForZSet().size(key);
                blog.setLiked(Integer.valueOf(likedNums.toString()));
            });
            return Result.ok(records);
        }
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();


        //获取当前用户ID
        Long currentUserId = UserHolder.getUser().getId();

        // 查询用户
        records.forEach(blog ->{
            Long userId = blog.getUserId();
            User user = userService.getById(userId);
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
            String key = "blog:like"+blog.getId();
            Long likedNums = redis.opsForZSet().size(key);
            blog.setLiked(Integer.valueOf(likedNums.toString()));
            Double score = redis.opsForZSet().score(key, currentUserId + "");
            if (score!=null) {
                blog.setIsLike(true);
            }else {
                blog.setIsLike(false);
            }
        });
        return Result.ok(records);
    }

    @Override
    public Result queryLikesById(Long id) {
        String key="blog:like"+id;
        Set<String> stringUserIdList = redis.opsForZSet().range(key, 0, 4);
        if (stringUserIdList==null||stringUserIdList.isEmpty()) {
            return Result.ok("no one liked");
        }
        List<Long> userIdList = stringUserIdList.stream().map(Long::valueOf).collect(Collectors.toList());
        String idAfterJoin = StrUtil.join(",", userIdList);
        List<UserDTO> userDTOList = userService.query().in("id", userIdList)
                .last("order by field (id," + idAfterJoin + ")")
                .list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        return Result.ok(userDTOList);

    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());



        // 保存探店博文
        boolean isSuccess = this.save(blog);
        if (!isSuccess) {
            return Result.fail("数据写入数据库失败");
        }

        //查询笔记作者的所有粉丝
        List<Follow> follow = followService.query().eq("follow_user_id", blog.getUserId()).list();

        //获取当前时间戳
        long time = System.currentTimeMillis();
        for (Follow follow1 : follow) {
            //拼接key
            String key="feed:"+ follow1.getUserId();

            //将消息推送到收件箱
            redis.opsForZSet().add(key,blog.getId().toString(),time);
        }
        return Result.ok(blog.getId());

        // 返回id
    }

    @Override
    public Result queryFollowBlogByPage(Long max, Integer offSet) {

        //获取当前用户id
        Long currUserId = UserHolder.getUser().getId();
        String key="feed:"+currUserId;

        //查Redis中的收件箱
        Set<ZSetOperations.TypedTuple<String>> typedTuples = redis.opsForZSet().reverseRangeByScoreWithScores(key, 0, max, offSet, 3);
        //解析数据 blogId,offSet,minScore
        int minTimeCount=1;
        Long minTime=0l;

        if (typedTuples==null||typedTuples.isEmpty()) {
            return Result.ok();
        }


        ArrayList<Long> idList=new ArrayList<>(typedTuples.size());

        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {

            //获取Redis收件箱中用户id列表
            idList.add(Long.valueOf(tuple.getValue()));


            //获取Redis收件箱中score字段即推送时的时间戳列表
            long currentTime = tuple.getScore().longValue();

            //获取同时为最小的时间戳个数
            if (currentTime==minTime) {
                minTimeCount++;
            }else{
                minTime=currentTime;
                minTimeCount=1;
            }
        }
        //拼接字符串
        String idAfterJoin=StrUtil.join(",",idList);

        //根据ID列表查询blog详细信息列表
        List<Blog> blogDetailList = this.query()
                .in("id", idList)
                .last("order by field (id," + idAfterJoin + ")")
                .list();

        //封装并返回给客户端
        ScrollResult result=new ScrollResult();
        result.setList(blogDetailList);
        result.setMinTime(minTime);
        result.setOffset(minTimeCount);

        return Result.ok(result);

    }


}
