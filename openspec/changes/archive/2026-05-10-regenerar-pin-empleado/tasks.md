# Tasks: Regenerar PIN de Empleado

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | +/- 180-250 líneas |
| 400-line budget risk | Low |
| Chained PRs recommended | No |
| Suggested split | Single PR |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Low

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | DTO + service tests (red) + impl + cleanup E16 + controller + README | PR 1 | Cambio atómico; todo verde al merge |

---

## Phase 1: DTO (foundation)

- [x] 1.1 Crear `staffflow-backend/src/main/java/com/staffflow/dto/response/RegenerarPinResponse.java` con campos `Long empleadoId`, `String pinTerminal`, Lombok (`@Getter @Setter @NoArgsConstructor @AllArgsConstructor`) y Javadoc → E65. Sin campo `mensaje`.

## Phase 2: Service tests — RED (strict TDD)

- [x] 2.1 Crear `staffflow-backend/src/test/java/com/staffflow/service/EmpleadoServiceTest.java`. Añadir `regenerarPin_actualizaYDevuelvePinNuevo()`: stub `findById` → empleado existente, `existsByPinTerminal` → false, `save` → echo; usar `ArgumentCaptor` sobre `save()`; verificar que `pinTerminal` del empleado capturado == `pinTerminal` del response. Cubre: spec happy-path ADMIN/ENCARGADO + PIN persistido + PIN generado vía `generarPinUnico` + unicidad.
- [x] 2.2 En `EmpleadoServiceTest`, añadir `regenerarPin_idInexistente_lanzaNotFoundException()`: stub `findById(99999L)` → empty; assertThrows `NotFoundException`. Cubre: spec Not found.
- [x] 2.3 En `EmpleadoServiceTest`, añadir `regenerarPin_pinDevueltoTieneCuatroDigitos()`: mismos stubs que 2.1; assert `pinTerminal` matches `"\\d{4}"`. Cubre: spec "exactamente 4 caracteres numéricos".

> Ejecutar gate → los 3 tests deben fallar (rojo).

## Phase 3: Service implementation — GREEN

- [x] 3.1 En `staffflow-backend/src/main/java/com/staffflow/service/EmpleadoService.java`, añadir método `@Transactional public RegenerarPinResponse regenerarPin(Long id)`: `findById` → orElseThrow `NotFoundException`, `generarPinUnico()`, `setPinTerminal`, `save`, return `new RegenerarPinResponse(empleado.getId(), nuevoPin)`. Reutiliza método privado existente.

> Ejecutar gate → los 3 tests deben pasar (verde).

## Phase 4: Eliminar pinTerminal de PATCH E16

- [x] 4.1 En `staffflow-backend/src/main/java/com/staffflow/dto/request/EmpleadoPatchRequest.java`, eliminar campo `pinTerminal` (línea 50) y cualquier Javadoc o anotación de validación asociada.
- [x] 4.2 En `staffflow-backend/src/main/java/com/staffflow/service/EmpleadoService.java`, eliminar bloque `if (request.getPinTerminal() != null) { ... }` (líneas 318-324 inclusive) de `actualizar()`.
- [x] 4.3 En `EmpleadoServiceTest` (si existe algún test que enviaba `pinTerminal` en el payload de `actualizar()`): adaptar o eliminar el test para que no use el campo eliminado. Verificar que tests pre-existentes de `actualizar()` siguen verdes.

> Ejecutar gate → debe permanecer verde.

## Phase 5: Javadoc cleanup

- [x] 5.1 En `staffflow-backend/src/main/java/com/staffflow/service/EmpleadoService.java`, limpiar Javadoc de `actualizar()` (líneas ~269-283): eliminar "409 Conflict → PIN o NFC duplicados" y reemplazar por "409 Conflict → NFC duplicado en otro empleado".
- [x] 5.2 En `staffflow-backend/src/main/java/com/staffflow/controller/EmpleadoController.java`, limpiar Javadoc del método E16 (línea ~162): eliminar mención "409 Conflict → PIN, DNI o número de empleado duplicados" → reemplazar por "409 Conflict → NFC o DNI duplicados".

