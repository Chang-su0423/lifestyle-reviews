package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate redis;
    @Autowired
    private UserMapper userMapper;
    @Override
    public Result sendCode(String phone, HttpSession Session) {
        //校验手机号
        //若不符合返回错误信息
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("illegal phone number");
        }
        //符合，生成验证码
        String code = RandomUtil.randomNumbers(6);
        //保存验证码到session
        //保存验证码到Redis
        //Session.setAttribute("code",code);
        redis.opsForValue().set(RedisConstants.LOGIN_CODE_KEY +phone,code, RedisConstants.LOGIN_CODE_TTL,TimeUnit.MINUTES);

        //发送验证码
        log.info("assume to send code {}",code);
        return Result.ok();

    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //验证手机号
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
            //手机号校验不通过
            return Result.fail("illegal phone number");
        }
        //验证码为Null
        if (redis.opsForValue().get(RedisConstants.LOGIN_CODE_KEY +loginForm.getPhone())==null) {
            return Result.fail("code is null");
        }
        //校验验证码
        if (!redis.opsForValue().get(RedisConstants.LOGIN_CODE_KEY +loginForm.getPhone()).equals(loginForm.getCode())
                ||redis.opsForValue().get(RedisConstants.LOGIN_CODE_KEY +loginForm.getPhone())==null) {
            return Result.fail("code error");
        }
        //检车用户是否存在
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getPhone, loginForm.getPhone());
        User user = userMapper.selectOne(queryWrapper);
        //若用户不存在，创建新用户
        if (user==null) {
            User user1 = new User();
            user1.setPhone(loginForm.getPhone());
            user1.setNickName("user_"+loginForm.getPhone());
            user=user1;
           save(user);
        }
        //将用户信息保存到session中(token)
        //随机生成一个token作为key存储到Redis中
        String token = RedisConstants.LOGIN_USER_KEY+UUID.randomUUID().toString();
        //将user对象转换为hash存储
        UserDTO userDTO = new UserDTO();
        userDTO.setId(user.getId());
        userDTO.setNickName(user.getNickName());
        //session.setAttribute("user",userDTO);
        Map<String,String> userMap02=new HashMap<>();
        userMap02.put("userId",user.getId().toString());
        userMap02.put("nickName",user.getNickName());
/*
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));*/
        //将用户信息保存到Redis中
        redis.opsForHash().putAll(token,userMap02);
        //设置token有效期
        redis.expire(token,RedisConstants.CACHE_SHOP_TTL, TimeUnit.DAYS);
        System.out.println(token);
        //将生成的token返回给客户端

        return Result.ok(token);
    }

    @Override
    public Result sign() {
        //获取当前登录用户
        Long userID = UserHolder.getUser().getId();

        //获取当前时间
        LocalDateTime now = LocalDateTime.now();

        //拼接Redis的key
        String keySuffix = now.format(DateTimeFormatter.ofPattern("yyyy/MM:"));
        String redisKey=userID+"sign:"+keySuffix;

        //获取当前日期是该月的的几天
        int day = now.getDayOfMonth();

        //向Redis的bitmap写入数据
        redis.opsForValue().setBit(redisKey,day-1,true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        //获取当前登录用户
        Long userID = UserHolder.getUser().getId();

        //获取当前时间
        LocalDateTime now = LocalDateTime.now();

        //拼接Redis的key
        String keySuffix = now.format(DateTimeFormatter.ofPattern("yyyy/MM:"));
        String redisKey=userID+"sign:"+keySuffix;

        //获取当前日期是该月的的几天
        int day = now.getDayOfMonth();

        //从bitmap中获取签到记录表
        List<Long> result = redis.opsForValue()
                .bitField(redisKey, BitFieldSubCommands.create()
                        .get(BitFieldSubCommands
                                .BitFieldType
                                .unsigned(day))
                        .valueAt(0));
        if (result==null) {
            return Result.ok(0);
        }
        Long finalResult = result.get(0);
        if (finalResult==null) {
            return Result.ok(0);
        }

        //遍历，获取连续签到天数
        int finalCount=0;
        while (true) {
            if((finalResult&1)==0){
                break;
            }else {
                finalCount++;
            }
            finalResult=finalResult>>>1;
        }
        return Result.ok(finalCount);
    }

    @Override
    public Result logOut() {
        UserHolder.removeUser();
        return Result.ok();
    }
}
