package com.ethan.familyadmin.transaction.dto;

import com.ethan.familyadmin.transaction.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateTransactionRequest(
    @NotNull(message = "收支类型不能为空") TransactionType type,
    @NotNull(message = "金额不能为空") @DecimalMin(value = "0.01", message = "金额必须大于 0") BigDecimal amount,
    @NotNull(message = "日期不能为空") LocalDate transactionDate,
    @NotBlank(message = "分类不能为空") @Size(max = 50, message = "分类不能超过 50 字") String category,
    @NotBlank(message = "成员不能为空") @Size(max = 100, message = "成员不能超过 100 字") String memberName,
    @Size(max = 500, message = "备注不能超过 500 字") String note
) {
}

