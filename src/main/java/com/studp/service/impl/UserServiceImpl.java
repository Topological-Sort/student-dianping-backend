package com.studp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.studp.dto.Void;
import com.studp.dto.LoginFormDTO;
import com.studp.dto.Result;
import com.studp.dto.UserDTO;
import com.studp.entity.User;
import com.studp.mapper.UserMapper;
import com.studp.service.IUserService;
import com.studp.utils.RegexUtils;
import com.studp.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.studp.utils.RedisConstants.*;
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
    private final UserMapper userMapper;

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
            user = this.createUserWithPhone(loginForm); // 并返回存储结果user
        }
//        /* Session+Cookie方式 */
//        // 4.登录并保存用户信息到session
//        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
//        // 5.返回成功，无数据
//        return Result.ok();

        /* Token方式 */
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

    /**
     * 压测模拟生成token
     * @param loginForm 登录表单信息（主要是手机号）
     * @return token
     */
    @Override
    public Result<String> testLogin(LoginFormDTO loginForm) {
        // 根据手机号查询用户
        User user = this.lambdaQuery()
                .eq(User::getPhone, loginForm.getPhone())
                .one();
        if(user == null){  // 如果不存在，则自动注册新用户，存入数据库
            user = this.createUserWithPhone(loginForm); // 并返回存储结果user
        }
        /* Token方式 */
        // 登录并保存token（随机字符串）到redis
        String token = UUID.randomUUID().toString(true);
        // 将User对象转为HashMap存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true));
//                        .setFieldValueEditor(
//                                (fieldName, fieldValue) -> fieldValue.toString()));
        userMap.replaceAll((s, v) -> v.toString());
        // 存储
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        // 设置token有效期
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        // 返回token
        return Result.ok(token);
    }

    @Override
    public Result<Void> sendCode(String phone, HttpSession session) {
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

    @Override
    public Result<UserDTO> queryUserById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.fail("用户不存在！");
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        return Result.ok(userDTO);
    }

    @Override
    public Result<Void> logout(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        // 清除当前用户对应的在redis中的token
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.delete(tokenKey);
        return Result.ok();
    }

    @Override
    public Result<Void> sign() {
        // 获取当前用户id和时间
        String userId = UserHolder.getUser().getId().toString();
        LocalDateTime now = LocalDateTime.now();
        // 拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 获取天数，存入redis的BitMap( [sign:userId:year_month] -> bit_day )
        int day = now.getDayOfMonth();
        stringRedisTemplate.opsForValue().setBit(key, day - 1, true);
        return Result.ok();
    }

    @Override
    public Result<Integer> signCount() {
        // 获取当前用户id和时间
        String userId = UserHolder.getUser().getId().toString();
        LocalDateTime now = LocalDateTime.now();
        // 拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 获取天数，获取该用户本月的所有签到记录
        int day = now.getDayOfMonth();
        // 获取本月截止今天为止的所有的签到记录，返回的是一个十进制的数字 BITFIELD sign:5:202203 GET u14 0
        List<Long> results = stringRedisTemplate.opsForValue().bitField( key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(day))
                        .valueAt(0));
        if (results == null || results.isEmpty()){
            return Result.ok(0);
        }
        Long num = results.get(0);  // 本月所有签到记录，从1号到月底相加的十进制数
        // 统计连续签到天数
        int count = 0;
        while (num != 0) {
            if ((num & 1) % 2 != 0)  count++;
            else  break;
            num >>= 1;
        }
        return Result.ok(count);
    }
}
