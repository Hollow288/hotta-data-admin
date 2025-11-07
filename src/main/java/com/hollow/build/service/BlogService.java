package com.hollow.build.service;

import com.hollow.build.dto.BlogDateListDto;
import com.hollow.build.dto.BlogDateMenuDto;
import com.hollow.build.entity.mysql.BlogPost;

import java.util.List;

public interface BlogService {
    List<BlogDateMenuDto> selectBlogDateMenu();

    List<BlogDateListDto> selectBlogDateListByDate(String date);

    BlogPost selectBlogById(String articleId);

    List<BlogDateListDto> selectBlogByKeyWord(String keyWord);
}
