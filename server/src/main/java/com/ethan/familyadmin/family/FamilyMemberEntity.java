package com.ethan.familyadmin.family;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ethan.familyadmin.common.model.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("family_member")
public class FamilyMemberEntity extends BaseEntity {
    private Long familyId;
    private Long userId;
    private String role;
}

