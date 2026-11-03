package com.ethan.familyadmin.notification;

import com.ethan.familyadmin.course.CourseEntity;
import com.ethan.familyadmin.course.CourseRecordEntity;
import org.apache.ibatis.annotations.*;
import java.time.*;
import java.util.List;

/** 课程通知读写及去重领取，所有查询显式列名且限制单次处理规模。 */
@Mapper
public interface CourseNotificationMapper {
    @Select("""
        SELECT COUNT(*) FROM sys_user u JOIN family f ON f.id=#{familyId} AND f.deleted=0 AND f.status='ACTIVE'
        WHERE u.id=#{userId} AND u.deleted=0 AND u.status='ACTIVE'
          AND (u.super_admin=1 OR EXISTS(SELECT 1 FROM family_member m WHERE m.family_id=f.id AND m.user_id=u.id AND m.deleted=0))
        """)
    int recipientAllowed(@Param("familyId") Long familyId, @Param("userId") Long userId);

    @Select("""
        SELECT c.id,c.family_id,c.member_id,c.member_name,c.title,c.course_date,c.start_time,c.end_time,c.created_at,c.updated_at
        FROM course c JOIN family f ON f.id=c.family_id AND f.deleted=0 AND f.status='ACTIVE'
        JOIN sys_user u ON u.id=c.member_id AND u.deleted=0 AND u.status='ACTIVE'
        JOIN family_member m ON m.family_id=c.family_id AND m.user_id=c.member_id AND m.deleted=0
        WHERE c.family_id=#{familyId} AND c.deleted=0 ORDER BY c.id LIMIT 201
        """)
    List<CourseEntity> activeCourses(Long familyId);

    @Select("""
        SELECT c.id,c.family_id,c.member_id,c.member_name,c.title,c.course_date,c.start_time,c.end_time,c.created_at,c.updated_at
        FROM course c JOIN family f ON f.id=c.family_id AND f.deleted=0 AND f.status='ACTIVE'
        JOIN sys_user u ON u.id=c.member_id AND u.deleted=0 AND u.status='ACTIVE'
        JOIN family_member m ON m.family_id=c.family_id AND m.user_id=c.member_id AND m.deleted=0
        WHERE c.id=#{id} AND c.deleted=0
        """)
    CourseEntity activeCourse(Long id);

    @Select("""
        SELECT id,family_id,course_id,member_id,member_name,title,course_date,start_time,end_time,status,automatic,
               remaining_lessons,total_lessons,created_at,updated_at FROM course_record
        WHERE course_id=#{courseId} AND course_date=#{date} AND deleted=0
        """)
    CourseRecordEntity record(@Param("courseId") Long courseId, @Param("date") LocalDate date);

    @Select("""
        SELECT id,family_id,course_id,member_id,member_name,title,course_date,start_time,end_time,status,automatic,
               remaining_lessons,total_lessons,created_at,updated_at FROM course_record WHERE id=#{id} AND deleted=0
        """)
    CourseRecordEntity recordById(Long id);

    @Select("""
        SELECT r.id,r.family_id,r.course_id,r.member_id,r.member_name,r.title,r.course_date,r.start_time,r.end_time,
               r.status,r.automatic,r.remaining_lessons,r.total_lessons,r.created_at,r.updated_at
        FROM course_record r WHERE r.family_id=#{familyId} AND r.deleted=0 AND r.automatic=1 AND r.status='ATTENDED'
          AND r.created_at>=#{enabledFrom} AND r.remaining_lessons IS NOT NULL
          AND NOT EXISTS(SELECT 1 FROM course_notification n WHERE n.dedup_key=CONCAT('ATTENDANCE:',r.id,':',#{userId}))
        ORDER BY r.id LIMIT 100
        """)
    List<CourseRecordEntity> missingAutomatic(@Param("familyId") Long familyId, @Param("userId") Long userId,
                                             @Param("enabledFrom") LocalDateTime enabledFrom);

