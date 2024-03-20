package com.studp.controller;


import com.studp.dto.Void;
import com.studp.dto.LoginFormDTO;
import com.studp.dto.Result;
import com.studp.dto.UserDTO;
import com.studp.entity.UserInfo;
import com.studp.service.IUserInfoService;
import com.studp.service.IUserService;
import com.studp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

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
    public Result<Void> sendCode(@RequestParam("phone") String phone, HttpSession session) {
        log.info("【User】发送验证码到手机号：{}", phone);
        return userService.sendCode(phone, session);
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result<String> login(@RequestBody LoginFormDTO loginForm, HttpSession session){
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
    public Result<Void> logout(HttpServletRequest request){
        return userService.logout(request);
    }

    /**
     * 获取个人基本信息
     * @return 个人基本信息（id、用户名、头像）
     */
    @GetMapping("/me")
    public Result<UserDTO> me(){
        Long userId = UserHolder.getUser().getId();
        return userService.queryUserById(userId);
    }

    /**
     * 查看用户详细信息
     * @param userId 用户id
     * @return 用户详细信息
     */
    @GetMapping("/info/{id}")
    public Result<UserInfo> info(@PathVariable("id") Long userId){
        return userInfoService.getInfo(userId);
    }

    /**
     * 当前登录用户签到
     * @return 无
     */
    @PostMapping("/sign")
    public Result<Void> sign(){
        return userService.sign();
    }

    /**
     * 统计用户连续签到次数
     * @return 连续签到次数
     */
    @GetMapping("/sign/count")
    public Result<Integer> signCount(){
        return userService.signCount();
    }
}
