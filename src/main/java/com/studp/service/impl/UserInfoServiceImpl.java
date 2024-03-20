package com.studp.service.impl;

import com.studp.dto.Result;
import com.studp.entity.UserInfo;
import com.studp.mapper.UserInfoMapper;
import com.studp.service.IUserInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-24
 */
@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {

    @Resource
    UserInfoMapper userInfoMapper;

    @Override
    public Result<UserInfo> getInfo(Long userId) {
        // 查询详情
        UserInfo info = userInfoMapper.selectById(userId);
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
