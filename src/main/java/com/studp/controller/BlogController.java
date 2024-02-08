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
//        log.info("【Blog/saveBlog】用户id = {}，保存博客：{}", userId, blog);
        // 保存探店博文
        blogService.save(blog);
        // 返回id
        return Result.ok(blog.getId());
    }

    @GetMapping("/{id}")
    public Result<Blog> getBlog(@PathVariable Long id) {
//        log.info("【Blog/getBlog】blogId = {}", id);
        // 查询时还需要设置isLike字段值
        return blogService.getBlogById(id);
    }

    @GetMapping("/likes/{id}")
    public Result<Integer> getBlogLikes(@PathVariable Long id) {
        Integer likes = blogService.getById(id).getLiked();
        return Result.ok(likes);
    }

    @PutMapping("/like/{id}")
    public Result<Null> likeBlog(@PathVariable("id") Long id) {
        return blogService.likeBlog(id);
    }

    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId())
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    @GetMapping("/hot")
    public Result<List<Blog>> queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.pageQueryHotBlog(current);
    }
}
