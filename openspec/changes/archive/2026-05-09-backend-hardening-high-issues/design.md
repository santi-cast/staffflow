# Design: Backend Hardening — 5 HIGH Issues

## Technical Approach

Cinco bloques de trabajo independientes, ejecutados en orden de riesgo creciente. Cada
bloque produce un commit convencional con rollback aislado por `git revert`. El bloque
de mayor riesgo (`@EnableMethodSecurity` + auditoría de 56 `@PreAuthorize`) se deja
para el final, una vez que las demás piezas (excepción de dominio, JPA LAZY,
externalización del JWT) están estabilizadas.

## ⚠ Bloqueadores antes de Apply (NEEDS BUSINESS CONFIRMATION)

La auditoría de los 56 `@PreAuthorize` (sección 5) detecta **6 endpoints** cuyo
`@PreAuthorize` actual usa `hasRole('EMPLEADO')` aunque la URL-rule en `SecurityConfig`
permita también ENCARGADO. Aplicando el role-model (engram id 259), la decisión correcta
es ampliar a `hasAnyRole('EMPLEADO','ENCARGADO')` — pero esto **RELAJA acceso** para
ENCARGADO en endpoints "personales" que hoy le bloquean.

| # | Endpoint | Estado |
|---|----------|--------|
| 1 | `GET /api/v1/empleados/me` | RESUELTO en spec (worked example) |
| 2 | `GET /api/v1/fichajes/me` | NEEDS CONFIRMATION |
| 3 | `GET /api/v1/ausencias/me` | NEEDS CONFIRMATION |
| 4 | `GET /api/v1/ausencias/me/informe` | NEEDS CONFIRMATION |
| 5 | `GET /api/v1/saldos/me` | NEEDS CONFIRMATION |
| 6 | `GET /api/v1/presencia/parte-diario/me` | NEEDS CONFIRMATION |
| 7 | `GET /api/v1/pausas/me` | NEEDS CONFIRMATION |
| 8 | `GET /api/v1/informes/me/horas` | NEEDS CONFIRMATION |

Pregunta única para el product owner: «¿ENCARGADO debe ver SUS propios fichajes,
saldos, ausencias, etc. en sus endpoints `/me`?» — el role-model dice SÍ (ENCARGADO
también es un empleado). Apply NO procede hasta confirmar.

## Execution Order (justificación)

1. **`NotFoundException` + remap GlobalExceptionHandler** — fundacional, bajo riesgo (clase nueva + cambio de status code en handler existente).
2. **Migrar 49 `IllegalStateException` en `service/`** — mecánico, aislado, beneficio inmediato (los duplicados PIN/NFC pasan a 409).
3. **Externalización JWT** — sólo configuración; sin cambio de runtime si la env var ya está definida (lo está localmente — proposal lo confirma).
4. **JPA LAZY + JOIN FETCH** — riesgo medio: puede exponer `LazyInitializationException` en perfil `mysql` con OSIV desactivado.
5. **`@EnableMethodSecurity` + auditoría 56 `@PreAuthorize`** — máximo riesgo: cambia comportamiento real de autorización.

## Architecture Decisions

### Decision: NotFoundException en `com.staffflow.exception`

**Choice**: Crear `NotFoundException extends RuntimeException` en el paquete existente, mismo patrón que `ConflictException` (verificado: archivo presente, RuntimeException, constructor `(String mensaje)`, Javadoc con autor).

**Alternatives consideradas**: Reutilizar `jakarta.persistence.EntityNotFoundException` (ya hay un handler en GlobalExceptionHandler línea 199). Rechazado: confunde semántica (excepción de JPA) y limita los mensajes de dominio.

**Rationale**: Consistencia con el patrón ya establecido en el proyecto.

### Decision: `IllegalStateException` → 500

**Choice**: Eliminar el handler específico de `IllegalStateException` (líneas 177-185 de `GlobalExceptionHandler`) y dejar que caiga al handler genérico `Exception.class` → 500 con mensaje «Error interno del servidor».

**Alternatives consideradas**: Mantener handler dedicado con HTTP 500 y mensaje específico. Rechazado: ISE legítimo (PdfService) representa fallo interno; no aporta valor distinguirlo del handler genérico.

