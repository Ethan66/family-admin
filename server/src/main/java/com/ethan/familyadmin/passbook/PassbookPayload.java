package com.ethan.familyadmin.passbook;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PassbookPayload(
    @NotNull @Positive Long memberId,
    @NotNull LocalDate depositDate,
    @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
    @NotNull @Min(1) @Max(100) Integer termYears,
    @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 4) BigDecimal interestRate,
    @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal totalInterest,
    @Pattern(regexp = "^(农商)?$", message = "请选择有效的银行") String bank,
    @Size(max = 500, message = "备注不能超过 500 字") String note
) {}
