package com.ethan.familyadmin.movie;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ethan.familyadmin.common.api.PageResponse;
import com.ethan.familyadmin.common.exception.BusinessException;
import com.ethan.familyadmin.common.exception.ErrorCode;
import com.ethan.familyadmin.movie.dto.CreateMovieRequest;
import com.ethan.familyadmin.movie.dto.MovieResponse;
import com.ethan.familyadmin.security.CurrentUser;
import com.ethan.familyadmin.security.TenantAccessGuard;
import com.ethan.familyadmin.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
public class MovieService {
    private final MovieMapper mapper;
    private final TenantAccessGuard guard;
    public MovieService(MovieMapper mapper, TenantAccessGuard guard) { this.mapper = mapper; this.guard = guard; }

    @Transactional(rollbackFor = Exception.class)
    public MovieResponse create(CreateMovieRequest request) {
        CurrentUser user = TenantContext.requiredUser();
        guard.requireBusinessCreate(user);
        MovieEntity entity = new MovieEntity();
        entity.setFamilyId(TenantContext.requiredFamilyId()); entity.setCreatedBy(user.userId()); entity.setUpdatedBy(user.userId());
        entity.setCreatedAt(LocalDateTime.now()); entity.setUpdatedAt(entity.getCreatedAt()); entity.setDeleted(0);
        apply(entity, request); mapper.insert(entity); return response(entity);
    }

    @Transactional(readOnly = true)
    public PageResponse<MovieResponse> list(String type, WatchStatus status, String keyword, long page, long pageSize) {
        var wrapper = new LambdaQueryWrapper<MovieEntity>().eq(MovieEntity::getFamilyId, TenantContext.requiredFamilyId())
            .eq(type != null && !type.isBlank(), MovieEntity::getType, type)
            .eq(status != null, MovieEntity::getWatchStatus, status == null ? null : status.name())
            .like(keyword != null && !keyword.isBlank(), MovieEntity::getTitle, keyword).orderByDesc(MovieEntity::getId);
        var result = mapper.selectPage(Page.of(page, pageSize), wrapper);
        return new PageResponse<>(result.getRecords().stream().map(this::response).toList(), result.getTotal(), page, pageSize);
    }

    @Transactional(rollbackFor = Exception.class)
    public MovieResponse update(Long id, CreateMovieRequest request) {
        MovieEntity entity = required(id); CurrentUser user = TenantContext.requiredUser(); guard.requireWrite(user, entity);
        apply(entity, request); entity.setUpdatedBy(user.userId()); entity.setUpdatedAt(LocalDateTime.now()); mapper.updateById(entity); return response(entity);
    }
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) { MovieEntity entity = required(id); guard.requireWrite(TenantContext.requiredUser(), entity); mapper.deleteById(id); }
    private MovieEntity required(Long id) { MovieEntity e = mapper.selectById(id); if (e == null) throw new BusinessException(ErrorCode.NOT_FOUND); guard.requireRead(TenantContext.requiredUser(), e); return e; }
    private void apply(MovieEntity e, CreateMovieRequest r) { e.setTitle(r.title()); e.setType(r.type()); e.setReleaseYear(r.releaseYear()); e.setDirector(r.director()); e.setMinutes(r.minutes()); e.setRating(r.rating()); e.setWatchStatus(r.watchStatus().name()); e.setProgress(r.watchStatus() == WatchStatus.FINISHED ? 100 : r.progress()); e.setDescription(r.description()); e.setCoverFileId(r.coverFileId()); }
    private MovieResponse response(MovieEntity e) { return new MovieResponse(e.getId(), e.getTitle(), e.getType(), e.getReleaseYear(), e.getDirector(), e.getMinutes(), e.getRating(), WatchStatus.valueOf(e.getWatchStatus()), e.getProgress(), e.getDescription(), e.getCoverFileId(), e.getCreatedBy(), e.getCreatedAt()); }
}
