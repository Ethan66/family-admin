package com.ethan.familyadmin.course;

import com.baomidou.mybatisplus.core.*;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ethan.familyadmin.family.*;
import com.ethan.familyadmin.security.*;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.DriverManager;
import java.time.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@EnabledIfEnvironmentVariable(named="COURSE_TEST_DB_URL",matches="jdbc:mysql://127\\.0\\.0\\.1:33319/course_verification.*")
class CourseAutoAttendancePersistenceTest {
    @Test void realTransactionsSerializeManualAndAutomaticWritesAndRollbackFailedDeductions() throws Exception {
        var url=System.getenv("COURSE_TEST_DB_URL");
        Flyway.configure().dataSource(url,"root","").load().migrate();
        try(var connection=DriverManager.getConnection(url,"root",""); var statement=connection.createStatement()) {
            statement.executeUpdate("INSERT INTO family(id,name) VALUES(731,'自动记课测试家庭')");
            statement.executeUpdate("INSERT INTO sys_user(id,username,password_hash,display_name) VALUES(707,'auto_course_test','unused','测试成员')");
            statement.executeUpdate("INSERT INTO family_member(family_id,user_id,role) VALUES(731,707,'FAMILY_MEMBER')");
        }
        var dataSource=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",url,"root","");
        var configuration=new MybatisConfiguration(); configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("auto-course-test",new SpringManagedTransactionFactory(),dataSource));
        configuration.addMapper(CourseMapper.class); configuration.addMapper(CourseRecordMapper.class);
        configuration.addMapper(CourseBalanceMapper.class); configuration.addMapper(CourseAutoAttendanceStateMapper.class);
        var session=new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(configuration));
        var courses=session.getMapper(CourseMapper.class); var records=session.getMapper(CourseRecordMapper.class);
        var balances=session.getMapper(CourseBalanceMapper.class); var states=session.getMapper(CourseAutoAttendanceStateMapper.class);
        var tx=new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        var worker=new CourseAutoAttendanceService(courses,records,balances);
        var date=LocalDate.of(2030,1,6); var enabled=date.minusDays(1).atTime(22,0); var now=date.atTime(15,1);
        var id=create(courses,balances,"自动英语",date);
        assertThat(tx.<Boolean>execute(status -> worker.complete(id,date,enabled,date.atTime(14,59))).booleanValue()).isFalse();
        var failedBalances=mock(CourseBalanceMapper.class);
        when(failedBalances.adjust(any(),any(),any(),anyInt(),any())).thenThrow(new IllegalStateException("simulated deduction failure"));
        var failedWorker=new CourseAutoAttendanceService(courses,records,failedBalances);
        assertThatThrownBy(() -> tx.execute(status -> failedWorker.complete(id,date,enabled,now))).isInstanceOf(IllegalStateException.class);
        assertThat(records.selectCount(Wrappers.<CourseRecordEntity>lambdaQuery().eq(CourseRecordEntity::getCourseId,id))).isZero();
        assertThat(balance(balances,"自动英语")).isEqualByComparingTo("5");
        // 扣减已成功而保存记录失败时，余额和快照必须一起回滚。
        var failedRecords=mock(CourseRecordMapper.class);
        doThrow(new IllegalStateException("simulated snapshot write failure")).when(failedRecords).upsert(any());
        var failedSnapshotWorker=new CourseAutoAttendanceService(courses,failedRecords,balances);
        assertThatThrownBy(() -> tx.execute(status -> failedSnapshotWorker.complete(id,date,enabled,now))).isInstanceOf(IllegalStateException.class);
        assertThat(balance(balances,"自动英语")).isEqualByComparingTo("5");
        assertThat(records.selectCount(Wrappers.<CourseRecordEntity>lambdaQuery().eq(CourseRecordEntity::getCourseId,id))).isZero();
        try(var executor=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            Callable<Boolean> task=() -> { start.await(); return tx.execute(status -> worker.complete(id,date,enabled,now)); };
            var first=executor.submit(task); var second=executor.submit(task); start.countDown();
            assertThat(List.of(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        assertThat(balance(balances,"自动英语")).isEqualByComparingTo("4");
        var automatic=records.selectOne(Wrappers.<CourseRecordEntity>lambdaQuery().eq(CourseRecordEntity::getCourseId,id));
        assertThat(automatic.getAutomatic()).isTrue();
        assertThat(automatic.getRemainingLessons()).isEqualByComparingTo("4");
        assertThat(automatic.getTotalLessons()).isEqualByComparingTo("5");
        assertThat(tx.<Boolean>execute(status -> worker.complete(id,date,enabled,now)).booleanValue()).isFalse();
        assertThat(balance(balances,"自动英语")).isEqualByComparingTo("4");

        // 手工与自动同时处理同一课次，两者共用行锁及唯一键，只扣一次。
        var today=LocalDate.now(); var raceId=create(courses,balances,"并发英语",today);
        var manual=new CourseService(courses,records,mock(UserMapper.class),mock(FamilyMemberMapper.class),balances);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            var auto=executor.submit(() -> { start.await(); return tx.execute(status -> worker.complete(raceId,today,today.atStartOfDay(),today.atTime(16,0))); });
            var human=executor.submit(() -> { start.await(); tx.executeWithoutResult(status -> TenantContext.runAs(new CurrentUser(707L,731L,Role.FAMILY_MEMBER,0),
                () -> manual.record(raceId,new AttendancePayload(today,"ATTENDED","手工确认")))); return true; });
            start.countDown(); auto.get(15,TimeUnit.SECONDS); human.get(15,TimeUnit.SECONDS);
        }
        assertThat(records.selectCount(Wrappers.<CourseRecordEntity>lambdaQuery().eq(CourseRecordEntity::getCourseId,raceId))).isEqualTo(1);
        assertThat(balance(balances,"并发英语")).isEqualByComparingTo("4");
        var manualAutomaticRecord=records.selectOne(Wrappers.<CourseRecordEntity>lambdaQuery().eq(CourseRecordEntity::getCourseId,raceId));
        assertThat(manualAutomaticRecord.getRemainingLessons()).isEqualByComparingTo("4");

        // 不同课程 ID 共享同一科目余额，两个快照必须分别对应扣减后的 4 和 3。
        var subjectFirst=create(courses,balances,"共享英语",date);
        var subjectSecond=create(courses,balances,"共享英语",date);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            var first=executor.submit(() -> { start.await(); return tx.execute(status -> worker.complete(subjectFirst,date,enabled,now)); });
            var second=executor.submit(() -> { start.await(); return tx.execute(status -> worker.complete(subjectSecond,date,enabled,now)); });
            start.countDown(); assertThat(first.get(15,TimeUnit.SECONDS)).isTrue(); assertThat(second.get(15,TimeUnit.SECONDS)).isTrue();
        }
        assertThat(balance(balances,"共享英语")).isEqualByComparingTo("3");
        var snapshots=records.selectList(Wrappers.<CourseRecordEntity>lambdaQuery().in(CourseRecordEntity::getCourseId,List.of(subjectFirst,subjectSecond)));
        assertThat(snapshots).extracting(row -> row.getRemainingLessons().intValueExact()).containsExactlyInAnyOrder(4,3);
        assertThat(snapshots).allSatisfy(row -> assertThat(row.getTotalLessons()).isEqualByComparingTo("5"));
        create(courses,balances,"任务英语",date);
        states.initialize(enabled);
        var job=new CourseAutoAttendanceJob(courses,states,worker,
            Clock.fixed(now.atZone(ZoneId.of("Asia/Shanghai")).toInstant(),ZoneId.of("Asia/Shanghai")));
        // Job 通过 Spring 事务代理调用 worker，验证其实际扫描及检查点。
        var proxy=new org.springframework.aop.framework.ProxyFactory(worker);
        proxy.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(
            new DataSourceTransactionManager(dataSource),new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        job=new CourseAutoAttendanceJob(courses,states,(CourseAutoAttendanceService)proxy.getProxy(),
            Clock.fixed(now.atZone(ZoneId.of("Asia/Shanghai")).toInstant(),ZoneId.of("Asia/Shanghai")));
        job.run(); job.run();
        assertThat(states.read().scannedThrough()).isEqualTo(now);
        assertThat(balance(balances,"任务英语")).isEqualByComparingTo("4");
    }
    private Long create(CourseMapper courses,CourseBalanceMapper balances,String title,LocalDate date) {
        var course=new CourseEntity(); course.setFamilyId(731L); course.setMemberId(707L); course.setMemberName("测试成员");
        course.setCreatedBy(707L); course.setUpdatedBy(707L); course.setCreatedAt(date.minusDays(2).atStartOfDay()); course.setUpdatedAt(course.getCreatedAt());
        course.setDeleted(0); course.setTitle(title); course.setCourseDate(LocalDate.of(2000,1,3).plusDays(date.getDayOfWeek().getValue()-1));
        course.setStartTime(LocalTime.of(13,30)); course.setEndTime(LocalTime.of(15,0)); course.setRepeatWeekly(true); course.setColor("blue"); courses.insert(course);
        var balance=new CourseBalanceEntity(); balance.setFamilyId(731L); balance.setMemberId(707L); balance.setTitle(title);
        balance.setRemainingLessons(new BigDecimal("5")); balance.setTotalLessons(new BigDecimal("5"));
        balance.setCreatedBy(707L); balance.setUpdatedBy(707L); balance.setCreatedAt(course.getCreatedAt()); balance.setUpdatedAt(course.getCreatedAt()); balances.upsert(balance);
        return course.getId();
    }
    private BigDecimal balance(CourseBalanceMapper balances,String title) {
        return balances.selectOne(Wrappers.<CourseBalanceEntity>lambdaQuery().eq(CourseBalanceEntity::getFamilyId,731L)
            .eq(CourseBalanceEntity::getMemberId,707L).eq(CourseBalanceEntity::getTitle,title)).getRemainingLessons();
    }
}
