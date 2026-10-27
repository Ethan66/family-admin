package com.ethan.familyadmin.quicknote;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ethan.familyadmin.common.model.BaseEntity;
import com.ethan.familyadmin.common.model.TenantOwnedEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("quick_note")
public class QuickNoteEntity extends BaseEntity implements TenantOwnedEntity {
    private Long familyId;
    private String title;
    private String body;
    private String tagsJson;
    private String imageIdsJson;
    private String tasksJson;
    private String color;
    private Boolean pinned;
    private String status;
    private Integer version;
}
