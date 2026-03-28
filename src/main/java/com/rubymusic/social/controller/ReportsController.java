package com.rubymusic.social.controller;

import com.rubymusic.social.dto.CreateReportRequest;
import com.rubymusic.social.dto.ReportPage;
import com.rubymusic.social.dto.ReportResponse;
import com.rubymusic.social.dto.ReportStatus;
import com.rubymusic.social.dto.ReportTargetSummary;
import com.rubymusic.social.dto.ReportTargetType;
import com.rubymusic.social.dto.UpdateReportStatusRequest;
import com.rubymusic.social.mapper.ReportMapper;
import com.rubymusic.social.service.ReportService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
        var targetType = com.rubymusic.social.model.enums.ReportTargetType.valueOf(body.getTargetType().name());
        var report = reportService.createReport(currentUserId(), targetType, body.getTargetId(), body.getReason());
        return ResponseEntity.status(HttpStatus.CREATED).body(reportMapper.toDto(report));
    }

    @Override
    public ResponseEntity<ReportPage> listReports(ReportStatus status, ReportTargetType targetType,
                                                   Integer page, Integer size) {
        var entityStatus = com.rubymusic.social.model.enums.ReportStatus.valueOf(status.name());
        var entityTargetType = targetType != null
                ? com.rubymusic.social.model.enums.ReportTargetType.valueOf(targetType.name())
                : null;
        var p = reportService.getReportsByStatus(entityStatus, entityTargetType, PageRequest.of(page, size));
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
        var entityStatus = com.rubymusic.social.model.enums.ReportStatus.valueOf(body.getStatus().name());
        var report = reportService.updateStatus(reportId, entityStatus);
        return ResponseEntity.ok(reportMapper.toDto(report));
    }

    @Override
    public ResponseEntity<Map<String, Object>> getReportStats() {
        return ResponseEntity.ok(reportService.getStats());
    }

    @Override
    public ResponseEntity<List<ReportTargetSummary>> listGroupedReports(ReportTargetType targetType,
                                                                          ReportStatus status) {
        var entityStatus = status != null
                ? com.rubymusic.social.model.enums.ReportStatus.valueOf(status.name())
                : com.rubymusic.social.model.enums.ReportStatus.PENDING;
        var entityTargetType = targetType != null
                ? com.rubymusic.social.model.enums.ReportTargetType.valueOf(targetType.name())
                : null;
        List<Map<String, Object>> groups = reportService.getGroupedReports(entityStatus, entityTargetType);
        List<ReportTargetSummary> result = groups.stream()
                .map(m -> {
                    LocalDateTime lat = (LocalDateTime) m.get("latestReportAt");
                    return new ReportTargetSummary()
                            .targetId((String) m.get("targetId"))
                            .targetType(ReportTargetType.valueOf((String) m.get("targetType")))
                            .reportCount((Long) m.get("reportCount"))
                            .latestReportAt(lat != null ? lat.atOffset(ZoneOffset.UTC) : null);
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
