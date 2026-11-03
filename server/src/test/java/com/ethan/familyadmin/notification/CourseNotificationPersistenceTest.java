package com.ethan.familyadmin.notification;

import com.baomidou.mybatisplus.core.*;
import com.ethan.familyadmin.course.*;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.DriverManager;
import java.time.*;
import java.util.List;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 独立本地临时库验证真实 SQL、去重与租约，不允许连接生产地址。 */
@EnabledIfEnvironmentVariable(named="COURSE_TEST_DB_URL",matches="jdbc:mysql://127\\.0\\.0\\.1:33319/course_verification.*")
class CourseNotificationPersistenceTest {
    @Test void migrationDeduplicationRecipientIsolationAndCrashRecovery() throws Exception {
        var url=System.getenv("COURSE_TEST_DB_URL");
        Flyway.configure().dataSource(url,"root","").load().migrate();
        try(var connection=DriverManager.getConnection(url,"root",""); var sql=connection.createStatement()) {
            sql.executeUpdate("INSERT INTO family(id,name) VALUES(901,'提醒测试家庭'),(911,'其他家庭')");
            sql.executeUpdate("INSERT INTO sys_user(id,username,password_hash,display_name) VALUES(902,'notice_zc','unused','zc'),(903,'notice_xi','unused','xi'),(904,'notice_han','unused','han'),(912,'other_notice_user','unused','其他')");
            sql.executeUpdate("INSERT INTO family_member(family_id,user_id,role) VALUES(901,902,'FAMILY_MEMBER'),(901,903,'FAMILY_MEMBER'),(901,904,'FAMILY_MEMBER'),(911,912,'FAMILY_MEMBER')");
            sql.executeUpdate("""
                INSERT INTO course(id,family_id,member_id,member_name,title,course_date,start_time,end_time,color,created_by,updated_by)
                VALUES(901,901,904,'han','英语','2000-01-04','18:00:00','19:00:00','blue',902,902)
                """);
            sql.executeUpdate("INSERT INTO course_balance(family_id,member_id,title,remaining_lessons,total_lessons,created_by,updated_by) VALUES(901,904,'英语',11,12,902,902),(901,904,'篮球',19,19,902,902)");
        }
        var dataSource=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",url,"root","");
        var configuration=new MybatisConfiguration(); configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("notification-test",new SpringManagedTransactionFactory(),dataSource));
        configuration.addMapper(CourseNotificationMapper.class);
        var session=new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(configuration));
        var mapper=session.getMapper(CourseNotificationMapper.class);
        assertThat(mapper.recipientAllowed(901L,912L)).isZero();
        var properties=CourseNotificationServiceTest.properties();
        properties.getDestinations().get(0).setFamilyId(901L); properties.getDestinations().get(0).setUserId(902L);
        properties.getDestinations().get(1).setFamilyId(901L); properties.getDestinations().get(1).setUserId(903L);
        var clock=Clock.fixed(Instant.parse("2026-11-03T09:30:00Z"),ZoneId.of("Asia/Shanghai"));
        var now=LocalDateTime.now(clock);
        var service=new CourseNotificationService(mapper,properties,clock);
        service.scanUpcoming(); service.scanUpcoming();
        assertThat(mapper.due(now,10)).hasSize(2);
        assertThat(mapper.familyBalances(901L)).extracting(CourseBalanceLine::title).containsExactlyInAnyOrder("英语","篮球");
        try(var connection=DriverManager.getConnection(url,"root",""); var sql=connection.createStatement()) {
            // 已提交的自动记录与今天请假的课次分开，不能为请假课程发送上课前提醒。
            sql.executeUpdate("""
                INSERT INTO course_record(id,family_id,course_id,member_id,member_name,title,course_date,start_time,end_time,status,
                    automatic,remaining_lessons,total_lessons,created_by,created_at,updated_by)
                VALUES(901,901,901,904,'han','英语','2026-10-27','18:00:00','19:00:00','ATTENDED',1,11,12,902,'2026-11-03 17:00:00',902),
                      (902,901,901,904,'han','英语','2026-11-03','18:00:00','19:00:00','LEAVE',0,NULL,NULL,902,'2026-11-03 17:00:00',902)
                """);
        }
        service.afterAutomaticAttendance(901L,LocalDate.of(2026,10,27));
        service.recoverCompleted(); service.recoverCompleted();
        assertThat(mapper.due(now,10)).hasSize(4);
        var sender=mock(HermesNotificationSender.class);
        var dispatcher=new CourseNotificationDispatcher(mapper,properties,sender,clock);
        dispatcher.dispatch();
        verify(sender,times(2)).send(any(),any());
        assertThat(mapper.due(now,10)).isEmpty();
        try(var connection=DriverManager.getConnection(url,"root",""); var sql=connection.createStatement();
            var rows=sql.executeQuery("SELECT status,COUNT(*) AS count FROM course_notification WHERE family_id=901 GROUP BY status ORDER BY status")) {
            assertThat(rows.next()).isTrue(); assertThat(rows.getString("status")).isEqualTo("CANCELLED"); assertThat(rows.getInt("count")).isEqualTo(2);
            assertThat(rows.next()).isTrue(); assertThat(rows.getString("status")).isEqualTo("SENT"); assertThat(rows.getInt("count")).isEqualTo(2);
        }

        var task=new CourseNotification(); task.setDedupKey("lease-test"); task.setFamilyId(901L); task.setRecipientUserId(902L);
        task.setDestinationKey("zc"); task.setKind("UPCOMING"); task.setReferenceId(901L); task.setCourseDate(LocalDate.of(2026,11,3));
        task.setTitle("租约验证"); task.setContent("验证"); task.setNextAttemptAt(now); task.setCreatedAt(now); task.setUpdatedAt(now);
        mapper.enqueue(task);
        var pending=mapper.due(now,10).getFirst();
        try(var executor=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            Callable<Integer> claim=() -> { start.await(); return mapper.claim(pending.getId(),0,now,now.plusMinutes(2)); };
            var a=executor.submit(claim); var b=executor.submit(claim); start.countDown();
            assertThat(a.get(10,TimeUnit.SECONDS)+b.get(10,TimeUnit.SECONDS)).isEqualTo(1);
        }
        assertThat(mapper.due(now.plusMinutes(1),10)).isEmpty();
        assertThat(mapper.due(now.plusMinutes(3),10)).hasSize(1);
        assertThat(mapper.claim(pending.getId(),1,now.plusMinutes(3),now.plusMinutes(5))).isEqualTo(1);
        pending.setAttempts(1);
        assertThat(mapper.finish(pending,"FAILED",now,now,"OLD_WORKER")).isZero();
        pending.setAttempts(2);
        assertThat(mapper.finish(pending,"SENT",now,now,null)).isEqualTo(1);
        assertThat(mapper.due(now.plusMinutes(10),10)).isEmpty();
    }
}
