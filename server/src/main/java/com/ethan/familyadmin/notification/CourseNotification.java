package com.ethan.familyadmin.notification;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 内部通知任务；只映射业务字段，不读取数据库维护的影子时间列。 */
@Getter @Setter
public class CourseNotification {
    /** 通知编号。 */
    private Long id;
    /** 同一事件和收件人跨重试保持不变的编号。 */
    private String dedupKey;
    /** 数据所属家庭。 */
    private Long familyId;
    /** 获准接收该家庭课程信息的项目账号。 */
    private Long recipientUserId;
    /** 服务端固定的 Hermes 通道别名。 */
    private String destinationKey;
    /** 上课前提醒或自动记课结果。 */
    private String kind;
    /** 课程编号或自动记课记录编号。 */
    private Long referenceId;
    /** 对应的真实上课日期。 */
    private LocalDate courseDate;
    /** 发送标题。 */
    private String title;
    /** 生成时的消息快照。 */
    private String content;
    /** 持久化发送状态。 */
    private String status;
    /** 已领取的发送次数。 */
    private int attempts;
    /** 下次允许发送的北京时间。 */
    private LocalDateTime nextAttemptAt;
    /** 发送租约到期后允许恢复处理。 */
    private LocalDateTime leaseUntil;
    /** 不含接口原文的错误分类。 */
    private String lastError;
    /** 通知生成时间。 */
    private LocalDateTime createdAt;
    /** 状态更新时间。 */
    private LocalDateTime updatedAt;
}
