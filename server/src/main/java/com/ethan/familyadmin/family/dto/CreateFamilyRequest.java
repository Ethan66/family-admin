package com.ethan.familyadmin.family.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateFamilyRequest(
    @NotBlank(message = "家庭名称不能为空") @Size(max = 100, message = "家庭名称不能超过 100 字") String name,
    @Size(max = 500, message = "备注不能超过 500 字") String remark
) {
}

