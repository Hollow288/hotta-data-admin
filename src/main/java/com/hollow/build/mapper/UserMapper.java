package com.hollow.build.mapper;


import com.hollow.build.entity.Role;
import com.hollow.build.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserMapper {

    @Select("select * from user where username = #{username} limit 1")
    User selectByUsername(String username);

    @Select("select role_key from role where role_id in (select role_id from user_role where user_id = #{userId})")
    List<String> selectRolesByUserId(Long userId);
}
