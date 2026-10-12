package com.ethan.familyadmin.exam;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ethan.familyadmin.common.model.BaseEntity;
import com.ethan.familyadmin.common.model.TenantOwnedEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@TableName("exam_paper")
public class ExamEntity extends BaseEntity implements TenantOwnedEntity {
    private Long familyId;
    private String name;
    private String subject;
    private String grade;
    private LocalDate examDate;
    private BigDecimal fullScore;
    private BigDecimal score;
    private String status;
    private String reviewNote;
}
