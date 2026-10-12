package com.ethan.familyadmin.transaction.dto;

import java.math.BigDecimal;

public record TransactionSummaryResponse(
    BigDecimal income,
    BigDecimal expense,
    BigDecimal balance,
    long incomeCount,
    long expenseCount
) {
}