**Rationale**: ISE residual en `PdfService` (8 usos) representa errores de generación PDF — son fallos internos. 500 con mensaje genérico es la respuesta correcta y oculta detalle interno al cliente.

### Decision: dos casos `EmpleadoService:316/323` → `ConflictException`

**Choice**: PIN/NFC duplicado en `actualizar()` debe lanzar `ConflictException` (HTTP 409), NO `NotFoundException`.

**Rationale**: La lógica es preventiva-de-unicidad (`existsByPinTerminalAndIdNot`). El propio Javadoc del método (línea 275) ya dice «409 Conflict → PIN o NFC duplicados». Es un bug de tipado (se usó ISE por la excepción más cercana). Otros casos similares en la misma clase (líneas 112, 117, 374) ya usan `ConflictException` correctamente.

### Decision: `@EnableMethodSecurity` en `SecurityConfig`

**Choice**: Añadir la anotación en la clase `SecurityConfig` ya existente (junto a `@EnableWebSecurity` línea 42).

**Alternatives consideradas**: Clase separada `MethodSecurityConfig`. Rechazado: añade un archivo sin beneficio — `SecurityConfig` ya es el punto de configuración de seguridad.

**Rationale**: Mínimo cambio, máximo descubrimiento.

### Decision: JWT secret — sin fallback en mysql

**Choice**: `application-dev.yml` con fallback dev-only literal; `application-mysql.yml` con `${JWT_SECRET:?...}` (fail-fast).

**Rationale**: Fail-fast en producción evita arrancar con secreto vacío o de desarrollo. Dev mantiene fricción cero para el TFG.

## Section 2 — Exception Model

### NotFoundException — clase

```
package com.staffflow.exception;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String mensaje) { super(mensaje); }
}
```

Ubicación: `staffflow-backend/src/main/java/com/staffflow/exception/NotFoundException.java`. Mismo paquete que `ConflictException` (verificado).

### Cambio en GlobalExceptionHandler

| Acción | Antes | Después |
|--------|-------|---------|
| Añadir handler | — | `@ExceptionHandler(NotFoundException.class)` → 404, mismo `buildError(...)` |
| Eliminar handler | `@ExceptionHandler(IllegalStateException.class)` (líneas 177-185) → 404 | Eliminado: cae a `Exception.class` → 500 |
| Mantener handler | `@ExceptionHandler(EntityNotFoundException.class)` (líneas 199-207) → 404 | Sin cambio (lo usa TerminalService) |

Cuerpo de respuesta: usa `buildError(mensaje, null, request)` — formato `{ error, timestamp, path }`. **No se cambia el contrato de error.**

### Checklist de migración de los 49 ISE (categorización)

| Archivo | Líneas | Categoría |
|---------|--------|-----------|
| `EmpresaService.java` | 53 | not-found → `NotFoundException` |
| `SaldoService.java` | 75, 86, 161, 173, 209, 332, 357 | not-found → `NotFoundException` |
| `SaldoService.java` | 340, 344 | invalid-state (datos saldo inconsistentes) → mantener ISE → 500 |
| `AusenciaService.java` | 88, 112, 161, 216, 263, 268, 319, 379, 412 | not-found → `NotFoundException` |
| `PresenciaService.java` | 247 | not-found (username sin perfil empleado) → `NotFoundException` |
| `UsuarioService.java` | 164, 203, 253 | not-found → `NotFoundException` |
| `ProcesoCierreDiario.java` | 99 | not-found (config empresa ausente) → `NotFoundException` |
| `InformeService.java` | 204 | not-found → `NotFoundException` |
| `InformeService.java` | 303 | not-found (sin datos saldo año) → `NotFoundException` |
| `EmpleadoService.java` | 107, 244, 284, 347, 370, 476, 480 | not-found → `NotFoundException` |
| `EmpleadoService.java` | 316, 323 | **conflict** → `ConflictException` (no NotFoundException) |
| `PdfService.java` | 139, 349 | not-found → `NotFoundException` |
| `PdfService.java` | 188, 235, 292, 318, 402, 593, 1684 | invalid-state (error generación iText7) → mantener ISE → 500 |

