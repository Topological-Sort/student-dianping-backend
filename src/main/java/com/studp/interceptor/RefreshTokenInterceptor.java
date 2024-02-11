package com.studp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.studp.dto.UserDTO;
import com.studp.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.studp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.studp.utils.RedisConstants.LOGIN_USER_TTL;

@RequiredArgsConstructor
@Slf4j
public class RefreshTokenInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.从消息头部获取token
        String token = request.getHeader("Authorization");
//        log.info("[RefreshTokenInterceptor(1)] 校验和更新token，token={}", token);
        if(StrUtil.isBlank(token)){
            log.info("[RefreshTokenInterceptor(1)] 用户未登录，直接放行");
            return true;  // 没登陆，不需要更新，直接放行，交给下一个拦截器
        }
        // 2.根据key查询redis中（已登录）用户是否存在
        String key = LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        if(userMap.isEmpty()){ // 用户未登录或缓存已过期
            log.info("[RefreshTokenInterceptor(1)] 用户未登录或缓存已过期，需重新登陆。tokenKey={}", key);
            return true;  // 需要重新登录，跳过更新
        }
        // 刷新token有效期
        // 3.HashMap --> UserDTO
        UserDTO userDTO = BeanUtil.fillBeanWithMap(
                userMap, new UserDTO(), false);
        // 4.将userDTO保存到ThreadLocal
        UserHolder.saveUser(userDTO);
        // 5.刷新redis中token有效期（10小时）
        stringRedisTemplate.expire(key, LOGIN_USER_TTL, TimeUnit.MINUTES);
//        log.info("[RefreshTokenInterceptor(1)] 更新成功：到期时间：{} 分钟", LOGIN_USER_TTL / 60);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 移除用户
        UserHolder.removeUser();
    }
}
