package com.hollow.build.dto;

import com.hollow.build.entity.mysql.User;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
public class CustomUserDetails implements UserDetails {

    private User user;
    private List<String> permissions;
    private List<GrantedAuthority> authorities;

    public CustomUserDetails(User user, List<String> permissions) {
        this.user = user;
        this.permissions = permissions != null ? permissions : Collections.emptyList();
        this.authorities = this.permissions.stream()
                // 如果需要角色匹配 hasRole()，请加 ROLE_ 前缀
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        // 假设 1 表示可用
        return "1".equals(user.getIsEnabled());
    }
}
