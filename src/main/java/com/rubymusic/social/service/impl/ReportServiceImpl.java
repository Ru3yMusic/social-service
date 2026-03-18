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
    public Page<Report> getReportsByStatus(ReportStatus status, Pageable pageable) {
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
}
