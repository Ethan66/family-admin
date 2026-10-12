package com.ethan.familyadmin.family;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ethan.familyadmin.common.model.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("sys_user")
public class UserEntity extends BaseEntity {
    private String username;
    private String passwordHash;
    private String displayName;
    private String status;
    private Boolean superAdmin;
    private Integer tokenVersion;
}

