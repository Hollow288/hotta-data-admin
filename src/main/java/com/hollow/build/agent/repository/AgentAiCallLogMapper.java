package com.hollow.build.agent.repository;

import com.hollow.build.agent.entity.AgentAiCallLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AgentAiCallLogMapper {

    /** 插入明细表 */
    void insert(AgentAiCallLog log);

    /** 主表写入完成后，把 request_log_id 回填给同 requestId 的所有明细行 */
    int updateRequestLogIdByRequestId(@Param("requestId") String requestId,
                                      @Param("requestLogId") Long requestLogId);
}
