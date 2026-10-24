package com.ethan.familyadmin.course;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ethan.familyadmin.common.model.BaseEntity;
import com.ethan.familyadmin.common.model.TenantOwnedEntity;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalTime;
import java.math.BigDecimal;
import com.baomidou.mybatisplus.annotation.TableField;

@Getter @Setter @TableName("course_record")
public class CourseRecordEntity extends BaseEntity implements TenantOwnedEntity {
    private Long familyId;
    private Long courseId;
    private Long memberId;
    private String memberName;
    private String title;
    private LocalDate courseDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String status;
    private String note;
    @TableField(exist = false)
    private BigDecimal remainingLessons;
    @TableField(exist = false)
    private BigDecimal totalLessons;
}
