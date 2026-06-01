package com.personal.finance.bulkupload.exception;

import com.personal.finance.common.exception.BaseException;
import com.personal.finance.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.UUID;

/** Spec §3.4: 404 if the job doesn't exist or belongs to a different user. */
public class JobNotFoundException extends BaseException {
    public JobNotFoundException(UUID jobId) {
        super(ErrorCode.JOB_NOT_FOUND, HttpStatus.NOT_FOUND,
                "Bulk upload job " + jobId + " not found for this user");
    }
}
