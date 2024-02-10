package com.studp.controller;


import cn.hutool.core.bean.BeanUtil;
import com.studp.dto.Null;
import com.studp.dto.LoginFormDTO;
import com.studp.dto.Result;
import com.studp.dto.UserDTO;
import com.studp.entity.Blog;
import com.studp.entity.User;
import com.studp.entity.UserInfo;
import com.studp.service.IUserInfoService;
import com.studp.service.IUserService;
import com.studp.utils.SystemConstants;
import com.studp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    /**
     * 发送手机验证码
     */
    @PostMapping("/code")
    public Result<Null> sendCode(@RequestParam("phone") String phone, HttpSession session) {
        log.info("【User】发送验证码到手机号：{}", phone);
        return userService.sendCode(phone, session);
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result<String> login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        log.info("【User】用户登录：{}", loginForm);
        return userService.login(loginForm, session);
    }

    // 根据id查询用户DTO
    @GetMapping("/{id}")
    public Result<UserDTO> queryUserById(@PathVariable("id") Long userId){
        return userService.queryUserById(userId);
    }

    /**
     * 登出功能
     * @return 无
     */
    @PostMapping("/logout")
    public Result<Null> logout(HttpServletRequest request){
        return userService.logout(request);
    }

    @GetMapping("/me")
    public Result<UserDTO> me(){
        Long userId = UserHolder.getUser().getId();
        return userService.queryUserById(userId);
    }

    @GetMapping("/info/{id}")
    public Result<UserInfo> info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }
}
