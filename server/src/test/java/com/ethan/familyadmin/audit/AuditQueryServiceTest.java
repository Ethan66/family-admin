package com.ethan.familyadmin.audit;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuditQueryServiceTest {
    @Test
    void returnsPagedOperationLogs() {
        OperationLogMapper mapper = mock(OperationLogMapper.class);
        OperationLogEntity log = new OperationLogEntity(); log.setId(1L); log.setAction("CREATE_FAMILY");
        Page<OperationLogEntity> page = Page.of(1, 20); page.setRecords(java.util.List.of(log)); page.setTotal(1);
        when(mapper.selectPage(any(), any())).thenReturn(page);
        var result = new AuditQueryService(mapper).list(8L, 1, 20);
        assertThat(result.records()).hasSize(1);
        assertThat(result.records().getFirst().action()).isEqualTo("CREATE_FAMILY");
    }
}