**Total**: 49 ISE → 38 a `NotFoundException`, 2 a `ConflictException`, 9 mantienen ISE (caen a 500 vía handler genérico).

## Section 3 — JWT Configuration

Propiedad leída en `JwtTokenProvider.java:51` con `@Value("${staffflow.jwt.secret}")` (verificado). No hay otros consumidores de la propiedad.

| Archivo | Línea | Antes | Después |
|---------|-------|-------|---------|
| `application-dev.yml` | 51 | `secret: staffflow-secret-key-para-desarrollo-minimo-32-caracteres` | `secret: ${JWT_SECRET:staffflow-dev-only-secret-no-usar-en-prod-ok-32}` |
| `application-mysql.yml` | 40 | `secret: staffflow-secret-key-para-desarrollo-minimo-32-caracteres` | `secret: ${JWT_SECRET:?JWT_SECRET must be set for mysql profile}` |

Documentación: crear `staffflow-backend/.env.example` con `JWT_SECRET=` y comentario «mínimo 32 caracteres».

**Out of scope (documentado)**: el secreto comprometido permanece en historia git. Rotación post-deploy queda como follow-up.

## Section 4 — JPA Fetch Strategy

### Mapa BEFORE/AFTER

| # | Entidad | Línea | Relación | Default actual | Después |
|---|---------|-------|----------|----------------|---------|
| 1 | `Empleado` | 28 | `@OneToOne usuario` | EAGER (default OneToOne) | `fetch = FetchType.LAZY` |
| 2 | `Fichaje` | 33 | `@ManyToOne empleado` | EAGER (default ManyToOne) | `fetch = FetchType.LAZY` |
| 3 | `Fichaje` | 62 | `@ManyToOne usuario` | EAGER | `fetch = FetchType.LAZY` |
| 4 | `Pausa` | 32 | `@ManyToOne fichaje` | EAGER | `fetch = FetchType.LAZY` |
| 5 | `Pausa` | 57 | `@ManyToOne empleado` | EAGER | `fetch = FetchType.LAZY` |
| 6 | `PlanificacionAusencia` | 39 | `@ManyToOne empleado` | EAGER | `fetch = FetchType.LAZY` |
| 7 | `PlanificacionAusencia` | 58 | `@ManyToOne creadoPor` | EAGER | `fetch = FetchType.LAZY` |
| 8 | `SaldoAnual` | 36 | `@ManyToOne empleado` | EAGER | `fetch = FetchType.LAZY` |

### Read paths y fixes requeridos

**Fichaje.empleado / Fichaje.usuario:**
- `FichajeRepository.findByFiltros` (línea 125): YA hace `JOIN FETCH f.empleado`. **NO** hace `JOIN FETCH f.usuario` — añadir `LEFT JOIN FETCH f.usuario` (usuario auditor puede ser null).
- `FichajeRepository` línea 157 (`findByFecha`): YA hace `JOIN FETCH f.empleado`. Verificar acceso a `f.usuario` en service. Si no se accede, no requiere cambio.

**Pausa.fichaje / Pausa.empleado:**
- `PausaRepository` líneas 118, 148, 164: ya hacen `JOIN FETCH p.empleado`. Acceso a `p.fichaje` SÓLO ocurre dentro de `@Transactional` en PausaService → safe sin JOIN FETCH adicional, pero **verificar mapper a DTO**.

**PlanificacionAusencia.empleado / PlanificacionAusencia.creadoPor:**
- `PlanificacionAusenciaRepository` líneas 83, 139, 175: hacen `LEFT JOIN FETCH a.empleado`. **Falta `LEFT JOIN FETCH a.creadoPor`** si DTO incluye nombre del creador. Auditar `AusenciaResponse` mapper.

**SaldoAnual.empleado:**
- `SaldoAnualRepository`: NO tiene `@Query` con JOIN FETCH (verificado). `SaldoService.listarTodos(...)` accede a `saldo.getEmpleado().getNombre()` en mapper → **REQUIERE** crear método `findAllByAnio` con `JOIN FETCH s.empleado`.

