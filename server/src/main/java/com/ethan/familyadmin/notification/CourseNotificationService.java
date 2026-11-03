package com.ethan.familyadmin.notification;

import com.ethan.familyadmin.course.CourseRecordEntity;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** 只生成持久化通知，不发送网络请求，也不修改上课记录或课时余额。 */
@Service
public class CourseNotificationService {
    private final CourseNotificationMapper mapper;
    private final CourseNotificationProperties properties;
    private final Clock clock;

    public CourseNotificationService(CourseNotificationMapper mapper, CourseNotificationProperties properties,
                                     @Qualifier("courseAttendanceClock") Clock clock) {
        this.mapper = mapper; this.properties = properties; this.clock = clock;
    }

    /** 在开课前的提醒窗口生成任务；同一课次、同一收件人由唯一键兜底去重。 */
    @Transactional(rollbackFor = Exception.class)
    public void scanUpcoming() {
        if (!properties.isEnabled()) return;
        var now = LocalDateTime.now(clock);
        if (now.isBefore(properties.getEnabledFrom())) return;
        var families = new LinkedHashSet<Long>();
        for (var destination : properties.getDestinations()) families.add(destination.getFamilyId());
        for (var family : families) {
            var recipients = recipients(family);
            if (recipients.isEmpty()) continue;
            var courses = mapper.activeCourses(family);
            if (courses.size() > 200) throw new IllegalStateException("课程提醒单次课程范围超限");
            for (var course : courses) {
                // 同时覆盖跨午夜的半小时窗口，课表锚点只表达星期，不是首次开课日期。
                for (var date = now.toLocalDate(); !date.isAfter(now.plusMinutes(properties.getLeadMinutes()).toLocalDate()); date = date.plusDays(1)) {
                    var start = date.atTime(course.getStartTime());
                    if (date.getDayOfWeek() != course.getCourseDate().getDayOfWeek() || !now.isBefore(start)
                        || now.isBefore(start.minusMinutes(properties.getLeadMinutes())) || mapper.record(course.getId(), date) != null) continue;
                    var content = upcomingContent(course, date);
                    for (var recipient : recipients) enqueue("UPCOMING", course.getId(), date, family, recipient, "上课前提醒", content, now);
                }
            }
        }
    }

    /** 仅供自动记课事务成功返回后调用；通知落库失败不应使扣课事务回滚。 */
    @Transactional(rollbackFor = Exception.class)
    public void afterAutomaticAttendance(Long courseId, LocalDate date) {
        if (!properties.isEnabled()) return;
        var record = mapper.record(courseId, date);
        if (!eligible(record)) return;
        var recipients = recipients(record.getFamilyId());
        if (recipients.isEmpty()) return;
        var content = attendanceContent(record);
        var now = LocalDateTime.now(clock);
        for (var recipient : recipients) enqueue("ATTENDANCE", record.getId(), record.getCourseDate(), record.getFamilyId(),
            recipient, "自动扣课结果", content, now);
    }

    /** 补偿已经提交的自动扣课结果，避免进程恰在扣课提交与通知生成之间退出造成漏发。 */
    @Transactional(rollbackFor = Exception.class)
    public void recoverCompleted() {
        // 以已提交的记录和固定事件编号恢复，旧数据及已生成的事件不会重新推送。
        if (!properties.isEnabled()) return;
        for (var recipient : properties.getDestinations()) {
            if (mapper.recipientAllowed(recipient.getFamilyId(), recipient.getUserId()) == 0) continue;
            for (var record : mapper.missingAutomatic(recipient.getFamilyId(), recipient.getUserId(), properties.getEnabledFrom())) {
                if (!eligible(record)) continue;
                enqueue("ATTENDANCE", record.getId(), record.getCourseDate(), record.getFamilyId(), recipient,
                    "自动扣课结果", attendanceContent(record), LocalDateTime.now(clock));
            }
        }
    }

    private boolean eligible(CourseRecordEntity record) {
        // 没有余额时原系统仍能记课，但未产生实际余额扣减，不声称“扣课成功”。
        return record != null && Boolean.TRUE.equals(record.getAutomatic()) && "ATTENDED".equals(record.getStatus())
            && record.getCreatedAt() != null && !record.getCreatedAt().isBefore(properties.getEnabledFrom())
            && record.getRemainingLessons() != null;
    }

    private List<CourseNotificationProperties.Destination> recipients(Long family) {
        return properties.getDestinations().stream().filter(destination -> family.equals(destination.getFamilyId()))
            .filter(destination -> mapper.recipientAllowed(family, destination.getUserId()) > 0).toList();
    }

    private String attendanceContent(CourseRecordEntity record) {
        // 全科余额包含独立科目并按成员分组，不只列出刚扣减的科目，也不增加不足阈值提醒。
        var lines = mapper.familyBalances(record.getFamilyId());
        if (lines.size() > 200) throw new IllegalStateException("课程余额通知单次范围超限");
        var text = new StringBuilder(record.getMemberName()).append("的").append(record.getTitle())
            .append("已自动记为上课，扣减 1 课时。\n上课日期：").append(record.getCourseDate())
            .append("\n当前家庭各课程剩余课时：");
        Long member = null;
        for (var line : lines) {
            if (!Objects.equals(member, line.memberId())) {
                text.append('\n').append(line.memberName()).append("："); member = line.memberId();
            }
            text.append("\n· ").append(line.title()).append("：");
            if (line.remainingLessons() == null) text.append("未设置");
            else {
                text.append(number(line.remainingLessons()));
                if (line.totalLessons() != null) text.append('/').append(number(line.totalLessons()));
                text.append(" 课时");
            }
        }
        if (text.length() > 16000) throw new IllegalStateException("课程余额通知内容超限");
        return text.toString();
    }

    private String number(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }

    static String upcomingContent(com.ethan.familyadmin.course.CourseEntity course, LocalDate date) {
        return course.getMemberName() + "的" + course.getTitle() + "即将开始。\n上课时间："
            + date.atTime(course.getStartTime()).format(DateTimeFormatter.ofPattern("MM月dd日 HH:mm"))
            + "–" + course.getEndTime().format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    private void enqueue(String kind, Long reference, LocalDate date, Long family,
                         CourseNotificationProperties.Destination recipient, String title, String content, LocalDateTime now) {
        var task = new CourseNotification();
        task.setDedupKey(kind + ":" + reference + ("UPCOMING".equals(kind) ? ":" + date : "") + ":" + recipient.getUserId());
        task.setKind(kind); task.setReferenceId(reference); task.setCourseDate(date); task.setFamilyId(family);
        task.setRecipientUserId(recipient.getUserId()); task.setDestinationKey(recipient.getKey());
        task.setTitle(title); task.setContent(content); task.setStatus("PENDING"); task.setNextAttemptAt(now);
        task.setCreatedAt(now); task.setUpdatedAt(now);
        mapper.enqueue(task);
    }
}
