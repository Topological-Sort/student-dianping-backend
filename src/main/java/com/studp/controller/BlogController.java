package com.studp.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.studp.dto.Null;
import com.studp.dto.Result;
import com.studp.dto.ScrollResult;
import com.studp.dto.UserDTO;
import com.studp.entity.Blog;
import com.studp.entity.Follow;
import com.studp.entity.User;
import com.studp.service.IBlogService;
import com.studp.service.IUserService;
import com.studp.utils.RedisConstants;
import com.studp.utils.SystemConstants;
import com.studp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Set;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@Slf4j
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;
    @Resource
    private IUserService userService;

    @PostMapping
    public Result<Long> saveBlog(@RequestBody Blog blog) {
        return blogService.saveBlog(blog);
    }

    @GetMapping("/{id}")
    public Result<Blog> getBlog(@PathVariable Long id) {
        // 查询时还需要设置isLike字段值
        return blogService.getBlogById(id);
    }

    /**
     * 获取为当前博客点赞的前5个用户信息
     * @param id 博客id
     * @return 用户信息(UserDTO) 列表
     */
    @GetMapping("/likes/{id}")
    public Result<List<UserDTO>> queryBlogLikes(@PathVariable Long id) {
        return blogService.queryBlogLikes(id);
    }

    @PutMapping("/like/{id}")
    public Result<Null> likeBlog(@PathVariable("id") Long id) {
        return blogService.likeBlog(id);
    }

    /**
     * 分页查询博主发表的博客
     * @param current 当前页码
     * @param id 博主id
     * @return 博客列表
     */
    @GetMapping("/of/user")
    public Result<List<Blog>> pageQueryUserBlog(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam("id") Long id) {
        return blogService.pageQueryUserBlog(current, id);
    }
    @GetMapping("/of/me")  // 分页查询当前用户发表的博客
    public Result<List<Blog>> pageQueryMyBlog(
            @RequestParam(value = "current", defaultValue = "1") Integer current) {
        Long userId = UserHolder.getUser().getId();
        return blogService.pageQueryUserBlog(current, userId);
    }
    /**
     * 按发布时间降序，分页查询当前登录用户所关注的博主的博客，和上面两种分页查询存在区别
     * @param max 上一次访问的时间戳最大（最近）的博客id
     * @param offset 一页显示的博客数
     * @return 关注博主的博客列表
     */
    @GetMapping("/of/follow")
    public Result<ScrollResult<Blog>> pageQueryBlogOfFollow(
            @RequestParam("lastId") Long max,
            @RequestParam(value = "offset", defaultValue = "0") Integer offset){
        return blogService.pageQueryBlogOfFollow(max, offset);
    }

    @GetMapping("/hot")
    public Result<List<Blog>> queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.pageQueryHotBlog(current);
    }
}
