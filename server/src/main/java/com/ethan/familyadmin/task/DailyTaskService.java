package com.ethan.familyadmin.task;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ethan.familyadmin.common.exception.BusinessException;
import com.ethan.familyadmin.common.exception.ErrorCode;
import com.ethan.familyadmin.family.*;
import com.ethan.familyadmin.security.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class DailyTaskService {
    private final DailyTaskMapper mapper;
    private final TaskCompletionMapper completions;
    private final UserMapper users;
    private final FamilyMemberMapper members;
    public DailyTaskService(DailyTaskMapper mapper, TaskCompletionMapper completions, UserMapper users, FamilyMemberMapper members) {
        this.mapper = mapper; this.completions = completions; this.users = users; this.members = members;
    }

    @Transactional(readOnly = true)
    public List<TaskOccurrence> list(LocalDate from, LocalDate to, Long memberId) {
        Long family = family();
        if (from == null || to == null || from.isAfter(to) || ChronoUnit.DAYS.between(from, to) > 41) invalid();
        var tasks = mapper.selectList(Wrappers.<DailyTaskEntity>lambdaQuery()
            .eq(DailyTaskEntity::getFamilyId, family)
            .eq(memberId != null, DailyTaskEntity::getMemberId, memberId)
            .le(DailyTaskEntity::getTaskDate, to)
            .and(w -> w.eq(DailyTaskEntity::getRepeatDaily, true).or().ge(DailyTaskEntity::getTaskDate, from)));
        var done = new HashSet<>(completions.list(family, from, to));
        List<TaskOccurrence> result = new ArrayList<>();
        for (var task : tasks) {
            LocalDate start = task.getTaskDate().isAfter(from) ? task.getTaskDate() : from;
            for (LocalDate date = start; !date.isAfter(to); date = date.plusDays(1)) {
                if (!Boolean.TRUE.equals(task.getRepeatDaily()) && !date.equals(task.getTaskDate())) break;
                result.add(new TaskOccurrence(task.getId(), task.getTitle(), task.getMemberId(), task.getMemberName(),
                    date, task.getTaskDate(), task.getTaskTime(), task.getCategory(), Boolean.TRUE.equals(task.getRepeatDaily()),
                    done.contains(new TaskCompletionMapper.Completion(task.getId(), date)), task.getNote()));
            }
        }
        result.sort(Comparator.comparing(TaskOccurrence::taskDate)
            .thenComparing(TaskOccurrence::taskTime, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(TaskOccurrence::id));
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void create(TaskPayload request) {
        var task = new DailyTaskEntity();
        task.setFamilyId(family()); task.setCreatedBy(TenantContext.requiredUser().userId());
        task.setCreatedAt(LocalDateTime.now()); task.setDeleted(0);
        apply(task, request); mapper.insert(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, TaskPayload request) {
        var task = requireTask(id);
        boolean reset = !Objects.equals(task.getMemberId(), request.memberId()) || !task.getTaskDate().equals(request.taskDate())
            || !Objects.equals(task.getRepeatDaily(), request.repeatDaily());
        apply(task, request); mapper.updateById(task);
        if (reset) completions.clear(task.getFamilyId(), id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        var task = requireTask(id);
        task.setUpdatedBy(TenantContext.requiredUser().userId());
        task.setUpdatedAt(LocalDateTime.now());
        mapper.deleteById(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public void complete(Long id, LocalDate date, boolean completed) {
        var task = requireTask(id);
        if (date == null || date.isBefore(task.getTaskDate()) || (!Boolean.TRUE.equals(task.getRepeatDaily()) && !date.equals(task.getTaskDate()))) invalid();
        if (completed) completions.complete(task.getFamilyId(), id, date, TenantContext.requiredUser().userId());
        else completions.undo(task.getFamilyId(), id, date);
    }

    private Long family() {
        Long family = TenantContext.requiredUser().familyId();
        if (family == null) throw new BusinessException(ErrorCode.FORBIDDEN);
        return family;
    }

    /** 修改、删除和打卡仍校验家庭与任务成员，查看全家任务不扩大写权限。 */
    private DailyTaskEntity requireTask(Long id) {
        Long family = family();
        var task = mapper.selectById(id);
        if (task == null || !Objects.equals(task.getFamilyId(), family)) throw new BusinessException(ErrorCode.NOT_FOUND);
        var actor = TenantContext.requiredUser();
        if (actor.role() != Role.SUPER_ADMIN && !Objects.equals(task.getMemberId(), actor.userId())) throw new BusinessException(ErrorCode.FORBIDDEN);
        return task;
    }

    /** 成员身份来自真实家庭关系，姓名作为任务展示快照保存。 */
    private void apply(DailyTaskEntity task, TaskPayload request) {
        var actor = TenantContext.requiredUser();
        var user = users.selectById(request.memberId());
        if (user == null || !"ACTIVE".equals(user.getStatus())) invalid();
        if (members.selectCount(Wrappers.<FamilyMemberEntity>lambdaQuery()
            .eq(FamilyMemberEntity::getFamilyId, task.getFamilyId()).eq(FamilyMemberEntity::getUserId, request.memberId())) == 0) invalid();
        task.setTitle(request.title().trim()); task.setMemberId(request.memberId()); task.setMemberName(user.getDisplayName());
        task.setTaskDate(request.taskDate()); task.setTaskTime(request.taskTime()); task.setCategory(request.category().trim());
        task.setRepeatDaily(request.repeatDaily()); task.setNote(request.note());
        task.setUpdatedBy(actor.userId()); task.setUpdatedAt(LocalDateTime.now());
    }

    private void invalid() { throw new BusinessException(ErrorCode.VALIDATION_ERROR); }
}
