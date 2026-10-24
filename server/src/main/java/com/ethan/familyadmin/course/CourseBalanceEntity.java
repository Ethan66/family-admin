package com.ethan.familyadmin.course;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ethan.familyadmin.common.model.BaseEntity;
import com.ethan.familyadmin.common.model.TenantOwnedEntity;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter @Setter @TableName("course_balance")
public class CourseBalanceEntity extends BaseEntity implements TenantOwnedEntity {
    private Long familyId;
    private Long memberId;
    private String title;
    private BigDecimal remainingLessons;
    private BigDecimal totalLessons;
}
