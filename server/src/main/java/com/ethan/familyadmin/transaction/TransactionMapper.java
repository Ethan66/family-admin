package com.ethan.familyadmin.transaction;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ethan.familyadmin.transaction.dto.TransactionAggregate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

@Mapper
public interface TransactionMapper extends BaseMapper<TransactionEntity> {
    @Select("""
        <script>
        SELECT
            COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END), 0) AS income,
            COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END), 0) AS expense,
            COUNT(CASE WHEN type = 'INCOME' THEN 1 END) AS incomeCount,
            COUNT(CASE WHEN type = 'EXPENSE' THEN 1 END) AS expenseCount
        FROM transaction_record
        WHERE family_id = #{familyId} AND deleted = 0
        <if test="type != null">AND type = #{type}</if>
        <if test="category != null and category != ''">AND category = #{category}</if>
        <if test="memberName != null and memberName != ''">AND member_name = #{memberName}</if>
        <if test="bankCard != null and bankCard != ''">AND bank_card = #{bankCard}</if>
        <if test="platform != null and platform != ''">AND platform = #{platform}</if>
        <if test="monthStart != null">
            AND transaction_date &gt;= #{monthStart}
            AND transaction_date &lt; #{monthEnd}
        </if>
        </script>
        """)
    TransactionAggregate selectAggregate(
        @Param("familyId") Long familyId,
        @Param("type") String type,
        @Param("category") String category,
        @Param("memberName") String memberName,
        @Param("bankCard") String bankCard,
        @Param("platform") String platform,
        @Param("monthStart") LocalDate monthStart,
        @Param("monthEnd") LocalDate monthEnd
    );
}
