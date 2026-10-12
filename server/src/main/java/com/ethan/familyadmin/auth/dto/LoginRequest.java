package com.ethan.familyadmin.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank(message = "请输入账号") String username,
    @NotBlank(message = "请输入密码") @Size(min = 3, max = 100, message = "密码长度必须为 3 到 100 位") String password
) {
}
