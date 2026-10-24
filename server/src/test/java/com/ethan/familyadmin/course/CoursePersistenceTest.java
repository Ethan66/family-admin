package com.ethan.familyadmin.course;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.annotation.DbType;
import com.ethan.familyadmin.family.*;
import com.ethan.familyadmin.security.*;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.DriverManager;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

/** 仅在明确指定独立本地临时库时执行，不读取业务数据库配置。 */
@EnabledIfEnvironmentVariable(named = "COURSE_TEST_DB_URL", matches = "jdbc:mysql://127\\.0\\.0\\.1:33319/course_verification.*")
class CoursePersistenceTest {
    @Test void migrationAndRealMapperRoundTripPreserveHistoryAndUpsert() throws Exception {
        var url = System.getenv("COURSE_TEST_DB_URL");
        Flyway.configure().dataSource(url, "root", "").load().migrate();
        try (var connection = DriverManager.getConnection(url, "root", ""); var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO family(id,name) VALUES(31,'测试家庭')");
            statement.executeUpdate("INSERT INTO sys_user(id,username,password_hash,display_name) VALUES(7,'course_test','unused','小宇')");
            statement.executeUpdate("INSERT INTO family_member(family_id,user_id,role) VALUES(31,7,'FAMILY_MEMBER')");
        }
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("course-test", new JdbcTransactionFactory(),
            new UnpooledDataSource("com.mysql.cj.jdbc.Driver", url, "root", "")));
        var pagination = new MybatisPlusInterceptor(); pagination.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        configuration.addInterceptor(pagination);
        configuration.addMapper(CourseMapper.class); configuration.addMapper(CourseRecordMapper.class); configuration.addMapper(CourseBalanceMapper.class);
        var factory = new MybatisSqlSessionFactoryBuilder().build(configuration);
        TenantContext.set(new CurrentUser(7L, 31L, Role.FAMILY_MEMBER, 0));
        try (var session = factory.openSession(true)) {
            var users = mock(UserMapper.class); var members = mock(FamilyMemberMapper.class);
            var user = new UserEntity(); user.setDisplayName("小宇"); user.setStatus("ACTIVE");
            when(users.selectById(7L)).thenReturn(user); when(members.selectCount(any())).thenReturn(1L);
            // 课程及记录读写使用真实 Mapper；成员信息固定为当前临时库中的测试身份。
            var courses = session.getMapper(CourseMapper.class); var records = session.getMapper(CourseRecordMapper.class);
            var service = new CourseService(courses, records, users, members, session.getMapper(CourseBalanceMapper.class));
            var date = LocalDate.now();
            service.create(new CoursePayload("英语", 7L, date.getDayOfWeek().getValue(), LocalTime.of(15, 0), LocalTime.of(16, 30), "blue"));
            var occurrence = service.list(null);
            assertThat(occurrence).hasSize(1);
            var id = occurrence.getFirst().id();
            service.saveBalance(7L, "英语", new java.math.BigDecimal("10"), new java.math.BigDecimal("40"));
            service.record(id, new AttendancePayload(date, "ATTENDED", "首次记录"));
            assertThat(service.balances(null).getFirst().remainingLessons()).isEqualByComparingTo("9");
            assertThat(service.history(null, 1, "英语", "ATTENDED", date.toString().substring(0, 7)).records().getFirst().getTotalLessons()).isEqualByComparingTo("40");
            assertThat(service.history(null, 1, "英语", "LEAVE", date.toString().substring(0, 7)).total()).isZero();
            assertThat(service.history(null, 1, "英语", "ATTENDED", "2099-01").total()).isZero();
            service.record(id, new AttendancePayload(date, "ATTENDED", "重复保存"));
            assertThat(service.balances(null).getFirst().remainingLessons()).isEqualByComparingTo("9");
            service.record(id, new AttendancePayload(date, "LEAVE", "请假"));
            assertThat(service.balances(null).getFirst().remainingLessons()).isEqualByComparingTo("10");
            assertThat(service.history(null, 1).total()).isEqualTo(1);
            assertThat(service.history(null, 1).records().getFirst().getStatus()).isEqualTo("LEAVE");
            assertThat(service.list(null).getFirst().status()).isEqualTo("LEAVE");
            service.update(id, new CoursePayload("英语新版", 7L, date.getDayOfWeek().getValue(), LocalTime.of(14, 0), LocalTime.of(15, 0), "green"));
            assertThat(service.history(null, 1).records().getFirst().getTitle()).isEqualTo("英语");
            service.delete(id);
            assertThat(service.list(null)).isEmpty();
            assertThat(service.history(null, 1).total()).isEqualTo(1);
            TenantContext.set(new CurrentUser(7L, 99L, Role.FAMILY_MEMBER, 0));
            assertThat(service.history(null, 1).total()).isZero();
        } finally { TenantContext.clear(); }
    }
}
