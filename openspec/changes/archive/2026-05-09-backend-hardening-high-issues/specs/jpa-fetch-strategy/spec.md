# JPA Fetch Strategy Specification

## Purpose

Declarar `fetch = FetchType.LAZY` explícito en los 8 `@ManyToOne` y `@OneToOne` de las
5 entidades de dominio que hoy usan el default EAGER de JPA, eliminando cargas en
cascada silenciosas que generan N+1 en cualquier listado.

## Requirements

### Requirement: All @ManyToOne and @OneToOne have explicit fetch = FetchType.LAZY

El sistema MUST declarar `fetch = FetchType.LAZY` explícito en las siguientes relaciones:

| Entidad | Relación | Campo | Línea aprox. |
|---------|----------|-------|--------------|
| `Fichaje` | `@ManyToOne` | `empleado` | 33 |
| `Fichaje` | `@ManyToOne` | `usuario` (auditor) | 62 |
| `Pausa` | `@ManyToOne` | `fichaje` | ~32 |
| `Pausa` | `@ManyToOne` | `empleado` | ~57 |
| `PlanificacionAusencia` | `@ManyToOne` | `empleado` | ~39 |
| `PlanificacionAusencia` | `@ManyToOne` | `creadoPor` (usuario) | ~58 |
| `SaldoAnual` | `@ManyToOne` | `empleado` | ~36 |
| `Empleado` | `@OneToOne` | `usuario` | ~28 |

#### Scenario: entidades compilan con LAZY explícito

- GIVEN cada una de las 5 entidades con `fetch = FetchType.LAZY` en sus relaciones
- WHEN se compila el proyecto
- THEN 0 errores de compilación relacionados con JPA

---

### Requirement: Read paths handle lazy loading correctly

Para cada relación convertida de EAGER a LAZY, el sistema MUST garantizar que todos los
paths de lectura que acceden a la relación en código fuera de la transacción de carga
usen `JOIN FETCH` o un `@EntityGraph`, o estén dentro del mismo contexto transaccional.

Verificación requerida por entidad:

**Fichaje.empleado / Fichaje.usuario:**
- `FichajeService.listar(...)` → `FichajeRepository.findByFiltros` ya usa `JOIN FETCH empleado`; verificar que también hace `JOIN FETCH usuario` o añadirlo.
- Respuestas DTO serializan solo campos escalares de `Empleado`: si el mapper accede a `fichaje.getEmpleado().getNombre()`, debe ejecutarse dentro de la transacción.

**Pausa.fichaje / Pausa.empleado:**
- `PausaService.listar(...)` → auditar que la consulta hace `JOIN FETCH` o que el mapper se ejecuta dentro de `@Transactional(readOnly=true)`.

**PlanificacionAusencia.empleado / PlanificacionAusencia.creadoPor:**
- `AusenciaService.listarPlanificaciones(...)` → auditar misma condición.

**SaldoAnual.empleado:**
- `SaldoService.listarTodos(...)` → auditar que la consulta incluye `JOIN FETCH empleado` o que el acceso ocurre en sesión abierta.

**Empleado.usuario:**
- `EmpleadoService.listar(...)` → si el DTO de respuesta incluye datos del usuario (username, email), la query DEBE hacer `JOIN FETCH usuario`.

Si un path de lectura accede a la relación fuera de sesión y NO tiene `JOIN FETCH`, el
implementador MUST añadir la cláusula al `@Query` correspondiente o usar un `@EntityGraph`
ANTES de marcar la tarea como completa.

Si la corrección es compleja o introduce riesgo, el implementador DEBE marcarla como
"⚠ Riesgo de LazyInitializationException" y documentarla para revisión antes del merge.

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
fuera de transacción Y la corrección con `JOIN FETCH` no es trivial (por ejemplo,
requiere reescribir una query nativa o cambiar un projection), el implementador DEBE:

1. Documentar el path en el PR description como "⚠ Acceso lazy sin JOIN FETCH corregido / pendiente".
2. Añadir un test unitario o de integración que cubra ese path.
3. No dejar `LazyInitializationException` silenciados con `try/catch`.

#### Scenario: riesgo de lazy documentado en PR

- GIVEN que `PdfService.generarInformePDF(empleadoId, anio)` accede a `empleado.getUsuario()` fuera de transacción
- WHEN el implementador detecta el riesgo
- THEN lo documenta en el PR description y añade `JOIN FETCH` en la consulta de carga del empleado
- OR decide cargar el usuario por separado dentro de la transacción del servicio

---

### Requirement: OSIV is disabled in mysql profile (already satisfied — verify only)

El sistema DEBE verificar que `application-mysql.yml` tiene `spring.jpa.open-in-view: false`
(ya presente según el archivo actual). Este ajuste hace que `LazyInitializationException`
sea detectable en el perfil mysql, forzando que los tests de smoke sean significativos.

#### Scenario: OSIV desactivado en mysql

- GIVEN `application-mysql.yml` con `open-in-view: false`
- WHEN una relación LAZY se accede fuera de transacción en el perfil mysql
- THEN se lanza `LazyInitializationException` (el error es visible, no enmascarado)
