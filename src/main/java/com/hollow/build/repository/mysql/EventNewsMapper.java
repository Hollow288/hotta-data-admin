package com.hollow.build.repository.mysql;

import com.hollow.build.dto.EventNewsDto;
import com.hollow.build.entity.mysql.EventNews;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface EventNewsMapper {
    
    List<EventNews> selectByPage(int offset, Integer pageSize, String searchName);

    long count(String searchName);

    @Select("select * from event_news where news_id = #{newsId}")
    EventNews selectById(Integer newsId);

    void addEventNews(EventNewsDto eventNewsDto);

    void updateEventNews(@Param("newsId") Integer newsId,
                         @Param("event") EventNewsDto eventNewsDto);

    void deleteEventNews(List<String> eventNewsList);
}
