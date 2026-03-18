package com.rubymusic.social.service;

import com.rubymusic.social.model.Report;
import com.rubymusic.social.model.enums.ReportStatus;
import com.rubymusic.social.model.enums.ReportTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ReportService {

    Report createReport(UUID reporterId, ReportTargetType targetType, String targetId, String reason);

    Page<Report> getReportsByStatus(ReportStatus status, Pageable pageable);

    Report updateStatus(UUID reportId, ReportStatus newStatus);
}
