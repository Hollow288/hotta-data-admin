package com.hollow.build.repository.mysql;


import com.hollow.build.entity.mysql.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户 MyBatis Mapper 接口，提供用户及角色权限相关的数据库操作。
 */
@Mapper
public interface UserMapper {

    /**
     * 根据用户名查询用户信息。
     *
     * @param username 用户名
     * @return 用户对象，未找到时返回 null
     */
    @Select("select * from user where username = #{username} limit 1")
    User selectByUsername(String username);

    /**
     * 根据用户 ID 查询该用户拥有的角色标识列表。
     *
     * @param userId 用户 ID
     * @return 角色标识键列表
     */
    @Select("select role_key from role where role_id in (select role_id from user_role where user_id = #{userId})")
    List<String> selectRolesByUserId(Long userId);

    /**
     * 根据用户 ID 获取该用户最大的每小时请求限制数。
     *
     * @param userId 用户 ID
     * @return 每小时请求限制数
     */
    @Select("select limit_per_hour from role where role_id in (select role_id from user_role where user_id = #{userId}) order by limit_per_hour desc limit 1")
    Integer getLimitPerHourByUserId(Long userId);

    /**
     * 根据 API Key 获取每小时请求限制数。
     *
     * @param apiKey API 密钥
     * @return 每小时请求限制数
     */
    @Select("select limit_per_hour from api_limit where api_key = #{apiKey}")
    Integer getLimitPerHourByApiKey(String apiKey);
}
