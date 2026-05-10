# Delta for security-authorization

## ADDED Requirements

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
