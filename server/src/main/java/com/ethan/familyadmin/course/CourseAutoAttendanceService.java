package com.ethan.familyadmin.course;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;

@Service
public class CourseAutoAttendanceService {
    private final CourseMapper courses;
    private final CourseRecordMapper records;
    private final CourseBalanceMapper balances;
    public CourseAutoAttendanceService(CourseMapper courses, CourseRecordMapper records, CourseBalanceMapper balances) {
        this.courses=courses; this.records=records; this.balances=balances;
    }

    /** 行锁与手工记课共用，任意已有记录（含请假）均保留，记录和扣课时在同一事务。 */
    @Transactional(rollbackFor = Exception.class)
    public boolean complete(Long id, LocalDate date, LocalDateTime enabledFrom, LocalDateTime now) {
        var course=courses.selectForUpdate(id);
        if (course==null || date.getDayOfWeek()!=course.getCourseDate().getDayOfWeek()) return false;
        var end=date.atTime(course.getEndTime());
        if (end.isAfter(now) || end.isBefore(enabledFrom) || end.isBefore(course.getCreatedAt()) || end.isBefore(course.getUpdatedAt())) return false;
        if (courses.autoAttendanceEligible(id,course.getFamilyId(),course.getMemberId())==0) return false;
        var existing=records.selectOne(Wrappers.<CourseRecordEntity>lambdaQuery().eq(CourseRecordEntity::getFamilyId,course.getFamilyId())
            .eq(CourseRecordEntity::getCourseId,id).eq(CourseRecordEntity::getCourseDate,date));
        if (existing!=null) return false;
        var record=new CourseRecordEntity();
        record.setFamilyId(course.getFamilyId()); record.setCourseId(id); record.setMemberId(course.getMemberId());
        record.setMemberName(course.getMemberName()); record.setTitle(course.getTitle()); record.setCourseDate(date);
        record.setStartTime(course.getStartTime()); record.setEndTime(course.getEndTime());
        record.setStatus("ATTENDED"); record.setNote("课程结束后自动记录"); record.setAutomatic(true);
        record.setCreatedBy(course.getCreatedBy()); record.setUpdatedBy(course.getCreatedBy());
        record.setCreatedAt(now); record.setUpdatedAt(now);
        records.upsert(record);
        balances.adjust(course.getFamilyId(),course.getMemberId(),course.getTitle(),-1,course.getCreatedBy());
        return true;
    }
}
