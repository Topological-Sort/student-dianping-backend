package com.studp.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.studp.dto.Null;
import com.studp.dto.Result;
import com.studp.dto.UserDTO;
import com.studp.entity.Blog;
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
        // 获取登录用户
        Long userId = UserHolder.getUser().getId();
        blog.setUserId(userId);
        log.info("【Blog/saveBlog】用户保存博客 userId = {}, blog = {}", userId, blog);
        // 保存探店博文
        blogService.save(blog);
        // 返回id
        return Result.ok(blog.getId());
    }

    @GetMapping("/{id}")
    public Result<Blog> getBlog(@PathVariable Long id) {
        log.info("【Blog/getBlog】查看博客详情，blogId = {}", id);
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
    @GetMapping("/of/me")
    public Result<List<Blog>> queryMyBlog(
            @RequestParam(value = "current", defaultValue = "1") Integer current) {
        Long userId = UserHolder.getUser().getId();
        return blogService.pageQueryUserBlog(current, userId);
    }

    @GetMapping("/hot")
    public Result<List<Blog>> queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.pageQueryHotBlog(current);
    }
}
