package com.rubymusic.social.service.impl;

import com.rubymusic.social.model.Report;
import com.rubymusic.social.model.enums.ReportStatus;
import com.rubymusic.social.model.enums.ReportTargetType;
import com.rubymusic.social.repository.ReportRepository;
import com.rubymusic.social.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportServiceImpl implements ReportService {

    private final ReportRepository reportRepository;

    @Override
    @Transactional
    public Report createReport(UUID reporterId, ReportTargetType targetType,
                               String targetId, String reason) {
        Report report = Report.builder()
                .reporterId(reporterId)
                .targetType(targetType)
                .targetId(targetId)
                .reason(reason)
                .build();
        return reportRepository.save(report);
    }

    @Override
    public Page<Report> getReportsByStatus(ReportStatus status, ReportTargetType targetType,
                                            Pageable pageable) {
        if (targetType != null) {
            return reportRepository.findAllByStatusAndTargetTypeOrderByCreatedAtDesc(
                    status, targetType, pageable);
        }
        return reportRepository.findAllByStatusOrderByCreatedAtDesc(status, pageable);
    }

    @Override
    @Transactional
    public Report updateStatus(UUID reportId, ReportStatus newStatus) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportId));
        report.setStatus(newStatus);
        return reportRepository.save(report);
    }

    @Override
    public List<Map<String, Object>> getGroupedReports(ReportStatus status, ReportTargetType targetType) {
        List<Object[]> rows = targetType != null
                ? reportRepository.findGroupedByStatusAndTargetType(status, targetType)
                : reportRepository.findGroupedByStatus(status);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("targetId", row[0].toString());
            entry.put("targetType", row[1].toString());
            entry.put("reportCount", row[2]);
            entry.put("latestReportAt", row[3]);
            result.add(entry);
        }
        return result;
    }

    @Override
    public List<Report> getReportsByTargetId(String targetId) {
        return reportRepository.findAllByTargetIdOrderByCreatedAtDesc(targetId);
    }

    @Override
    @Transactional
    public void dismissAllByTargetId(String targetId) {
        reportRepository.dismissAllPendingByTargetId(targetId);
    }

    @Override
    public Map<String, Object> getStats() {
        long total = reportRepository.count();

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (Object[] row : reportRepository.countByStatus()) {
            byStatus.put(row[0].toString(), (Long) row[1]);
        }

        Map<String, Long> byTargetType = new LinkedHashMap<>();
        for (Object[] row : reportRepository.countByTargetType()) {
            byTargetType.put(row[0].toString(), (Long) row[1]);
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("byStatus", byStatus);
        stats.put("byTargetType", byTargetType);
        return stats;
    }
}
