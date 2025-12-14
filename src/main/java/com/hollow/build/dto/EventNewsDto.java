package com.hollow.build.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.ZonedDateTime;

/**
* 活动资讯
* @TableName event_news
*/
@Data
public class EventNewsDto implements Serializable {

    /**
    * ID
    */
    @Schema(description ="ID")
    private Integer newsId;
    /**
    * 活动标题
    */
    @Schema(description ="活动标题")
    private String newsTitle;
    /**
    * 活动描述
    */
    @Schema(description ="活动描述")
    private String newsDescribe;
    /**
    * 活动缩略图地址
    */
    @Schema(description ="活动缩略图地址")
    private String newsImgUrl;
    /**
    * 活动开始时间
    */
    @Schema(description ="活动开始时间")
    private ZonedDateTime newsStart;
    /**
    * 活动结束时间
    */
    @Schema(description ="活动结束时间")
    private ZonedDateTime newsEnd;

    /**
     * 图片显示路径
     */
    @Schema(description ="图片显示路径")
    private String newsShowImgUrl;

}
