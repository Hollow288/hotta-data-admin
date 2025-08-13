package com.hollow.build.entity;



import java.io.Serializable;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;


/**
* 用户表
* @TableName user
*/

@Getter
@Setter
public class User implements Serializable {

    /**
    * 主键
    */
    @Schema(description = "主键")
    private Long userId;
    /**
    * 用户名
    */
    @Schema(description = "用户名")
    private String username;
    /**
    * 密码
    */
    @Schema(description = "密码")
    private String password;
    /**
    * 是否启用
    */
    @Schema(description = "是否启用")
    private String isEnabled;

}
