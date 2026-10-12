package com.ethan.familyadmin.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
    @NotBlank(message = "请输入原密码") String currentPassword,
    @NotBlank(message = "请输入新密码") @Size(min = 3, max = 100, message = "新密码长度必须为 3 到 100 位") String newPassword
) {
}
