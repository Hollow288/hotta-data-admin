package com.hollow.build.dto;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
public class UserDto implements Serializable {
    private String userName;
    private String password;
    private boolean enabled;
    private List<String> roles;
}
