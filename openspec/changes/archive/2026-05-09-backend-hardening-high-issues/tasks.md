# Tasks: Backend Hardening — 5 HIGH Issues

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines — Block 1 (Exception model) | ~80 líneas |
| Estimated changed lines — Block 2 (ISE migration) | ~160 líneas (38 NotFoundException + 2 ConflictException, ~4 líneas por sustitución) |
| Estimated changed lines — Block 3 (JWT) | ~15 líneas |
| Estimated changed lines — Block 4 (LAZY fetch) | ~60 líneas (8 anotaciones + JOIN FETCH en 3–4 repos) |
| Estimated changed lines — Block 5 (Method security) | ~50 líneas (1 anotación + 8 @PreAuthorize ajustados) |
| **Total estimated changed lines** | **~365 líneas** |
| 400-line budget risk | Medium |
| Chained PRs recommended | No |
| Suggested split | Single PR |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Medium

### Nota sobre el presupuesto

El cambio está por debajo de 400 líneas como PR único. El bloque más largo (Block 2, ISE migration)
es mecánico y revisable porque sigue un patrón uniforme: `orElseThrow(() -> new IllegalStateException(...))`
→ `orElseThrow(() -> new NotFoundException(...))`. Los bloques 3–5 son quirúrgicos. Se recomienda
PR único con secciones claramente delimitadas en la descripción del PR.

---

## Block 1: Exception Domain Model (fundacional)

- [x] 1.1 Crear `exception/NotFoundException.java` — extiende `RuntimeException`, constructor `(String mensaje)`, Javadoc referenciando ISE-01; modelo: `ConflictException.java`
- [x] 1.2 En `GlobalExceptionHandler.java` — añadir `@ExceptionHandler(NotFoundException.class)` → HTTP 404, body `{error, timestamp, path}` (mismo formato que handlers existentes); añadir Javadoc
- [x] 1.3 En `GlobalExceptionHandler.java` — eliminar handler `IllegalStateException` (líneas 177–185); ISE cae al handler genérico `Exception.class` → HTTP 500
- [x] 1.4 Escribir `GlobalExceptionHandlerNotFoundTest` — test unitario que verifica: (a) `NotFoundException` → 404 con body correcto; (b) `IllegalStateException` → 500 (no 404)
- [x] **Gate 1**: `./mvnw test -Dtest='SaldoServiceTest,JwtTokenProviderTest,TerminalServiceTest'` → 0 fallos (solo pre-existing mezclaDeTipos)

## Block 2: ISE Migration — service layer (38 NotFoundException + 2 ConflictException)

### Tarea 2.1 — EmpresaService + SaldoService
- [x] 2.1a `EmpresaService.java:53` — `IllegalStateException` → `NotFoundException` (empresa singleton no existe)
- [x] 2.1b `SaldoService.java:75,86,161,173,209,332,357` — 7 × `orElseThrow` → `NotFoundException`
- [x] 2.1c `SaldoService.java:340,344` — MANTENER como `IllegalStateException` (datos inconsistentes, semántica 500)
- [x] **Gate 2.1**: tests verdes (solo pre-existing mezclaDeTipos)

### Tarea 2.2 — AusenciaService
- [x] 2.2 `AusenciaService.java:88,112,161,216,263,268,319,379,412` — 9 × `orElseThrow` → `NotFoundException`
- [x] **Gate 2.2**: tests verdes

### Tarea 2.3 — PresenciaService + UsuarioService + ProcesoCierreDiario
- [x] 2.3a `PresenciaService.java:247` — `orElseThrow` → `NotFoundException` (perfil empleado no encontrado)
- [x] 2.3b `UsuarioService.java:164,203,253` — 3 × `orElseThrow` → `NotFoundException`; actualizar Javadoc de clase (ISE → 404 → NotFoundException → 404)
- [x] 2.3c `ProcesoCierreDiario.java:99` — `orElseThrow` → `NotFoundException` (usuario sistema no encontrado)
- [x] **Gate 2.3**: tests verdes

### Tarea 2.4 — InformeService + EmpleadoService
- [x] 2.4a `InformeService.java:204` — `orElseThrow` → `NotFoundException`
- [x] 2.4b `InformeService.java:303` — `IllegalStateException` → `NotFoundException` (sin datos de saldo para el año)
- [x] 2.4c `EmpleadoService.java:107,244,284,347,370,476,480` — 7 × `orElseThrow` → `NotFoundException`
- [x] 2.4d `EmpleadoService.java:316,323` — `IllegalStateException` → `ConflictException` (PIN/NFC duplicado)
- [x] **Gate 2.4**: tests verdes

