package com.rubymusic.social.repository;

import com.rubymusic.social.model.Report;
import com.rubymusic.social.model.enums.ReportStatus;
import com.rubymusic.social.model.enums.ReportTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReportRepository extends JpaRepository<Report, UUID> {

    Page<Report> findAllByStatusOrderByCreatedAtDesc(ReportStatus status, Pageable pageable);

    Page<Report> findAllByStatusAndTargetTypeOrderByCreatedAtDesc(
            ReportStatus status, ReportTargetType targetType, Pageable pageable);

    boolean existsByReporterIdAndTargetId(UUID reporterId, String targetId);

    /** All individual reports for a given target, newest first (for detail history modal) */
    List<Report> findAllByTargetIdOrderByCreatedAtDesc(String targetId);

    /**
     * Grouped summary: one row per distinct (targetId, targetType) with PENDING count + latest date.
     * Object[] = [targetId(String), targetType(ReportTargetType), count(Long), latestAt(LocalDateTime)]
     */
    @Query("SELECT r.targetId, r.targetType, COUNT(r), MAX(r.createdAt) " +
           "FROM Report r WHERE r.status = :status " +
           "GROUP BY r.targetId, r.targetType ORDER BY MAX(r.createdAt) DESC")
    List<Object[]> findGroupedByStatus(@Param("status") ReportStatus status);

    @Query("SELECT r.targetId, r.targetType, COUNT(r), MAX(r.createdAt) " +
           "FROM Report r WHERE r.status = :status AND r.targetType = :targetType " +
           "GROUP BY r.targetId, r.targetType ORDER BY MAX(r.createdAt) DESC")
    List<Object[]> findGroupedByStatusAndTargetType(@Param("status") ReportStatus status,
                                                     @Param("targetType") ReportTargetType targetType);

    /** Bulk-dismiss all PENDING reports for a given target (admin Descartar action) */
    @Modifying
    @Query("UPDATE Report r SET r.status = com.rubymusic.social.model.enums.ReportStatus.DISMISSED " +
           "WHERE r.targetId = :targetId AND r.status = com.rubymusic.social.model.enums.ReportStatus.PENDING")
    void dismissAllPendingByTargetId(@Param("targetId") String targetId);

    /** For admin dashboard donut chart: distribution of reports by status */
    @Query("SELECT r.status, COUNT(r) FROM Report r GROUP BY r.status")
    List<Object[]> countByStatus();

    /** For admin dashboard: distribution of reports by target type (COMMENT, SONG, USER) */
    @Query("SELECT r.targetType, COUNT(r) FROM Report r GROUP BY r.targetType")
    List<Object[]> countByTargetType();
}
