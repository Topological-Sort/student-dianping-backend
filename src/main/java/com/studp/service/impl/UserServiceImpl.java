package com.studp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.studp.dto.Null;
import com.studp.dto.LoginFormDTO;
import com.studp.dto.Result;
import com.studp.dto.UserDTO;
import com.studp.entity.User;
import com.studp.mapper.UserMapper;
import com.studp.service.IUserService;
import com.studp.utils.RegexUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.studp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.studp.utils.RedisConstants.LOGIN_USER_TTL;
import static com.studp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public User createUserWithPhone(LoginFormDTO loginForm) {
        User user = User.builder()
                .phone(loginForm.getPhone())
                .password(loginForm.getPassword())
                .nickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10))
                .build();
        this.save(user);
        return user;
    }

    @Override
    public Result<String> login(LoginFormDTO loginForm, HttpSession session) {
        // 1.校验手机号
        if(!RegexUtils.isPhoneInvalid(loginForm.getPhone())){
            Result.fail("手机号格式错误！");
        }
        // 2.校验验证码
        Object cacheCode = session.getAttribute("code");
        String code = loginForm.getCode();
        if(cacheCode == null || !code.equals((String) cacheCode)){
            Result.fail("验证码错误！");
        }
        // 3.根据手机号查询用户
        User user = this.lambdaQuery()
                .eq(User::getPhone, loginForm.getPhone())
                .one();
        if(user == null){  // 如果不存在，则自动注册新用户，存入数据库
            // TODO: 注册新用户（User + UserInfo），存入数据库
            user = this.createUserWithPhone(loginForm); // 并返回存储结果user
        }
//        // 4.登录并保存用户信息到session
//        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
//        // 5.返回成功，无数据
//        return Result.ok();

        // 4.登录并保存token（随机字符串）到redis
        String token = UUID.randomUUID().toString(true);
            // 1.将User对象转为HashMap存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true));
//                        .setFieldValueEditor(
//                                (fieldName, fieldValue) -> fieldValue.toString()));
        userMap.replaceAll((s, v) -> v.toString());
            // 2.存储
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
            // 3.设置token有效期
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        // 5.返回token
        return Result.ok(token);
    }

    @Override
    public Result<Null> sendCode(String phone, HttpSession session) {
//        // 1.校验手机号格式
//        if(!RegexUtils.isPhoneInvalid(phone)){
//            return Result.fail("手机号格式错误！");
//        }
        // 2.生成6位数字验证码
        String code = RandomUtil.randomNumbers(6);
        // 3.保存验证码到session
        session.setAttribute("code", code);
        // 4.发送验证码
        log.info("验证码发送成功：{}", code);
        // 5.返回成功消息
        return Result.ok();
    }
}
