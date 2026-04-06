package com.hollow.build.service.impl;

import com.hollow.build.common.ApiResponse;
import com.hollow.build.common.enums.GlobalErrorCodeConstants;
import com.hollow.build.config.OcrConfigurationProperties;
import com.hollow.build.config.RabbitMQConfig;
import com.hollow.build.dto.OcrTaskDto;
import com.hollow.build.service.OcrService;
import com.hollow.build.utils.RedisUtil;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * OCR 服务实现类，充当消息队列的<b>生产者</b>角色。
 * <p>
 * 负责接收前端上传的图片，将其转换为 Base64 后封装成消息发送到 RabbitMQ 队列，
 * 同时在 Redis 中维护任务状态，供前端轮询查询。
 * <p>
 * 本类<b>不直接调用</b> RapidOCR 服务，实际的 OCR 识别由消费者 {@link com.hollow.build.mq.OcrConsumer} 完成。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OcrServiceImpl implements OcrService {

    /** Redis 中 OCR 任务结果的 key 前缀，完整 key 格式为 "ocr:result:{taskId}" */
    private static final String OCR_RESULT_PREFIX = "ocr:result:";

    /**
     * RabbitTemplate 是 Spring AMQP 提供的消息发送工具，
     * 调用 convertAndSend() 方法可将 Java 对象序列化为 JSON 后发送到指定的交换机和路由键。
     * 序列化方式由 {@link RabbitMQConfig#jsonMessageConverter()} 中注册的 Jackson2JsonMessageConverter 决定。
     */
    private final RabbitTemplate rabbitTemplate;

    private final RedisUtil redisUtil;
    private final OcrConfigurationProperties ocrConfig;

    /**
     * {@inheritDoc}
     * <p>
     * 实现细节：
     * <ol>
     *   <li>通过 UUID 生成唯一任务标识 taskId</li>
     *   <li>将 MultipartFile 的字节内容转为 Base64 字符串（因为 RabbitMQ 消息需要可序列化的格式）</li>
     *   <li>构建消息体 Map，包含 taskId、imageBase64、fileName 三个字段</li>
     *   <li>在 Redis 中写入初始状态 PENDING，key 为 "ocr:result:{taskId}"，过期时间由配置决定</li>
     *   <li>通过 RabbitTemplate 将消息发送到 ocr.exchange 交换机，路由键为 ocr.task，
     *       交换机根据路由键将消息投递到 ocr.queue 队列</li>
     *   <li>消息发送完成后立即返回 taskId，不等待 OCR 处理结果</li>
     * </ol>
     */
    @Override
    public ApiResponse<OcrTaskDto> submitTask(MultipartFile file) {
        try {
            String taskId = UUID.randomUUID().toString();

            // 将图片转为 Base64 字符串，使其可以作为 JSON 消息体的一部分通过 RabbitMQ 传输
            String base64Image = Base64.getEncoder().encodeToString(file.getBytes());

            // 构建发送到 RabbitMQ 的消息体
            Map<String, String> message = new HashMap<>();
            message.put("taskId", taskId);
            message.put("imageBase64", base64Image);
            message.put("fileName", file.getOriginalFilename());

            // 在 Redis 中创建初始任务记录，状态为 PENDING，表示任务已提交但尚未被消费者处理
            OcrTaskDto pending = OcrTaskDto.builder()
                    .taskId(taskId)
                    .status("PENDING")
                    .build();
            redisUtil.set(OCR_RESULT_PREFIX + taskId, JSON.toJSONString(pending), ocrConfig.getResultTtl());

            // 将消息发送到 RabbitMQ：
            // 参数1: exchange — 交换机名称（ocr.exchange），负责根据路由键分发消息
            // 参数2: routingKey — 路由键（ocr.task），交换机据此将消息投递到绑定了该键的队列
            // 参数3: message — 消息体，会被 Jackson2JsonMessageConverter 序列化为 JSON
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.OCR_EXCHANGE,
                    RabbitMQConfig.OCR_ROUTING_KEY,
                    message
            );

            log.info("OCR 任务已提交: taskId={}, fileName={}", taskId, file.getOriginalFilename());

            return ApiResponse.success(pending);

        } catch (Exception e) {
            log.error("提交 OCR 任务失败", e);
            return new ApiResponse<>(
                    GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getCode(),
                    "提交 OCR 任务失败: " + e.getMessage(),
                    null
            );
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * 实现细节：
     * 直接从 Redis 中读取 key 为 "ocr:result:{taskId}" 的值并反序列化为 OcrTaskDto 返回。
     * 该值由消费者 {@link com.hollow.build.mq.OcrConsumer} 在处理过程中实时更新。
     */
    @Override
    public ApiResponse<OcrTaskDto> getResult(String taskId) {
        Object resultJson = redisUtil.get(OCR_RESULT_PREFIX + taskId);
        if (resultJson == null) {
            return new ApiResponse<>(
                    GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.getCode(),
                    "任务不存在或已过期",
                    null
            );
        }

        OcrTaskDto result = JSON.parseObject(resultJson.toString(), OcrTaskDto.class);
        return ApiResponse.success(result);
    }
}
