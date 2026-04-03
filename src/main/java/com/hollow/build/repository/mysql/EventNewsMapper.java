package com.hollow.build.repository.mysql;

import com.hollow.build.dto.EventNewsDto;
import com.hollow.build.entity.mysql.EventNews;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 活动资讯 MyBatis Mapper 接口，提供活动资讯相关的数据库操作。
 */
@Mapper
public interface EventNewsMapper {

    /**
     * 分页查询活动资讯列表。
     *
     * @param offset     偏移量
     * @param pageSize   每页数量
     * @param searchName 搜索关键词，可为空
     * @return 分页后的活动资讯列表
     */
    List<EventNews> selectByPage(int offset, Integer pageSize, String searchName);

    /**
     * 获取活动资讯总数（支持按名称搜索）。
     *
     * @param searchName 搜索关键词，可为空
     * @return 活动资讯总数
     */
    long count(String searchName);

    /**
     * 根据资讯 ID 查询活动资讯详情。
     *
     * @param newsId 资讯 ID
     * @return 活动资讯对象，未找到时返回 null
     */
    @Select("select * from event_news where news_id = #{newsId}")
    EventNews selectById(Integer newsId);

    /**
     * 新增活动资讯。
     *
     * @param eventNewsDto 活动资讯数据传输对象
     */
    void addEventNews(EventNewsDto eventNewsDto);

    /**
     * 更新指定 ID 的活动资讯。
     *
     * @param newsId       资讯 ID
     * @param eventNewsDto 更新后的活动资讯数据传输对象
     */
    void updateEventNews(@Param("newsId") Integer newsId,
                         @Param("event") EventNewsDto eventNewsDto);

    /**
     * 批量删除活动资讯。
     *
     * @param eventNewsList 待删除的资讯 ID 列表
     */
    void deleteEventNews(List<String> eventNewsList);
}
