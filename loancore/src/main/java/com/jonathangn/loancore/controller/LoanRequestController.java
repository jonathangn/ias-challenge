package com.jonathangn.loancore.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jonathangn.loancore.dto.CreateLoanRequestRequest;
import com.jonathangn.loancore.dto.LoanRequestResponse;
import com.jonathangn.loancore.service.LoanRequestService;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/loan-requests")
@RequiredArgsConstructor
public class LoanRequestController {

    private final LoanRequestService loanRequestService;

    @PostMapping
    public Mono<LoanRequestResponse> create(
            @RequestBody CreateLoanRequestRequest request) {

        return loanRequestService.process(request);
    }

    @GetMapping("/{requestReference}")
    public Mono<LoanRequestResponse> findByReference(
            @PathVariable String requestReference) {
        return loanRequestService.findByReference(requestReference);
    }

    @GetMapping("/recent")
    public Flux<LoanRequestResponse> getRecent() {
        return loanRequestService.findRecent();
    }
}
