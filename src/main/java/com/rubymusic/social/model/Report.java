package com.rubymusic.social.model;

import com.rubymusic.social.model.enums.ReportStatus;
import com.rubymusic.social.model.enums.ReportTargetType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reports", indexes = {
        @Index(name = "idx_reports_status", columnList = "status"),
        @Index(name = "idx_reports_reporter", columnList = "reporter_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "reporter_id", nullable = false)
    private UUID reporterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 10)
    private ReportTargetType targetType;

    /**
     * String because the target can live in different services
     * (comment ObjectId, song UUID, user UUID).
     */
    @Column(name = "target_id", nullable = false, length = 255)
    private String targetId;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private ReportStatus status = ReportStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
