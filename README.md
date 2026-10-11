# loan-core

Core de originación de préstamos de consumo: recibe, valida, registra y consulta solicitudes de préstamo. Una misma persona puede solicitar más de un préstamo y el core conserva resultados consistentes ante peticiones repetidas o casi simultáneas.

## Stack

- Java 25 + Spring Boot 4.1 (WebFlux reactivo)
- Spring Data R2DBC sobre PostgreSQL 16
- Flyway para migraciones
- Gradle, Lombok
- Testcontainers para tests de integración

## Cómo levantar

```bash
docker compose up -d
./gradlew bootRun
```

- API: http://localhost:8080/api/loan-requests
- Adminer: http://localhost:8081 (PostgreSQL / postgres / postgres / appdb)

## Cómo ejecutar las pruebas

```bash
./gradlew test
```

Son 14 tests de integración que levantan su propio PostgreSQL con Testcontainers (requieren Docker; no usan la base de `docker-compose`). Cubren aprobación, rechazos por cada regla, idempotencia, conflicto por datos distintos, consultas y concurrencia real. Las pruebas manuales con `curl` están documentadas en `docs/pruebas-manuales.md`.

## API

| Método | Ruta | Descripción |
| --- | --- | --- |
| POST | `/api/loan-requests` | Procesa una solicitud |
| GET | `/api/loan-requests/{requestReference}` | Consulta por referencia (404 si no existe) |
| GET | `/api/loan-requests/recent` | Últimas 10 solicitudes procesadas |

Body del POST:

```json
{ "requestReference": "LR-001", "clientCode": "USR-10", "amount": 100000, "termMonths": 12 }
```

Códigos de respuesta: `200` procesada (aprobada o rechazada), `400` payload inválido, `404` no encontrada, `409` referencia ya procesada con datos distintos, `500` error inesperado.

## Reglas de negocio (RF01–RF06)

- Se aprueba solo si `amount > 0`, `6 <= termMonths <= 60`, el cliente existe y está `ACTIVE`, y `available_loan_capacity >= amount`.
- Toda solicitud procesada se persiste con estado, `processedAt` y razón del resultado. Aprobada descuenta capacidad; rechazada no la modifica.
- Sin paginación: "recientes" son las últimas 10 por `processedAt` descendente.

## Decisiones de diseño (RF04/RF05)

- **Concurrencia (RF04)**: cada procesamiento corre en una transacción reactiva (`TransactionalOperator`). El descuento de capacidad es un `UPDATE` condicional atómico:

  ```sql
  UPDATE clients
     SET available_loan_capacity = available_loan_capacity - :amount
   WHERE client_code = :clientCode
     AND status = 'ACTIVE'
     AND available_loan_capacity >= :amount
  ```

  Si actualiza 0 filas, la solicitud se rechaza por capacidad. La fila del cliente actúa como punto de serialización, por lo que el total aprobado nunca excede la capacidad disponible.
- **Idempotencia (RF05)**: `request_reference` es `UNIQUE`. Si la referencia ya existe y los datos coinciden, se devuelve el resultado original sin reprocesar ni volver a descontar. Si los datos difieren, responde `409 Conflict` preservando la solicitud original. Si dos peticiones con la misma referencia entran a la vez, la restricción `UNIQUE` + la captura de `DuplicateKeyException` (fuera de la transacción) garantizan una sola solicitud: la petición perdedora espera el commit de la ganadora y devuelve ese resultado.
- **FK eliminada** (`loan_requests.client_code → clients.client_code`): RF03 exige persistir toda solicitud procesada, incluidas las rechazadas por cliente inexistente, algo imposible con la FK vigente. La integridad se valida en el servicio.

## Estructura

```
src/main/java/com/jonathangn/loancore/
├── controller/   LoanRequestController, ApiExceptionHandler
├── domain/       Client, LoanRequest (+ enums, @Table)
├── dto/          CreateLoanRequestRequest, LoanRequestResponse, ApiError
├── repository/   ClientRepository, LoanRequestRepository (ReactiveCrudRepository)
└── service/      LoanRequestService (reglas + idempotencia + concurrencia)
src/main/resources/db/migration/   V1–V3: esquema y datos semilla
src/test/java/com/jonathangn/loancore/   tests de integración (Testcontainers)
```

## Ejemplos

```bash
curl -X POST localhost:8080/api/loan-requests -H 'Content-Type: application/json' \
  -d '{"requestReference":"LR-001","clientCode":"USR-10","amount":100000,"termMonths":12}'

curl localhost:8080/api/loan-requests/LR-001
curl localhost:8080/api/loan-requests/recent
```

Datos semilla: `USR-10` (ACTIVE, 1.000.000), `USR-20` (ACTIVE, 2.000.000), `USR-30` (BLOCKED, 800.000).
