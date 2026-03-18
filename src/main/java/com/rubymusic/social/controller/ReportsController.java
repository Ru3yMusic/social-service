package com.rubymusic.social.controller;

import com.rubymusic.social.dto.CreateReportRequest;
import com.rubymusic.social.dto.ReportPage;
import com.rubymusic.social.dto.ReportResponse;
import com.rubymusic.social.dto.ReportStatus;
import com.rubymusic.social.dto.UpdateReportStatusRequest;
import com.rubymusic.social.mapper.ReportMapper;
import com.rubymusic.social.service.ReportService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ReportsController implements ReportsApi {

    private final ReportService reportService;
    private final ReportMapper reportMapper;
    private final HttpServletRequest httpRequest;

    @Override
    public Optional<HttpServletRequest> getRequest() {
        return Optional.of(httpRequest);
    }

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
    public ResponseEntity<ReportPage> listReports(ReportStatus status, Integer page, Integer size) {
        var entityStatus = com.rubymusic.social.model.enums.ReportStatus.valueOf(status.name());
        var p = reportService.getReportsByStatus(entityStatus, PageRequest.of(page, size));
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
}
