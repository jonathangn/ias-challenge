package com.jonathangn.loancore.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.jonathangn.loancore.domain.Client;
import com.jonathangn.loancore.domain.ClientStatus;
import com.jonathangn.loancore.domain.LoanRequest;
import com.jonathangn.loancore.domain.LoanRequestStatus;
import com.jonathangn.loancore.dto.CreateLoanRequestRequest;
import com.jonathangn.loancore.dto.LoanRequestResponse;
import com.jonathangn.loancore.repository.ClientRepository;
import com.jonathangn.loancore.repository.LoanRequestRepository;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class LoanRequestService {

    private static final int MIN_TERM_MONTHS = 6;
    private static final int MAX_TERM_MONTHS = 60;

    private final ClientRepository clientRepository;
    private final LoanRequestRepository loanRequestRepository;
    private final DatabaseClient databaseClient;
    private final TransactionalOperator transactionalOperator;

    public Mono<LoanRequestResponse> process(CreateLoanRequestRequest request) {
        if (!StringUtils.hasText(request.requestReference())
                || !StringUtils.hasText(request.clientCode())
                || request.amount() == null
                || request.termMonths() == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "requestReference, clientCode, amount and termMonths are required"));
        }

        return loanRequestRepository.findByRequestReference(request.requestReference())
                .flatMap(existing -> replay(request, existing))
                .switchIfEmpty(Mono.defer(() -> transactionalOperator.transactional(create(request))))
                .onErrorResume(DuplicateKeyException.class, error -> resolveReplay(request));
    }

    public Mono<LoanRequestResponse> findByReference(String requestReference) {
        return loanRequestRepository.findByRequestReference(requestReference)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Loan request not found: " + requestReference)))
                .map(this::toResponse);
    }

    public Flux<LoanRequestResponse> findRecent() {
        return loanRequestRepository.findTop10ByOrderByProcessedAtDesc()
                .map(this::toResponse);
    }

    private Mono<LoanRequestResponse> create(CreateLoanRequestRequest request) {
        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            return persist(request, LoanRequestStatus.REJECTED, "Amount must be greater than zero");
        }
        if (request.termMonths() < MIN_TERM_MONTHS || request.termMonths() > MAX_TERM_MONTHS) {
            return persist(request, LoanRequestStatus.REJECTED,
                    "Term must be between " + MIN_TERM_MONTHS + " and " + MAX_TERM_MONTHS + " months");
        }
        return clientRepository.findByClientCode(request.clientCode())
                .flatMap(client -> approveOrReject(request, client))
                .switchIfEmpty(Mono.defer(() ->
                        persist(request, LoanRequestStatus.REJECTED, "Client not found")));
    }

    private Mono<LoanRequestResponse> approveOrReject(CreateLoanRequestRequest request, Client client) {
        if (client.status() != ClientStatus.ACTIVE) {
            return persist(request, LoanRequestStatus.REJECTED, "Client is not enabled");
        }
        return deductCapacity(request.clientCode(), request.amount())
                .flatMap(rowsUpdated -> rowsUpdated > 0
                        ? persist(request, LoanRequestStatus.APPROVED, null)
                        : persist(request, LoanRequestStatus.REJECTED, "Insufficient available capacity"));
    }

    private Mono<Long> deductCapacity(String clientCode, BigDecimal amount) {
        return databaseClient.sql("""
                        UPDATE clients
                           SET available_loan_capacity = available_loan_capacity - :amount,
                               updated_at = CURRENT_TIMESTAMP
                         WHERE client_code = :clientCode
                           AND status = 'ACTIVE'
                           AND available_loan_capacity >= :amount
                        """)
                .bind("amount", amount)
                .bind("clientCode", clientCode)
                .fetch()
                .rowsUpdated();
    }

    private Mono<LoanRequestResponse> persist(
            CreateLoanRequestRequest request, LoanRequestStatus status, String resultReason) {
        LocalDateTime now = LocalDateTime.now();
        LoanRequest loanRequest = new LoanRequest(
                null,
                request.requestReference(),
                request.clientCode(),
                request.amount(),
                request.termMonths(),
                status,
                resultReason,
                now,
                now);
        return loanRequestRepository.save(loanRequest).map(this::toResponse);
    }

    private Mono<LoanRequestResponse> resolveReplay(CreateLoanRequestRequest request) {
        return loanRequestRepository.findByRequestReference(request.requestReference())
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Duplicate requestReference could not be resolved")))
                .flatMap(existing -> replay(request, existing));
    }

    private Mono<LoanRequestResponse> replay(CreateLoanRequestRequest request, LoanRequest existing) {
        if (!matches(request, existing)) {
            return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                    "requestReference " + request.requestReference()
                            + " was already processed with different data"));
        }
        return Mono.just(toResponse(existing));
    }

    private boolean matches(CreateLoanRequestRequest request, LoanRequest existing) {
        return existing.clientCode().equals(request.clientCode())
                && existing.requestedAmount().compareTo(request.amount()) == 0
                && existing.requestedTermMonths().equals(request.termMonths());
    }

    private LoanRequestResponse toResponse(LoanRequest loanRequest) {
        return new LoanRequestResponse(
                loanRequest.requestReference(),
                loanRequest.status(),
                loanRequest.resultReason(),
                loanRequest.processedAt());
    }
}
