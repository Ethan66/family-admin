package com.ethan.familyadmin.movie;

import com.ethan.familyadmin.movie.dto.CreateMovieRequest;
import com.ethan.familyadmin.security.CurrentUser;
import com.ethan.familyadmin.security.Role;
import com.ethan.familyadmin.security.TenantAccessGuard;
import com.ethan.familyadmin.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MovieServiceTest {
    @AfterEach void clear() { TenantContext.clear(); }

    @Test
    void createNormalizesWatchProgress() {
        MovieMapper mapper = mock(MovieMapper.class);
        TenantContext.set(new CurrentUser(1L, 3L, Role.FAMILY_MEMBER, 0));
        MovieService service = new MovieService(mapper, new TenantAccessGuard());

        var result = service.create(new CreateMovieRequest("寻梦环游记", "动画", 2017, "李·昂克里奇",
            105, new BigDecimal("9.1"), WatchStatus.WATCHING, 30, "家庭电影", null));

        verify(mapper).insert(any(MovieEntity.class));
        assertThat(result.progress()).isEqualTo(30);
        assertThat(result.watchStatus()).isEqualTo(WatchStatus.WATCHING);
    }
}
