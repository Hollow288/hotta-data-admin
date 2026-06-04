package com.hollow.build.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记接口方法的限流令牌消耗数，未声明时默认每次请求消耗 1 个令牌。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimitCost {

    long value() default 1L;
}
