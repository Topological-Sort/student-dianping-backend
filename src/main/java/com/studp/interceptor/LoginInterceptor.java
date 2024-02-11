package com.studp.interceptor;

import cn.hutool.core.util.StrUtil;
import com.studp.dto.UserDTO;
import com.studp.utils.RedisConstants;
import com.studp.utils.UserHolder;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


@Slf4j
@AllArgsConstructor
public class LoginInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    /**
     * 基于token和redis实现
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("Authorization");
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
//        log.info("[UserInterceptor(0)] 校验登陆状态，tokenKey = {} ", tokenKey);
        // token为空
        if (StrUtil.isBlank(tokenKey)){
            log.info("[UserInterceptor(0)] 未登录 ");
            response.setStatus(401);
            return false;
        }
        // 用户不存在或token已过期，缓存被删除
        if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(tokenKey))){
            log.info("[UserInterceptor(0)] 用户不存在或登陆状态已过期：tokenKey = {} ", tokenKey);
            response.setStatus(401);
            return false;
        }
        // 获取用户缓存信息
        Long id = Long.parseLong(stringRedisTemplate.opsForHash().get(tokenKey, "id").toString());
        String nickName = stringRedisTemplate.opsForHash().get(tokenKey, "nickName").toString();
        UserDTO userDTO = new UserDTO();
        userDTO.setId(id);
        userDTO.setNickName(nickName);
        // 保存用户信息
        UserHolder.saveUser(userDTO);
//        log.info("[UserInterceptor(0)] 校验通过，用户id = {}", id);
        // 放行
        return true;
    }

    /**
     * 基于session实现
     */
//    @Override
//    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//       //1.获取session
//        HttpSession session = request.getSession();
//        //2.获取session中的用户
//        Object user = session.getAttribute("user");
//        //3.判断用户是否存在
//        if(user == null){
//              //4.不存在，拦截，返回401状态码
//              response.setStatus(401);
//              return false;
//        }
//        //5.存在，保存用户信息到Threadlocal
//        UserHolder.saveUser((UserDTO) user);
//        //6.放行
//        return true;
//    }
}