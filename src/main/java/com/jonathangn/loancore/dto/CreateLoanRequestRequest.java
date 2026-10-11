package com.jonathangn.loancore.dto;

import java.math.BigDecimal;

public record CreateLoanRequestRequest(
        String requestReference,
        String clientCode,
        BigDecimal amount,
        Integer termMonths
) {
}