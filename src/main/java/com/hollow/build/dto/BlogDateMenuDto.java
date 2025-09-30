package com.hollow.build.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class BlogDateMenuDto implements Serializable {

    /** 创建时间 */
    private String yearMonth;

    /** 数量 */
    private Integer count;
}
