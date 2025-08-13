package com.hollow.build.common;

import com.hollow.build.common.enums.GlobalErrorCodeConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

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

    public ApiResponse() {}

    public ApiResponse(Integer code, String message) {
        this.code = code;
        this.msg = message;
    }

    public ApiResponse(Integer code, String message, T data) {
        this.code = code;
        this.msg = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(GlobalErrorCodeConstants.SUCCESS.getCode(), GlobalErrorCodeConstants.SUCCESS.getMsg(), null);
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(GlobalErrorCodeConstants.SUCCESS.getCode(), GlobalErrorCodeConstants.SUCCESS.getMsg(), data);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(GlobalErrorCodeConstants.SUCCESS.getCode(), message, data);
    }


}
