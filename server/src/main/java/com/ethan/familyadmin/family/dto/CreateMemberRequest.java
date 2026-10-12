package com.ethan.familyadmin.family.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateMemberRequest(
    @NotBlank(message = "登录名不能为空")
    @Pattern(regexp = "[A-Za-z0-9_]{3,64}", message = "登录名只能包含字母、数字、下划线且长度为 3 到 64 位")
    String username,
    @NotBlank(message = "显示名称不能为空") @Size(max = 100, message = "显示名称不能超过 100 字") String displayName,
    @NotBlank(message = "初始密码不能为空") @Size(min = 8, max = 100, message = "初始密码长度必须为 8 到 100 位") String password
) {
}
