package com.ethan.familyadmin.passbook;

import java.math.BigDecimal;
import java.util.List;

public record PassbookPageResponse(
    List<PassbookResponse> records, long total, long page, long pageSize,
    BigDecimal totalPrincipal, BigDecimal totalAmount
) {}
