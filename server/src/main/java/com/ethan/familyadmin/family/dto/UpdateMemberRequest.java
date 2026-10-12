package com.ethan.familyadmin.family.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateMemberRequest(
    @NotBlank(message = "显示名称不能为空") @Size(max = 100, message = "显示名称不能超过 100 字") String displayName,
    @NotNull(message = "启用状态不能为空") Boolean active
) {
}
