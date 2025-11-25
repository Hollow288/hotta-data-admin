package com.hollow.build.repository.mysql;

import com.hollow.build.dto.BlogDateListDto;
import com.hollow.build.dto.BlogDateMenuDto;
import com.hollow.build.entity.mysql.BlogPost;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BlogMapper {

    List<BlogDateMenuDto> getBlogDateMenu();

    List<BlogDateListDto> getBlogDateListByDate(String date);

    BlogPost selectBlogById(String articleId);

    List<BlogDateListDto> selectBlogByKeyWord(String keyWord);

    Integer getCountBlog(@Param("searchName") String searchName);

    List<BlogDateListDto> getBlogInfoByPage(@Param("offset") int offset, @Param("limit") int limit, @Param("searchName") String searchName);

    void addBlogPost(BlogPost blogPost);

    void updateBlog(@Param("blogId") Integer blogId, @Param("blogPost") BlogPost blogPost);

    void deleteBlog(List<String> blogList);
}
