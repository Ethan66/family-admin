package com.ethan.familyadmin.photo;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ethan.familyadmin.common.model.BaseEntity;
import com.ethan.familyadmin.common.model.TenantOwnedEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@TableName("photo")
public class PhotoEntity extends BaseEntity implements TenantOwnedEntity {
    private Long familyId;
    private String title;
    private String album;
    private LocalDate photoDate;
    private String description;
    private Long fileId;
}
