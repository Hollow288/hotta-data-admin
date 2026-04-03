package com.hollow.build.common;

import com.hollow.build.common.enums.GlobalErrorCodeConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 统一 API 响应体，封装业务状态码、提示信息和响应数据。
 *
 * @param <T> 响应数据类型
 */
@Getter
@Setter
@Schema(description = "统一API响应体")
public class ApiResponse<T> {

    @Schema(description = "业务状态码，200表示成功")
    private Integer code;

    @Schema(description = "提示信息")
    private String msg;

    @Schema(description = "响应数据")
    private T data;

    /**
     * 无参构造方法。
     */
    public ApiResponse() {}

    /**
     * 构造带状态码和提示信息的响应对象。
     *
     * @param code    业务状态码
     * @param message 提示信息
     */
    public ApiResponse(Integer code, String message) {
        this.code = code;
        this.msg = message;
    }

    /**
     * 构造带状态码、提示信息和数据的响应对象。
     *
     * @param code    业务状态码
     * @param message 提示信息
     * @param data    响应数据
     */
    public ApiResponse(Integer code, String message, T data) {
        this.code = code;
        this.msg = message;
        this.data = data;
    }

    /**
     * 创建不带数据的成功响应。
     *
     * @param <T> 响应数据类型
     * @return 成功的 ApiResponse 对象
     */
    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(GlobalErrorCodeConstants.SUCCESS.getCode(), GlobalErrorCodeConstants.SUCCESS.getMsg(), null);
    }

    /**
     * 创建带数据的成功响应。
     *
     * @param data 响应数据
     * @param <T>  响应数据类型
     * @return 成功的 ApiResponse 对象
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(GlobalErrorCodeConstants.SUCCESS.getCode(), GlobalErrorCodeConstants.SUCCESS.getMsg(), data);
    }

    /**
     * 创建带自定义提示信息和数据的成功响应。
     *
     * @param message 自定义提示信息
     * @param data    响应数据
     * @param <T>     响应数据类型
     * @return 成功的 ApiResponse 对象
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(GlobalErrorCodeConstants.SUCCESS.getCode(), message, data);
    }


}
