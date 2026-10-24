package com.ethan.familyadmin.task;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ethan.familyadmin.common.model.BaseEntity;
import com.ethan.familyadmin.common.model.TenantOwnedEntity;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalTime;

@Getter @Setter @TableName("daily_task")
public class DailyTaskEntity extends BaseEntity implements TenantOwnedEntity {
    private Long familyId;
    private Long memberId;
    private String memberName;
    private String title;
    private LocalDate taskDate;
    private LocalTime taskTime;
    private String category;
    private Boolean repeatDaily;
    private String note;
}
