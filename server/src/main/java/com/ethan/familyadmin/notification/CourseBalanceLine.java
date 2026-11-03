package com.ethan.familyadmin.notification;

import java.math.BigDecimal;

/** 全科余额通知的一行数据，包含没有固定课表的独立余额科目。 */
public record CourseBalanceLine(Long memberId, String memberName, String title, BigDecimal remainingLessons, BigDecimal totalLessons) {}
