package com.personal.finance.bulkupload.entity;

import com.personal.finance.bulkupload.dto.ErrorRow;
import com.personal.finance.bulkupload.enums.JobStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * bulk_upload_jobs — spec §1.5. Tracks each async CSV ingestion job; only
 * failed rows live here (in {@code error_detail} JSONB) since successful rows
 * are queryable directly from the transactions table via {@code bulk_job_id}.
 */
@Entity
@Table(name = "bulk_upload_jobs", indexes = {
        @Index(name = "idx_bulk_jobs_user_id", columnList = "user_id"),
        @Index(name = "idx_bulk_jobs_status",  columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkUploadJob {

    @Id
    @UuidGenerator
    @Column(name = "job_id", nullable = false, updatable = false)
    private UUID jobId;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private JobStatus status;

    @Column(name = "total_rows")
    private Integer totalRows;

    @Column(name = "success_count", nullable = false)
    @Builder.Default
    private Integer successCount = 0;

    @Column(name = "error_count", nullable = false)
    @Builder.Default
    private Integer errorCount = 0;

    /**
     * Array of per-row failures. Hibernate 6 maps {@code @JdbcTypeCode(JSON)}
     * to MySQL {@code JSON} (and Postgres {@code jsonb}) natively — Jackson
     * handles serialisation. Nullable when no errors.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "error_detail", columnDefinition = "json")
    private List<ErrorRow> errorDetail;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;
}
