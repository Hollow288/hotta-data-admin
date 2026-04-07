package com.hollow.build.service;

import com.alibaba.fastjson2.JSON;
import com.hollow.build.config.OcrConfigurationProperties;
import com.hollow.build.dto.OcrTaskDto;
import com.hollow.build.utils.RedisUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * OCR 任务状态存储服务，统一负责 Redis 中任务状态的读写与超时判定。
 */
@Service
@RequiredArgsConstructor
public class OcrTaskStateService {

    public static final String OCR_RESULT_PREFIX = "ocr:result:";

    private final RedisUtil redisUtil;
    private final OcrConfigurationProperties ocrConfig;

    /**
     * 创建新的 PENDING 任务记录并写入 Redis。
     */
    public OcrTaskDto createPendingTask(String taskId) {
        long now = System.currentTimeMillis();
        OcrTaskDto dto = OcrTaskDto.builder()
                .taskId(taskId)
                .status("PENDING")
                .retryCount(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
        persist(dto);
        return dto;
    }

    /**
     * 根据任务 ID 读取 Redis 中的任务记录。
     */
    public OcrTaskDto getTask(String taskId) {
        Object resultJson = redisUtil.get(buildKey(taskId));
        if (resultJson == null) {
            return null;
        }
        return JSON.parseObject(resultJson.toString(), OcrTaskDto.class);
    }

    /**
     * 更新任务状态并刷新 TTL。
     */
    public OcrTaskDto updateStatus(String taskId,
                                   String status,
                                   List<OcrTaskDto.OcrResultItem> results,
                                   String errorMsg,
                                   Integer retryCount) {
        long now = System.currentTimeMillis();
        OcrTaskDto current = getTask(taskId);
        OcrTaskDto dto = current != null ? current : new OcrTaskDto();
        dto.setTaskId(taskId);
        dto.setStatus(status);
        dto.setResults(results);
        dto.setErrorMsg(errorMsg);
        dto.setRetryCount(retryCount != null ? retryCount : (dto.getRetryCount() != null ? dto.getRetryCount() : 0));
        dto.setCreatedAt(dto.getCreatedAt() != null ? dto.getCreatedAt() : now);
        dto.setUpdatedAt(now);
        persist(dto);
        return dto;
    }

    /**
     * 将任务标记为等待重试，状态回到 PENDING。
     */
    public OcrTaskDto markPendingForRetry(String taskId, int retryCount, String errorMsg) {
        return updateStatus(taskId, "PENDING", null, errorMsg, retryCount);
    }

    /**
     * 将长时间未被消费的任务标记为 FAILED。
     */
    public OcrTaskDto markPendingTimeout(String taskId) {
        return updateStatus(taskId, "FAILED", null, "OCR 任务排队超时，请稍后重新提交", null);
    }

    /**
     * 判断任务是否已经在 PENDING 状态超时。
     */
    public boolean isPendingTimedOut(OcrTaskDto task) {
        return task != null
                && "PENDING".equals(task.getStatus())
                && task.getUpdatedAt() != null
                && System.currentTimeMillis() - task.getUpdatedAt() > ocrConfig.getPendingTimeoutSeconds() * 1000;
    }

    /**
     * 获取所有 OCR 任务 ID，用于定时扫描异常任务。
     */
    public Set<String> findAllTaskIds() {
        return redisUtil.keys(OCR_RESULT_PREFIX + "*").stream()
                .map(key -> key.substring(OCR_RESULT_PREFIX.length()))
                .collect(Collectors.toSet());
    }

    private void persist(OcrTaskDto dto) {
        redisUtil.set(buildKey(dto.getTaskId()), JSON.toJSONString(dto), ocrConfig.getResultTtl());
    }

    private String buildKey(String taskId) {
        return OCR_RESULT_PREFIX + taskId;
    }
}
