package com.ethan.familyadmin.transaction.dto;

import com.ethan.familyadmin.transaction.TransactionType;

public record TransactionQuery(String month, TransactionType type, String category, String memberName, String bankCard, String platform, long page, long pageSize) {
    public TransactionQuery {
        page = Math.max(page, 1);
        pageSize = Math.min(Math.max(pageSize, 1), 100);
    }
}