## Phase 6: Controller

- [x] 6.1 En `staffflow-backend/src/main/java/com/staffflow/controller/EmpleadoController.java`, añadir método `regenerarPin(@PathVariable Long id)` con `@PostMapping("/{id}/regenerar-pin")`, `@PreAuthorize("hasAnyRole('ADMIN','ENCARGADO')")`, retorna `ResponseEntity<RegenerarPinResponse>`. Javadoc → E65.
- [x] 6.2 Crear `staffflow-backend/src/test/java/com/staffflow/controller/EmpleadoControllerTest.java` con clase `@WebMvcTest(EmpleadoController.class)`. Añadir 5 tests (excluidos del gate, existen para futura activación):
  - `regenerarPin_admin_devuelve200()` — `@WithMockUser(roles="ADMIN")`, service mock devuelve response. Cubre: spec happy-path ADMIN.
  - `regenerarPin_encargado_devuelve200()` — `@WithMockUser(roles="ENCARGADO")`. Cubre: spec happy-path ENCARGADO.
  - `regenerarPin_empleado_devuelve403()` — `@WithMockUser(roles="EMPLEADO")`. Cubre: spec Forbidden.
  - `regenerarPin_sinAuth_devuelve401()` — sin `@WithMockUser`. Cubre: spec Unauthorized.
  - `regenerarPin_idInexistente_devuelve404()` — service lanza `NotFoundException`. Cubre: spec Not found.
  - Añadir 2 tests adicionales para delta E16 (`PATCH ignora pinTerminal — PIN no cambia`, `PATCH normal sigue funcionando sin pinTerminal`). Cubre: spec security-authorization.

  > `EmpleadoControllerTest` NO se incluye en el gate de verify (igual que `GlobalExceptionHandlerTest`). Exclusión documentada en commit message y verify report.

## Phase 7: README

- [x] 7.1 En `README.md`, insertar fila E65 en la sección empleados (entre E20 y E21): `| E65 | POST /{id}/regenerar-pin | ADMIN, ENCARGADO | Regenera el PIN de terminal del empleado y lo devuelve UNA sola vez | P15, P29 |`.
- [x] 7.2 En `README.md`, modificar fila E16: añadir al final de la descripción "(PIN de terminal NO se modifica aquí — usar E65)".
- [x] 7.3 Verificar que el conteo total de endpoints del README sigue siendo correcto tras insertar E65.

## Phase 8: Verificación local

- [x] 8.1 Ejecutar el gate completo (`./mvnw test -Dgroups=...` o comando de `sdd-init` engram_id 248, excluyendo `EmpleadoControllerTest` y `GlobalExceptionHandlerTest`). Confirmar verde.
- [x] 8.2 Confirmar que los 3 tests de `EmpleadoServiceTest.regenerarPin_*` pasan.
- [x] 8.3 Confirmar que tests pre-existentes de `EmpleadoService.actualizar()` siguen pasando.

---

## Commit Plan

**Recomendación: 2 commits.**

| # | Scope | Mensaje sugerido | Por qué |
|---|-------|-----------------|---------|
| 1 | DTO + service + E16 cleanup + controller + service tests + controller tests | `feat(empleados): add POST /{id}/regenerar-pin endpoint (E65) and remove pinTerminal from PATCH E16` | Núcleo funcional; todo verde al merge. |
| 2 | README | `docs(readme): add E65 endpoint and annotate E16 pin change` | Revisable independientemente; no afecta compilación ni tests. |

> **Nota sobre exclusión de `EmpleadoControllerTest`**: mencionar en el commit message del commit 1: "EmpleadoControllerTest excluido del gate por @WebMvcTest context pre-roto (mismo criterio que GlobalExceptionHandlerTest)".

---

## Risks

- Ninguno bloqueante. Todos los riesgos fueron resueltos en design/tasks prompt.
- Risk menor: si `EmpleadoServiceTest.java` ya existe con otros tests, las tareas 2.1-2.3 son "añadir tests" en archivo existente, no "crear archivo". El subagente de apply debe verificar antes de crear.
