package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RefreshTokenInterceptor implements HandlerInterceptor {
    private StringRedisTemplate redis;

    public RefreshTokenInterceptor(StringRedisTemplate redis) {
        this.redis = redis;
    }

    //访问接口前获取用户信息
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //获取请求头中token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            //response.setStatus(401);
            return true;
        }
        //获取Redis中用户  token->user
        Map<Object, Object> valueMap = redis.opsForHash().entries(token);
                                   /* //从cookie中的sessionID获取用户信息
                                    //HttpSession session = request.getSession();
                                    //Object user = session.getAttribute("user");*/
        //判断用户信息是否存在
        if (valueMap.isEmpty()) {
            //不存在，直接报错并拦截

            return true;
        }
        //hash数据转换为userDTO数据
        UserDTO userDTO = new UserDTO();
        userDTO.setNickName(valueMap.get("nickName").toString());
        userDTO.setId(Long.valueOf(valueMap.get("userId").toString()));


        //存在，保存在当前线程中
        UserHolder.saveUser(userDTO);
        //刷新token过期时间
        redis.expire(token, RedisConstants.CACHE_SHOP_TTL, TimeUnit.DAYS);
        //放行
        return true;
    }
}