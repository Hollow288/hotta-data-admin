package com.hollow.build.agent.log;

import com.hollow.build.agent.entity.AgentAiCallLog;
import com.hollow.build.agent.entity.AgentRequestLog;
import com.hollow.build.agent.repository.AgentAiCallLogMapper;
import com.hollow.build.agent.repository.AgentRequestLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Agent 日志写入入口。
 *
 * <p>所有写库操作走 {@code @Async}，避免拖慢用户请求。日志失败不影响主流程，
 * 这里只在内部 catch 后打 warn，不向上抛。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentLogService {

    private final AgentRequestLogMapper requestLogMapper;
    private final AgentAiCallLogMapper aiCallLogMapper;

    /** 异步保存主表，并把生成的主键回填到所有同 requestId 的明细行。 */
    @Async
    public void saveRequestLog(AgentRequestLog logEntity) {
        try {
            requestLogMapper.insert(logEntity);
            if (logEntity.getId() != null && logEntity.getRequestId() != null) {
                aiCallLogMapper.updateRequestLogIdByRequestId(
                        logEntity.getRequestId(), logEntity.getId());
            }
        } catch (Exception e) {
            log.warn("保存 agent_request_log 失败 requestId={}", logEntity.getRequestId(), e);
        }
    }

    /** 异步保存单次 AI 调用明细。 */
    @Async
    public void saveAiCallLog(AgentAiCallLog logEntity) {
        try {
            aiCallLogMapper.insert(logEntity);
        } catch (Exception e) {
            log.warn("保存 agent_ai_call_log 失败 requestId={} iter={}",
                    logEntity.getRequestId(), logEntity.getIterationIndex(), e);
        }
    }
}
