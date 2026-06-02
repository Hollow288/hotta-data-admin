package com.hollow.build.hotta.news;

import com.hollow.build.hotta.news.EventNewsDto;
import com.hollow.build.common.PageResult;
import com.hollow.build.hotta.news.EventNews;
import com.hollow.build.hotta.news.EventNewsMapper;
import com.hollow.build.hotta.news.EventNewsService;
import com.hollow.build.utils.DtoMapperUtil;
import com.hollow.build.utils.MinioUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 活动资讯服务实现类，负责活动资讯的分页查询、详情查询和后台维护。
 */
@Service
@RequiredArgsConstructor
public class EventNewsServiceImpl implements EventNewsService {

    private final EventNewsMapper eventNewsMapper;

    private final MinioUtil minioUtil;

    /**
     * 分页查询活动资讯，并补全展示图片访问地址。
     *
     * @param page 当前页码
     * @param pageSize 每页条数
     * @param searchName 搜索关键词，可为空
     * @return 活动资讯分页结果
     */
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

    /**
     * 根据活动 ID 查询详情，并补全展示图片访问地址。
     *
     * @param newsId 活动资讯 ID
     * @return 活动资讯详情，未找到时返回 null
     */
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

    /**
     * 新增活动资讯。
     *
     * @param eventNewsDto 活动资讯数据
     */
    @Override
    @CacheEvict(value = "eventNewsPage", allEntries = true)
    public void addEventNews(EventNewsDto eventNewsDto) {
        eventNewsMapper.addEventNews(eventNewsDto);

    }

    /**
     * 更新指定活动资讯。
     *
     * @param newsId 活动资讯 ID
     * @param eventNewsDto 更新后的活动资讯数据
     */
    @Override
    @Caching(evict = {
            @CacheEvict(value = "eventNewsPage", allEntries = true), // 分页都要清
            @CacheEvict(value = "eventNewsById", key = "#newsId")    // 只清理当前 newsId
    })
    public void updateEventNews(Integer newsId, EventNewsDto eventNewsDto) {
        eventNewsMapper.updateEventNews(newsId, eventNewsDto);
    }

    /**
     * 批量删除活动资讯。
     *
     * @param eventEventNews 包含待删除活动 ID 列表的参数映射
     */
    @Override
    @CacheEvict(value = {"eventNewsPage", "eventNewsById"}, allEntries = true)
    public void deleteEventNews(Map<String, Object> eventEventNews) {
        List<String> eventNewsList = (List<String>)eventEventNews.get("eventNewsIds");
        if (eventNewsList != null && !eventNewsList.isEmpty()) {
            eventNewsMapper.deleteEventNews(eventNewsList);
        }
    }
}
