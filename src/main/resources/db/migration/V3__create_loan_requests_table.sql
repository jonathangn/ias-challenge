CREATE TABLE loan_requests (
    id BIGSERIAL PRIMARY KEY,
    request_reference VARCHAR(20) NOT NULL UNIQUE,
    client_code VARCHAR(20) NOT NULL,
    requested_amount NUMERIC(15,2) NOT NULL,
    requested_term_months INTEGER NOT NULL,

    status VARCHAR(20) NOT NULL,
    result_reason VARCHAR(255),
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,

    CONSTRAINT fk_loan_requests_customer
    FOREIGN KEY (client_code)
    REFERENCES clients(client_code),

    CONSTRAINT chk_loan_request_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX idx_loan_requests_processed_at
ON loan_requests(processed_at DESC);