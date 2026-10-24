package com.ethan.familyadmin.course;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CourseBalanceMapper extends BaseMapper<CourseBalanceEntity> {
    @Insert("""
        INSERT INTO course_balance (family_id, member_id, title, remaining_lessons, total_lessons, created_by, created_at, updated_by, updated_at, deleted)
        VALUES (#{familyId}, #{memberId}, #{title}, #{remainingLessons}, #{totalLessons}, #{createdBy}, #{createdAt}, #{updatedBy}, #{updatedAt}, 0)
        ON DUPLICATE KEY UPDATE remaining_lessons = VALUES(remaining_lessons), total_lessons = VALUES(total_lessons), updated_by = VALUES(updated_by), updated_at = VALUES(updated_at)
        """)
    void upsert(CourseBalanceEntity balance);

    @Update("""
        UPDATE course_balance SET remaining_lessons = remaining_lessons + #{delta}, updated_by = #{actor}, updated_at = CURRENT_TIMESTAMP
        WHERE family_id = #{familyId} AND member_id = #{memberId} AND title = #{title} AND deleted = 0
        """)
    int adjust(@Param("familyId") Long familyId, @Param("memberId") Long memberId, @Param("title") String title,
        @Param("delta") int delta, @Param("actor") Long actor);
}
