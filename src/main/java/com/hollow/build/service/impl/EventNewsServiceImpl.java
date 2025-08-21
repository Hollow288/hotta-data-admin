package com.hollow.build.service.impl;

import com.hollow.build.common.ApiResponse;
import com.hollow.build.dto.EventNewsDto;
import com.hollow.build.dto.PageResult;
import com.hollow.build.entity.EventNews;
import com.hollow.build.mapper.EventNewsMapper;
import com.hollow.build.service.EventNewsService;
import com.hollow.build.utils.DtoMapperUtil;
import com.hollow.build.utils.MinioUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EventNewsServiceImpl implements EventNewsService {

    private final EventNewsMapper eventNewsMapper;

    private final MinioUtil minioUtil;

    @Override
    @Cacheable(value = "eventNewsPage", key = "#page + '-' + #pageSize + '-' + #searchName")
    public PageResult<EventNewsDto> eventNewsByPage(Integer page, Integer pageSize, String searchName) {
        int offset = (page - 1) * pageSize;
        List<EventNews> list = eventNewsMapper.selectByPage(offset, pageSize, searchName);

        List<EventNewsDto> eventNewsDtoList = list.stream()
                .map(eventNews -> DtoMapperUtil.map(eventNews, EventNewsDto.class))
                .peek(e -> e.setNewsShowImgUrl(minioUtil.fileUrlEncoderChance(e.getNewsImgUrl())))
                .toList();

        long total = eventNewsMapper.count(searchName);

        return new PageResult<>(eventNewsDtoList, total);
    }

    @Override
    @Cacheable(value = "eventNewsById", key = "#newsId")
    public EventNewsDto eventNewsById(Integer newsId) {
        EventNews eventNews = eventNewsMapper.selectById(newsId);
        if (eventNews != null) {
            EventNewsDto eventNewsDto = DtoMapperUtil.map(eventNews, EventNewsDto.class);
            eventNewsDto.setNewsShowImgUrl(minioUtil.fileUrlEncoderChance(eventNewsDto.getNewsImgUrl()));
            return eventNewsDto;
        }
        return null;
    }

    @Override
    @CacheEvict(value = "eventNewsPage", allEntries = true)
    public void addEventNews(EventNewsDto eventNewsDto) {
        eventNewsMapper.addEventNews(eventNewsDto);

    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "eventNewsPage", allEntries = true), // 分页都要清
            @CacheEvict(value = "eventNewsById", key = "#newsId")    // 只清理当前 newsId
    })
    public void updateEventNews(Integer newsId, EventNewsDto eventNewsDto) {
        eventNewsMapper.updateEventNews(newsId, eventNewsDto);
    }

    @Override
    public void deleteEventNews(Map<String, Object> eventEventNews) {
        List<String> eventNewsList = (List<String>)eventEventNews.get("eventNewsIds");
        if (eventNewsList != null && !eventNewsList.isEmpty()) {
            eventNewsMapper.deleteEventNews(eventNewsList);
        }
    }
}
