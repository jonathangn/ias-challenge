package com.jonathangn.loancore.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import com.jonathangn.loancore.domain.LoanRequest;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface LoanRequestRepository extends ReactiveCrudRepository<LoanRequest, Long> {

    Mono<LoanRequest> findByRequestReference(String requestReference);

    Flux<LoanRequest> findTop10ByOrderByProcessedAtDesc();
}
