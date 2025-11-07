package com.hollow.build.service.impl;

import com.hollow.build.dto.BlogDateListDto;
import com.hollow.build.dto.BlogDateMenuDto;
import com.hollow.build.entity.mysql.BlogPost;
import com.hollow.build.repository.mysql.BlogMapper;
import com.hollow.build.service.BlogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;


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
}
