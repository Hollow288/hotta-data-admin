package com.hollow.build.service.impl;

import com.hollow.build.dto.BlogDateListDto;
import com.hollow.build.dto.BlogDateMenuDto;
import com.hollow.build.dto.PageResult;
import com.hollow.build.entity.mysql.BlogPost;
import com.hollow.build.repository.mysql.BlogMapper;
import com.hollow.build.service.BlogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
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
        // 1. 假设前端传来的 date 格式是 "2023-11"
        YearMonth inputMonth = YearMonth.parse(date);

        // 2. 计算【月初时间】：2023-11-01 00:00:00
        String startDate = inputMonth.atDay(1)
                .atStartOfDay()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        // 3. 计算【下月月初】：2023-12-01 00:00:00
        // (YearMonth 会自动处理跨年，比如输入 2023-12，这里会自动变成 2024-01)
        String endDate = inputMonth.plusMonths(1)
                .atDay(1)
                .atStartOfDay()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        // 4. 传入两个参数调用 Mapper
        return blogMapper.getBlogDateListByDate(startDate, endDate);
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
