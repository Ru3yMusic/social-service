package com.rubymusic.social.service.impl;

import com.rubymusic.social.model.Report;
import com.rubymusic.social.model.enums.ReportStatus;
import com.rubymusic.social.model.enums.ReportTargetType;
import com.rubymusic.social.repository.ReportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Mock private ReportRepository reportRepository;

    @InjectMocks
    private ReportServiceImpl service;

    // ── createReport ──────────────────────────────────────────────────────────

    @Test
    void createReport_persistsReportWithDefaults() {
        UUID reporterId = UUID.randomUUID();
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

        Report result = service.createReport(reporterId, ReportTargetType.SONG,
                "song-123", "spam");

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(captor.capture());
        Report saved = captor.getValue();
        assertThat(saved.getReporterId()).isEqualTo(reporterId);
        assertThat(saved.getTargetType()).isEqualTo(ReportTargetType.SONG);
        assertThat(saved.getTargetId()).isEqualTo("song-123");
        assertThat(saved.getReason()).isEqualTo("spam");
        assertThat(result).isSameAs(saved);
    }

    // ── getReportsByStatus ────────────────────────────────────────────────────

    @Test
    void getReportsByStatus_withTargetType_usesFilteredQuery() {
        Page<Report> expected = new PageImpl<>(List.of(mockReport()));
        PageRequest pr = PageRequest.of(0, 20);
        when(reportRepository.findAllByStatusAndTargetTypeOrderByCreatedAtDesc(
                ReportStatus.PENDING, ReportTargetType.SONG, pr)).thenReturn(expected);

        Page<Report> result = service.getReportsByStatus(
                ReportStatus.PENDING, ReportTargetType.SONG, pr);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void getReportsByStatus_nullTargetType_usesStatusOnlyQuery() {
        Page<Report> expected = new PageImpl<>(List.of(mockReport()));
        PageRequest pr = PageRequest.of(0, 20);
        when(reportRepository.findAllByStatusOrderByCreatedAtDesc(
                ReportStatus.REVIEWED, pr)).thenReturn(expected);

        Page<Report> result = service.getReportsByStatus(ReportStatus.REVIEWED, null, pr);

        assertThat(result).isSameAs(expected);
    }

    // ── updateStatus ──────────────────────────────────────────────────────────

    @Test
    void updateStatus_existing_savesNewStatus() {
        UUID id = UUID.randomUUID();
        Report existing = Report.builder().id(id).status(ReportStatus.PENDING).build();
        when(reportRepository.findById(id)).thenReturn(Optional.of(existing));
        when(reportRepository.save(existing)).thenReturn(existing);

        Report result = service.updateStatus(id, ReportStatus.REVIEWED);

        assertThat(result.getStatus()).isEqualTo(ReportStatus.REVIEWED);
    }

    @Test
    void updateStatus_notFound_throwsIllegalArgument() {
        UUID id = UUID.randomUUID();
        when(reportRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus(id, ReportStatus.REVIEWED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Report not found");
    }

    // ── getGroupedReports ─────────────────────────────────────────────────────

    @Test
    void getGroupedReports_withTargetType_usesFilteredQuery() {
        LocalDateTime when = LocalDateTime.of(2026, 5, 1, 10, 0);
        Object[] row = new Object[]{"song-1", ReportTargetType.SONG, 5L, when};
        when(reportRepository.findGroupedByStatusAndTargetType(
                ReportStatus.PENDING, ReportTargetType.SONG))
                .thenReturn(List.<Object[]>of(row));

        List<Map<String, Object>> result = service.getGroupedReports(
                ReportStatus.PENDING, ReportTargetType.SONG);

        assertThat(result).hasSize(1);
        Map<String, Object> entry = result.get(0);
        assertThat(entry.get("targetId")).isEqualTo("song-1");
        assertThat(entry.get("targetType")).isEqualTo("SONG");
        assertThat(entry.get("reportCount")).isEqualTo(5L);
        assertThat(entry.get("latestReportAt")).isEqualTo(when);
    }

    @Test
    void getGroupedReports_nullTargetType_usesStatusOnlyQuery() {
        Object[] row = new Object[]{"user-1", ReportTargetType.USER, 2L, LocalDateTime.now()};
        when(reportRepository.findGroupedByStatus(ReportStatus.PENDING))
                .thenReturn(List.<Object[]>of(row));

        List<Map<String, Object>> result = service.getGroupedReports(ReportStatus.PENDING, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("targetId")).isEqualTo("user-1");
    }

    @Test
    void getGroupedReports_empty_returnsEmptyList() {
        when(reportRepository.findGroupedByStatus(ReportStatus.DISMISSED))
                .thenReturn(List.of());

        assertThat(service.getGroupedReports(ReportStatus.DISMISSED, null)).isEmpty();
    }

    // ── getReportsByTargetId ──────────────────────────────────────────────────

    @Test
    void getReportsByTargetId_returnsList() {
        Report r1 = mockReport();
        when(reportRepository.findAllByTargetIdOrderByCreatedAtDesc("song-1"))
                .thenReturn(List.of(r1));

        List<Report> result = service.getReportsByTargetId("song-1");

        assertThat(result).containsExactly(r1);
    }

    // ── dismissAllByTargetId ──────────────────────────────────────────────────

    @Test
    void dismissAllByTargetId_delegatesToRepository() {
        service.dismissAllByTargetId("song-1");

        verify(reportRepository).dismissAllPendingByTargetId("song-1");
    }

    // ── getStats ──────────────────────────────────────────────────────────────

    @Test
    void getStats_buildsAggregatedMap() {
        when(reportRepository.count()).thenReturn(100L);
        when(reportRepository.countByStatus())
                .thenReturn(List.of(
                        new Object[]{ReportStatus.PENDING, 60L},
                        new Object[]{ReportStatus.REVIEWED, 40L}));
        when(reportRepository.countByTargetType())
                .thenReturn(List.of(
                        new Object[]{ReportTargetType.SONG, 70L},
                        new Object[]{ReportTargetType.USER, 30L}));

        Map<String, Object> result = service.getStats();

        assertThat(result.get("total")).isEqualTo(100L);
        @SuppressWarnings("unchecked")
        Map<String, Long> byStatus = (Map<String, Long>) result.get("byStatus");
        assertThat(byStatus).containsEntry("PENDING", 60L).containsEntry("REVIEWED", 40L);
        @SuppressWarnings("unchecked")
        Map<String, Long> byTargetType = (Map<String, Long>) result.get("byTargetType");
        assertThat(byTargetType).containsEntry("SONG", 70L).containsEntry("USER", 30L);
    }

    @Test
    void getStats_emptyRepository_returnsZeros() {
        when(reportRepository.count()).thenReturn(0L);
        when(reportRepository.countByStatus()).thenReturn(List.of());
        when(reportRepository.countByTargetType()).thenReturn(List.of());

        Map<String, Object> result = service.getStats();

        assertThat(result.get("total")).isEqualTo(0L);
        @SuppressWarnings("unchecked")
        Map<String, Long> byStatus = (Map<String, Long>) result.get("byStatus");
        assertThat(byStatus).isEmpty();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Report mockReport() {
        return Report.builder()
                .id(UUID.randomUUID())
                .reporterId(UUID.randomUUID())
                .targetType(ReportTargetType.SONG)
                .targetId("t-" + UUID.randomUUID())
                .status(ReportStatus.PENDING)
                .build();
    }
}