**Empleado.usuario:**
- `EmpleadoService.listar(...)` y `obtenerPorId(...)` mapean campos del usuario asociado en `EmpleadoResponse` → **REQUIERE** que las consultas usen `@EntityGraph(attributePaths = "usuario")` o `JOIN FETCH e.usuario`. Auditar `EmpleadoRepository.findAll()` y `findById()`.

### Manual smoke test plan

| Flujo Android | Endpoint | Verificación |
|---------------|----------|--------------|
| Login + lista empleados | `POST /auth/login` + `GET /empleados` | 200 sin LazyInit |
| Listado fichajes con filtro | `GET /fichajes?fecha=...` | 200 sin LazyInit |
| Fichaje propio | `GET /fichajes/me` | 200 sin LazyInit |
| Mi saldo | `GET /saldos/me` | 200 sin LazyInit |
| Mis ausencias | `GET /ausencias/me` | 200 sin LazyInit |
| Listar pausas | `GET /pausas?fecha=...` | 200 sin LazyInit |

Ejecutar **con perfil `mysql`** (OSIV desactivado, `application-mysql.yml:8`).

## Section 5 — Security Authorization (auditoría de los 56 `@PreAuthorize`)

### Activación

`@EnableMethodSecurity` se añade en `SecurityConfig` justo debajo de `@EnableWebSecurity` (línea 42).

### Tabla maestra de auditoría (56 anotaciones + 7 endpoints sin anotación)

Categorías: **G** = Gestión, **C** = Cotidiana, **P** = Personal, **PUB** = Pública.

