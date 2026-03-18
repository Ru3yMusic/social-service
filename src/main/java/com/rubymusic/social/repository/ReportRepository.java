package com.rubymusic.social.repository;

import com.rubymusic.social.model.Report;
import com.rubymusic.social.model.enums.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ReportRepository extends JpaRepository<Report, UUID> {

    Page<Report> findAllByStatusOrderByCreatedAtDesc(ReportStatus status, Pageable pageable);

    boolean existsByReporterIdAndTargetId(UUID reporterId, String targetId);
}
