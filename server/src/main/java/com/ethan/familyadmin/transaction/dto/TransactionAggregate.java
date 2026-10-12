package com.ethan.familyadmin.transaction.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class TransactionAggregate {
    private BigDecimal income;
    private BigDecimal expense;
    private Long incomeCount;
    private Long expenseCount;
}
