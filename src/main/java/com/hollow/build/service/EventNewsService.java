package com.hollow.build.service;

import com.hollow.build.common.ApiResponse;
import com.hollow.build.dto.EventNewsDto;
import com.hollow.build.dto.PageResult;
import com.hollow.build.entity.EventNews;

import java.util.List;
import java.util.Map;

public interface EventNewsService {
    PageResult<EventNewsDto> eventNewsByPage(Integer page, Integer pageSize, String searchName);

    EventNewsDto eventNewsById(Integer newsId);

    void addEventNews(EventNewsDto eventNewsDto);

    void updateEventNews(Integer newsId, EventNewsDto eventNewsDto);

    void deleteEventNews(Map<String, Object> eventEventNews);
}