| # | Controller.method | Verb + URL | URL-rule actual | @PreAuthorize actual | Categoría | Propuesta | Status |
|---|-------------------|-----------|-----------------|----------------------|-----------|-----------|--------|
| 1 | `AuthController.login` | POST `/auth/login` | permitAll | — | PUB | (none) | matches |
| 2 | `AuthController.me` | GET `/auth/me` | authenticated (`/auth/**`) | `isAuthenticated()` | P | `isAuthenticated()` | matches |
| 3 | `AuthController.cambiarPassword` | PUT `/auth/password` | authenticated | `isAuthenticated()` | P | `isAuthenticated()` | matches |
| 4 | `AuthController.recuperarPassword` | POST `/auth/password/recovery` | permitAll | — | PUB | (none) | matches |
| 5 | `AuthController.resetPassword` | POST `/auth/password/reset` | permitAll | — | PUB | (none) | matches |
| 6 | `EmpresaController.obtener` | GET `/empresa` | hasRole(ADMIN) | `hasRole('ADMIN')` | G | `hasRole('ADMIN')` | matches |
| 7 | `EmpresaController.actualizar` | PUT `/empresa` | hasRole(ADMIN) | `hasRole('ADMIN')` | G | `hasRole('ADMIN')` | matches |
| 8 | `UsuarioController.crear` | POST `/usuarios` | hasRole(ADMIN) | `hasRole('ADMIN')` | G | `hasRole('ADMIN')` | matches |
| 9 | `UsuarioController.listar` | GET `/usuarios` | hasRole(ADMIN) | `hasRole('ADMIN')` | G | `hasRole('ADMIN')` | matches |
| 10 | `UsuarioController.obtener` | GET `/usuarios/{id}` | hasRole(ADMIN) | `hasRole('ADMIN')` | G | `hasRole('ADMIN')` | matches |
| 11 | `UsuarioController.actualizar` | PATCH `/usuarios/{id}` | hasRole(ADMIN) | `hasRole('ADMIN')` | G | `hasRole('ADMIN')` | matches |
| 12 | `UsuarioController.eliminar` | DELETE `/usuarios/{id}` | hasRole(ADMIN) | `hasRole('ADMIN')` | G | `hasRole('ADMIN')` | matches |
| 13 | `EmpleadoController.crear` | POST `/empleados` | hasAnyRole(ADMIN,ENCARGADO) | `hasAnyRole('ADMIN','ENCARGADO')` | G | igual | matches |
| 14 | `EmpleadoController.listar` | GET `/empleados` | hasAnyRole(ADMIN,ENCARGADO) | `hasAnyRole('ADMIN','ENCARGADO')` | G | igual | matches |
| 15 | `EmpleadoController.obtenerPorId` | GET `/empleados/{id}` | hasAnyRole(ADMIN,ENCARGADO) | igual | G | igual | matches |
| 16 | `EmpleadoController.actualizar` | PATCH `/empleados/{id}` | hasAnyRole(ADMIN,ENCARGADO) | igual | G | igual | matches |
| 17 | `EmpleadoController.darDeBaja` | PATCH `/empleados/{id}/baja` | hasAnyRole(ADMIN,ENCARGADO) | igual | G | igual | matches |
| 18 | `EmpleadoController.reactivar` | PATCH `/empleados/{id}/reactivar` | hasAnyRole(ADMIN,ENCARGADO) | igual | G | igual | matches |
| 19 | `EmpleadoController.obtenerEstado` | GET `/empleados/estado` | hasAnyRole(ADMIN,ENCARGADO) | igual | G | igual | matches |
| 20 | `EmpleadoController.exportar` | GET `/empleados/export` | hasAnyRole(ADMIN,ENCARGADO) | igual | G | igual | matches |
| 21 | `EmpleadoController.obtenerMiPerfil` | GET `/empleados/me` | hasAnyRole(EMPLEADO,ENCARGADO) | `hasRole('EMPLEADO')` | P | `hasAnyRole('EMPLEADO','ENCARGADO')` | **RELAJA (worked example, role-model: OK)** |
| 22 | `FichajeController.crear` | POST `/fichajes` | hasAnyRole(ADMIN,ENCARGADO) | igual | G | igual | matches |
| 23 | `FichajeController.actualizar` | PATCH `/fichajes/{id}` | hasAnyRole(ADMIN,ENCARGADO) | igual | G | igual | matches |
| 24 | `FichajeController.listar` | GET `/fichajes` | hasAnyRole(ADMIN,ENCARGADO) | igual | G | igual | matches |
| 25 | `FichajeController.incompletos` | GET `/fichajes/incompletos` | hasAnyRole(ADMIN,ENCARGADO) | igual | G | igual | matches |
| 26 | `FichajeController.misFichajes` | GET `/fichajes/me` | hasAnyRole(EMPLEADO,ENCARGADO) | `hasRole('EMPLEADO')` | P | `hasAnyRole('EMPLEADO','ENCARGADO')` | **NEEDS CONFIRMATION** |
| 27 | `PausaController.crear` | POST `/pausas` | hasAnyRole(ADMIN,ENCARGADO) | igual | G | igual | matches |
| 28 | `PausaController.actualizar` | PATCH `/pausas/{id}` | hasAnyRole(ADMIN,ENCARGADO) | igual | G | igual | matches |
| 29 | `PausaController.misPausas` | GET `/pausas/me` | hasAnyRole(EMPLEADO,ENCARGADO) | `hasRole('EMPLEADO')` | P | `hasAnyRole('EMPLEADO','ENCARGADO')` | **NEEDS CONFIRMATION** |
| 30 | `PausaController.listar` | GET `/pausas` | hasAnyRole(ADMIN,ENCARGADO) | igual | G | igual | matches |
| 31 | `AusenciaController.misAusencias` | GET `/ausencias/me` | hasAnyRole(EMPLEADO,ENCARGADO) | `hasRole('EMPLEADO')` | P | `hasAnyRole('EMPLEADO','ENCARGADO')` | **NEEDS CONFIRMATION** |
| 32 | `AusenciaController.miInforme` | GET `/ausencias/me/informe` | hasAnyRole(EMPLEADO,ENCARGADO) | `hasRole('EMPLEADO')` | P | `hasAnyRole('EMPLEADO','ENCARGADO')` | **NEEDS CONFIRMATION** |
| 33 | `AusenciaController.informeEmpleado` | GET `/ausencias/{id}/informe` | hasAnyRole(ADMIN,ENCARGADO) | igual | G | igual | matches |
| 34 | `AusenciaController.crearRango` | POST `/ausencias/rango` | hasAnyRole(ADMIN,ENCARGADO) | igual | G | igual | matches |
| 35 | `AusenciaController.crear` | POST `/ausencias` | hasAnyRole(ADMIN,ENCARGADO) | igual | G | igual | matches |
| 36 | `AusenciaController.listar` | GET `/ausencias` | hasAnyRole(ADMIN,ENCARGADO) | igual | G | igual | matches |
| 37 | `AusenciaController.planificacionVacAp` | GET `/ausencias/planificacion-vac-ap` | hasAnyRole(ADMIN,ENCARGADO) | igual | G | igual | matches |
| 38 | `AusenciaController.actualizar` | PATCH `/ausencias/{id}` | hasAnyRole(ADMIN,ENCARGADO) | igual | G | igual | matches |
| 39 | `AusenciaController.eliminar` | DELETE `/ausencias/{id}` | hasAnyRole(ADMIN,ENCARGADO) | igual | G | igual | matches |
| 40 | `PresenciaController.parteDiarioMe` | GET `/presencia/parte-diario/me` | hasAnyRole(EMPLEADO,ENCARGADO) | `hasRole('EMPLEADO')` | P | `hasAnyRole('EMPLEADO','ENCARGADO')` | **NEEDS CONFIRMATION** |
| 41 | `PresenciaController.parteDiario` | GET `/presencia/parte-diario` | hasAnyRole(ADMIN,ENCARGADO) | igual | G | igual | matches |
| 42 | `PresenciaController.sinJustificar` | GET `/presencia/sin-justificar` | hasAnyRole(ADMIN,ENCARGADO) | igual | G | igual | matches |
| 43 | `SaldoController.miSaldo` | GET `/saldos/me` | hasAnyRole(EMPLEADO,ENCARGADO) | `hasRole('EMPLEADO')` | P | `hasAnyRole('EMPLEADO','ENCARGADO')` | **NEEDS CONFIRMATION** |
| 44 | `SaldoController.obtenerPorEmpleado` | GET `/saldos/{empleadoId}` | hasAnyRole(ADMIN,ENCARGADO) | igual | G | igual | matches |
| 45 | `SaldoController.listar` | GET `/saldos` | hasAnyRole(ADMIN,ENCARGADO) | igual | G | igual | matches |
| 46 | `SaldoController.recalcular` | POST `/saldos/{id}/recalcular` | hasRole(ADMIN) | `hasRole('ADMIN')` | G | `hasRole('ADMIN')` | matches |
| 47 | `InformeController.misHoras` | GET `/informes/me/horas` | hasAnyRole(EMPLEADO,ENCARGADO) | `hasRole('EMPLEADO')` | P | `hasAnyRole('EMPLEADO','ENCARGADO')` | **NEEDS CONFIRMATION** |
| 48 | `InformeController.horasEmpleado` | GET `/informes/horas/{id}` | hasAnyRole(ADMIN,ENCARGADO) | igual | G | igual | matches |
| 49 | `InformeController.horas` | GET `/informes/horas` | hasAnyRole(ADMIN,ENCARGADO) | igual | G | igual | matches |
| 50 | `InformeController.semana` | GET `/informes/semana` | hasAnyRole(ADMIN,ENCARGADO) | igual | G | igual | matches |
| 51 | `InformeController.saldos` | GET `/informes/saldos` | hasAnyRole(ADMIN,ENCARGADO) | igual | G | igual | matches |
| 52 | `InformeController.ausencias` | GET `/informes/ausencias` | hasAnyRole(ADMIN,ENCARGADO) | igual | G | igual | matches |
| 53 | `PdfController.horasEmpleado` | GET `/informes/pdf/horas/{id}` | hasAnyRole(ADMIN,ENCARGADO) | igual | G | igual | matches |
| 54 | `PdfController.horas` | GET `/informes/pdf/horas` | hasAnyRole(ADMIN,ENCARGADO) | igual | G | igual | matches |
| 55 | `PdfController.saldos` | GET `/informes/pdf/saldos` | hasAnyRole(ADMIN,ENCARGADO) | igual | G | igual | matches |
| 56 | `PdfController.vacaciones` | GET `/informes/pdf/vacaciones` | hasAnyRole(ADMIN,ENCARGADO) | igual | G | igual | matches |

