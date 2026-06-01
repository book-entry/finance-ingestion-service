package com.personal.finance.bulkupload.repository;

import com.personal.finance.bulkupload.entity.BulkUploadJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BulkUploadJobRepository extends JpaRepository<BulkUploadJob, UUID> {

    /** Spec §3.4 GET — only the user who submitted the job may see it. */
    Optional<BulkUploadJob> findByJobIdAndUserId(UUID jobId, String userId);
}
