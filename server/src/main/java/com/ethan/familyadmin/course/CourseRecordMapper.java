package com.ethan.familyadmin.course;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CourseRecordMapper extends BaseMapper<CourseRecordEntity> {
    // 重复提交只更新状态和备注，保留首次记录的课程、成员及时间快照。
    @Insert("""
        INSERT INTO course_record (family_id, course_id, member_id, member_name, title, course_date,
            start_time, end_time, status, note, automatic, created_by, created_at, updated_by, updated_at, deleted)
        VALUES (#{familyId}, #{courseId}, #{memberId}, #{memberName}, #{title}, #{courseDate},
            #{startTime}, #{endTime}, #{status}, #{note}, #{automatic}, #{createdBy}, #{createdAt}, #{updatedBy}, #{updatedAt}, 0)
        ON DUPLICATE KEY UPDATE status = VALUES(status), note = VALUES(note),
            updated_by = VALUES(updated_by), updated_at = VALUES(updated_at)
        """)
    void upsert(CourseRecordEntity record);
}
