package com.jonathangn.loancore.domain;

import java.math.BigDecimal;

import org.springframework.data.relational.core.mapping.Table;

@Table("clients")
public record Client(
        Long id,
        String clientCode,
        ClientStatus status,
        BigDecimal availableLoanCapacity
) {
}
