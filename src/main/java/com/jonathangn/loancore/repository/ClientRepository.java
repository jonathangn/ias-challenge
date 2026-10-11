package com.jonathangn.loancore.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import com.jonathangn.loancore.domain.Client;

import reactor.core.publisher.Mono;

public interface ClientRepository extends ReactiveCrudRepository<Client, Long> {

    Mono<Client> findByClientCode(String clientCode);
}
