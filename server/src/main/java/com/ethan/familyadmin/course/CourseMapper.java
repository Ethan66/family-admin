package com.ethan.familyadmin.course;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import java.util.List;
@Mapper
public interface CourseMapper extends BaseMapper<CourseEntity> {
    @Select("SELECT * FROM course WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    CourseEntity selectForUpdate(Long id);

    @Select("""
        SELECT c.* FROM course c
        JOIN family f ON f.id=c.family_id AND f.deleted=0 AND f.status='ACTIVE'
        JOIN sys_user u ON u.id=c.member_id AND u.deleted=0 AND u.status='ACTIVE'
        JOIN family_member m ON m.family_id=c.family_id AND m.user_id=c.member_id AND m.deleted=0
        WHERE c.deleted=0 ORDER BY c.family_id,c.id
        """)
    List<CourseEntity> autoAttendanceCandidates();

    @Select("""
        SELECT COUNT(*) FROM course c
        JOIN family f ON f.id=c.family_id AND f.deleted=0 AND f.status='ACTIVE'
        JOIN sys_user u ON u.id=c.member_id AND u.deleted=0 AND u.status='ACTIVE'
        JOIN family_member m ON m.family_id=c.family_id AND m.user_id=c.member_id AND m.deleted=0
        WHERE c.id=#{id} AND c.family_id=#{familyId} AND c.member_id=#{memberId} AND c.deleted=0
        """)
    int autoAttendanceEligible(@Param("id") Long id, @Param("familyId") Long familyId, @Param("memberId") Long memberId);
}
