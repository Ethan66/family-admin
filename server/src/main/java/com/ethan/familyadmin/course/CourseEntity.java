package com.ethan.familyadmin.course;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ethan.familyadmin.common.model.BaseEntity;
import com.ethan.familyadmin.common.model.TenantOwnedEntity;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalTime;

@Getter @Setter @TableName("course")
public class CourseEntity extends BaseEntity implements TenantOwnedEntity {
    private Long familyId;
    private Long memberId;
    private String memberName;
    private String title;
    private LocalDate courseDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private Boolean repeatWeekly;
    private String color;
}
