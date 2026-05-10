# Proposal: Regenerar PIN de Empleado

## Intent

Un empleado puede sospechar que alguien vio su PIN de terminal. Hoy el único remedio es que un ADMIN/ENCARGADO edite manualmente el empleado vía PATCH (E16) enviando un nuevo PIN a ciegas — lo cual además dejará de funcionar una vez que limpiemos ese campo. El endpoint E65 provee una acción dedicada: el servidor genera un PIN único y seguro, y lo devuelve UNA vez para que el admin se lo entregue al empleado en mano.

## Scope

### In Scope

- `POST /api/v1/empleados/{id}/regenerar-pin` (E65) — acción autenticada (ADMIN, ENCARGADO)
- `RegenerarPinResponse` DTO nuevo con `empleadoId`, `pinTerminal`, `mensaje`
- Eliminar `pinTerminal` de `EmpleadoPatchRequest` (E16) y del bloque correspondiente en `EmpleadoService.actualizar()`
- Limpiar Javadoc de `EmpleadoController` (E16) y de `EmpleadoService.actualizar()` que mencionan PIN duplicado
- Tests: unitario de servicio + slice `@WebMvcTest` del controller (200, 403, 404)
- README sincronizado: E65 añadido, E16 anotado como "sin pinTerminal"

### Out of Scope

- Limpieza del contador de intentos fallidos (el contador vive en memoria en `TerminalService` por `dispositivoId`; desacoplado del empleado por Decisión nº16 — desbloqueo vía E54)
- Android / UI
- Auditoría / log de regeneraciones
- Cambio de PIN desde la terminal o iniciado por el propio empleado

## Capabilities

### New Capabilities

- `regenerar-pin-empleado`: endpoint POST que genera un PIN seguro server-side y lo devuelve una única vez (E65, excepción a D-018)

### Modified Capabilities

- `security-authorization`: E16 (PATCH empleado) deja de aceptar `pinTerminal` — el campo sale del DTO de request y del bloque de actualización en el servicio

## Approach

1. Añadir `POST /{id}/regenerar-pin` en `EmpleadoController` con `@PreAuthorize("hasAnyRole('ADMIN','ENCARGADO')")`.
2. Nuevo método `EmpleadoService.regenerarPin(Long id)`: llama a `generarPinUnico()` (ya existe, privado → sin cambio), persiste y devuelve `RegenerarPinResponse`.
3. Quitar campo `pinTerminal` de `EmpleadoPatchRequest` y el bloque `if (request.getPinTerminal() != null)` de `actualizar()` (líneas 318-324).
4. No hay cambio de esquema BD (`pin_terminal` ya es `NOT NULL UNIQUE CHAR(4)`).
5. Tests: `EmpleadoServiceTest` (mock repo) + `EmpleadoControllerTest` (@WebMvcTest).

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `staffflow-backend/src/main/java/com/staffflow/controller/EmpleadoController.java` | Modified | Nuevo método `regenerarPin()` (E65); limpiar Javadoc E16 |
| `staffflow-backend/src/main/java/com/staffflow/service/EmpleadoService.java` | Modified | Nuevo método `regenerarPin()`; eliminar bloque pinTerminal de `actualizar()` (líneas 318-324); limpiar Javadoc |
| `staffflow-backend/src/main/java/com/staffflow/dto/request/EmpleadoPatchRequest.java` | Modified | Eliminar campo `pinTerminal` (línea 50) |
| `staffflow-backend/src/main/java/com/staffflow/dto/response/RegenerarPinResponse.java` | **New** | DTO con `empleadoId`, `pinTerminal`, `mensaje` + Javadoc referenciando E65 |
| `staffflow-backend/src/test/java/com/staffflow/service/EmpleadoServiceTest.java` | **New** | Tests unitarios de `regenerarPin()` |
| `staffflow-backend/src/test/java/com/staffflow/controller/EmpleadoControllerTest.java` | **New** | @WebMvcTest slice: 200, 403, 404 |
| `README.md` | Modified | Añadir E65; anotar E16 sin pinTerminal |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| `@WebMvcTest` sigue roto por contexto sin BD (ya pre-existente) | Medium | Excluir del gate de verify igual que `GlobalExceptionHandlerTest`; o configurar test context mínimo en la fase de tasks |
| Cliente Android que hoy envíe `pinTerminal` en PATCH E16 empieza a recibir campo ignorado | Low | Campo se elimina del DTO; el JSON extra es ignorado por Jackson por defecto — no hay breaking change en la wire |

## Rollback Plan

Sin migración de BD: revertir con `git revert` del commit de apply. No hay datos persistidos nuevos que requieran limpieza.

## Dependencies

- Ninguna externa. `generarPinUnico()` ya existe en `EmpleadoService`.

## Success Criteria

- [ ] `POST /api/v1/empleados/{id}/regenerar-pin` devuelve 200 con `pinTerminal` de 4 dígitos para usuario ADMIN/ENCARGADO
- [ ] El PIN devuelto está efectivamente persistido en BD para el empleado
- [ ] `PATCH /api/v1/empleados/{id}` ya NO acepta ni procesa `pinTerminal`
- [ ] 403 si el rol es EMPLEADO; 404 si el `id` no existe
- [ ] Javadoc de E16 y `actualizar()` no menciona PIN duplicado
- [ ] README refleja E65 y el cambio en E16
