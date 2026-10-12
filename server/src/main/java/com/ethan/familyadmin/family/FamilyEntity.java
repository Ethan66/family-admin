package com.ethan.familyadmin.family;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ethan.familyadmin.common.model.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("family")
public class FamilyEntity extends BaseEntity {
    private String name;
    private String status;
    private String remark;
}

