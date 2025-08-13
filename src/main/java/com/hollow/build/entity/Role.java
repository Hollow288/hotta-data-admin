package com.hollow.build.entity;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;


/**
* 角色表
* @TableName role
*/

@Getter
@Setter
public class Role implements Serializable {

    /**
    * 
    */
    @Schema(description = "ID")
    private Long roleId;
    /**
    * 角色名称
    */
    @Schema(description = "角色名称")
    private String roleName;
    /**
    * 角色权限字符串
    */
    @Schema(description = "角色权限字符串")
    private String roleKey;
}
