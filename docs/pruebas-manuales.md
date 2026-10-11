# Pruebas manuales — loan-core

Los `curl` se ejecutaron desde la terminal durante el desarrollo; este archivo los deja documentados por caso.

## Requisitos previos

```bash
docker compose up -d
./gradlew bootRun
```

- Base URL: `http://localhost:8080/api/loan-requests`
- Para partir de estado limpio: `docker compose down -v && docker compose up -d`
- Los valores esperados de capacidad asumen los datos semilla: `USR-10` = 1.000.000 ACTIVE, `USR-20` = 2.000.000 ACTIVE, `USR-30` = 800.000 BLOCKED.
- El orden de los casos importa porque las aprobaciones descuentan capacidad.

## 1. RF01/RF03 — Solicitud aprobada

```bash
curl -s -w ' [%{http_code}]\n' -X POST localhost:8080/api/loan-requests \
  -H 'Content-Type: application/json' \
  -d '{"requestReference":"LR-001","clientCode":"USR-10","amount":100000,"termMonths":12}'
```

- **Esperado**: 200 con `status=APPROVED`, `resultReason=null`, `processedAt` con la fecha/hora de procesamiento.
- **Verificado**: `{"requestReference":"LR-001","status":"APPROVED","resultReason":null,"processedAt":"2026-10-10T20:11:03.483041"} [200]`
- Capacidad de `USR-10` pasa de 1.000.000 a 900.000.

## 2. RF05 — Misma referencia, mismos datos (reintento)

```bash
curl -s -w ' [%{http_code}]\n' -X POST localhost:8080/api/loan-requests \
  -H 'Content-Type: application/json' \
  -d '{"requestReference":"LR-001","clientCode":"USR-10","amount":100000,"termMonths":12}'
```

- **Esperado**: 200 con exactamente el resultado original (mismo `processedAt`); sin segunda solicitud y sin segundo descuento.
- **Verificado**: mismo body y mismo `processedAt` que el caso 1; capacidad sigue en 900.000 (ver consulta al final).

## 3. RF05 — Misma referencia, datos distintos

```bash
curl -s -w ' [%{http_code}]\n' -X POST localhost:8080/api/loan-requests \
  -H 'Content-Type: application/json' \
  -d '{"requestReference":"LR-001","clientCode":"USR-10","amount":999999,"termMonths":12}'
```

- **Esperado**: 409 Conflict; la solicitud original queda intacta.
- **Verificado**: `{"status":409,"error":"Conflict","path":"/api/loan-requests"} [409]`

## 4. RF06 — Consulta por referencia

```bash
curl -s -w ' [%{http_code}]\n' localhost:8080/api/loan-requests/LR-001
```

- **Esperado**: 200 con la solicitud original.
- **Verificado**: `{"requestReference":"LR-001","status":"APPROVED","resultReason":null,"processedAt":"2026-10-10T20:11:03.483041"} [200]`

## 5. RF06 — Referencia inexistente

```bash
curl -s -o /dev/null -w '[%{http_code}]\n' localhost:8080/api/loan-requests/NOPE-1
```

- **Esperado**: 404.
- **Verificado**: `[404]`

## 6. RF02 — Monto igual a cero

```bash
curl -s -w ' [%{http_code}]\n' -X POST localhost:8080/api/loan-requests \
  -H 'Content-Type: application/json' \
  -d '{"requestReference":"LR-002","clientCode":"USR-10","amount":0,"termMonths":12}'
```

- **Esperado**: `REJECTED` con razón `Amount must be greater than zero`; la rechazada se persiste (RF03) y no toca capacidad.
- **Verificado**: `{"requestReference":"LR-002","status":"REJECTED","resultReason":"Amount must be greater than zero",...} [200]`

## 7. RF02 — Plazo fuera de rango (3 meses)

```bash
curl -s -w ' [%{http_code}]\n' -X POST localhost:8080/api/loan-requests \
  -H 'Content-Type: application/json' \
  -d '{"requestReference":"LR-003","clientCode":"USR-10","amount":1000,"termMonths":3}'
```

- **Esperado**: `REJECTED` con razón `Term must be between 6 and 60 months`.
- **Verificado**: `{"requestReference":"LR-003","status":"REJECTED","resultReason":"Term must be between 6 and 60 months",...} [200]`

## 8. RF02 — Cliente bloqueado

```bash
curl -s -w ' [%{http_code}]\n' -X POST localhost:8080/api/loan-requests \
  -H 'Content-Type: application/json' \
  -d '{"requestReference":"LR-004","clientCode":"USR-30","amount":1000,"termMonths":12}'
```

- **Esperado**: `REJECTED` con razón `Client is not enabled`; capacidad de `USR-30` no cambia.
- **Verificado**: `{"requestReference":"LR-004","status":"REJECTED","resultReason":"Client is not enabled",...} [200]`

