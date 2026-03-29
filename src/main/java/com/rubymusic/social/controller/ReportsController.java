package com.rubymusic.social.controller;

import com.rubymusic.social.dto.CreateReportRequest;
import com.rubymusic.social.dto.ReportPage;
import com.rubymusic.social.dto.ReportResponse;
import com.rubymusic.social.dto.ReportStatsResponse;
import com.rubymusic.social.dto.ReportTargetSummary;
import com.rubymusic.social.dto.UpdateReportStatusRequest;
import com.rubymusic.social.model.enums.ReportStatus;
import com.rubymusic.social.model.enums.ReportTargetType;
import com.rubymusic.social.mapper.ReportMapper;
import com.rubymusic.social.service.ReportService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class ReportsController implements ReportsApi {

    private final ReportService reportService;
    private final ReportMapper reportMapper;
    private final HttpServletRequest httpRequest;

    private UUID currentUserId() {
        return UUID.fromString(httpRequest.getHeader("X-User-Id"));
    }

    @Override
    public ResponseEntity<ReportResponse> createReport(CreateReportRequest body) {
        var report = reportService.createReport(currentUserId(), body.getTargetType(), body.getTargetId(), body.getReason());
        return ResponseEntity.status(HttpStatus.CREATED).body(reportMapper.toDto(report));
    }

    @Override
    public ResponseEntity<ReportPage> listReports(ReportStatus status, ReportTargetType targetType,
                                                   Integer page, Integer size) {
        var p = reportService.getReportsByStatus(status, targetType, PageRequest.of(page, size));
        ReportPage dto = new ReportPage()
                .content(reportMapper.toDtoList(p.getContent()))
                .totalElements((int) p.getTotalElements())
                .totalPages(p.getTotalPages())
                .page(p.getNumber())
                .size(p.getSize());
        return ResponseEntity.ok(dto);
    }

    @Override
    public ResponseEntity<ReportResponse> updateReportStatus(UUID reportId, UpdateReportStatusRequest body) {
        var report = reportService.updateStatus(reportId, body.getStatus());
        return ResponseEntity.ok(reportMapper.toDto(report));
    }

    @Override
    public ResponseEntity<ReportStatsResponse> getReportStats() {
        Map<String, Object> stats = reportService.getStats();
        ReportStatsResponse response = new ReportStatsResponse()
                .total((Long) stats.get("total"))
                .byStatus((Map<String, Long>) stats.get("byStatus"))
                .byTargetType((Map<String, Long>) stats.get("byTargetType"));
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<List<ReportTargetSummary>> listGroupedReports(ReportTargetType targetType,
                                                                          ReportStatus status) {
        ReportStatus effectiveStatus = status != null ? status : ReportStatus.PENDING;
        List<Map<String, Object>> groups = reportService.getGroupedReports(effectiveStatus, targetType);
        List<ReportTargetSummary> result = groups.stream()
                .map(m -> {
                    LocalDateTime lat = (LocalDateTime) m.get("latestReportAt");
                    return new ReportTargetSummary()
                            .targetId((String) m.get("targetId"))
                            .targetType(ReportTargetType.valueOf((String) m.get("targetType")))
                            .reportCount((Long) m.get("reportCount"))
                            .latestReportAt(lat);
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @Override
    public ResponseEntity<List<ReportResponse>> getReportsByTarget(String targetId) {
        return ResponseEntity.ok(reportMapper.toDtoList(reportService.getReportsByTargetId(targetId)));
    }

    @Override
    public ResponseEntity<Void> dismissReportsByTarget(String targetId) {
        reportService.dismissAllByTargetId(targetId);
        return ResponseEntity.noContent().build();
    }
}
