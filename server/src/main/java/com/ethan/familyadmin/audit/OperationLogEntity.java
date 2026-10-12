package com.ethan.familyadmin.audit;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("operation_log")
public class OperationLogEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private Long operatorId;
    private String action;
    private String resourceType;
    private String resourceId;
    private String result;
    private String contextJson;
    private LocalDateTime createdAt;
}

