# Registro de uso de IA

Cómo se empleó IA, qué resultados se incorporaron, cómo se validaron y qué se corrigió, adaptó o descartó.

## Herramienta

- **opencode** (agente CLI de desarrollo) con modelo `deepseek-v4.1-flash`, usado como asistente durante la sesión de trabajo.

## Para qué se empleó

- Verificación del estado del proyecto y análisis de los requisitos (RF01–RF06) en la capa de servicio.
- Precisiones en la capa de servicio y respuestas de error (400/404/409), e implementación de tests de integración.
- Implementación del frontend Angular para RF07 (scaffold con el CLI estándar y ajustes de integración).

## Qué resultados se incorporaron (tras revisión)

- `LoanRequestService`: reglas RF02, persistencia RF03, concurrencia RF04 (transacción + `UPDATE` condicional atómico) e idempotencia RF05 (UNIQUE + `DuplicateKeyException` + 409 ante datos distintos).
- `ClientRepository` / `LoanRequestRepository` como interfaces `ReactiveCrudRepository`.
- `ApiExceptionHandler` + `ApiError` para respuestas de error consistentes.
- Tests de integración con Testcontainers (14 casos, incluida concurrencia real).
- Frontend `loanweb/`: servicio HTTP con `HttpClient`, UI (formulario, resultado y recientes) y proxy `/api` hacia el backend.
- `README.md` del proyecto y `docs/pruebas-manuales.md`.

## Cómo se validó

- Compilación con `./gradlew compileJava`.
- Pruebas end-to-end manuales con `curl` contra PostgreSQL real, verificando capacidades y filas con `psql` (`docs/pruebas-manuales.md`).
- Suite `./gradlew test`: 14 tests en verde, incluidos 3 concurrentes contra el cliente y 2 concurrentes con la misma referencia.
- Frontend: `pnpm build`, `pnpm test` (2 en verde) y verificación end-to-end a través del dev server con proxy (`localhost:4200` → `:8080`).
- Revisión manual del SQL, de la lógica transaccional y de los resultados en base de datos.

## Qué se corrigió, adaptó o descartó

- **Decisión**: eliminar la FK `loan_requests.client_code → clients.client_code`, porque impedía persistir rechazos de clientes inexistentes (RF03).
- **Ajustado**: el patrón de Testcontainers (contenedor singleton para compartir contexto de Spring) y el cliente de pruebas ligado al puerto aleatorio, tras fallos de conexión entre clases de test.
- **Ajustado**: la configuración de pnpm 11 (`allowBuilds` en `pnpm-workspace.yaml`) para permitir los postinstall de las dependencias del frontend.
- **Decisión propia**: integración del frontend mediante proxy del dev server, sin modificar CORS en el backend.
- **Descartado**: manejar manualmente JSON malformado; se delega en el framework con un handler específico.

## Nota

- No se incluyeron datos confidenciales, reales ni credenciales en prompts, código ni repositorio; los datos usados son semilla ficticia (`USR-10`, `USR-20`, `USR-30`).
