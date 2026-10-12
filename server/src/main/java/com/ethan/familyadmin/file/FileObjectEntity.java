package com.ethan.familyadmin.file;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ethan.familyadmin.common.model.BaseEntity;
import com.ethan.familyadmin.common.model.TenantOwnedEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("file_object")
public class FileObjectEntity extends BaseEntity implements TenantOwnedEntity {
    private Long familyId;
    private String originalName;
    private String storageKey;
    private String mediaType;
    private Long sizeBytes;
    private String sha256;
    private String status;
}
