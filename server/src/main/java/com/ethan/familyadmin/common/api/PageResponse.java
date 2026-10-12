package com.ethan.familyadmin.common.api;

import java.util.List;

public record PageResponse<T>(List<T> records, long total, long page, long pageSize) {
}

