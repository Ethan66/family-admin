package com.ethan.familyadmin.task;
import org.apache.ibatis.annotations.*;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface TaskCompletionMapper {
    record Completion(Long taskId, LocalDate taskDate) {}

    @Select("SELECT task_id, task_date FROM daily_task_completion WHERE family_id=#{familyId} AND task_date BETWEEN #{from} AND #{to}")
    List<Completion> list(@Param("familyId") Long familyId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Insert("INSERT INTO daily_task_completion(task_id,task_date,family_id,created_by) VALUES(#{id},#{date},#{familyId},#{actor}) ON DUPLICATE KEY UPDATE task_id=task_id")
    void complete(@Param("familyId") Long familyId, @Param("id") Long id, @Param("date") LocalDate date, @Param("actor") Long actor);

    @Delete("DELETE FROM daily_task_completion WHERE family_id=#{familyId} AND task_id=#{id} AND task_date=#{date}")
    void undo(@Param("familyId") Long familyId, @Param("id") Long id, @Param("date") LocalDate date);

    @Delete("DELETE FROM daily_task_completion WHERE family_id=#{familyId} AND task_id=#{id}")
    void clear(@Param("familyId") Long familyId, @Param("id") Long id);
}
