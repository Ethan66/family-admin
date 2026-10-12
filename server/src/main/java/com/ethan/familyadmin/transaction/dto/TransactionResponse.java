package com.ethan.familyadmin.transaction.dto;

import com.ethan.familyadmin.transaction.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record TransactionResponse(
    Long id,
    TransactionType type,
    BigDecimal amount,
    LocalDate transactionDate,
    String category,
    String memberName,
    String note,
    Long createdBy,
    LocalDateTime createdAt
) {
}

