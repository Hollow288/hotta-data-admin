package com.hollow.build.repository.mysql;

import com.hollow.build.dto.BlogDateListDto;
import com.hollow.build.dto.BlogDateMenuDto;
import com.hollow.build.entity.mysql.BlogPost;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface BlogMapper {

    List<BlogDateMenuDto> getBlogDateMenu();

    List<BlogDateListDto> getBlogDateListByDate(String date);

    BlogPost selectBlogById(String articleId);

    List<BlogDateListDto> selectBlogByKeyWord(String keyWord);
}
