package com.jonathangn.loancore;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;

import com.jonathangn.loancore.dto.CreateLoanRequestRequest;
import com.jonathangn.loancore.dto.LoanRequestResponse;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> POSTGRES.getJdbcUrl().replace("jdbc:", "r2dbc:"));
        registry.add("spring.r2dbc.username", POSTGRES::getUsername);
        registry.add("spring.r2dbc.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @Autowired
    protected DatabaseClient databaseClient;

    @LocalServerPort
    protected int port;

    protected WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
        resetDatabase();
    }

    private void resetDatabase() {
        databaseClient.sql("TRUNCATE TABLE loan_requests RESTART IDENTITY").then().block();
        databaseClient.sql("""
                        UPDATE clients
                           SET available_loan_capacity = CASE client_code
                                   WHEN 'USR-10' THEN 1000000.00
                                   WHEN 'USR-20' THEN 2000000.00
                                   ELSE 800000.00
                               END,
                               status = CASE client_code
                                   WHEN 'USR-30' THEN 'BLOCKED'
                                   ELSE 'ACTIVE'
                               END
                        """).then().block();
    }

    protected LoanRequestResponse postOk(
            String reference, String clientCode, BigDecimal amount, Integer termMonths) {
        return webTestClient.post()
                .uri("/api/loan-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CreateLoanRequestRequest(reference, clientCode, amount, termMonths))
                .exchange()
                .expectStatus().isOk()
                .expectBody(LoanRequestResponse.class)
                .returnResult()
                .getResponseBody();
    }

    protected Mono<LoanRequestResponse> post(
            String reference, String clientCode, BigDecimal amount, Integer termMonths) {
        return Mono.fromCallable(() -> webTestClient.post()
                        .uri("/api/loan-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new CreateLoanRequestRequest(reference, clientCode, amount, termMonths))
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody(LoanRequestResponse.class)
                        .returnResult()
                        .getResponseBody())
                .subscribeOn(Schedulers.boundedElastic());
    }

    protected BigDecimal capacity(String clientCode) {
        return databaseClient.sql("SELECT available_loan_capacity FROM clients WHERE client_code = :clientCode")
                .bind("clientCode", clientCode)
                .map((row, metadata) -> row.get("available_loan_capacity", BigDecimal.class))
                .one()
                .block();
    }

    protected long requestCount(String reference) {
        return databaseClient.sql("SELECT count(*) AS total FROM loan_requests WHERE request_reference = :reference")
                .bind("reference", reference)
                .map((row, metadata) -> row.get("total", Long.class))
                .one()
                .block();
    }
}
