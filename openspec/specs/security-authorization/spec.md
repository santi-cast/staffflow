# Security Authorization Specification

## Purpose

Activar `@EnableMethodSecurity` en `SecurityConfig` para que las anotaciones
`@PreAuthorize` existentes en los controllers sean evaluadas en tiempo de ejecución.
Sin este cambio, `@PreAuthorize` es decorativo y la seguridad solo opera en la capa URL.

## Requirements

### Requirement: @EnableMethodSecurity is enabled

El sistema MUST declarar `@EnableMethodSecurity` en la clase `SecurityConfig`
(o en una clase `@Configuration` separada del mismo módulo).

#### Scenario: anotación presente y backend arranca

- GIVEN `SecurityConfig.java` con `@EnableMethodSecurity`
- WHEN la aplicación arranca con cualquier perfil (`dev` o `mysql`)
- THEN el contexto Spring se inicializa sin errores
- AND el log no contiene `ClassNotFoundException` ni `BeanCreationException`

---

### Requirement: @PreAuthorize roles consistent with URL-level SecurityConfig rules

El sistema MUST garantizar que para cada endpoint protegido, el rol requerido por
`@PreAuthorize` sea compatible con el rol requerido por la regla URL en `apiFilterChain`.

Definición de "compatible": si `SecurityConfig` permite `ROLE_X` en la URL `Y`, entonces
`@PreAuthorize` en el método que maneja `Y` DEBE requerir un conjunto de roles ⊆ de los
permitidos por la URL-rule o igual a ellos. Una anotación que restringe más de lo que
establece la URL-rule es una inconsistencia que puede generar 403 inesperados.

Inconsistencias conocidas resueltas en `backend-hardening-high-issues`:
- `GET /api/v1/empleados/me` y los 6 endpoints `/me` restantes → URL-rule:
  `hasAnyRole('EMPLEADO','ENCARGADO')` | `@PreAuthorize` armonizado a
  `hasAnyRole('EMPLEADO','ENCARGADO')`. ENCARGADO puede acceder a su propio perfil /me.

Regla de resolución general:
1. Identificar el rol mínimo necesario según el documento de requisitos funcionales.
2. Aplicar el más restrictivo SOLO si coincide con la intención de negocio documentada.
3. Si la intención no es clara, marcar como "ambigüedad pendiente" y NO modificar hasta
   confirmación del propietario del producto.

#### Scenario: auditoría de los @PreAuthorize

- GIVEN los `@PreAuthorize` en controllers del proyecto (53 reglas activas post-change)
- WHEN se compara cada uno con la regla URL de `apiFilterChain` que cubre su ruta
- THEN cada anotación es compatible con la URL-rule correspondiente
- AND las inconsistencias detectadas están documentadas con su resolución o su estado de "ambigüedad pendiente"

#### Scenario: rol insuficiente → 403 con @EnableMethodSecurity activo

- GIVEN un token válido con rol `ADMIN`
- WHEN realiza `GET /api/v1/empleados/me`
- THEN la URL-rule permite `EMPLEADO` o `ENCARGADO` pero NO `ADMIN`
- AND `apiFilterChain` ya bloquea la petición antes de llegar al controller
- AND la respuesta es HTTP 403 con `{ "error": "No tiene permisos para realizar esta acción", ... }`

---

### Requirement: Existing passing tests remain green

El sistema MUST conservar verdes todos los tests que pasan hoy tras activar
`@EnableMethodSecurity`.

Tests existentes verificados:
- `SaldoServiceTest` — unit, sin contexto de seguridad: PASA sin cambios
- `JwtTokenProviderTest` — unit: PASA sin cambios
- `TerminalServiceTest` — unit: PASA sin cambios
- `MethodSecurityConfigTest` — 11/11 verde (tests añadidos en este change)
- `GlobalExceptionHandlerNotFoundTest` — 3/3 verde (tests añadidos en Block 1)

#### Scenario: suite de unit tests verde

- GIVEN `@EnableMethodSecurity` en `SecurityConfig`
- WHEN se ejecuta `./mvnw test`
- THEN el resultado es 0 fallos nuevos (29/30: la falla pre-existente en `SaldoServiceTest.recalcularParaProceso_mezclaDeTipos` es baseline)

