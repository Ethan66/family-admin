package com.ethan.familyadmin.transaction;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ethan.familyadmin.common.api.PageResponse;
import com.ethan.familyadmin.common.exception.BusinessException;
import com.ethan.familyadmin.common.exception.ErrorCode;
import com.ethan.familyadmin.family.UserMapper;
import com.ethan.familyadmin.security.CurrentUser;
import com.ethan.familyadmin.security.Role;
import com.ethan.familyadmin.security.TenantAccessGuard;
import com.ethan.familyadmin.security.TenantContext;
import com.ethan.familyadmin.transaction.dto.TransactionAggregate;
import com.ethan.familyadmin.transaction.dto.CreateTransactionRequest;
import com.ethan.familyadmin.transaction.dto.TransactionQuery;
import com.ethan.familyadmin.transaction.dto.TransactionResponse;
import com.ethan.familyadmin.transaction.dto.TransactionSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

@Service
public class TransactionService {

    private final TransactionMapper transactionMapper;
    private final TenantAccessGuard accessGuard;
    private final UserMapper userMapper;

    public TransactionService(TransactionMapper transactionMapper, TenantAccessGuard accessGuard, UserMapper userMapper) {
        this.transactionMapper = transactionMapper;
        this.accessGuard = accessGuard;
        this.userMapper = userMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public TransactionResponse create(CreateTransactionRequest request) {
        CurrentUser current = TenantContext.requiredUser();
        accessGuard.requireBusinessCreate(current);
        Long familyId = TenantContext.requiredFamilyId();
        TransactionEntity entity = new TransactionEntity();
        entity.setFamilyId(familyId);
        entity.setCreatedBy(current.userId());
        entity.setUpdatedBy(current.userId());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(entity.getCreatedAt());
        entity.setDeleted(0);
        apply(entity, request);
        transactionMapper.insert(entity);
        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> list(TransactionQuery query) {
        Page<TransactionEntity> page = transactionMapper.selectPage(
            Page.of(query.page(), query.pageSize()),
            buildQuery(query)
        );
        List<TransactionResponse> records = page.getRecords().stream().map(this::toResponse).toList();
        return new PageResponse<>(records, page.getTotal(), query.page(), query.pageSize());
    }

    @Transactional(readOnly = true)
    public TransactionSummaryResponse summary(TransactionQuery query) {
        YearMonth month = query.month() == null || query.month().isBlank() ? null : YearMonth.parse(query.month());
        LocalDate monthStart = month == null ? null : month.atDay(1);
        LocalDate monthEnd = month == null ? null : month.plusMonths(1).atDay(1);
        TransactionAggregate aggregate = transactionMapper.selectAggregate(
            TenantContext.requiredFamilyId(),
            query.type() == null ? null : query.type().name(),
            query.category() == null || query.category().isBlank() ? null : query.category(),
            query.memberName() == null || query.memberName().isBlank() ? null : query.memberName(),
            query.bankCard() == null || query.bankCard().isBlank() ? null : query.bankCard(),
            query.platform() == null || query.platform().isBlank() ? null : query.platform(),
            monthStart,
            monthEnd
        );
        BigDecimal income = aggregate.getIncome();
        BigDecimal expense = aggregate.getExpense();
        return new TransactionSummaryResponse(income, expense, income.subtract(expense),
            aggregate.getIncomeCount(), aggregate.getExpenseCount());
    }

    @Transactional(rollbackFor = Exception.class)
    public TransactionResponse update(Long id, CreateTransactionRequest request) {
        CurrentUser current = TenantContext.requiredUser();
        TransactionEntity entity = requireEntity(id);
        requireTransactionWrite(current, entity);
        apply(entity, request);
        entity.setUpdatedBy(current.userId());
        entity.setUpdatedAt(LocalDateTime.now());
        transactionMapper.updateById(entity);
        return toResponse(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        CurrentUser current = TenantContext.requiredUser();
        TransactionEntity entity = requireEntity(id);
        requireTransactionWrite(current, entity);
        entity.setUpdatedBy(current.userId());
        entity.setUpdatedAt(LocalDateTime.now());
        transactionMapper.deleteById(entity);
    }

    /** 账目归属成员也可管理；先校验家庭，避免同名成员获得跨家庭权限。 */
    private void requireTransactionWrite(CurrentUser current, TransactionEntity entity) {
        if (accessGuard.canWrite(current, entity)) return;
        if (current.role() == Role.FAMILY_MEMBER && Objects.equals(current.familyId(), entity.getFamilyId())
            && entity.getMemberName() != null && !entity.getMemberName().isBlank()) {
            var user = userMapper.selectById(current.userId());
            if (user != null && entity.getMemberName().equals(user.getDisplayName())) return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    private LambdaQueryWrapper<TransactionEntity> buildQuery(TransactionQuery query) {
        LambdaQueryWrapper<TransactionEntity> wrapper = new LambdaQueryWrapper<TransactionEntity>()
            .eq(TransactionEntity::getFamilyId, TenantContext.requiredFamilyId())
            .eq(TransactionEntity::getDeleted, 0)
            .eq(query.type() != null, TransactionEntity::getType, query.type() == null ? null : query.type().name())
            .eq(query.category() != null && !query.category().isBlank(), TransactionEntity::getCategory, query.category())
            .eq(query.memberName() != null && !query.memberName().isBlank(), TransactionEntity::getMemberName, query.memberName())
            .eq(query.bankCard() != null && !query.bankCard().isBlank(), TransactionEntity::getBankCard, query.bankCard())
            .eq(query.platform() != null && !query.platform().isBlank(), TransactionEntity::getPlatform, query.platform())
            .orderByDesc(TransactionEntity::getTransactionDate)
            .orderByDesc(TransactionEntity::getId);
        if (query.month() != null && !query.month().isBlank()) {
            YearMonth month = YearMonth.parse(query.month());
            wrapper.ge(TransactionEntity::getTransactionDate, month.atDay(1));
            wrapper.lt(TransactionEntity::getTransactionDate, month.plusMonths(1).atDay(1));
        }
        return wrapper;
    }

    private TransactionEntity requireEntity(Long id) {
        TransactionEntity entity = transactionMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return entity;
    }

    private void apply(TransactionEntity entity, CreateTransactionRequest request) {
        entity.setType(request.type().name());
        entity.setAmount(request.amount());
        entity.setTransactionDate(request.transactionDate());
        entity.setCategory(request.category());
        entity.setMemberName(request.memberName());
        entity.setNote(request.note());
        entity.setBankCard(request.bankCard() == null || request.bankCard().isEmpty() ? null : request.bankCard());
        entity.setPlatform(request.platform() == null || request.platform().isEmpty() ? null : request.platform());
    }

    private TransactionResponse toResponse(TransactionEntity entity) {
        return new TransactionResponse(
            entity.getId(),
            TransactionType.valueOf(entity.getType()),
            entity.getAmount(),
            entity.getTransactionDate(),
            entity.getCategory(),
            entity.getMemberName(),
            entity.getNote(),
            entity.getBankCard(),
            entity.getPlatform(),
            entity.getCreatedBy(),
            entity.getCreatedAt()
        );
    }
}
