# Proposal: Backend Hardening — 5 HIGH Issues

## Intent

Llevar el backend de StaffFlow a un estado production-ready antes de la entrega del TFG (2026-06-15)
y como base sólida para v2.0. El backend tiene 5 issues HIGH pendientes post-auditoría que comprometen
seguridad real (autorización decorativa, secreto JWT en git) y estabilidad (N+1 silenciosos,
semántica HTTP incorrecta, excepción de dominio faltante). Uno de los 11 HIGH (`@EnableScheduling`)
ya fue corregido en una sesión previa. Este change cierra los 5 restantes.

## Scope

### In Scope

- **ISE-01** `NotFoundException` — nueva clase de excepción de dominio + mapeo HTTP 404
- **ISE-02** `IllegalStateException → 404` — remap a 500 en `GlobalExceptionHandler`; reemplazar ~49 usos de `IllegalStateException` en services por `NotFoundException`
- **ISE-03** `@EnableMethodSecurity` — activar en `SecurityConfig`; auditar los 56 `@PreAuthorize` y corregir los que contradigan las reglas URL
- **ISE-04** JWT secret externalización — reemplazar literal hardcoded en `application-dev.yml` y `application-mysql.yml` por `${JWT_SECRET:?JWT_SECRET must be set}` con fallback local documentado
- **ISE-05** LAZY fetch sweep — declarar `fetch = FetchType.LAZY` explícito en los 8 `@ManyToOne` / `@OneToOne` de `Empleado`, `Fichaje`, `Pausa`, `PlanificacionAusencia`, `SaldoAnual`

### Out of Scope

- Issues MEDIUM y LOW del audit (paginación, auditoría JPA, TX nightly job, etc.)
- Cualquier cambio en `staffflow-android/`
- Corrección del bug de aserción en `SaldoServiceTest.recalcularParaProceso_mezclaDeTipos`
- Reparación del contexto `@WebMvcTest` roto (`GlobalExceptionHandlerTest`)
- CORS configuration (LOW para Android-only)
- BCrypt strength upgrade
- Flyway migrations

## Capabilities

### New Capabilities

- `exception-domain-model`: Introduce `NotFoundException` como excepción de dominio estándar con mapeo HTTP 404 correcto, eliminando el uso de `IllegalStateException` como señal de "not found"

### Modified Capabilities

- `security-authorization`: `@EnableMethodSecurity` activa la evaluación de `@PreAuthorize` en controllers; el contrato HTTP 403 para roles insuficientes pasa a ser doble capa (URL + método)
- `jwt-configuration`: El secreto JWT se externaliza a variable de entorno; la app falla rápido en arranque si `JWT_SECRET` no está definida en producción

## Approach

Orden de implementación elegido para minimizar riesgo de regresión:

1. **`NotFoundException` + ISE remap** — base semántica que los otros cambios no rompen; tests existentes (TerminalServiceTest) no dependen de `IllegalStateException`
2. **`@EnableMethodSecurity`** — una vez que los servicios tiran `NotFoundException` (que el handler ya sabe procesar), activar method security no introduce paths inesperados de 404
3. **JWT secret externalization** — cambio de config puro, sin impacto en lógica; `.env` local pre-existente ya tiene la variable
4. **LAZY fetch sweep** — último porque puede exponer `LazyInitializationException` en paths que hoy funcionan por EAGER accidental; se verifica con smoke test manual

## Affected Areas

| Área | Impacto | Descripción |
|------|---------|-------------|
| `exception/NotFoundException.java` | Nuevo | Clase de excepción de dominio |
| `exception/GlobalExceptionHandler.java` | Modificado | Remap `IllegalStateException` → 500; nuevo handler `NotFoundException` → 404 |
| `service/*.java` (13 archivos) | Modificado | ~49 usos de `IllegalStateException` → `NotFoundException` en paths "not found" |
| `config/SecurityConfig.java` | Modificado | Añadir `@EnableMethodSecurity` |
| `application-dev.yml` | Modificado | JWT secret → `${JWT_SECRET:staffflow-dev-secret-minimo-32-chars}` |
| `application-mysql.yml` | Modificado | JWT secret → `${JWT_SECRET:?JWT_SECRET must be set}` |
| `domain/entity/Empleado.java` | Modificado | `@OneToOne` → `fetch = FetchType.LAZY` |
| `domain/entity/Fichaje.java` | Modificado | 2× `@ManyToOne` → `fetch = FetchType.LAZY` |
| `domain/entity/Pausa.java` | Modificado | 2× `@ManyToOne` → `fetch = FetchType.LAZY` |
| `domain/entity/PlanificacionAusencia.java` | Modificado | 2× `@ManyToOne` → `fetch = FetchType.LAZY` |
| `domain/entity/SaldoAnual.java` | Modificado | `@ManyToOne` → `fetch = FetchType.LAZY` |

## Risks

| Riesgo | Probabilidad | Mitigación |
|--------|--------------|------------|
| Activar `@EnableMethodSecurity` rompe endpoints donde URL-rule y `@PreAuthorize` no coinciden | Alta | Auditar los 56 `@PreAuthorize` ANTES de hacer merge; verificar con smoke test login + /me + /empleados |
| LAZY fetch expone `LazyInitializationException` en paths que hoy cargan EAGER por defecto | Media | Revisar service methods con `@Transactional(readOnly=true)` que accedan asociaciones; añadir `JOIN FETCH` donde sea necesario |
| Remap `IllegalStateException → 500` desvela bugs silenciosos que hoy se disfrazaban de 404 | Baja | Deseable: señala fallos reales; documentar en release notes |
| JWT secret change invalida tokens activos en el momento del deploy | Baja | Aceptable: re-login necesario; documentar en release notes |

## Rollback Plan

- Cada issue se implementa en un commit convencional independiente (`fix:` o `refactor:`)
- Rollback por issue: `git revert <sha>` del commit correspondiente
- Rollback completo: `git revert` de los commits del change en orden inverso
- No hay cambios de esquema de BD — rollback no requiere migración

## Dependencies

- Variable de entorno `JWT_SECRET` debe existir en el entorno local antes de arrancar con perfil `mysql`
- Los tests verdes actuales (`SaldoServiceTest`, `JwtTokenProviderTest`, `TerminalServiceTest`) deben mantenerse verdes tras cada paso

## Success Criteria

- [ ] `NotFoundException` existe en `com.staffflow.exception` y `GlobalExceptionHandler` la mapea a 404
- [ ] `GlobalExceptionHandler` mapea `IllegalStateException` a 500 (o elimina el handler)
- [ ] Todos los `orElseThrow(() -> new IllegalStateException(...))` en services usan `NotFoundException` en paths "not found"
- [ ] `SecurityConfig` tiene `@EnableMethodSecurity` y el backend arranca sin errores
- [ ] Smoke test: `GET /api/v1/empleados/me` con token ADMIN devuelve 403 (no 200)
- [ ] Smoke test: `POST /api/v1/auth/login` con credenciales válidas devuelve 200
- [ ] Smoke test: fichaje de entrada desde terminal devuelve 200
- [ ] `application-dev.yml` y `application-mysql.yml` no contienen el secreto JWT literal
- [ ] Todos los `@ManyToOne` / `@OneToOne` en entidades tienen `fetch = FetchType.LAZY` explícito
- [ ] Suite de tests verdes: `./mvnw test -Dtest='SaldoServiceTest,JwtTokenProviderTest,TerminalServiceTest'` — 0 fallos nuevos
