package com.studp.service;

import com.studp.dto.Result;
import com.studp.entity.UserInfo;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-24
 */
public interface IUserInfoService extends IService<UserInfo> {

    Result<UserInfo> getInfo(Long userId);
}
