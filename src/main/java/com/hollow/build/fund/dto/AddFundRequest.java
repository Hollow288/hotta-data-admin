package com.hollow.build.fund.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 添加监控基金的请求体。
 */
@Data
@Schema(name = "AddFundRequest", description = "添加监控基金请求")
public class AddFundRequest implements Serializable {

    /** 基金代码（6位） */
    @Schema(description = "基金代码（6位）", example = "270042")
    private String code;
}
