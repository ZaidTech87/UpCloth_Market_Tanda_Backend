package com.clothmarket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReportRequest {
    /** USER, POST or MESSAGE */
    @NotBlank(message = "Report type is required")
    private String targetType;

    @NotNull(message = "Report target is required")
    private Long targetId;

    /** SPAM, SCAM, INAPPROPRIATE, HARASSMENT, FAKE, COUNTERFEIT, OTHER */
    @NotBlank(message = "Please choose a reason")
    private String reason;

    @Size(max = 500, message = "Details are too long (max 500 characters)")
    private String details;
}
