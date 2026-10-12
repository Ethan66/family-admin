package com.ethan.familyadmin.dashboard;

import java.math.BigDecimal;

public record DashboardOverview(
    BigDecimal income,
    BigDecimal expense,
    BigDecimal balance,
    long examCount,
    long photoCount,
    long movieCount,
    long noteCount
) {
}
