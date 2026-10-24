package com.ethan.familyadmin.course;

import com.ethan.familyadmin.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import java.time.LocalDate;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CourseControllerTest {
    final CourseService service = mock(CourseService.class);
    final MockMvc mvc = MockMvcBuilders.standaloneSetup(new CourseController(service))
        .setControllerAdvice(new GlobalExceptionHandler()).build();
    @Test void listOnlyNeedsMemberFilterWithoutCalendarDates() throws Exception {
        when(service.list(7L)).thenReturn(List.of());
        mvc.perform(get("/api/courses").param("memberId", "7"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray());
        verify(service).list(7L);
    }
    @Test void invalidMemberReturnsBadRequestInsteadOfServerError() throws Exception {
        mvc.perform(get("/api/courses").param("memberId", "bad"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void invalidAttendanceStateDoesNotReachService() throws Exception {
        mvc.perform(put("/api/courses/3/attendance").contentType(MediaType.APPLICATION_JSON)
            .content("{\"date\":\"2026-10-24\",\"status\":\"PENDING\",\"note\":\"\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void rejectsMissingRequiredCourseFields() throws Exception {
        mvc.perform(post("/api/courses").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void historyPassesAllFiltersToTheService() throws Exception {
        when(service.history(7L, 2, "英语", "ATTENDED", "2026-10"))
            .thenReturn(new CourseService.RecordPage(List.of(), 0));
        mvc.perform(get("/api/courses/records").param("memberId", "7").param("page", "2")
            .param("title", "英语").param("status", "ATTENDED").param("month", "2026-10"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        verify(service).history(7L, 2, "英语", "ATTENDED", "2026-10");
    }
}