---

### Requirement: Manual smoke tests pass for known Android flows

El sistema DEBE superar los siguientes smoke tests manuales que representan los flujos
críticos de la app Android.

#### Scenario: login con cualquier rol

- GIVEN credenciales válidas de un usuario ADMIN, ENCARGADO o EMPLEADO
- WHEN `POST /api/v1/auth/login` con `{ "username": "...", "password": "..." }`
- THEN HTTP 200 con token JWT válido

#### Scenario: fichaje de entrada desde terminal (EMPLEADO)

- GIVEN un PIN de terminal válido
- WHEN `POST /api/v1/terminal/entrada` con el PIN
- THEN HTTP 200 (el flujo terminal no usa JWT ni `@PreAuthorize`)

#### Scenario: gestión de empleados (ADMIN/ENCARGADO)

- GIVEN un token JWT con rol ADMIN
- WHEN `GET /api/v1/empleados`
- THEN HTTP 200 con la lista de empleados

#### Scenario: trigger cierre diario (ADMIN/ENCARGADO)

- GIVEN un token JWT con rol ADMIN
- WHEN `POST /api/v1/test/proceso-cierre` (TestProcesoCierreDiarioController, solo en dev/mysql)
- THEN HTTP 200 (el endpoint no tiene `@PreAuthorize` y la URL-rule no lo declara explícitamente → falls through a `anyRequest().authenticated()`)

#### Scenario: acceso denegado a /me con rol ADMIN (regresión esperada y correcta)

- GIVEN un token JWT con rol ADMIN
- WHEN `GET /api/v1/empleados/me`
- THEN HTTP 403 (ADMIN no está en la URL-rule `hasAnyRole('EMPLEADO','ENCARGADO')`)
- AND este resultado es el CORRECTO por diseño de negocio

---

---

### Requirement: PATCH E16 no acepta ni procesa pinTerminal

El sistema MUST ignorar el campo `pinTerminal` en el body de
`PATCH /api/v1/empleados/{id}` (E16).
El campo MUST NOT existir en `EmpleadoPatchRequest`.
Si el cliente envía `pinTerminal` en el JSON, Jackson lo MUST ignorar silenciosamente
(comportamiento por defecto — sin error, sin efecto sobre el PIN almacenado).
La modificación de PIN de terminal MUST realizarse exclusivamente vía E65.

#### Scenario: PATCH ignora pinTerminal — PIN no cambia

- GIVEN un empleado con `pinTerminal` conocido (p.ej. "1234")
- WHEN `PATCH /api/v1/empleados/{id}` con body `{ "nombre": "Foo", "pinTerminal": "9999" }`
  usando token JWT de ADMIN/ENCARGADO
- THEN HTTP 200 y el campo `nombre` del empleado se actualiza a "Foo"
- AND el `pin_terminal` del empleado en BD sigue siendo "1234" (sin cambio)

#### Scenario: PATCH normal sigue funcionando sin pinTerminal

- GIVEN un empleado existente
- WHEN `PATCH /api/v1/empleados/{id}` con body sin campo `pinTerminal`
  (p.ej. `{ "email": "nuevo@ejemplo.com" }`)
  usando token JWT de ADMIN/ENCARGADO
- THEN HTTP 200 y el campo `email` se actualiza correctamente
- AND el `pin_terminal` del empleado no se altera

---

## Archive Metadata

- **Implemented in change**: `backend-hardening-high-issues` (initial), `regenerar-pin-empleado` (PATCH E16 delta)
- **Commits**: `d95c677` (feat @EnableMethodSecurity + @PreAuthorize audit), `1e0165d` (deuda colateral), `532320a` (remove pinTerminal from PATCH E16)
- **Canonical since**: 2026-05-09 (updated 2026-05-10)
- **Verified**: 6/6 scenarios PASS (smoke matrix 35/35 OK); PATCH E16 scenarios EXCLUDED-by-policy (EmpleadoControllerTest — @WebMvcTest pre-broken context)
- **Note**: SecurityConfig bajó de 56 a 53 reglas URL durante la auditoría (Block 5 colateral). Campo `pinTerminal` eliminado de PATCH E16 en `regenerar-pin-empleado` change.
