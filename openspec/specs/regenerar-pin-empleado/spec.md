# Regenerar PIN de Empleado Specification

## Purpose

Acción dedicada para que un ADMIN o ENCARGADO autenticado regenere el PIN de terminal
de un empleado. El servidor genera el nuevo PIN de forma segura y lo devuelve UNA única vez.

## Requirements

### Requirement: Regenerar PIN vía endpoint dedicado (E65)

El sistema MUST exponer `POST /api/v1/empleados/{id}/regenerar-pin` (E65).
Solo roles ADMIN y ENCARGADO MUST poder invocarlo.
El servidor MUST generar un PIN de 4 dígitos único vía `generarPinUnico()`,
persistirlo en BD y devolverlo UNA sola vez en la respuesta.

#### Scenario: Happy path ADMIN

- GIVEN un token JWT válido con rol `ADMIN`
- WHEN `POST /api/v1/empleados/{id}/regenerar-pin` con un `{id}` existente
- THEN HTTP 200 con body `{ "empleadoId": {id}, "pinTerminal": "<4 dígitos>" }`
- AND `pinTerminal` es una cadena de exactamente 4 caracteres numéricos

#### Scenario: Happy path ENCARGADO

- GIVEN un token JWT válido con rol `ENCARGADO`
- WHEN `POST /api/v1/empleados/{id}/regenerar-pin` con un `{id}` existente
- THEN HTTP 200 con body `{ "empleadoId": {id}, "pinTerminal": "<4 dígitos>" }`

#### Scenario: PIN nuevo persistido en BD

- GIVEN un empleado con PIN previo conocido
- WHEN `POST /api/v1/empleados/{id}/regenerar-pin` se ejecuta con éxito
- THEN la columna `pin_terminal` del empleado en BD coincide con el valor devuelto en la respuesta

#### Scenario: PIN generado mediante generarPinUnico

- GIVEN una llamada al endpoint E65
- WHEN el servicio procesa la petición
- THEN `EmpleadoService.generarPinUnico()` es invocado exactamente una vez
- AND el resultado de esa invocación es el valor persistido y devuelto

#### Scenario: PIN nuevo es único entre todos los empleados

- GIVEN el estado actual de la BD con N empleados activos
- WHEN se persiste el nuevo PIN para el empleado `{id}`
- THEN el `pinTerminal` almacenado no coincide con el de ningún otro empleado

#### Scenario: Forbidden — rol EMPLEADO

- GIVEN un token JWT válido con rol `EMPLEADO`
- WHEN `POST /api/v1/empleados/{id}/regenerar-pin`
- THEN HTTP 403

#### Scenario: Unauthorized — sin token

- GIVEN una petición sin cabecera `Authorization`
- WHEN `POST /api/v1/empleados/{id}/regenerar-pin`
- THEN HTTP 401

#### Scenario: Not found — empleado inexistente

- GIVEN un token JWT válido con rol `ADMIN`
- WHEN `POST /api/v1/empleados/99999/regenerar-pin` con un `{id}` que no existe en BD
- THEN HTTP 404
- AND la excepción lanzada es `NotFoundException` (nunca `IllegalStateException`)

---

## Archive Metadata

- **Implemented in change**: `regenerar-pin-empleado`
- **Commits**: `532320a` (feat E65 + remove pinTerminal from PATCH E16), `f21e4a5` (docs README), `3768bc5` (docs javadocs sync)
- **Canonical since**: 2026-05-10
- **Verified**: 8 escenarios cubiertos — 5 PASS en gate (EmpleadoServiceTest), 3 EXCLUDED-by-policy (EmpleadoControllerTest — @WebMvcTest pre-broken context)
- **Gate**: PASS WITH WARNINGS (48/48, 0 CRITICAL)
