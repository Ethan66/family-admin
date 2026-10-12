package com.ethan.familyadmin.note;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ethan.familyadmin.common.model.BaseEntity;
import com.ethan.familyadmin.common.model.TenantOwnedEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("study_note")
public class StudyNoteEntity extends BaseEntity implements TenantOwnedEntity {
    private Long familyId;
    private String title;
    private String tag;
    private String markdownBody;
}
