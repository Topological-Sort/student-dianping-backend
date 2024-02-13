package com.studp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.studp.dto.Null;
import com.studp.dto.LoginFormDTO;
import com.studp.dto.Result;
import com.studp.dto.UserDTO;
import com.studp.entity.User;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {

    User createUserWithPhone(LoginFormDTO loginForm);

    Result<String> login(LoginFormDTO loginForm, HttpSession session);

    Result<Null> sendCode(String phone, HttpSession session);

    Result<UserDTO> queryUserById(Long userId);

    Result<Null> logout(HttpServletRequest request);

    Result<Null> sign();

    Result<Integer> signCount();
}
