package com.rubymusic.social.controller;

import com.rubymusic.social.dto.ReportResponse;
import com.rubymusic.social.exception.GlobalExceptionHandler;
import com.rubymusic.social.mapper.ReportMapper;
import com.rubymusic.social.model.Report;
import com.rubymusic.social.model.enums.ReportStatus;
import com.rubymusic.social.model.enums.ReportTargetType;
import com.rubymusic.social.service.ReportService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReportsControllerTest {

    @Mock private ReportService reportService;
    @Mock private ReportMapper reportMapper;
    @Mock private HttpServletRequest httpRequest;

    @InjectMocks
    private ReportsController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ── createReport ──────────────────────────────────────────────────────────

    @Test
    void createReport_returns201() throws Exception {
        UUID reporterId = UUID.randomUUID();
        Report created = mock(Report.class);

        when(httpRequest.getHeader("X-User-Id")).thenReturn(reporterId.toString());
        when(reportService.createReport(reporterId, ReportTargetType.SONG,
                "song-123", "inappropriate")).thenReturn(created);
        when(reportMapper.toDto(created)).thenReturn(new ReportResponse());

        mockMvc.perform(post("/api/v1/social/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"SONG\",\"targetId\":\"song-123\"," +
                                "\"reason\":\"inappropriate\"}"))
                .andExpect(status().isCreated());

        verify(reportService).createReport(reporterId, ReportTargetType.SONG,
                "song-123", "inappropriate");
    }

    // ── listReports ───────────────────────────────────────────────────────────

    @Test
    void listReports_withTargetType_returns200() throws Exception {
        Page<Report> page = new PageImpl<>(List.of(mock(Report.class)), PageRequest.of(0, 20), 1);
        when(reportService.getReportsByStatus(eq(ReportStatus.PENDING),
                eq(ReportTargetType.SONG), any(PageRequest.class))).thenReturn(page);
        when(reportMapper.toDtoList(anyList())).thenReturn(List.of(new ReportResponse()));

        mockMvc.perform(get("/api/v1/social/reports")
                        .param("status", "PENDING")
                        .param("targetType", "SONG")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void listReports_withoutTargetType_returns200() throws Exception {
        Page<Report> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(reportService.getReportsByStatus(eq(ReportStatus.REVIEWED), any(),
                any(PageRequest.class))).thenReturn(page);
        when(reportMapper.toDtoList(anyList())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/social/reports")
                        .param("status", "REVIEWED")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // ── updateReportStatus ────────────────────────────────────────────────────

    @Test
    void updateReportStatus_returns200() throws Exception {
        UUID reportId = UUID.randomUUID();
        Report updated = mock(Report.class);
        when(reportService.updateStatus(reportId, ReportStatus.REVIEWED)).thenReturn(updated);
        when(reportMapper.toDto(updated)).thenReturn(new ReportResponse());

        mockMvc.perform(patch("/api/v1/social/reports/{reportId}/status", reportId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REVIEWED\"}"))
                .andExpect(status().isOk());

        verify(reportService).updateStatus(reportId, ReportStatus.REVIEWED);
    }

    @Test
    void updateReportStatus_notFound_returns400() throws Exception {
        UUID reportId = UUID.randomUUID();
        when(reportService.updateStatus(eq(reportId), any()))
                .thenThrow(new IllegalArgumentException("Report not found"));

        mockMvc.perform(patch("/api/v1/social/reports/{reportId}/status", reportId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REVIEWED\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── getReportStats ────────────────────────────────────────────────────────

    @Test
    void getReportStats_returns200_withCounts() throws Exception {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", 42L);
        stats.put("byStatus", Map.of("PENDING", 20L, "REVIEWED", 22L));
        stats.put("byTargetType", Map.of("SONG", 30L, "USER", 12L));
        when(reportService.getStats()).thenReturn(stats);

        mockMvc.perform(get("/api/v1/social/reports/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(42))
                .andExpect(jsonPath("$.byStatus.PENDING").value(20))
                .andExpect(jsonPath("$.byTargetType.SONG").value(30));
    }

    // ── listGroupedReports ────────────────────────────────────────────────────

    @Test
    void listGroupedReports_withStatusAndType_returns200() throws Exception {
        LocalDateTime when = LocalDateTime.of(2026, 5, 1, 12, 0);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("targetId", "song-1");
        entry.put("targetType", "SONG");
        entry.put("reportCount", 3L);
        entry.put("latestReportAt", when);

        when(reportService.getGroupedReports(ReportStatus.PENDING,
                ReportTargetType.SONG)).thenReturn(List.of(entry));

        mockMvc.perform(get("/api/v1/social/reports/grouped")
                        .param("status", "PENDING")
                        .param("targetType", "SONG"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].targetId").value("song-1"))
                .andExpect(jsonPath("$[0].targetType").value("SONG"))
                .andExpect(jsonPath("$[0].reportCount").value(3));
    }

    @Test
    void listGroupedReports_nullStatus_defaultsToPending() throws Exception {
        when(reportService.getGroupedReports(eq(ReportStatus.PENDING), any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/social/reports/grouped"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(reportService).getGroupedReports(ReportStatus.PENDING, null);
    }

    // ── getReportsByTarget ────────────────────────────────────────────────────

    @Test
    void getReportsByTarget_returns200() throws Exception {
        when(reportService.getReportsByTargetId("song-1"))
                .thenReturn(List.of(mock(Report.class), mock(Report.class)));
        when(reportMapper.toDtoList(anyList()))
                .thenReturn(List.of(new ReportResponse(), new ReportResponse()));

        mockMvc.perform(get("/api/v1/social/reports/target/{targetId}", "song-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getReportsByTarget_notFound_returns404() throws Exception {
        when(reportService.getReportsByTargetId("missing"))
                .thenThrow(new NoSuchElementException("No reports for target"));

        mockMvc.perform(get("/api/v1/social/reports/target/{targetId}", "missing"))
                .andExpect(status().isNotFound());
    }

    // ── dismissReportsByTarget ────────────────────────────────────────────────

    @Test
    void dismissReportsByTarget_returns204() throws Exception {
        mockMvc.perform(patch("/api/v1/social/reports/target/{targetId}/dismiss", "song-1"))
                .andExpect(status().isNoContent());

        verify(reportService).dismissAllByTargetId("song-1");
    }
}
