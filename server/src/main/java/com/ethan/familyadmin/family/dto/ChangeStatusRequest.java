package com.ethan.familyadmin.family.dto;

import jakarta.validation.constraints.NotNull;

public record ChangeStatusRequest(@NotNull(message = "启用状态不能为空") Boolean active) {
}

