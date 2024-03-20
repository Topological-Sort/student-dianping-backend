package com.studp.controller;


import com.studp.dto.Void;
import com.studp.dto.Result;
import com.studp.dto.UserDTO;
import com.studp.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/follow")
public class FollowController {
    @Resource
    IFollowService followService;

    /**
     * 关注
     * @param id 要关注的博主id
     * @param isFollow 请求关注(true) / 取关(false)
     * @return 无
     */
    @PutMapping("/{id}/{isFollow}")
    public Result<Void> follow(@PathVariable Long id,
                               @PathVariable Boolean isFollow){
        return followService.follow(id, isFollow);
    }

    /**
     * 是否关注
     * @param id 点进去的用户id
     * @return 关注/未关注
     */
    @GetMapping("/or/not/{id}")
    public Result<Boolean> isFollow(@PathVariable Long id){
        return followService.isFollow(id);
    }

    /**
     * 共同关注
     * @param id 点进去的用户id
     * @return 共同关注的用户列表
     */
    @GetMapping("/common/{id}")
    public Result<List<UserDTO>> commonFollows(@PathVariable Long id){
        return followService.commonFollow(id);
    }
}
