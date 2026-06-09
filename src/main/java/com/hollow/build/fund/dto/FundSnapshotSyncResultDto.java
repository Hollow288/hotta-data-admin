package com.hollow.build.fund.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 基金扩展快照同步结果。
 */
@Data
@Schema(name = "FundSnapshotSyncResultDto", description = "基金扩展快照同步结果")
public class FundSnapshotSyncResultDto implements Serializable {

    @Schema(description = "基金代码")
    private String fundCode;

    @Schema(description = "本次拉取并写入的阶段排名条数")
    private Integer periodRankCount;

    @Schema(description = "备注")
    private String note;
}
