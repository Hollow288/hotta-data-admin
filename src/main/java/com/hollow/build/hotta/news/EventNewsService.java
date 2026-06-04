package com.hollow.build.hotta.news;

import com.hollow.build.common.PageResult;

import java.util.Map;

/**
 * 活动新闻服务接口，提供活动新闻的增删改查功能
 */
public interface EventNewsService {

    /**
     * 分页查询活动新闻列表
     *
     * @param page 当前页码
     * @param pageSize 每页数量
     * @param searchName 搜索关键词
     * @return 分页结果
     */
    PageResult<EventNewsDto> eventNewsByPage(Integer page, Integer pageSize, String searchName);

    /**
     * 根据新闻ID查询活动新闻详情
     *
     * @param newsId 新闻ID
     * @return 活动新闻DTO
     */
    EventNewsDto eventNewsById(Integer newsId);

    /**
     * 新增活动新闻
     *
     * @param eventNewsDto 活动新闻DTO
     */
    void addEventNews(EventNewsDto eventNewsDto);

    /**
     * 更新活动新闻
     *
     * @param newsId 新闻ID
     * @param eventNewsDto 更新后的活动新闻DTO
     */
    void updateEventNews(Integer newsId, EventNewsDto eventNewsDto);

    /**
     * 删除活动新闻
     *
     * @param eventEventNews 包含待删除新闻信息的Map
     */
    void deleteEventNews(Map<String, Object> eventEventNews);
}
