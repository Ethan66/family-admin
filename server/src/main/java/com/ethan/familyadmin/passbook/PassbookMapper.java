package com.ethan.familyadmin.passbook;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PassbookMapper extends BaseMapper<PassbookEntity> {
    @Select("""
        SELECT COALESCE(SUM(amount), 0) AS totalPrincipal,
               COALESCE(SUM(amount + total_interest), 0) AS totalAmount
        FROM passbook ${ew.customSqlSegment}
        """)
    PassbookTotals selectTotals(@Param(Constants.WRAPPER) Wrapper<PassbookEntity> query);
}
