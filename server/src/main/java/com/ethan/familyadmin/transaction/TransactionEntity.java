package com.ethan.familyadmin.transaction;

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
@TableName("transaction_record")
public class TransactionEntity extends BaseEntity implements TenantOwnedEntity {
    private Long familyId;
    private String type;
    private BigDecimal amount;
    private LocalDate transactionDate;
    private String category;
    private String memberName;
    private String note;
    // 清空下拉选择时必须将数据库旧值同步更新为空。
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String bankCard;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String platform;
}

