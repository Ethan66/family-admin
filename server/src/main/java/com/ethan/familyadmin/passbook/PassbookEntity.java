package com.ethan.familyadmin.passbook;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.ethan.familyadmin.common.model.BaseEntity;
import com.ethan.familyadmin.common.model.TenantOwnedEntity;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@TableName("passbook")
public class PassbookEntity extends BaseEntity implements TenantOwnedEntity {
    private Long familyId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String bank;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String note;
    private Long memberId;
    private String memberName;
    private LocalDate depositDate;
    private BigDecimal amount;
    private Integer termYears;
    private BigDecimal interestRate;
    private BigDecimal totalInterest;
}
