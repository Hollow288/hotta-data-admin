package com.hollow.build.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ 配置类，声明 OCR 任务所需的交换机、队列和绑定关系。
 *
 * <h2>RabbitMQ 消息路由模型</h2>
 * <pre>
 *                          routingKey = "ocr.task"
 * Producer ──> [ocr.exchange] ──────────────────> [ocr.queue] ──> Consumer
 *             (Direct Exchange)                    (持久化队列)    (OcrConsumer)
 * </pre>
 *
 * <h2>核心概念说明</h2>
 * <ul>
 *   <li><b>Exchange（交换机）</b>：消息的路由中心，生产者将消息发送到交换机而非直接发送到队列。
 *       交换机根据类型和路由键（routingKey）决定将消息投递到哪个队列。
 *       这里使用 DirectExchange，即精确匹配路由键。</li>
 *   <li><b>Queue（队列）</b>：消息的存储容器，消费者从队列中拉取消息进行处理。
 *       设置 durable=true 后队列会持久化到磁盘，RabbitMQ 重启后队列和消息不会丢失。</li>
 *   <li><b>Binding（绑定）</b>：将交换机和队列通过路由键关联起来。
 *       当消息的路由键与绑定的路由键匹配时，交换机才会将消息投递到对应队列。</li>
 *   <li><b>MessageConverter（消息转换器）</b>：决定消息体的序列化/反序列化方式。
 *       这里使用 Jackson2JsonMessageConverter，生产者发送时将 Java 对象序列化为 JSON，
 *       消费者接收时将 JSON 反序列化回 Java 对象。</li>
 * </ul>
 */
@Configuration
public class RabbitMQConfig {

    /** 交换机名称：OCR 任务使用的 Direct 类型交换机 */
    public static final String OCR_EXCHANGE = "ocr.exchange";

    /** 队列名称：存放待处理的 OCR 任务消息 */
    public static final String OCR_QUEUE = "ocr.queue";

    /** 路由键：生产者发送消息时指定此键，交换机据此将消息路由到 OCR 队列 */
    public static final String OCR_ROUTING_KEY = "ocr.task";

    /** 重试交换机名称：消费失败的任务先进入重试队列等待 TTL 到期 */
    public static final String OCR_RETRY_EXCHANGE = "ocr.retry.exchange";

    /** 重试队列名称：持久化保存待重试的任务 */
    public static final String OCR_RETRY_QUEUE = "ocr.retry.queue";

    /** 重试路由键 */
    public static final String OCR_RETRY_ROUTING_KEY = "ocr.retry";

    /** 死信交换机名称：超过最大重试次数或消息异常的任务进入死信队列 */
    public static final String OCR_DEAD_EXCHANGE = "ocr.dead.exchange";

    /** 死信队列名称 */
    public static final String OCR_DEAD_QUEUE = "ocr.dead.queue";

    /** 死信路由键 */
    public static final String OCR_DEAD_ROUTING_KEY = "ocr.dead";

    /** OCR 图片翻译标注交换机名称 */
    public static final String OCR_TRANSLATE_EXCHANGE = "ocr.translate.exchange";

    /** OCR 图片翻译标注主队列名称 */
    public static final String OCR_TRANSLATE_QUEUE = "ocr.translate.queue";

    /** OCR 图片翻译标注主路由键 */
    public static final String OCR_TRANSLATE_ROUTING_KEY = "ocr.translate.task";

    /** OCR 图片翻译标注重试交换机名称 */
    public static final String OCR_TRANSLATE_RETRY_EXCHANGE = "ocr.translate.retry.exchange";

    /** OCR 图片翻译标注重试队列名称 */
    public static final String OCR_TRANSLATE_RETRY_QUEUE = "ocr.translate.retry.queue";

    /** OCR 图片翻译标注重试路由键 */
    public static final String OCR_TRANSLATE_RETRY_ROUTING_KEY = "ocr.translate.retry";

