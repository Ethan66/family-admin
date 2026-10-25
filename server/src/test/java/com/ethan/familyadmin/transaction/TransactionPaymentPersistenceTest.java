package com.ethan.familyadmin.transaction;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.ethan.familyadmin.security.*;
import com.ethan.familyadmin.transaction.dto.*;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

/** 仅连接本任务显式指定的独立本地临时库，不读取业务数据库配置。 */
@EnabledIfEnvironmentVariable(named = "TRANSACTION_TEST_DB_URL", matches = "jdbc:mysql://127\\.0\\.0\\.1:33329/transaction_verification.*")
class TransactionPaymentPersistenceTest {
    @Test void migrationPreservesHistoryAndRealMapperSupportsFilteringAndClearing() throws Exception {
        var url = System.getenv("TRANSACTION_TEST_DB_URL");
        Flyway.configure().dataSource(url, "root", "").target("7").load().migrate();
        try (var connection = DriverManager.getConnection(url, "root", ""); var sql = connection.createStatement()) {
            sql.executeUpdate("INSERT INTO family(id,name) VALUES(31,'测试家庭'),(32,'其他家庭')");
            sql.executeUpdate("INSERT INTO transaction_record(family_id,type,amount,transaction_date,category,member_name,created_by,updated_by) " +
                "VALUES(31,'INCOME',50,'2026-10-25','工资','爸爸',7,7)");
        }
        assertThat(Flyway.configure().dataSource(url, "root", "").target("8").load().migrate().migrationsExecuted).isEqualTo(1);
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("transaction-test", new JdbcTransactionFactory(),
            new UnpooledDataSource("com.mysql.cj.jdbc.Driver", url, "root", "")));
        var pagination = new MybatisPlusInterceptor();
        pagination.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        configuration.addInterceptor(pagination);
        configuration.addMapper(TransactionMapper.class);
        var factory = new MybatisSqlSessionFactoryBuilder().build(configuration);
        TenantContext.set(new CurrentUser(7L, 31L, Role.FAMILY_MEMBER, 0));
        try (var session = factory.openSession(true)) {
            var mapper = session.getMapper(TransactionMapper.class);
            var service = new TransactionService(mapper, new TenantAccessGuard());
            var legacy = service.list(query(null, null)).records().getFirst();
            assertThat(legacy.bankCard()).isNull();
            assertThat(legacy.platform()).isNull();
            assertThat(legacy.amount()).isEqualByComparingTo("50");
            var created = service.create(request("招商", "京东"));
            service.create(request("工商", "京东"));
            service.create(request("招商", "微信"));
            TenantContext.set(new CurrentUser(7L, 32L, Role.FAMILY_MEMBER, 0));
            service.create(request("招商", "京东"));
            TenantContext.set(new CurrentUser(7L, 31L, Role.FAMILY_MEMBER, 0));
            assertThat(service.list(query("招商", "京东")).records()).extracting(TransactionResponse::id).containsExactly(created.id());
            assertThat(service.summary(query("招商", "京东")).income()).isEqualByComparingTo("100");
            assertThat(service.summary(query("招商", "京东")).incomeCount()).isEqualTo(1);
            assertThat(service.list(query("招商", null)).total()).isEqualTo(2);
            assertThat(service.list(query(null, "京东")).total()).isEqualTo(2);
            service.update(created.id(), request("", null));
            session.clearCache();
            assertThat(mapper.selectById(created.id()).getBankCard()).isNull();
            assertThat(mapper.selectById(created.id()).getPlatform()).isNull();
            assertThat(service.list(query("招商", "京东")).total()).isZero();
            assertThat(service.summary(query("招商", "京东")).income()).isEqualByComparingTo("0");
            assertThat(service.list(query(null, null)).total()).isEqualTo(4);
        } finally {
            TenantContext.clear();
        }
    }

    private TransactionQuery query(String bankCard, String platform) {
        return new TransactionQuery("2026-10", null, null, "爸爸", bankCard, platform, 1, 20);
    }

    private CreateTransactionRequest request(String bankCard, String platform) {
        return new CreateTransactionRequest(TransactionType.INCOME, new BigDecimal("100"), LocalDate.of(2026, 10, 25), "工资", "爸爸", null, bankCard, platform);
    }
}
