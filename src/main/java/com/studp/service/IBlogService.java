package com.studp.service;

import com.studp.dto.Null;
import com.studp.dto.Result;
import com.studp.dto.UserDTO;
import com.studp.entity.Blog;
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
public interface IBlogService extends IService<Blog> {

    Result<Null> likeBlog(Long id);

    Result<Blog> getBlogById(Long id);

    Result<List<Blog>> pageQueryHotBlog(Integer current);

    Result<List<UserDTO>> queryBlogLikes(Long id);

    Result<List<Blog>> pageQueryUserBlog(Integer current, Long id);
}
