package com.jonathangn.loancore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.jonathangn.loancore.domain.LoanRequestStatus;
import com.jonathangn.loancore.dto.LoanRequestResponse;

import reactor.core.publisher.Flux;

class LoanRequestConcurrencyTest extends AbstractIntegrationTest {

    @Test
    void parallelRequestsCannotExceedAvailableCapacity() {
        List<LoanRequestResponse> responses = Flux.merge(
                post("CR-01", "USR-20", new BigDecimal("900000"), 12),
                post("CR-02", "USR-20", new BigDecimal("900000"), 12),
                post("CR-03", "USR-20", new BigDecimal("900000"), 12))
                .collectList()
                .block();

        long approved = responses.stream()
                .filter(response -> response.status() == LoanRequestStatus.APPROVED)
                .count();
        long rejected = responses.stream()
                .filter(response -> response.status() == LoanRequestStatus.REJECTED)
                .count();

        assertEquals(2, approved);
        assertEquals(1, rejected);
        assertTrue(responses.stream()
                .anyMatch(response -> "Insufficient available capacity".equals(response.resultReason())));
        assertEquals(0, capacity("USR-20").compareTo(new BigDecimal("200000.00")));
    }

    @Test
    void parallelRequestsWithSameReferenceAreProcessedOnce() {
        List<LoanRequestResponse> responses = Flux.merge(
                post("DR-01", "USR-10", new BigDecimal("50000"), 12),
                post("DR-01", "USR-10", new BigDecimal("50000"), 12))
                .collectList()
                .block();

        assertEquals(2, responses.size());
        assertTrue(responses.stream()
                .allMatch(response -> response.status() == LoanRequestStatus.APPROVED));
        assertEquals(responses.get(0).processedAt(), responses.get(1).processedAt());
        assertEquals(1, requestCount("DR-01"));
        assertEquals(0, capacity("USR-10").compareTo(new BigDecimal("950000.00")));
    }
}
