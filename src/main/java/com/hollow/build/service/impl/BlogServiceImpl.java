package com.hollow.build.service.impl;

import com.hollow.build.dto.BlogDateListDto;
import com.hollow.build.dto.BlogDateMenuDto;
import com.hollow.build.dto.PageResult;
import com.hollow.build.entity.mysql.BlogPost;
import com.hollow.build.repository.mysql.BlogMapper;
import com.hollow.build.service.BlogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;


@Service
@RequiredArgsConstructor
public class BlogServiceImpl implements BlogService {

    private final BlogMapper blogMapper;

    @Override
    public List<BlogDateMenuDto> selectBlogDateMenu() {
        return blogMapper.getBlogDateMenu();
    }

    @Override
    public List<BlogDateListDto> selectBlogDateListByDate(String date) {
        return blogMapper.getBlogDateListByDate(date);
    }

    @Override
    public BlogPost selectBlogById(String articleId) {
        return blogMapper.selectBlogById(articleId);
    }

    @Override
    public List<BlogDateListDto> selectBlogByKeyWord(String keyWord) {

        List<BlogDateListDto> blogPosts = blogMapper.selectBlogByKeyWord(keyWord);

        return blogPosts;
    }

    @Override
    public PageResult<BlogDateListDto> selectBlogByPage(Integer page, Integer pageSize, String searchName) {
        int offset = (page - 1) * pageSize;
        int limit = pageSize;
        List<BlogDateListDto> allBlogInfoByPage = blogMapper.getBlogInfoByPage(offset,limit,searchName);

        return new PageResult<>(allBlogInfoByPage, blogMapper.getCountBlog(searchName));
    }

    @Override
    public void addBlog(BlogPost blogPost) {
        blogMapper.addBlogPost(blogPost);
    }

    @Override
    public void updateBlog(Integer blogId, BlogPost blogPost) {
        blogMapper.updateBlog(blogId, blogPost);
    }

    @Override
    public void deleteBlog(Map<String, Object> blogPost) {
        List<String> blogList = (List<String>)blogPost.get("blogIds");
        if (blogList != null && !blogList.isEmpty()) {
            blogMapper.deleteBlog(blogList);
        }
    }
}