## 9. RF02 — Cliente inexistente

```bash
curl -s -w ' [%{http_code}]\n' -X POST localhost:8080/api/loan-requests \
  -H 'Content-Type: application/json' \
  -d '{"requestReference":"LR-005","clientCode":"USR-99","amount":1000,"termMonths":12}'
```

- **Esperado**: `REJECTED` con razón `Client not found`; la rechazada se persiste igualmente (esto es posible porque se eliminó la FK de `loan_requests` hacia `clients`, ver nota al final).
- **Verificado**: `{"requestReference":"LR-005","status":"REJECTED","resultReason":"Client not found",...} [200]`

## 10. RF02 — Capacidad insuficiente

```bash
curl -s -w ' [%{http_code}]\n' -X POST localhost:8080/api/loan-requests \
  -H 'Content-Type: application/json' \
  -d '{"requestReference":"LR-006","clientCode":"USR-10","amount":950000,"termMonths":12}'
```

- **Precondición**: `USR-10` tiene 900.000 disponibles (viene del caso 1).
- **Esperado**: `REJECTED` con razón `Insufficient available capacity`.
- **Verificado**: `{"requestReference":"LR-006","status":"REJECTED","resultReason":"Insufficient available capacity",...} [200]`

## 11. RF04 — Solicitudes simultáneas, misma persona

Tres peticiones en paralelo de 900.000 para `USR-20` (capacidad 2.000.000):

```bash
for r in CR-01 CR-02 CR-03; do
  curl -s -X POST localhost:8080/api/loan-requests \
    -H 'Content-Type: application/json' \
    -d "{\"requestReference\":\"$r\",\"clientCode\":\"USR-20\",\"amount\":900000,\"termMonths\":12}" &
done
wait
```

- **Esperado**: exactamente 2 `APPROVED` y 1 `REJECTED` (`Insufficient available capacity`); capacidad final 200.000; total aprobado (1.800.000) ≤ 2.000.000.
- **Verificado**: CR-02 y CR-03 `APPROVED`, CR-01 `REJECTED`; capacidad final `200000.00`. Cuál petición se rechaza es no determinista.

## 12. RF05 — Solicitudes simultáneas, misma referencia

Dos peticiones en paralelo con la misma `requestReference`:

```bash
for i in 1 2; do
  curl -s -X POST localhost:8080/api/loan-requests \
    -H 'Content-Type: application/json' \
    -d '{"requestReference":"DR-01","clientCode":"USR-10","amount":50000,"termMonths":12}' &
done
wait
```

- **Esperado**: ambas responden 200 con el mismo resultado (idéntico `processedAt`); una sola fila en BD; un solo descuento (900.000 → 850.000).
- **Verificado**: ambas respuestas `APPROVED` con `processedAt":"2026-10-10T20:11:31.485022"`; 1 fila en BD; capacidad `850000.00`.

## 13. RF06 — Solicitudes recientes

```bash
curl -s -w ' [%{http_code}]\n' localhost:8080/api/loan-requests/recent
```

- **Esperado**: 200 con las últimas 10 solicitudes ordenadas por `processedAt` descendente.
- **Verificado**: lista con DR-01, CR-01, CR-03, CR-02, LR-006... `[200]`

## Verificación en base de datos

```bash
docker exec loancore-postgres-1 psql -U postgres -d appdb -c \
  "SELECT client_code, available_loan_capacity FROM clients ORDER BY client_code;"

docker exec loancore-postgres-1 psql -U postgres -d appdb -c \
  "SELECT request_reference, status, result_reason FROM loan_requests ORDER BY id;"
```

Resultado final observado tras todos los casos:

```
 USR-10 | 850000.00
 USR-20 | 200000.00
 USR-30 | 800000.00

 LR-001 | APPROVED | (null)
 LR-002 | REJECTED | Amount must be greater than zero
 LR-003 | REJECTED | Term must be between 6 and 60 months
 LR-004 | REJECTED | Client is not enabled
 LR-005 | REJECTED | Client not found
 LR-006 | REJECTED | Insufficient available capacity
 CR-01..03 (2 APPROVED, 1 REJECTED)
 DR-01  | APPROVED (una sola fila)
```

## Nota: FK eliminada en V3

Se eliminó la FK `loan_requests.client_code → clients.client_code` porque RF03 exige persistir toda solicitud procesada, incluidas las rechazadas. El caso "cliente inexistente" debe generar una solicitud `REJECTED` persistida, algo imposible con la FK vigente (el INSERT fallaba con violación de integridad). La integridad de negocio se garantiza en la capa de servicio: se valida el cliente y el descuento de capacidad usa un UPDATE condicional atómico.
