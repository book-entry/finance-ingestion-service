package com.personal.finance.bulkupload.exception;

import com.personal.finance.common.exception.BaseException;
import com.personal.finance.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** Spec §3.4: 400 if the multipart file is missing, not CSV, or unreadable. */
public class InvalidCsvException extends BaseException {
    public InvalidCsvException(String reason) {
        super(ErrorCode.INVALID_CSV, HttpStatus.BAD_REQUEST, reason);
    }

    public InvalidCsvException(String reason, Throwable cause) {
        super(ErrorCode.INVALID_CSV, HttpStatus.BAD_REQUEST, cause);
    }
}
