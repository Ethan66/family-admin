package com.ethan.familyadmin.passbook;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PassbookResponse(
    Long id, Long memberId, String memberName, LocalDate depositDate, BigDecimal amount,
    Integer termYears, BigDecimal interestRate, LocalDate maturityDate,
    BigDecimal totalInterest, BigDecimal totalAmount, Long createdBy, String bank, String note
) {}
