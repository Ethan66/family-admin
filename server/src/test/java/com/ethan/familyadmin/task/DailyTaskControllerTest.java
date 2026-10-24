package com.ethan.familyadmin.task;

import com.ethan.familyadmin.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DailyTaskControllerTest {
    @Test void malformedAndMissingDatesAreValidationErrorsInsteadOfServerFailures() throws Exception {
        var service = mock(DailyTaskService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new DailyTaskController(service))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/daily-tasks").param("from", "not-a-date").param("to", "2026-10-31"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/daily-tasks").param("to", "2026-10-31"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
