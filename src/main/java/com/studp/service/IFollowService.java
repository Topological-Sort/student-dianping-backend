package com.studp.service;

import com.studp.dto.Void;
import com.studp.dto.Result;
import com.studp.dto.UserDTO;
import com.studp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    Result<Void> follow(Long id, Boolean isFollow);

    Result<Boolean> isFollow(Long id);


    Result<List<UserDTO>> commonFollow(Long id);
}
