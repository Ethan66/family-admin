package com.ethan.familyadmin.passbook;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ethan.familyadmin.common.exception.BusinessException;
import com.ethan.familyadmin.common.exception.ErrorCode;
import com.ethan.familyadmin.family.*;
import com.ethan.familyadmin.security.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class PassbookService {
    private final PassbookMapper mapper;
    private final UserMapper users;
    private final FamilyMemberMapper members;
    private final TenantAccessGuard guard;

    public PassbookService(PassbookMapper mapper, UserMapper users, FamilyMemberMapper members, TenantAccessGuard guard) {
        this.mapper = mapper;
        this.users = users;
        this.members = members;
        this.guard = guard;
    }

    @Transactional(readOnly = true)
    public PassbookPageResponse list(Long memberId, LocalDate startDate, LocalDate endDate, long page, long pageSize) {
        return list(memberId, startDate, endDate, page, pageSize, PassbookSort.DEPOSIT_DATE_DESC);
    }

    @Transactional(readOnly = true)
    public PassbookPageResponse list(Long memberId, LocalDate startDate, LocalDate endDate, long page, long pageSize, PassbookSort sort) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "开始日期不能晚于结束日期");
        }
        long current = Math.max(1, page);
        long size = Math.min(100, Math.max(1, pageSize));
        var query = buildQuery(memberId, startDate, endDate);
        // 到期日只由存款日和年限推导；静态 SQL 白名单确保全量排序发生在分页之前。
        switch (sort) {
            case DEPOSIT_DATE_DESC -> query.orderByDesc(PassbookEntity::getDepositDate).orderByDesc(PassbookEntity::getId);
            case DEPOSIT_DATE_ASC -> query.orderByAsc(PassbookEntity::getDepositDate).orderByDesc(PassbookEntity::getId);
            case MATURITY_DATE_ASC -> query.last("ORDER BY DATE_ADD(deposit_date, INTERVAL term_years YEAR) ASC, id DESC");
            case MATURITY_DATE_DESC -> query.last("ORDER BY DATE_ADD(deposit_date, INTERVAL term_years YEAR) DESC, id DESC");
        }
        var result = mapper.selectPage(Page.of(current, size), query);
        var totals = mapper.selectTotals(buildQuery(memberId, startDate, endDate));
        return new PassbookPageResponse(result.getRecords().stream().map(this::response).toList(), result.getTotal(), current, size,
            totals.totalPrincipal(), totals.totalAmount());
    }

    /** 分页与全量合计共用筛选条件；自定义统计 SQL 必须显式排除逻辑删除记录。 */
    private LambdaQueryWrapper<PassbookEntity> buildQuery(Long memberId, LocalDate startDate, LocalDate endDate) {
        return Wrappers.<PassbookEntity>lambdaQuery()
            .eq(PassbookEntity::getFamilyId, family())
            .eq(PassbookEntity::getDeleted, 0)
            .eq(memberId != null, PassbookEntity::getMemberId, memberId)
            .ge(startDate != null, PassbookEntity::getDepositDate, startDate)
            .le(endDate != null, PassbookEntity::getDepositDate, endDate);
    }

    @Transactional(rollbackFor = Exception.class)
    public PassbookResponse create(PassbookPayload payload) {
        var actor = TenantContext.requiredUser();
        guard.requireBusinessCreate(actor);
        var entity = new PassbookEntity();
        entity.setFamilyId(family());
        entity.setCreatedBy(actor.userId());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setDeleted(0);
        apply(entity, payload);
        mapper.insert(entity);
        return response(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public PassbookResponse update(Long id, PassbookPayload payload) {
        var entity = requireEntity(id);
        guard.requireWrite(TenantContext.requiredUser(), entity);
        apply(entity, payload);
        mapper.updateById(entity);
        return response(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        var entity = requireEntity(id);
        guard.requireWrite(TenantContext.requiredUser(), entity);
        // 逻辑删除也会写审计字段，必须传入完整实体而非只有 ID 的空对象。
        entity.setUpdatedBy(TenantContext.requiredUser().userId());
        entity.setUpdatedAt(LocalDateTime.now());
        mapper.deleteById(entity);
    }

    private Long family() {
        guard.requireBusinessCreate(TenantContext.requiredUser());
        return TenantContext.requiredFamilyId();
    }

    private PassbookEntity requireEntity(Long id) {
        var entity = mapper.selectById(id);
        if (entity == null || !Objects.equals(entity.getFamilyId(), family())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return entity;
    }

    private void apply(PassbookEntity entity, PassbookPayload payload) {
        // 到期日期遵循日历年规则：闰年 2 月 29 日到非闰年时为 2 月 28 日。
        if (payload.depositDate().getYear() < 1000 || payload.depositDate().getYear() + payload.termYears() > 9999) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "存款日期或到期日期超出有效范围");
        }
        var user = users.selectById(payload.memberId());
        if (user == null || !"ACTIVE".equals(user.getStatus()) || members.selectCount(Wrappers.<FamilyMemberEntity>lambdaQuery()
            .eq(FamilyMemberEntity::getFamilyId, entity.getFamilyId()).eq(FamilyMemberEntity::getUserId, payload.memberId())) == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择当前家庭的有效成员");
        }
        entity.setMemberId(payload.memberId());
        entity.setMemberName(user.getDisplayName());
        entity.setDepositDate(payload.depositDate());
        entity.setAmount(payload.amount());
        entity.setTermYears(payload.termYears());
        entity.setInterestRate(payload.interestRate());
        entity.setTotalInterest(payload.totalInterest());
        entity.setNote(payload.note() == null || payload.note().isEmpty() ? null : payload.note());
        entity.setBank(payload.bank() == null || payload.bank().isEmpty() ? null : payload.bank());
        entity.setUpdatedBy(TenantContext.requiredUser().userId());
        entity.setUpdatedAt(LocalDateTime.now());
    }

    private PassbookResponse response(PassbookEntity entity) {
        return new PassbookResponse(entity.getId(), entity.getMemberId(), entity.getMemberName(), entity.getDepositDate(),
            entity.getAmount(), entity.getTermYears(), entity.getInterestRate(), entity.getDepositDate().plusYears(entity.getTermYears()),
            entity.getTotalInterest(), entity.getAmount().add(entity.getTotalInterest()), entity.getCreatedBy(), entity.getBank(), entity.getNote());
    }
}