    /** OCR 图片翻译标注死信交换机名称 */
    public static final String OCR_TRANSLATE_DEAD_EXCHANGE = "ocr.translate.dead.exchange";

    /** OCR 图片翻译标注死信队列名称 */
    public static final String OCR_TRANSLATE_DEAD_QUEUE = "ocr.translate.dead.queue";

    /** OCR 图片翻译标注死信路由键 */
    public static final String OCR_TRANSLATE_DEAD_ROUTING_KEY = "ocr.translate.dead";

    /**
     * 声明 Direct 类型的交换机。
     * <p>
     * Direct Exchange 是最常用的交换机类型，它根据消息携带的 routingKey 进行精确匹配：
     * 只有当消息的 routingKey 与 Binding 中声明的 routingKey 完全相同时，消息才会被投递到对应队列。
     */
    @Bean
    public DirectExchange ocrExchange() {
        return new DirectExchange(OCR_EXCHANGE);
    }

    /**
     * 声明持久化队列。
     * <p>
     * durable=true 表示队列会被持久化到磁盘：
     * <ul>
     *   <li>RabbitMQ 服务重启后队列依然存在</li>
     *   <li>配合消息持久化（Spring AMQP 默认开启），未被消费的消息也不会丢失</li>
     * </ul>
     */
    @Bean
    public Queue ocrQueue() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("x-dead-letter-exchange", OCR_DEAD_EXCHANGE);
        arguments.put("x-dead-letter-routing-key", OCR_DEAD_ROUTING_KEY);
        return new Queue(OCR_QUEUE, true, false, false, arguments);
    }

    /**
     * 将队列绑定到交换机，并指定路由键。
     * <p>
     * 绑定关系建立后，当生产者通过 RabbitTemplate 向 ocr.exchange 发送 routingKey 为 "ocr.task" 的消息时，
     * 交换机会将该消息投递到 ocr.queue 队列中。
     */
    @Bean
    public Binding ocrBinding(@Qualifier("ocrQueue") Queue ocrQueue,
                              @Qualifier("ocrExchange") DirectExchange ocrExchange) {
        return BindingBuilder.bind(ocrQueue).to(ocrExchange).with(OCR_ROUTING_KEY);
    }

    /**
     * 声明重试交换机。
     */
    @Bean
    public DirectExchange ocrRetryExchange() {
        return new DirectExchange(OCR_RETRY_EXCHANGE);
    }

    /**
     * 声明重试队列。
     * <p>
     * 消费失败的任务进入该队列后会等待 TTL 到期，
     * 然后自动死信回主交换机并重新投递到 OCR 主队列。
     */
    @Bean
    public Queue ocrRetryQueue(OcrConfigurationProperties ocrConfig) {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("x-message-ttl", ocrConfig.getRetryDelayMillis());
        arguments.put("x-dead-letter-exchange", OCR_EXCHANGE);
        arguments.put("x-dead-letter-routing-key", OCR_ROUTING_KEY);
        return new Queue(OCR_RETRY_QUEUE, true, false, false, arguments);
    }

    /**
     * 将重试队列绑定到重试交换机。
     */
    @Bean
    public Binding ocrRetryBinding(@Qualifier("ocrRetryQueue") Queue ocrRetryQueue,
                                   @Qualifier("ocrRetryExchange") DirectExchange ocrRetryExchange) {
        return BindingBuilder.bind(ocrRetryQueue).to(ocrRetryExchange).with(OCR_RETRY_ROUTING_KEY);
    }

    /**
     * 声明死信交换机。
     */
    @Bean
    public DirectExchange ocrDeadExchange() {
        return new DirectExchange(OCR_DEAD_EXCHANGE);
    }

    /**
     * 声明死信队列，用于保存超过最大重试次数或消息结构异常的任务。
     */
    @Bean
    public Queue ocrDeadQueue() {
        return new Queue(OCR_DEAD_QUEUE, true);
    }

    /**
     * 将死信队列绑定到死信交换机。
     */
    @Bean
    public Binding ocrDeadBinding(@Qualifier("ocrDeadQueue") Queue ocrDeadQueue,
                                  @Qualifier("ocrDeadExchange") DirectExchange ocrDeadExchange) {
        return BindingBuilder.bind(ocrDeadQueue).to(ocrDeadExchange).with(OCR_DEAD_ROUTING_KEY);
    }

    @Bean
    public DirectExchange ocrTranslateExchange() {
        return new DirectExchange(OCR_TRANSLATE_EXCHANGE);
    }

    @Bean
    public Queue ocrTranslateQueue() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("x-dead-letter-exchange", OCR_TRANSLATE_DEAD_EXCHANGE);
        arguments.put("x-dead-letter-routing-key", OCR_TRANSLATE_DEAD_ROUTING_KEY);
        return new Queue(OCR_TRANSLATE_QUEUE, true, false, false, arguments);
    }

    @Bean
    public Binding ocrTranslateBinding(@Qualifier("ocrTranslateQueue") Queue ocrTranslateQueue,
                                       @Qualifier("ocrTranslateExchange") DirectExchange ocrTranslateExchange) {
        return BindingBuilder.bind(ocrTranslateQueue).to(ocrTranslateExchange).with(OCR_TRANSLATE_ROUTING_KEY);
    }

    @Bean
    public DirectExchange ocrTranslateRetryExchange() {
        return new DirectExchange(OCR_TRANSLATE_RETRY_EXCHANGE);
    }

    @Bean
    public Queue ocrTranslateRetryQueue(OcrConfigurationProperties ocrConfig) {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("x-message-ttl", ocrConfig.getRetryDelayMillis());
        arguments.put("x-dead-letter-exchange", OCR_TRANSLATE_EXCHANGE);
        arguments.put("x-dead-letter-routing-key", OCR_TRANSLATE_ROUTING_KEY);
        return new Queue(OCR_TRANSLATE_RETRY_QUEUE, true, false, false, arguments);
    }

    @Bean
    public Binding ocrTranslateRetryBinding(@Qualifier("ocrTranslateRetryQueue") Queue ocrTranslateRetryQueue,
                                            @Qualifier("ocrTranslateRetryExchange") DirectExchange ocrTranslateRetryExchange) {
        return BindingBuilder.bind(ocrTranslateRetryQueue).to(ocrTranslateRetryExchange).with(OCR_TRANSLATE_RETRY_ROUTING_KEY);
    }

    @Bean
    public DirectExchange ocrTranslateDeadExchange() {
        return new DirectExchange(OCR_TRANSLATE_DEAD_EXCHANGE);
    }

    @Bean
    public Queue ocrTranslateDeadQueue() {
        return new Queue(OCR_TRANSLATE_DEAD_QUEUE, true);
    }

    @Bean
    public Binding ocrTranslateDeadBinding(@Qualifier("ocrTranslateDeadQueue") Queue ocrTranslateDeadQueue,
                                           @Qualifier("ocrTranslateDeadExchange") DirectExchange ocrTranslateDeadExchange) {
        return BindingBuilder.bind(ocrTranslateDeadQueue).to(ocrTranslateDeadExchange).with(OCR_TRANSLATE_DEAD_ROUTING_KEY);
    }

    /**
     * 注册 JSON 消息转换器，替代 Spring AMQP 默认的 Java 序列化。
     * <p>
     * 使用 JSON 格式的好处：
     * <ul>
     *   <li>消息可读性好，便于在 RabbitMQ 管理界面中查看消息内容</li>
     *   <li>跨语言兼容，如果未来有其他语言的消费者也能解析</li>
     *   <li>避免 Java 序列化带来的版本兼容和安全问题</li>
     * </ul>
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