### Tarea 2.5 — PdfService
- [x] 2.5a `PdfService.java:139,349` — 2 × `orElseThrow` → `NotFoundException` (empleado no encontrado)
- [x] 2.5b `PdfService.java:188,235,292,318,402,593,1684` — MANTENER como `IllegalStateException` (errores iText7, semántica 500)
- [x] **Gate 2.5**: tests verdes

### Tarea 2.6 — Verificación sweep final
- [x] 2.6 Sweep verificado: 9 ISEs restantes confirmados (SaldoService:341,345; PdfService:189,236,293,319,403,594,1685) — líneas desplazadas +1 por adición de import NotFoundException

## Block 3: JWT Externalization ✅

- [x] 3.1 `application-dev.yml:51` — reemplazado literal por `${JWT_SECRET:dev-only-staffflow-2026-05-08-NOT-FOR-PRODUCTION-pad01}`; comentario YAML añadido (rotación 2026-05-08). También actualizado `application.yaml` (archivo base con mismo literal, descubierto durante apply).
- [x] 3.2 `application-mysql.yml:40` — reemplazado por `${JWT_SECRET:?JWT_SECRET must be set for mysql profile}` (fail-fast); comentario YAML añadido.
- [x] 3.3 Verificado (lectura): `JwtTokenProvider.java:51` usa `@Value("${staffflow.jwt.secret}")` — sin cambio de código necesario. Propiedad name no cambia; solo cambia la fuente del valor.
- [x] 3.4 Creado `staffflow-backend/.env.example` con `JWT_SECRET=` y comentario sobre `openssl rand -base64 48`.
- [x] 3.5 SKIPPED (documentado): `JwtSecretConfigurationTest` diferido. La change es config-only; `JwtTokenProviderTest` ya valida el provider con secreto inyectado via `ReflectionTestUtils`. `@WebMvcTest` context roto en este proyecto — añadir `@SpringBootTest` sería costoso. Verificación manual: old literal eliminado (`rg 'staffflow-secret-key-para-desarrollo'` → 0 resultados en src/); `${JWT_SECRET...}` confirmado en los 3 archivos YAML.
- [x] **Gate 3**: 29/30 tests — solo pre-existing mezclaDeTipos. Literal antiguo ELIMINADO. Tres archivos YAML usan `${JWT_SECRET...}`. `.env.example` creado.

## Block 4: LAZY Fetch Sweep ✅

### Tarea 4.0 — Read-path map (ejecutado antes de cambios)
- [x] Mapa completo de relaciones y paths de lectura documentado en apply-progress (engram id 263)

### Tarea 4.1 — Empleado.java ✅
- [x] 4.1a `Empleado.java:28` — añadido `fetch = FetchType.LAZY` al `@OneToOne usuario`
- [x] 4.1b `EmpleadoRepository.java` — auditado: EmpleadoService.listar() ya es @Transactional(readOnly=true); toEmpleadoResponse() accede a usuario dentro de la sesión. Sin @EntityGraph necesario.

### Tarea 4.2 — Fichaje.java ✅
- [x] 4.2a `Fichaje.java:33,62` — añadido `fetch = FetchType.LAZY` a ambos @ManyToOne (empleado, usuario)
- [x] 4.2b `FichajeRepository.java` — confirmado: findByFiltros tiene JOIN FETCH empleado. El acceso a fichaje.getUsuario() en InformeService cubierto añadiendo @Transactional(readOnly=true) en los métodos de InformeService.

### Tarea 4.3 — Pausa.java ✅
- [x] 4.3a `Pausa.java:32,57` — añadido `fetch = FetchType.LAZY` a ambos @ManyToOne (empleado, usuario)
- [x] 4.3b `PausaRepository.java` / `PausaService.listar(...)` — confirmado: PausaService.listar() y listarPropios() son @Transactional(readOnly=true); findByFiltros tiene JOIN FETCH empleado. Acceso a pausa.getUsuario() en InformeService cubierto con @Transactional en InformeService.

### Tarea 4.4 — PlanificacionAusencia.java ✅
- [x] 4.4a `PlanificacionAusencia.java:39,58` — añadido `fetch = FetchType.LAZY` a @ManyToOne empleado y @ManyToOne usuario (creadoPor)
- [x] 4.4b Auditado: findByFiltros tiene LEFT JOIN FETCH empleado. findByEmpleadoIdAndRango no tiene JOIN FETCH empleado/usuario — cubierto añadiendo @Transactional(readOnly=true) a AusenciaService.listar() y AusenciaService.listarMias()

### Tarea 4.5 — SaldoAnual.java ✅
- [x] 4.5a `SaldoAnual.java:36` — añadido `fetch = FetchType.LAZY` a @ManyToOne empleado
- [x] 4.5b `SaldoAnualRepository.java` — creado `findAllByAnioWithEmpleado` con JOIN FETCH s.empleado. SaldoService.listarTodos() anotado con @Transactional(readOnly=true) para que el lazy loading opere dentro de sesión abierta (N+1 aceptable para PYME max 50 empleados).

