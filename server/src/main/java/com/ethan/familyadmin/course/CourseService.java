package com.ethan.familyadmin.course;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ethan.familyadmin.common.exception.*;
import com.ethan.familyadmin.family.*;
import com.ethan.familyadmin.security.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import java.math.BigDecimal;

@Service
public class CourseService {
    private final CourseMapper mapper;
    private final CourseRecordMapper records;
    private final UserMapper users;
    private final FamilyMemberMapper members;
    private final CourseBalanceMapper balanceMapper;
    public CourseService(CourseMapper mapper, CourseRecordMapper records, UserMapper users, FamilyMemberMapper members, CourseBalanceMapper balanceMapper) {
        this.mapper = mapper; this.records = records; this.users = users; this.members = members;
        this.balanceMapper = balanceMapper;
    }
    private Long family() {
        var family = TenantContext.requiredUser().familyId();
        if (family == null) throw new BusinessException(ErrorCode.FORBIDDEN);
        return family;
    }
    private void invalid() { throw new BusinessException(ErrorCode.VALIDATION_ERROR); }

    @Transactional(readOnly = true)
    public List<CourseOccurrence> list(Long memberId) {
        var familyId = family();
        var today = LocalDate.now();
        var from = today.minusDays(today.getDayOfWeek().getValue() - 1);
        var to = from.plusDays(6);
        var courses = mapper.selectList(Wrappers.<CourseEntity>lambdaQuery().eq(CourseEntity::getFamilyId, familyId)
            .eq(memberId != null, CourseEntity::getMemberId, memberId));
        var saved = records.selectList(Wrappers.<CourseRecordEntity>lambdaQuery().eq(CourseRecordEntity::getFamilyId, familyId)
            .between(CourseRecordEntity::getCourseDate, from, to));
        var attendance = new HashMap<String, CourseRecordEntity>();
        for (var record : saved) attendance.put(record.getCourseId() + ":" + record.getCourseDate(), record);
        List<CourseOccurrence> result = new ArrayList<>();
        for (var course : courses) {
            var day = course.getCourseDate().getDayOfWeek().getValue();
            var date = from.plusDays(day - 1);
            var record = attendance.get(course.getId() + ":" + date);
            result.add(new CourseOccurrence(course.getId(), course.getCreatedBy(), course.getMemberId(), course.getMemberName(), course.getTitle(),
                day, date, course.getStartTime(), course.getEndTime(),
                course.getColor(), record == null ? "PENDING" : record.getStatus(), record == null ? "" : record.getNote()));
        }
        result.sort(Comparator.comparing(CourseOccurrence::date).thenComparing(CourseOccurrence::startTime).thenComparing(CourseOccurrence::id));
        return result;
    }

    public record RecordPage(List<CourseRecordEntity> records, long total) {}
    @Transactional(readOnly = true)
    public RecordPage history(Long memberId, int page) {
        return history(memberId, page, null, null, null);
    }
    /** 返回每条记课记录保存的课时快照；旧记录不使用当前余额回填。 */
    @Transactional(readOnly = true)
    public RecordPage history(Long memberId, int page, String title, String status, String month) {
        // 筛选只改变记录范围；课时取记录自身快照，不能关联当前余额覆盖历史。
        if (page < 1) invalid();
        if (title != null && (title.isBlank() || title.length() > 120)) invalid();
        if (status != null && !Set.of("ATTENDED", "LEAVE").contains(status)) invalid();
        YearMonth selectedMonth = null;
        if (month != null) {
            if (!month.matches("[0-9]{4}-(0[1-9]|1[0-2])")) invalid();
            try { selectedMonth = YearMonth.parse(month); } catch (java.time.format.DateTimeParseException error) { invalid(); }
        }
        var familyId = family();
        var query = Wrappers.<CourseRecordEntity>lambdaQuery()
            .eq(CourseRecordEntity::getFamilyId, familyId).eq(memberId != null, CourseRecordEntity::getMemberId, memberId)
            .eq(title != null, CourseRecordEntity::getTitle, title).eq(status != null, CourseRecordEntity::getStatus, status);
        if (selectedMonth != null) query.ge(CourseRecordEntity::getCourseDate, selectedMonth.atDay(1))
            .lt(CourseRecordEntity::getCourseDate, selectedMonth.plusMonths(1).atDay(1));
        query.orderByDesc(CourseRecordEntity::getCourseDate).orderByDesc(CourseRecordEntity::getStartTime).orderByDesc(CourseRecordEntity::getId);
        var result = records.selectPage(new Page<CourseRecordEntity>(page, 20), query);
        return new RecordPage(result.getRecords(), result.getTotal());
    }

