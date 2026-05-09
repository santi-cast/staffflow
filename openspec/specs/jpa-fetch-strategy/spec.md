# JPA Fetch Strategy Specification

## Purpose

Declarar `fetch = FetchType.LAZY` explícito en los `@ManyToOne` y `@OneToOne` de las
entidades de dominio que usaban el default EAGER de JPA, eliminando cargas en
cascada silenciosas que generan N+1 en cualquier listado.

## Requirements

### Requirement: All @ManyToOne and @OneToOne have explicit fetch = FetchType.LAZY

El sistema MUST declarar `fetch = FetchType.LAZY` explícito en las siguientes relaciones:

| Entidad | Relación | Campo |
|---------|----------|-------|
| `Fichaje` | `@ManyToOne` | `empleado` |
| `Fichaje` | `@ManyToOne` | `usuario` (auditor) |
| `Pausa` | `@ManyToOne` | `fichaje` |
| `Pausa` | `@ManyToOne` | `empleado` |
| `PlanificacionAusencia` | `@ManyToOne` | `empleado` |
| `PlanificacionAusencia` | `@ManyToOne` | `creadoPor` (usuario) |
| `SaldoAnual` | `@ManyToOne` | `empleado` |
| `Empleado` | `@OneToOne` | `usuario` |

#### Scenario: entidades compilan con LAZY explícito

- GIVEN cada una de las 5 entidades con `fetch = FetchType.LAZY` en sus relaciones
- WHEN se compila el proyecto
- THEN 0 errores de compilación relacionados con JPA

---

### Requirement: Read paths handle lazy loading correctly

Para cada relación convertida de EAGER a LAZY, el sistema MUST garantizar que todos los
paths de lectura que acceden a la relación en código fuera de la transacción de carga
usen `JOIN FETCH` o un `@EntityGraph`, o estén dentro del mismo contexto transaccional.

Verificación por entidad:

**Fichaje.empleado / Fichaje.usuario:**
- `FichajeService.listar(...)` → `FichajeRepository.findByFiltros` usa `JOIN FETCH empleado`
  y `JOIN FETCH usuario`; mappers ejecutados dentro de la transacción.

**Pausa.fichaje / Pausa.empleado:**
- `PausaService.listar(...)` → anotado con `@Transactional(readOnly=true)`;
  accesos al proxy ocurren dentro de la sesión.

**PlanificacionAusencia.empleado / PlanificacionAusencia.creadoPor:**
- `AusenciaService.listarPlanificaciones(...)` → `@Transactional(readOnly=true)` presente.

**SaldoAnual.empleado:**
- `SaldoService.listarTodos(...)` → `@Transactional(readOnly=true)` presente.

**Empleado.usuario:**
- `EmpleadoService.listar(...)` → query incluye `JOIN FETCH usuario`.

Si un path de lectura accede a la relación fuera de sesión y NO tiene `JOIN FETCH`, el
implementador MUST añadir la cláusula al `@Query` correspondiente o usar un `@EntityGraph`
ANTES de marcar la tarea como completa.

#### Scenario: listado de fichajes no lanza LazyInitializationException

- GIVEN perfil `mysql` activo (OSIV desactivado)
- WHEN `GET /api/v1/fichajes` con token ADMIN
- THEN HTTP 200 con la lista de fichajes correcta
- AND no aparece `LazyInitializationException` en los logs

#### Scenario: detalle de empleado no lanza LazyInitializationException

- GIVEN perfil `mysql` activo
- WHEN `GET /api/v1/empleados/{id}` con token ADMIN
- THEN HTTP 200 con los datos del empleado incluyendo datos de usuario (si el DTO los incluye)
- AND no aparece `LazyInitializationException` en los logs

#### Scenario: saldo anual propio no lanza LazyInitializationException

- GIVEN perfil `mysql` activo y token EMPLEADO
- WHEN `GET /api/v1/saldos/me`
- THEN HTTP 200 con el saldo del empleado autenticado
- AND no aparece `LazyInitializationException` en los logs

#### Scenario: listado de planificaciones de ausencia correcto

- GIVEN perfil `mysql` activo y token ADMIN
- WHEN `GET /api/v1/ausencias/planificaciones`
- THEN HTTP 200 con la lista de planificaciones
- AND no aparece `LazyInitializationException` en los logs

#### Scenario: listado de pausas correcto

- GIVEN perfil `mysql` activo y token ADMIN
- WHEN `GET /api/v1/pausas` con parámetros de filtro
- THEN HTTP 200 con la lista de pausas
- AND no aparece `LazyInitializationException` en los logs

---

### Requirement: Lazy loading risk flagging

Si durante la implementación se detecta un path de lectura que accede a la relación
fuera de transacción Y la corrección con `JOIN FETCH` no es trivial, el implementador DEBE:

1. Documentar el path en el PR description como "⚠ Acceso lazy sin JOIN FETCH corregido / pendiente".
2. Añadir un test unitario o de integración que cubra ese path.
3. No dejar `LazyInitializationException` silenciados con `try/catch`.

#### Scenario: riesgo de lazy documentado en PR

- GIVEN que se detecta acceso a relación lazy fuera de transacción
- WHEN el implementador lo resuelve
- THEN lo documenta en el PR description y añade `JOIN FETCH` en la consulta de carga
- OR decide cargar el objeto por separado dentro de la transacción del servicio

---

### Requirement: OSIV is disabled in mysql profile

El sistema DEBE verificar que `application-mysql.yml` tiene `spring.jpa.open-in-view: false`.
Este ajuste hace que `LazyInitializationException` sea detectable en el perfil mysql,
forzando que los smoke tests sean significativos.

#### Scenario: OSIV desactivado en mysql

- GIVEN `application-mysql.yml` con `open-in-view: false`
- WHEN una relación LAZY se accede fuera de transacción en el perfil mysql
- THEN se lanza `LazyInitializationException` (el error es visible, no enmascarado)

---

## Archive Metadata

- **Implemented in change**: `backend-hardening-high-issues`
- **Commits**: `55391cf` (fetch LAZY explícito), `ba9bbe6` (@Transactional readOnly + JOIN FETCH)
- **Canonical since**: 2026-05-09
- **Verified**: 10/10 scenarios PASS (smoke matrix 3 roles, 0 LazyInitializationException)
