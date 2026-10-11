package com.jonathangn.loancore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import com.jonathangn.loancore.domain.LoanRequestStatus;
import com.jonathangn.loancore.dto.CreateLoanRequestRequest;
import com.jonathangn.loancore.dto.LoanRequestResponse;

class LoanRequestApiTest extends AbstractIntegrationTest {

    @Test
    void approvesRequestAndDeductsCapacity() {
        LoanRequestResponse response = postOk("LR-001", "USR-10", new BigDecimal("100000"), 12);

        assertEquals(LoanRequestStatus.APPROVED, response.status());
        assertNull(response.resultReason());
        assertNotNull(response.processedAt());
        assertEquals(0, capacity("USR-10").compareTo(new BigDecimal("900000.00")));
    }

    @Test
    void replaysSameReferenceWithoutProcessingAgain() {
        LoanRequestResponse first = postOk("LR-001", "USR-10", new BigDecimal("100000"), 12);
        LoanRequestResponse replay = postOk("LR-001", "USR-10", new BigDecimal("100000.00"), 12);

        assertEquals(first.processedAt(), replay.processedAt());
        assertEquals(LoanRequestStatus.APPROVED, replay.status());
        assertEquals(1, requestCount("LR-001"));
        assertEquals(0, capacity("USR-10").compareTo(new BigDecimal("900000.00")));
    }

    @Test
    void rejectsSameReferenceWithDifferentData() {
        postOk("LR-001", "USR-10", new BigDecimal("100000"), 12);

        webTestClient.post()
                .uri("/api/loan-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CreateLoanRequestRequest("LR-001", "USR-10", new BigDecimal("999999"), 12))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.status").isEqualTo(409);

        assertEquals(LoanRequestStatus.APPROVED, getOk("LR-001").status());
        assertEquals(0, capacity("USR-10").compareTo(new BigDecimal("900000.00")));
    }

    @Test
    void rejectsNonPositiveAmount() {
        LoanRequestResponse zero = postOk("LR-002", "USR-10", BigDecimal.ZERO, 12);
        LoanRequestResponse negative = postOk("LR-003", "USR-10", new BigDecimal("-100"), 12);

        assertEquals(LoanRequestStatus.REJECTED, zero.status());
        assertEquals("Amount must be greater than zero", zero.resultReason());
        assertEquals(LoanRequestStatus.REJECTED, negative.status());
        assertEquals(0, capacity("USR-10").compareTo(new BigDecimal("1000000.00")));
    }

    @Test
    void rejectsTermOutOfRange() {
        LoanRequestResponse tooShort = postOk("LR-004", "USR-10", new BigDecimal("1000"), 3);
        LoanRequestResponse tooLong = postOk("LR-005", "USR-10", new BigDecimal("1000"), 61);

        assertEquals(LoanRequestStatus.REJECTED, tooShort.status());
        assertEquals("Term must be between 6 and 60 months", tooShort.resultReason());
        assertEquals(LoanRequestStatus.REJECTED, tooLong.status());
        assertEquals(0, capacity("USR-10").compareTo(new BigDecimal("1000000.00")));
    }

    @Test
    void rejectsBlockedClientWithoutTouchingCapacity() {
        LoanRequestResponse response = postOk("LR-006", "USR-30", new BigDecimal("1000"), 12);

        assertEquals(LoanRequestStatus.REJECTED, response.status());
        assertEquals("Client is not enabled", response.resultReason());
        assertEquals(0, capacity("USR-30").compareTo(new BigDecimal("800000.00")));
    }

    @Test
    void rejectsUnknownClientAndPersistsTheResult() {
        LoanRequestResponse response = postOk("LR-007", "USR-99", new BigDecimal("1000"), 12);

        assertEquals(LoanRequestStatus.REJECTED, response.status());
        assertEquals("Client not found", response.resultReason());
        assertEquals(1, requestCount("LR-007"));
        assertEquals(LoanRequestStatus.REJECTED, getOk("LR-007").status());
    }

    @Test
    void rejectsWhenCapacityIsInsufficient() {
        LoanRequestResponse response = postOk("LR-008", "USR-10", new BigDecimal("1000000.01"), 12);

        assertEquals(LoanRequestStatus.REJECTED, response.status());
        assertEquals("Insufficient available capacity", response.resultReason());
        assertEquals(0, capacity("USR-10").compareTo(new BigDecimal("1000000.00")));
    }

    @Test
    void rejectsStructuralInvalidPayloadsWithBadRequest() {
        webTestClient.post()
                .uri("/api/loan-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CreateLoanRequestRequest("LR-009", "USR-10", null, 12))
                .exchange()
                .expectStatus().isBadRequest();

        webTestClient.post()
                .uri("/api/loan-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CreateLoanRequestRequest("  ", "USR-10", new BigDecimal("1000"), 12))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void findsByReferenceAndReturnsNotFound() {
        postOk("LR-010", "USR-10", new BigDecimal("1000"), 12);

        assertEquals(LoanRequestStatus.APPROVED, getOk("LR-010").status());

        webTestClient.get()
                .uri("/api/loan-requests/NOPE-1")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.status").isEqualTo(404);
    }

    @Test
    void listsMostRecentRequestsNewestFirst() {
        postOk("LR-011", "USR-10", new BigDecimal("100"), 6);
        postOk("LR-012", "USR-10", new BigDecimal("100"), 6);
        postOk("LR-013", "USR-10", new BigDecimal("100"), 6);

        webTestClient.get()
                .uri("/api/loan-requests/recent")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(LoanRequestResponse.class)
                .value(list -> {
                    assertEquals(3, list.size());
                    assertEquals("LR-013", list.get(0).requestReference());
                    assertEquals("LR-011", list.get(2).requestReference());
                });
    }

    private LoanRequestResponse getOk(String reference) {
        return webTestClient.get()
                .uri("/api/loan-requests/{reference}", reference)
                .exchange()
                .expectStatus().isOk()
                .expectBody(LoanRequestResponse.class)
                .returnResult()
                .getResponseBody();
    }
}
