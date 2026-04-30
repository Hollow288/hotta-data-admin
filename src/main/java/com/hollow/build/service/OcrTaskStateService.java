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
    public static final String OCR_ACTIVE_TASKS_KEY = "ocr:active-tasks";

    private final RedisUtil redisUtil;
    private final OcrConfigurationProperties ocrConfig;

    /**
     * 创建新的 PENDING 任务记录并写入 Redis，同时将 taskId 加入活跃任务集合（ocr:active-tasks）。
     */
    public OcrTaskDto createPendingTask(String taskId) {
        return createPendingTask(taskId, null);
    }

    /**
     * 创建新的 PENDING 任务记录，并保留用户提交时选择的返回模式，便于消费者按 mode 调用远程服务。
     */
    public OcrTaskDto createPendingTask(String taskId, String mode) {
        long now = System.currentTimeMillis();
        OcrTaskDto dto = OcrTaskDto.builder()
                .taskId(taskId)
                .status("PENDING")
                .retryCount(0)
                .mode(mode)
                .createdAt(now)
                .updatedAt(now)
                .build();
        persist(dto);
        redisUtil.addToSet(OCR_ACTIVE_TASKS_KEY, taskId);
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
     * 更新任务状态并刷新 TTL。当任务进入终态（SUCCESS/FAILED）时，自动从活跃任务集合中移除。
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
        if ("SUCCESS".equals(status) || "FAILED".equals(status)) {
            redisUtil.removeSetMembers(OCR_ACTIVE_TASKS_KEY, taskId);
        }
        return dto;
    }

    /**
     * 写入识别成功的最终结果。根据远程服务返回的 mode 选择落库到 results / textList / fullText 之一，
     * 并附带 pages、elapseSeconds 等元数据。状态固定为 SUCCESS，会从活跃任务集合中移除。
     */
    public OcrTaskDto updateSuccess(String taskId,
                                    String mode,
                                    List<OcrTaskDto.OcrResultItem> results,
                                    List<String> textList,
                                    String fullText,
                                    Integer pages,
                                    Double elapseSeconds,
                                    Integer retryCount) {
        long now = System.currentTimeMillis();
        OcrTaskDto current = getTask(taskId);
        OcrTaskDto dto = current != null ? current : new OcrTaskDto();
        dto.setTaskId(taskId);
        dto.setStatus("SUCCESS");
        dto.setMode(mode);
        dto.setResults(results);
        dto.setTextList(textList);
        dto.setFullText(fullText);
        dto.setPages(pages);
        dto.setElapseSeconds(elapseSeconds);
        dto.setErrorMsg(null);
        dto.setRetryCount(retryCount != null ? retryCount : (dto.getRetryCount() != null ? dto.getRetryCount() : 0));
        dto.setCreatedAt(dto.getCreatedAt() != null ? dto.getCreatedAt() : now);
        dto.setUpdatedAt(now);
        persist(dto);
        redisUtil.removeSetMembers(OCR_ACTIVE_TASKS_KEY, taskId);
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
     * 获取所有活跃（未终态）的 OCR 任务 ID，用于定时扫描异常任务。
     * 通过 Redis Set（ocr:active-tasks）维护，避免使用 KEYS 命令阻塞 Redis。
     */
    public Set<String> findAllTaskIds() {
        Set<Object> members = redisUtil.getSetMembers(OCR_ACTIVE_TASKS_KEY);
        if (members == null || members.isEmpty()) {
            return Set.of();
        }
        return members.stream()
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    /**
     * 从活跃任务集合中移除指定任务 ID，用于清理已过期的残留记录。
     */
    public void removeFromActiveSet(String taskId) {
        redisUtil.removeSetMembers(OCR_ACTIVE_TASKS_KEY, taskId);
    }

    private void persist(OcrTaskDto dto) {
        redisUtil.set(buildKey(dto.getTaskId()), JSON.toJSONString(dto), ocrConfig.getResultTtl());
    }

    private String buildKey(String taskId) {
        return OCR_RESULT_PREFIX + taskId;
    }
}