    private record SubjectKey(Long memberId, String title) {}
    public record LessonBalance(Long courseId, String title, Long memberId, String memberName, BigDecimal remainingLessons, BigDecimal totalLessons, boolean editable) {}
    @Transactional(readOnly = true)
    public List<LessonBalance> balances(Long memberId) {
        var familyId = family();
        var courses = mapper.selectList(Wrappers.<CourseEntity>lambdaQuery().eq(CourseEntity::getFamilyId, familyId)
            .eq(memberId != null, CourseEntity::getMemberId, memberId).orderByAsc(CourseEntity::getMemberId, CourseEntity::getTitle, CourseEntity::getId));
        var saved = balanceMapper.selectList(Wrappers.<CourseBalanceEntity>lambdaQuery().eq(CourseBalanceEntity::getFamilyId, familyId)
            .eq(memberId != null, CourseBalanceEntity::getMemberId, memberId));
        var bySubject = new HashMap<SubjectKey, CourseBalanceEntity>();
        for (var balance : saved) bySubject.put(new SubjectKey(balance.getMemberId(), balance.getTitle()), balance);
        var groups = new LinkedHashMap<SubjectKey, CourseEntity>();
        for (var course : courses) {
            var key = new SubjectKey(course.getMemberId(), course.getTitle());
            if (!groups.containsKey(key) || canMaintain(course)) groups.put(key, course);
        }
        var result = new ArrayList<LessonBalance>();
        for (var entry : groups.entrySet()) {
            var course = entry.getValue();
            var balance = bySubject.get(entry.getKey());
            result.add(new LessonBalance(course.getId(), course.getTitle(), course.getMemberId(), course.getMemberName(),
                balance == null ? null : balance.getRemainingLessons(), balance == null ? null : balance.getTotalLessons(), canMaintain(course)));
        }
        for (var balance : saved) {
            if (groups.containsKey(new SubjectKey(balance.getMemberId(), balance.getTitle()))) continue;
            var user = users.selectById(balance.getMemberId());
            if (user == null) continue;
            var actor = TenantContext.requiredUser();
            boolean editable = actor.role() == Role.SUPER_ADMIN || actor.userId().equals(balance.getMemberId()) || actor.userId().equals(balance.getCreatedBy());
            result.add(new LessonBalance(null, balance.getTitle(), balance.getMemberId(), user.getDisplayName(), balance.getRemainingLessons(), balance.getTotalLessons(), editable));
        }
        return result;
    }
    @Transactional(rollbackFor = Exception.class)
    public void saveBalance(Long memberId, String title, BigDecimal remainingLessons, BigDecimal totalLessons) {
        if (remainingLessons == null || remainingLessons.signum() < 0 || remainingLessons.scale() > 2 || remainingLessons.compareTo(new BigDecimal("99999999.99")) > 0) invalid();
        if (totalLessons != null && (totalLessons.signum() < 0 || totalLessons.scale() > 2 || totalLessons.compareTo(new BigDecimal("99999999.99")) > 0)) invalid();
        if (memberId == null || title == null || title.isBlank() || title.length() > 120) invalid();
        var familyId = family();
        var user = users.selectById(memberId);
        if (user == null || !"ACTIVE".equals(user.getStatus()) || members.selectCount(Wrappers.<FamilyMemberEntity>lambdaQuery()
            .eq(FamilyMemberEntity::getFamilyId, familyId).eq(FamilyMemberEntity::getUserId, memberId)) == 0) invalid();
        var existing = balanceMapper.selectOne(Wrappers.<CourseBalanceEntity>lambdaQuery().eq(CourseBalanceEntity::getFamilyId, familyId)
            .eq(CourseBalanceEntity::getMemberId, memberId).eq(CourseBalanceEntity::getTitle, title.trim()));
        var actor = TenantContext.requiredUser();
        if (existing != null && actor.role() != Role.SUPER_ADMIN && !actor.userId().equals(memberId) && !actor.userId().equals(existing.getCreatedBy())) {
            var courses = mapper.selectList(Wrappers.<CourseEntity>lambdaQuery().eq(CourseEntity::getFamilyId, familyId)
                .eq(CourseEntity::getMemberId, memberId).eq(CourseEntity::getTitle, title.trim()));
            if (courses.stream().noneMatch(this::canMaintain)) throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        var balance = new CourseBalanceEntity();
        balance.setFamilyId(familyId); balance.setMemberId(memberId); balance.setTitle(title.trim());
        balance.setRemainingLessons(remainingLessons); balance.setCreatedBy(TenantContext.requiredUser().userId());
        balance.setTotalLessons(totalLessons);
        balance.setUpdatedBy(balance.getCreatedBy()); balance.setCreatedAt(LocalDateTime.now()); balance.setUpdatedAt(balance.getCreatedAt());
        balanceMapper.upsert(balance);
    }

    @Transactional(rollbackFor = Exception.class)
    public void create(CoursePayload payload) {
        var course = new CourseEntity(); course.setFamilyId(family());
        course.setCreatedBy(TenantContext.requiredUser().userId()); course.setCreatedAt(LocalDateTime.now()); course.setDeleted(0);
        apply(course, payload); mapper.insert(course);
    }
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, CoursePayload payload) {
        var course = requireCourse(id, true); apply(course, payload); mapper.updateById(course);
    }
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) { mapper.deleteById(requireCourse(id, true)); }

    private boolean canMaintain(CourseEntity course) {
        var actor = TenantContext.requiredUser();
        return actor.role() == Role.SUPER_ADMIN || actor.userId().equals(course.getMemberId()) || actor.userId().equals(course.getCreatedBy());
    }
    /** 锁定课程可串行化同一课次的重复提交，家庭校验始终在权限判断前执行。 */
    private CourseEntity requireCourse(Long id, boolean allowCreator) {
        var familyId = family(); var course = mapper.selectForUpdate(id);
        if (course == null || !familyId.equals(course.getFamilyId())) throw new BusinessException(ErrorCode.NOT_FOUND);
        var actor = TenantContext.requiredUser();
        if (actor.role() != Role.SUPER_ADMIN && !actor.userId().equals(course.getMemberId())
            && !(allowCreator && actor.userId().equals(course.getCreatedBy()))) throw new BusinessException(ErrorCode.FORBIDDEN);
        return course;
    }
    private void apply(CourseEntity course, CoursePayload payload) {
        if (payload.title() == null || payload.title().isBlank() || payload.title().length() > 120 || payload.memberId() == null
            || payload.dayOfWeek() == null || payload.dayOfWeek() < 1 || payload.dayOfWeek() > 7
            || payload.startTime() == null || payload.endTime() == null || !payload.startTime().isBefore(payload.endTime())
            || payload.color() == null || !Set.of("blue", "purple", "green", "cyan", "orange").contains(payload.color())) invalid();
        var user = users.selectById(payload.memberId());
        if (user == null || !"ACTIVE".equals(user.getStatus()) || members.selectCount(Wrappers.<FamilyMemberEntity>lambdaQuery()
            .eq(FamilyMemberEntity::getFamilyId, course.getFamilyId()).eq(FamilyMemberEntity::getUserId, payload.memberId())) == 0) invalid();
        course.setMemberId(payload.memberId()); course.setMemberName(user.getDisplayName()); course.setTitle(payload.title().trim());
        // 沿用现有存储列，以固定周一为锚点保存星期；不再具有首次上课日期语义。
        course.setCourseDate(LocalDate.of(2000, 1, 3).plusDays(payload.dayOfWeek() - 1));
        course.setStartTime(payload.startTime()); course.setEndTime(payload.endTime());
        course.setRepeatWeekly(true); course.setColor(payload.color());
        course.setUpdatedBy(TenantContext.requiredUser().userId()); course.setUpdatedAt(LocalDateTime.now());
    }
    private boolean occurs(CourseEntity course, LocalDate date) {
        return date.getDayOfWeek() == course.getCourseDate().getDayOfWeek();
    }
    /** 在同一事务中处理出勤变化和余额快照，重复保存备注不改写原快照。 */
    @Transactional(rollbackFor = Exception.class)
    public void record(Long id, AttendancePayload payload) {
        var course = requireCourse(id, true);
        if (payload.date() == null || !occurs(course, payload.date()) || (payload.date().isAfter(LocalDate.now()) && !"LEAVE".equals(payload.status()))
            || payload.status() == null || !Set.of("ATTENDED", "LEAVE").contains(payload.status())
            || payload.note() == null || payload.note().length() > 500) invalid();
        // 课程转给其他成员后，原成员的历史记录仍不能由新成员改写。
        var existing = records.selectOne(Wrappers.<CourseRecordEntity>lambdaQuery()
            .eq(CourseRecordEntity::getFamilyId, course.getFamilyId()).eq(CourseRecordEntity::getCourseId, id)
            .eq(CourseRecordEntity::getCourseDate, payload.date()));
        var actor = TenantContext.requiredUser();
        if (existing != null && actor.role() != Role.SUPER_ADMIN && !actor.userId().equals(existing.getMemberId())
            && !actor.userId().equals(existing.getCreatedBy()))
            throw new BusinessException(ErrorCode.FORBIDDEN);
        var record = new CourseRecordEntity();
        record.setFamilyId(course.getFamilyId()); record.setCourseId(id); record.setMemberId(course.getMemberId());
        record.setMemberName(course.getMemberName()); record.setTitle(course.getTitle()); record.setCourseDate(payload.date());
        record.setStartTime(course.getStartTime()); record.setEndTime(course.getEndTime()); record.setStatus(payload.status()); record.setNote(payload.note());
        record.setAutomatic(false);
        record.setCreatedBy(TenantContext.requiredUser().userId()); record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedBy(record.getCreatedBy()); record.setUpdatedAt(record.getCreatedAt());
        int previous = existing != null && "ATTENDED".equals(existing.getStatus()) ? 1 : 0;
        int current = "ATTENDED".equals(payload.status()) ? 1 : 0;
        var balanceMember = existing == null ? course.getMemberId() : existing.getMemberId();
        var balanceTitle = existing == null ? course.getTitle() : existing.getTitle();
        if (previous != current) balanceMapper.adjust(course.getFamilyId(), balanceMember, balanceTitle, previous - current, actor.userId());
        if (existing == null || previous != current) {
            // 同科目不同课表共享余额；锁定余额行后读取最新值，避免并发或手工校准导致快照串值。
            var balance = balanceMapper.selectOne(Wrappers.<CourseBalanceEntity>lambdaQuery()
                .eq(CourseBalanceEntity::getFamilyId, course.getFamilyId()).eq(CourseBalanceEntity::getMemberId, balanceMember)
                .eq(CourseBalanceEntity::getTitle, balanceTitle).last("FOR UPDATE"));
            if (balance != null) {
                record.setRemainingLessons(balance.getRemainingLessons()); record.setTotalLessons(balance.getTotalLessons());
            }
        } else {
            // 包含无快照的旧记录：只修改备注时保留未知状态，不把今天的余额伪装成历史余额。
            record.setRemainingLessons(existing.getRemainingLessons()); record.setTotalLessons(existing.getTotalLessons());
        }
        records.upsert(record);
    }
}
