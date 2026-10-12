package com.ethan.familyadmin.transaction;

import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionMapperSqlTest {

    @Test
    void aggregateSqlWithoutOptionalFiltersStillRequiresFamilyAndNonDeletedRows() {
        Configuration configuration = new Configuration();
        configuration.addMapper(TransactionMapper.class);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("familyId", 31L);
        parameters.put("type", null);
        parameters.put("category", null);
        parameters.put("monthStart", null);
        parameters.put("monthEnd", null);

        BoundSql boundSql = configuration.getMappedStatement(
            TransactionMapper.class.getName() + ".selectAggregate").getBoundSql(parameters);

        assertThat(boundSql.getSql()).contains("family_id = ?", "deleted = 0")
            .doesNotContain("AND type =", "AND category =", "transaction_date >=");
        assertThat(boundSql.getParameterMappings()).hasSize(1);
    }

    @Test
    void aggregateSqlAlwaysScopesTheCurrentFamilyAndCanApplyAllFilters() {
        Configuration configuration = new Configuration();
        configuration.addMapper(TransactionMapper.class);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("familyId", 31L);
        parameters.put("type", "EXPENSE");
        parameters.put("category", "餐饮");
        parameters.put("monthStart", LocalDate.of(2026, 10, 1));
        parameters.put("monthEnd", LocalDate.of(2026, 11, 1));

        BoundSql boundSql = configuration.getMappedStatement(
            TransactionMapper.class.getName() + ".selectAggregate").getBoundSql(parameters);

        assertThat(boundSql.getSql()).contains("family_id = ?", "deleted = 0", "type = ?",
            "category = ?", "transaction_date >= ?", "transaction_date < ?");
        assertThat(boundSql.getParameterMappings()).hasSize(5);
    }
}
