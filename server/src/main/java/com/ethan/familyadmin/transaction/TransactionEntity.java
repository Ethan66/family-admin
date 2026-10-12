package com.ethan.familyadmin.transaction;

import com.baomidou.mybatisplus.annotation.TableName;
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
}

