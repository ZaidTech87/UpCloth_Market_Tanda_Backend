package com.clothmarket.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** Optional body of the delete/block calls: why the admin is doing it (stored in the audit log). */
@Data
public class AdminReasonRequest {

    @Size(max = 500, message = "Reason is too long")
    private String reason;
}