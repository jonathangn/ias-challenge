package com.jonathangn.loancore.dto;

import java.time.LocalDateTime;

import com.jonathangn.loancore.domain.LoanRequestStatus;

public record LoanRequestResponse(
        String requestReference,
        LoanRequestStatus status,
        String resultReason,
        LocalDateTime processedAt
) {

        
}
