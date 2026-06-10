package com.jenerator.common.model;

public enum JobStatus {
    QUEUED,
    CLAIMED,
    RUNNING,
    WAITING_FOR_APPROVAL,
    APPROVED_FOR_UPLOAD,
    UPLOADING,
    COMPLETED,
    FAILED,
    CANCELLED
}
