package com.ethan.familyadmin.course;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ethan.familyadmin.common.model.BaseEntity;
import com.ethan.familyadmin.common.model.TenantOwnedEntity;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalTime;
import java.math.BigDecimal;

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
    private Boolean automatic;
    /** 本次记课处理后的剩余课时快照；旧记录或未设置余额时为空。 */
    private BigDecimal remainingLessons;
    /** 与剩余课时同时保存的总课时快照，之后调整余额不会覆盖。 */
    private BigDecimal totalLessons;
}