### Endpoints sin `@PreAuthorize` (no cuentan en los 56)

| Controller.method | URL-rule | Comentario |
|-------------------|----------|------------|
| `TerminalController.*` (7 métodos) | `/terminal/bloqueo` → ADMIN/ENCARGADO; resto → `anyRequest().authenticated()` ¿? Verificar en apply | El terminal usa PIN, no JWT — algunos endpoints son `permitAll` implícito. **VERIFY in apply** |
| `HealthController.health` | permitAll | OK |
| `TestProcesoCierreDiarioController.disparar` | falls through → `authenticated()` | OK para dev |

### Resumen del estado de la auditoría

- **49 anotaciones**: matches current → sin cambio
- **1 anotación**: RESUELTA en spec (`/empleados/me`)
- **6 anotaciones**: **NEEDS BUSINESS CONFIRMATION** (todos los `/me` con `hasRole('EMPLEADO')`)
- **0 TIGHTENS**: ningún @PreAuthorize aplica más restricción que la URL-rule
- **0 ELIMINA acceso**: nadie pierde acceso

Si se confirman los 6, el cambio neto es: **ENCARGADO gana acceso a sus 6 endpoints `/me`**. Esto es coherente con el role-model (engram id 259).

## Section 6 — Risks & Rollback

| Bloque | Riesgo principal | Rollback |
|--------|------------------|----------|
| 1. NotFoundException + remap | Algún ISE residual ahora cae a 500 (visible en logs) | `git revert` del commit |
| 2. Migración 49 ISE | Test que aserte excepción concreta podría romper (no hay según `discovery/backend-test-failures`) | `git revert` |
| 3. JWT externalización | `mysql` no arranca si falta env var | `git revert` + setear `JWT_SECRET` |
| 4. JPA LAZY + JOIN FETCH | `LazyInitializationException` en endpoint no auditado | `git revert` (LAZY explícito en entidades) |
| 5. `@EnableMethodSecurity` + audit | Endpoint que hoy responde 200 ahora 403 por mismatch | `git revert` del commit; @PreAuthorize vuelve a ser decorativo |

