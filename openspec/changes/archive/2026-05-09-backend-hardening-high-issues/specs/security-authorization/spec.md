# Security Authorization Specification

## Purpose

Activar `@EnableMethodSecurity` en `SecurityConfig` para que las 56 anotaciones
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

Inconsistencias conocidas a resolver ANTES del merge (identificadas de la auditoría):
- `GET /api/v1/empleados/me` → URL-rule: `hasAnyRole('EMPLEADO','ENCARGADO')` |
  `@PreAuthorize` en `EmpleadoController`: `hasRole('EMPLEADO')` → inconsistencia:
  ENCARGADO puede pasar la URL-rule pero falla en `@PreAuthorize`. RESOLUCIÓN: cambiar
  `@PreAuthorize` a `hasAnyRole('EMPLEADO','ENCARGADO')` (más restrictivo = más correcto
  respecto al negocio si ENCARGADO también necesita su propio perfil).
  **⚠ Requiere confirmación del business intent antes de implementar.**

Regla de resolución general:
1. Identificar el rol mínimo necesario según el documento de requisitos funcionales.
2. Aplicar el más restrictivo SOLO si coincide con la intención de negocio documentada.
3. Si la intención no es clara, marcar como "ambigüedad pendiente" y NO modificar hasta
   confirmación del propietario del producto.

#### Scenario: auditoría de los 56 @PreAuthorize

- GIVEN los 56 `@PreAuthorize` en controllers del proyecto
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

Tests existentes a verificar:
- `SaldoServiceTest` — unit, sin contexto de seguridad: DEBE pasar sin cambios
- `JwtTokenProviderTest` — unit: DEBE pasar sin cambios
- `TerminalServiceTest` — unit: DEBE pasar sin cambios

#### Scenario: suite de unit tests verde

- GIVEN `@EnableMethodSecurity` en `SecurityConfig`
- WHEN se ejecuta `./mvnw test -Dtest='SaldoServiceTest,JwtTokenProviderTest,TerminalServiceTest'`
- THEN el resultado es 0 fallos nuevos

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
