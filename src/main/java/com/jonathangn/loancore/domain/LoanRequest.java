package com.jonathangn.loancore.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.data.relational.core.mapping.Table;

@Table("loan_requests")
public record LoanRequest(
        Long id,
        String requestReference,
        String clientCode,
        BigDecimal requestedAmount,
        Integer requestedTermMonths,
        LoanRequestStatus status,
        String resultReason,
        LocalDateTime createdAt,
        LocalDateTime processedAt
) {
}