### Tarea 4.6 — Verificación explícita ✅
- [x] `rg '@ManyToOne|@OneToOne'` → 8 relaciones, todas con `fetch = FetchType.LAZY`
- [x] `rg 'FetchType.EAGER'` → 0 resultados (ningún EAGER intencional)

- [x] **Gate 4**: `./mvnw test -Dtest='SaldoServiceTest,JwtTokenProviderTest,TerminalServiceTest'` → 29/30 (solo pre-existing mezclaDeTipos). Smoke test manual pendiente en Batch 4 (usuario ejecutará contra MySQL).

## Block 5: @EnableMethodSecurity + @PreAuthorize Audit

### Tarea 5.1 — Activar method security
- [ ] 5.1 `SecurityConfig.java:42` — añadir `@EnableMethodSecurity` junto a `@EnableWebSecurity`

### Tarea 5.2 — Corregir /me endpoints (6 controllers): ENCARGADO incluido
- [ ] 5.2a `AusenciaController.java:85,120` — `hasRole('EMPLEADO')` → `hasAnyRole('EMPLEADO','ENCARGADO')` (2 endpoints /me de ausencias)
- [ ] 5.2b `FichajeController.java:247` — `hasRole('EMPLEADO')` → `hasAnyRole('EMPLEADO','ENCARGADO')` (/fichajes/me)
- [ ] 5.2c `InformeController.java:61` — `hasRole('EMPLEADO')` → `hasAnyRole('EMPLEADO','ENCARGADO')` (/informes/me/horas)
- [ ] 5.2d `PausaController.java:158` — `hasRole('EMPLEADO')` → `hasAnyRole('EMPLEADO','ENCARGADO')` (/pausas/me)
- [ ] 5.2e `PresenciaController.java:85` — `hasRole('EMPLEADO')` → `hasAnyRole('EMPLEADO','ENCARGADO')` (/presencia/parte-diario/me)
- [ ] 5.2f `SaldoController.java:62` — `hasRole('EMPLEADO')` → `hasAnyRole('EMPLEADO','ENCARGADO')` (/saldos/me)

### Tarea 5.3 — Auditar /empleados/me (ya resuelto en spec)
- [ ] 5.3 `EmpleadoController.java:296` — verificar que ya es `hasAnyRole('EMPLEADO','ENCARGADO')` o corregir si sigue en `hasRole('EMPLEADO')`

### Tarea 5.4 — Verificar controllers sin modificación necesaria
- [ ] 5.4a Confirmar que `AuthController.java:82,109` con `isAuthenticated()` es correcto (no requiere rol específico)
- [ ] 5.4b Confirmar que `TerminalController.java` (0 @PreAuthorize, 7 métodos) funciona correctamente bajo @EnableMethodSecurity: `/terminal/bloqueo` → ADMIN/ENCARGADO por URL-rule; resto → `anyRequest().authenticated()`

- [ ] **Gate 5**: `./mvnw test -Dtest='SaldoServiceTest,JwtTokenProviderTest,TerminalServiceTest'` → 0 fallos; smoke matrix completo:
  - ADMIN → `GET /api/v1/empleados` → 200
  - ADMIN → `GET /api/v1/empleados/me` → 403 (correcto por diseño)
  - EMPLEADO → `GET /api/v1/empleados/me` → 200
  - ENCARGADO → `GET /api/v1/empleados/me` → 200
  - `POST /api/v1/auth/login` → 200
  - `POST /api/v1/terminal/entrada` → 200

## Cross-Cutting: Final Verification

- [ ] X.1 Ejecutar `./mvnw test -Dtest='SaldoServiceTest,JwtTokenProviderTest,TerminalServiceTest'` — confirmar 0 fallos nuevos en la suite completa tras todos los bloques
- [ ] X.2 Documentar en la descripción del PR los ítems diferidos fuera de scope: (1) rotación del secreto JWT comprometido en historial git; (2) reparación `GlobalExceptionHandlerTest` (@WebMvcTest context roto); (3) corrección `SaldoServiceTest.mezclaDeTipos`; (4) `StaffflowBackendApplicationTests` requiere MySQL activo

---

## Deferred (Out of Scope — documentar en PR)

- Rotación del secreto JWT comprometido en historia de git (scrubbing git history)
- Reparación `GlobalExceptionHandlerTest` — @WebMvcTest context requiere `application.properties` de test con JWT secret y mail config
- `SaldoServiceTest.recalcularParaProceso_mezclaDeTipos` — expectativa incorrecta en test (no en producción)
- `StaffflowBackendApplicationTests` — requiere instancia MySQL activa, excluir de CI unit test runs
- Issues MEDIUM y LOW del audit (paginación, auditoría JPA, CORS, BCrypt strength, Flyway)
