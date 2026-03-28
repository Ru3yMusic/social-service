package com.rubymusic.social.service;

import com.rubymusic.social.model.Report;
import com.rubymusic.social.model.enums.ReportStatus;
import com.rubymusic.social.model.enums.ReportTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ReportService {

    Report createReport(UUID reporterId, ReportTargetType targetType, String targetId, String reason);

    Page<Report> getReportsByStatus(ReportStatus status, ReportTargetType targetType, Pageable pageable);

    Report updateStatus(UUID reportId, ReportStatus newStatus);

    /** Returns aggregated rows grouped by (targetId, targetType) — drives the admin panel card list */
    List<Map<String, Object>> getGroupedReports(ReportStatus status, ReportTargetType targetType);

    /** All individual reports for a target, newest first — drives the admin history modal */
    List<Report> getReportsByTargetId(String targetId);

    /** Dismisses all PENDING reports for a target (admin Descartar action) */
    void dismissAllByTargetId(String targetId);

    /**
     * Aggregate counts for the admin dashboard "Reportes de Gravedad" donut chart.
     * Returns: { total, byStatus: { PENDING, REVIEWED, DISMISSED }, byTargetType: { COMMENT, SONG, USER } }
     */
    Map<String, Object> getStats();
}