**Bloque 5 = riesgo más alto**. Por eso va último y exige confirmación previa de los 6 NEEDS CONFIRMATION.

## Section 7 — Test Strategy

### Tests verdes que deben mantenerse

```
./mvnw test -Dtest='SaldoServiceTest,JwtTokenProviderTest,TerminalServiceTest'
```

Tests pre-existentes ROTOS — NO se tocan en este change:
- `SaldoServiceTest.recalcularParaProceso_mezclaDeTipos` (assertion bug)
- `GlobalExceptionHandlerTest` (`@WebMvcTest` falta `application.properties`)
- `StaffflowBackendApplicationTests` (requiere MySQL live)

### Nuevos tests recomendados

| Test | Tipo | Justificación |
|------|------|---------------|
| `NotFoundExceptionTest` | Unit | Verifica constructor y mensaje |
| (opcional) `JwtSecretConfigurationTest` | Unit | Asserta que `@Value` lee de env |

Para `@PreAuthorize`: **manual smoke test matrix** es aceptable (TFG scope). NO se introducen tests de seguridad automatizados — esto requeriría reparar el contexto `@WebMvcTest` (out of scope).

### Manual smoke test matrix (post-apply, perfil mysql)

| Token | Endpoint | Esperado |
|-------|----------|----------|
| ADMIN | GET `/empleados` | 200 |
| ADMIN | GET `/empleados/me` | 403 (correcto: ADMIN no es empleado) |
| EMPLEADO | GET `/empleados/me` | 200 |
| ENCARGADO | GET `/empleados/me` | 200 (si NEEDS CONFIRMATION resuelto SÍ) |
| ENCARGADO | GET `/empleados` | 200 |
| sin token | POST `/auth/login` | 200 con credenciales válidas |
| sin token | POST `/terminal/entrada` con PIN | 200 |

## Migration / Rollout

Sin migración de BD. Cinco commits convencionales en el orden documentado. Variable
`JWT_SECRET` debe estar definida antes del primer arranque post-deploy en producción.

## Open Questions

- [ ] **Confirmar con product owner**: ¿ENCARGADO debe acceder a sus propios endpoints `/me` (filas 26, 29, 31, 32, 40, 43, 47 de la tabla)? — Apply NO procede sin esta confirmación.
- [ ] (verify in apply) Comportamiento real de `TerminalController` endpoints con `@EnableMethodSecurity` activo.
