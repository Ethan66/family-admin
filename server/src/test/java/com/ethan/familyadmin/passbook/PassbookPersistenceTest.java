package com.ethan.familyadmin.passbook;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.ethan.familyadmin.family.*;
import com.ethan.familyadmin.security.*;
import com.ethan.familyadmin.common.exception.BusinessException;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;

/** 独立本地临时库，环境变量不匹配时跳过；禁止连接业务数据库。 */
@EnabledIfEnvironmentVariable(named = "PASSBOOK_TEST_DB_URL", matches = "jdbc:mysql://127\\.0\\.0\\.1:33329/passbook_verification.*")
class PassbookPersistenceTest {
    @Test void migrationAndRealMapperPreserveManualInterestAndFamilyBoundaries() throws Exception {
        var url = System.getenv("PASSBOOK_TEST_DB_URL");
        Flyway.configure().dataSource(url, "root", "").target("10").load().migrate();
        assertThat(Flyway.configure().dataSource(url, "root", "").load().migrate().migrationsExecuted).isEqualTo(1);
        try (var connection = DriverManager.getConnection(url, "root", ""); var sql = connection.createStatement()) {
            sql.executeUpdate("INSERT INTO family(id,name) VALUES(31,'测试家庭'),(32,'其他家庭')");
            sql.executeUpdate("INSERT INTO sys_user(id,username,password_hash,display_name) VALUES(7,'passbook_test','unused','爸爸'),(8,'other_test','unused','其他成员')");
            sql.executeUpdate("INSERT INTO family_member(family_id,user_id,role) VALUES(31,7,'FAMILY_MEMBER'),(32,8,'FAMILY_MEMBER')");
        }
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("passbook-test", new JdbcTransactionFactory(), new UnpooledDataSource("com.mysql.cj.jdbc.Driver", url, "root", "")));
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        configuration.addInterceptor(interceptor);
        configuration.addMapper(PassbookMapper.class);
        configuration.addMapper(UserMapper.class);
        configuration.addMapper(FamilyMemberMapper.class);
        try (var session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(true)) {
            var service = new PassbookService(session.getMapper(PassbookMapper.class), session.getMapper(UserMapper.class), session.getMapper(FamilyMemberMapper.class), new TenantAccessGuard());
            TenantContext.set(new CurrentUser(7L, 31L, Role.FAMILY_MEMBER, 0));
            var created = service.create(payload(7L, "123.45"));
            assertThat(created.bank()).isEqualTo("农商");
            assertThat(created.note()).isEqualTo("学费备用");
            assertThat(service.list(7L, LocalDate.of(2024, 2, 29), LocalDate.of(2024, 2, 29), 1, 20).records().getFirst().totalAmount()).isEqualByComparingTo("10123.45");
            assertThat(service.list(null, LocalDate.of(2024, 3, 1), null, 1, 20).total()).isZero();
            assertThatThrownBy(() -> service.create(payload(8L, "5"))).isInstanceOf(BusinessException.class);
            TenantContext.set(new CurrentUser(8L, 32L, Role.FAMILY_MEMBER, 0));
            assertThat(service.list(null, null, null, 1, 20).total()).isZero();
            assertThatThrownBy(() -> service.update(created.id(), payload(8L, "5"))).isInstanceOf(BusinessException.class);
            service.create(payload(8L, "10"));
            TenantContext.set(new CurrentUser(7L, 31L, Role.FAMILY_MEMBER, 0));
            var update = payload(7L, "200.01");
            service.update(created.id(), new PassbookPayload(update.memberId(), update.depositDate(), update.amount(), update.termYears(), update.interestRate(), update.totalInterest(), "", ""));
            session.clearCache();
            var row = service.list(null, null, null, 1, 20).records().getFirst();
            assertThat(row.bank()).isNull();
            assertThat(row.note()).isNull();
            assertThat(row.totalInterest()).isEqualByComparingTo("200.01");
            assertThat(row.totalAmount()).isEqualByComparingTo("10200.01");
            assertThat(row.maturityDate()).isEqualTo(LocalDate.of(2027, 2, 28));
            var additional = service.create(new PassbookPayload(7L, LocalDate.of(2024, 1, 1), new BigDecimal("200"), 1, BigDecimal.ONE, new BigDecimal("10"), "农商", null));
            var firstPage = service.list(7L, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31), 1, 1);
            assertThat(firstPage.records()).hasSize(1);
            assertThat(firstPage.total()).isEqualTo(2);
            assertThat(firstPage.totalPrincipal()).isEqualByComparingTo("10200");
            assertThat(firstPage.totalAmount()).isEqualByComparingTo("10410.01");
            var secondPage = service.list(7L, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31), 2, 1);
            assertThat(secondPage.totalPrincipal()).isEqualByComparingTo(firstPage.totalPrincipal());
            assertThat(secondPage.totalAmount()).isEqualByComparingTo(firstPage.totalAmount());
            var emptyYear = service.list(7L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), 1, 20);
            assertThat(emptyYear.total()).isZero();
            assertThat(emptyYear.totalPrincipal()).isEqualByComparingTo("0");
            assertThat(emptyYear.totalAmount()).isEqualByComparingTo("0");
            for (var sort : PassbookSort.values()) {
                var sorted = service.list(7L, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31), 1, 1, sort);
                var expectedId = sort.name().endsWith("ASC") ? additional.id() : created.id();
                assertThat(sorted.records().getFirst().id()).isEqualTo(expectedId);
                assertThat(sorted.total()).isEqualTo(2);
                assertThat(sorted.totalPrincipal()).isEqualByComparingTo("10200");
                assertThat(sorted.totalAmount()).isEqualByComparingTo("10410.01");
            }
            service.delete(additional.id());
            service.delete(created.id());
            var empty = service.list(null, null, null, 1, 20);
            assertThat(empty.total()).isZero();
            assertThat(empty.totalPrincipal()).isEqualByComparingTo("0");
            assertThat(empty.totalAmount()).isEqualByComparingTo("0");
            TenantContext.set(new CurrentUser(8L, 32L, Role.FAMILY_MEMBER, 0));
            assertThat(service.list(null, null, null, 1, 20).total()).isEqualTo(1);
        } finally { TenantContext.clear(); }
    }

    private PassbookPayload payload(Long memberId, String interest) {
        return new PassbookPayload(memberId, LocalDate.of(2024, 2, 29), new BigDecimal("10000"), 3, new BigDecimal("2.15"), new BigDecimal(interest), "农商", "学费备用");
    }
}
