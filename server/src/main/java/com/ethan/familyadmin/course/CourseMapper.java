package com.ethan.familyadmin.course;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
@Mapper
public interface CourseMapper extends BaseMapper<CourseEntity> {
    @Select("SELECT * FROM course WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    CourseEntity selectForUpdate(Long id);
}
