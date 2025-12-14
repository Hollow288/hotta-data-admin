package com.hollow.build.entity.mysql;

import java.io.Serializable;
import java.time.ZonedDateTime;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
* 活动资讯
* @TableName event_news
*/
@Data
public class EventNews implements Serializable {

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
    * 是否删除（0未删除 1已删除）
    */
    @Schema(description ="是否删除（0未删除 1已删除）")
    private String delFlag;

}
