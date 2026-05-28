package com.hollow.build.service;

import com.alibaba.fastjson2.JSON;
import com.hollow.build.config.OcrConfigurationProperties;
import com.hollow.build.dto.OcrTranslateImageTaskDto;
import com.hollow.build.utils.RedisUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * OCR 图片翻译标注任务状态存储服务。
 */
@Service
@RequiredArgsConstructor
public class OcrTranslateImageTaskStateService {

    public static final String RESULT_PREFIX = "ocr:translate-image:result:";
    public static final String ACTIVE_TASKS_KEY = "ocr:translate-image:active-tasks";

    private final RedisUtil redisUtil;
    private final OcrConfigurationProperties ocrConfig;

    public OcrTranslateImageTaskDto createPendingTask(String taskId,
                                                      String targetLanguage,
                                                      Double minConfidence) {
        long now = System.currentTimeMillis();
        OcrTranslateImageTaskDto dto = OcrTranslateImageTaskDto.builder()
                .taskId(taskId)
                .status("PENDING")
                .retryCount(0)
                .targetLanguage(targetLanguage)
                .minConfidence(minConfidence)
                .createdAt(now)
                .updatedAt(now)
                .build();
        persist(dto);
        redisUtil.addToSet(ACTIVE_TASKS_KEY, taskId);
        return dto;
    }

    public OcrTranslateImageTaskDto getTask(String taskId) {
        Object resultJson = redisUtil.get(buildKey(taskId));
        if (resultJson == null) {
            return null;
        }
        return JSON.parseObject(resultJson.toString(), OcrTranslateImageTaskDto.class);
    }

    public OcrTranslateImageTaskDto updateStatus(String taskId,
                                                 String status,
                                                 String errorMsg,
                                                 Integer retryCount) {
        long now = System.currentTimeMillis();
        OcrTranslateImageTaskDto current = getTask(taskId);
        OcrTranslateImageTaskDto dto = current != null ? current : new OcrTranslateImageTaskDto();
        dto.setTaskId(taskId);
        dto.setStatus(status);
        dto.setErrorMsg(errorMsg);
        dto.setRetryCount(retryCount != null ? retryCount : (dto.getRetryCount() != null ? dto.getRetryCount() : 0));
        dto.setCreatedAt(dto.getCreatedAt() != null ? dto.getCreatedAt() : now);
        dto.setUpdatedAt(now);
        persist(dto);
        if ("SUCCESS".equals(status) || "FAILED".equals(status)) {
            redisUtil.removeSetMembers(ACTIVE_TASKS_KEY, taskId);
        } else {
            redisUtil.addToSet(ACTIVE_TASKS_KEY, taskId);
        }
        return dto;
    }

    public OcrTranslateImageTaskDto updateSuccess(String taskId,
                                                  int itemCount,
                                                  String resultImageUrl,
                                                  String resultBucketName,
                                                  String resultObjectName,
                                                  Integer retryCount) {
        long now = System.currentTimeMillis();
        OcrTranslateImageTaskDto current = getTask(taskId);
        OcrTranslateImageTaskDto dto = current != null ? current : new OcrTranslateImageTaskDto();
        dto.setTaskId(taskId);
        dto.setStatus("SUCCESS");
        dto.setItemCount(itemCount);
        dto.setResultImageUrl(resultImageUrl);
        dto.setResultBucketName(resultBucketName);
        dto.setResultObjectName(resultObjectName);
        dto.setErrorMsg(null);
        dto.setRetryCount(retryCount != null ? retryCount : (dto.getRetryCount() != null ? dto.getRetryCount() : 0));
        dto.setCreatedAt(dto.getCreatedAt() != null ? dto.getCreatedAt() : now);
        dto.setUpdatedAt(now);
        persist(dto);
        redisUtil.removeSetMembers(ACTIVE_TASKS_KEY, taskId);
        return dto;
    }

    public OcrTranslateImageTaskDto markPendingForRetry(String taskId, int retryCount, String errorMsg) {
        return updateStatus(taskId, "PENDING", errorMsg, retryCount);
    }

    public OcrTranslateImageTaskDto markPendingTimeout(String taskId) {
        return updateStatus(taskId, "FAILED", "OCR 翻译图片任务排队超时，请稍后重新提交", null);
    }

    public boolean isPendingTimedOut(OcrTranslateImageTaskDto task) {
        return task != null
                && "PENDING".equals(task.getStatus())
                && task.getUpdatedAt() != null
                && System.currentTimeMillis() - task.getUpdatedAt() > ocrConfig.getPendingTimeoutSeconds() * 1000;
    }

    public Set<String> findAllTaskIds() {
        Set<Object> members = redisUtil.getSetMembers(ACTIVE_TASKS_KEY);
        if (members == null || members.isEmpty()) {
            return Set.of();
        }
        return members.stream()
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    public void removeFromActiveSet(String taskId) {
        redisUtil.removeSetMembers(ACTIVE_TASKS_KEY, taskId);
    }

    private void persist(OcrTranslateImageTaskDto dto) {
        redisUtil.set(buildKey(dto.getTaskId()), JSON.toJSONString(dto), ocrConfig.getResultTtl());
    }

    private String buildKey(String taskId) {
        return RESULT_PREFIX + taskId;
    }
}
