package com.hollow.build.service.impl;


import com.hollow.build.dto.CustomUserDetails;
import com.hollow.build.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@Transactional
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private UserMapper userMapper;


    //实现UserDetailsService接口，重写UserDetails方法，自定义用户的信息从数据中查询
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        //（认证，即校验该用户是否存在）查询用户信息
        final var user = userMapper.selectByUsername(username);

        //如果没有查询到用户
        if (Objects.isNull(user)){
            throw new RuntimeException("用户名不存在！");
        }

        List<String> rolesList = userMapper.selectRolesByUserId(user.getUserId());

        //把数据封装成UserDetails返回
        return new CustomUserDetails(user,rolesList);
    }
}

