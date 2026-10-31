-- 只为之后的记课保存处理后的余额；历史数据缺少调整流水，不推算回填。
ALTER TABLE course_record
    ADD COLUMN remaining_lessons DECIMAL(10,2) NULL COMMENT '本次记课处理后的剩余课时快照' AFTER automatic,
    ADD COLUMN total_lessons DECIMAL(10,2) NULL COMMENT '本次记课处理后的总课时快照' AFTER remaining_lessons;
