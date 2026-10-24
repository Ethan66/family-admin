package com.ethan.familyadmin.course;

import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;

@Mapper
public interface CourseAutoAttendanceStateMapper {
    record State(LocalDateTime enabledFrom, LocalDateTime scannedThrough) {}
    @Insert("INSERT IGNORE INTO course_auto_attendance_state(id,enabled_from,scanned_through) VALUES(1,#{now},#{now})")
    int initialize(LocalDateTime now);
    @Select("SELECT enabled_from AS enabledFrom,scanned_through AS scannedThrough FROM course_auto_attendance_state WHERE id=1")
    State read();
    @Update("UPDATE course_auto_attendance_state SET scanned_through=GREATEST(scanned_through,#{through}) WHERE id=1")
    void advance(LocalDateTime through);
}