    @Select("""
        SELECT c.member_id,u.display_name AS member_name,c.title,b.remaining_lessons,b.total_lessons FROM course c
        JOIN family f ON f.id=c.family_id AND f.deleted=0 AND f.status='ACTIVE'
        JOIN sys_user u ON u.id=c.member_id AND u.deleted=0 AND u.status='ACTIVE'
        JOIN family_member m ON m.family_id=c.family_id AND m.user_id=c.member_id AND m.deleted=0
        LEFT JOIN course_balance b ON b.family_id=c.family_id AND b.member_id=c.member_id AND b.title=c.title AND b.deleted=0
        WHERE c.family_id=#{familyId} AND c.deleted=0
        UNION
        SELECT b.member_id,u.display_name AS member_name,b.title,b.remaining_lessons,b.total_lessons FROM course_balance b
        JOIN family f ON f.id=b.family_id AND f.deleted=0 AND f.status='ACTIVE'
        JOIN sys_user u ON u.id=b.member_id AND u.deleted=0 AND u.status='ACTIVE'
        JOIN family_member m ON m.family_id=b.family_id AND m.user_id=b.member_id AND m.deleted=0
        WHERE b.family_id=#{familyId} AND b.deleted=0 ORDER BY member_id,title LIMIT 201
        """)
    List<CourseBalanceLine> familyBalances(Long familyId);

    @Insert("""
        INSERT INTO course_notification(dedup_key,family_id,recipient_user_id,destination_key,kind,reference_id,course_date,
            title,content,status,attempts,next_attempt_at,created_at,updated_at)
        VALUES(#{dedupKey},#{familyId},#{recipientUserId},#{destinationKey},#{kind},#{referenceId},#{courseDate},
            #{title},#{content},'PENDING',0,#{nextAttemptAt},#{createdAt},#{updatedAt})
        ON DUPLICATE KEY UPDATE id=id
        """)
    int enqueue(CourseNotification task);

    @Select("""
        SELECT id,dedup_key,family_id,recipient_user_id,destination_key,kind,reference_id,course_date,title,content,status,
               attempts,next_attempt_at,lease_until,last_error,created_at,updated_at FROM course_notification
        WHERE (status='PENDING' AND next_attempt_at<=#{now}) OR (status='SENDING' AND lease_until<=#{now})
        ORDER BY id LIMIT #{limit}
        """)
    List<CourseNotification> due(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Update("""
        UPDATE course_notification SET status='SENDING',attempts=attempts+1,lease_until=#{until},updated_at=#{now}
        WHERE id=#{id} AND attempts=#{attempts}
          AND ((status='PENDING' AND next_attempt_at<=#{now}) OR (status='SENDING' AND lease_until<=#{now}))
        """)
    int claim(@Param("id") Long id, @Param("attempts") int attempts, @Param("now") LocalDateTime now,
              @Param("until") LocalDateTime until);

    @Update("""
        UPDATE course_notification SET status=#{status},next_attempt_at=#{next},lease_until=NULL,last_error=#{error},
            title=#{task.title},content=#{task.content},updated_at=#{now}
        WHERE id=#{task.id} AND status='SENDING' AND attempts=#{task.attempts}
        """)
    int finish(@Param("task") CourseNotification task, @Param("status") String status,
               @Param("next") LocalDateTime next, @Param("now") LocalDateTime now, @Param("error") String error);

    @Update("""
        UPDATE course_notification SET status='PENDING',attempts=attempts-1,next_attempt_at=#{next},lease_until=NULL,updated_at=#{now}
        WHERE id=#{id} AND status='SENDING' AND attempts=#{attempts}
        """)
    int defer(@Param("id") Long id, @Param("attempts") int attempts, @Param("next") LocalDateTime next,
              @Param("now") LocalDateTime now);

    @Update("""
        UPDATE course_notification SET status='FAILED',lease_until=NULL,last_error='ATTEMPTS_EXHAUSTED',updated_at=#{now}
        WHERE id=#{id} AND attempts=#{attempts}
          AND ((status='PENDING' AND next_attempt_at<=#{now}) OR (status='SENDING' AND lease_until<=#{now}))
        """)
    int exhaust(@Param("id") Long id, @Param("attempts") int attempts, @Param("now") LocalDateTime now);
}
