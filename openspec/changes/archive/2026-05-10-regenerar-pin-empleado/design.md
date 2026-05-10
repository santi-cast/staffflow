# Design: Regenerar PIN de Empleado

## Technical Approach

Endpoint dedicado `POST /api/v1/empleados/{id}/regenerar-pin` (E65) que delega en `EmpleadoService.regenerarPin(Long id)`. El service reutiliza la utilidad privada `generarPinUnico()` ya existente, persiste el nuevo PIN en `empleados.pin_terminal` y devuelve un DTO `RegenerarPinResponse` con `empleadoId` y `pinTerminal`. Mismo commit elimina el campo `pinTerminal` del DTO de PATCH (E16) y su bloque en `actualizar()`. Sin migración BD.

## Architecture Decisions

### 1. Endpoint dedicado vs flag en PATCH E16

**Choice**: Endpoint POST dedicado.
**Alternatives considered**: Flag `regenerarPin: true` en PATCH (E16).
**Rationale**: La acción genera un secreto irrecuperable que solo se devuelve UNA vez — semántica RPC, no actualización parcial. PATCH con flag mezcla side-effect crítico con CRUD genérico y obliga a leer el body para saber si hubo regeneración. POST dedicado es testeable, auditable y autodocumentado.

### 2. Reuso de `generarPinUnico()` privado

**Choice**: Mantener `generarPinUnico()` como `private`. El nuevo método `regenerarPin()` vive en el MISMO `EmpleadoService` → puede invocarlo sin cambiar visibilidad.
**Alternatives considered**: Subir a `package-private` o extraer a un componente `PinGenerator`.
**Rationale**: Verificado en `EmpleadoService.java:500` — el método ya está en la clase que necesitamos. No hay justificación para ampliar visibilidad ni extraer (YAGNI). Mantener encapsulación.

### 3. Excepción para empleado inexistente → 404

**Choice**: `NotFoundException` (mapeado a 404 por `GlobalExceptionHandler`).
**Alternatives considered**: `IllegalStateException` (sería 5xx).
**Rationale**: AGENTS.md y patrón existente en `EmpleadoService` (líneas 111, 248, 288, 351, 374, 480, 484) usan `NotFoundException` con `.orElseThrow()` sobre `findById()`. Consistencia.

### 4. Capability assignment de E16 → `security-authorization`

**Choice**: El delta de E16 (eliminar `pinTerminal` del PATCH) vive en la spec de `security-authorization`.
**Alternatives considered**: Crear capability nueva `empleados-administracion`.
**Rationale**: Decisión cerrada con el usuario. RIESGO: el cambio no es estrictamente de "autorización" sino de superficie de input — futuro revisor podría no encontrarlo. Anotado para archive.

### 5. DTO sin campo `mensaje`

**Choice**: `RegenerarPinResponse` con SOLO `empleadoId` y `pinTerminal`.
**Alternatives considered**: Incluir `mensaje: String` (como sugería el proposal).
**Rationale**: Decisión cerrada con el usuario. Wire mínima, sin texto de UI mezclado en el contract de API. El proposal está desactualizado en este punto — la decisión cerrada manda.

## Data Flow

    Client (ADMIN/ENCARGADO)
         │ POST /api/v1/empleados/{id}/regenerar-pin
         ▼
    EmpleadoController.regenerarPin(id)         ← @PreAuthorize roles
         │
         ▼
    EmpleadoService.regenerarPin(id)            ← @Transactional
         │
         ├─ empleadoRepository.findById(id)     → orElseThrow NotFoundException
         ├─ generarPinUnico()                   → loops hasta no-collision
         ├─ empleado.setPinTerminal(nuevo)
         ├─ empleadoRepository.save(empleado)
         │
         ▼
    RegenerarPinResponse { empleadoId, pinTerminal }

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `staffflow-backend/src/main/java/com/staffflow/controller/EmpleadoController.java` | Modify | Añadir `regenerarPin(Long id)` con `@PostMapping("/{id}/regenerar-pin")` y `@PreAuthorize("hasAnyRole('ADMIN','ENCARGADO')")`. Limpiar Javadoc de E16 (quitar mención a `pinTerminal`). |
| `staffflow-backend/src/main/java/com/staffflow/service/EmpleadoService.java` | Modify | Añadir método `regenerarPin(Long id)`. Eliminar bloque `if (request.getPinTerminal() != null)` líneas 318-324 dentro de `actualizar()`. Limpiar Javadoc relacionado. |
| `staffflow-backend/src/main/java/com/staffflow/dto/request/EmpleadoPatchRequest.java` | Modify | Eliminar campo `pinTerminal` (línea 50) y cualquier validación/Javadoc asociado. |
| `staffflow-backend/src/main/java/com/staffflow/dto/response/RegenerarPinResponse.java` | **New** | DTO con `empleadoId` y `pinTerminal`. Lombok + Javadoc → E65. |
| `staffflow-backend/src/test/java/com/staffflow/service/EmpleadoServiceTest.java` | **New** | Tests unitarios de `regenerarPin()` con Mockito. |
| `staffflow-backend/src/test/java/com/staffflow/controller/EmpleadoControllerTest.java` | **New** | `@WebMvcTest` slice: 200 ADMIN, 200 ENCARGADO, 403 EMPLEADO, 401 sin auth, 404 id inexistente. |
| `README.md` | Modify | Añadir fila E65 en sección de empleados (tras E21). Anotar en E16 que ya no acepta `pinTerminal`. |

## Interfaces / Contracts

### Endpoint E65

| Aspecto | Valor |
|---------|-------|
| Método | POST |
| Path | `/api/v1/empleados/{id}/regenerar-pin` |
| Path params | `id: Long` |
| Request body | (ninguno) |
| Auth | JWT Bearer obligatorio. Cae bajo cadena JWT (Order=2). |
| Roles | `ADMIN` o `ENCARGADO` (`@PreAuthorize` a nivel método) |
| 200 | `RegenerarPinResponse { empleadoId: Long, pinTerminal: String(4 dígitos) }` |
| 401 | Filter chain JWT (sin token o token inválido) |
| 403 | `@PreAuthorize` (rol EMPLEADO) |
| 404 | `NotFoundException` → `GlobalExceptionHandler` |

### Service signature

```java
@Transactional
public RegenerarPinResponse regenerarPin(Long id)
```

Algoritmo:
1. `empleadoRepository.findById(id).orElseThrow(() -> new NotFoundException("Empleado no encontrado: " + id))`.
2. `String nuevoPin = generarPinUnico();`
3. `empleado.setPinTerminal(nuevoPin);`
4. `empleadoRepository.save(empleado);`
5. `return new RegenerarPinResponse(empleado.getId(), nuevoPin);`

### DTO `RegenerarPinResponse`

- Paquete: `com.staffflow.dto.response`
- Anotaciones: `@Getter @Setter @NoArgsConstructor @AllArgsConstructor`
- Campos: `private Long empleadoId;`, `private String pinTerminal;`
- Javadoc: "Respuesta del endpoint E65 — POST /api/v1/empleados/{id}/regenerar-pin. Devuelve UNA SOLA VEZ el PIN regenerado."

## Testing Strategy

### EmpleadoServiceTest (unit, Mockito)

| Test method | Stubs/mocks | Assert clave |
|-------------|-------------|--------------|
| `regenerarPin_actualizaYDevuelvePinNuevo()` | `empleadoRepository.findById(1L)` → empleado; `existsByPinTerminal(...)` → false; `save(...)` echo | Captor sobre `save()` confirma `pin_terminal` cambió y coincide con el response.pinTerminal; cubre escenarios "Happy path ADMIN/ENCARGADO" + "PIN persistido" + "PIN generado mediante generarPinUnico" + "único entre empleados" |
| `regenerarPin_idInexistente_lanzaNotFoundException()` | `empleadoRepository.findById(99999L)` → `Optional.empty()` | `assertThatThrownBy(...).isInstanceOf(NotFoundException.class)`; cubre "Not found — empleado inexistente" |
| `regenerarPin_pinDevueltoTieneCuatroDigitos()` | igual al primero | `assertThat(response.getPinTerminal()).matches("\\d{4}")`; cubre "exactamente 4 caracteres numéricos" |

Convención del proyecto verificada en `SaldoServiceTest`: `@ExtendWith(MockitoExtension.class)`, `@DisplayName`, `@InjectMocks`, AssertJ + Mockito static imports, snake_case con underscores en method names.

### EmpleadoControllerTest (slice, `@WebMvcTest`)

| Test method | Setup | Assert clave |
|-------------|-------|--------------|
| `regenerarPin_admin_devuelve200()` | `@WithMockUser(roles="ADMIN")`; `EmpleadoService.regenerarPin(1L)` mock → response | status 200, body json `empleadoId` + `pinTerminal` | Cubre "Happy path ADMIN" |
| `regenerarPin_encargado_devuelve200()` | `@WithMockUser(roles="ENCARGADO")` | status 200 | Cubre "Happy path ENCARGADO" |
| `regenerarPin_empleado_devuelve403()` | `@WithMockUser(roles="EMPLEADO")` | status 403 | Cubre "Forbidden — rol EMPLEADO" |
| `regenerarPin_sinAuth_devuelve401()` | sin `@WithMockUser` | status 401 | Cubre "Unauthorized — sin token" |
| `regenerarPin_idInexistente_devuelve404()` | `@WithMockUser(roles="ADMIN")`; service lanza `NotFoundException` | status 404 | Cubre "Not found — empleado inexistente" |

### Delta E16 (cobertura ya existente o nueva)

Spec `security-authorization` exige 2 escenarios sobre PATCH ignorando `pinTerminal`. Se cubren mediante:
- Test ya existente o nuevo en `EmpleadoControllerTest`: PATCH con `{ "nombre":"Foo", "pinTerminal":"9999" }` → assert `nombre` actualizado y `service.actualizar()` recibe DTO sin campo `pinTerminal` (Jackson lo ignoró).
- Test nuevo: PATCH sin `pinTerminal` sigue funcionando (regression).

## Migration / Rollout

No migration required. Schema sin cambios (`pin_terminal CHAR(4) NOT NULL UNIQUE` ya existente). Rollback: `git revert` del commit de apply.

## README plan

Editar `README.md`:
1. **Insertar fila E65** entre E20 y E21 (orden numérico no estricto, pero agruparlo con empleados):
   `| E65 | POST /{id}/regenerar-pin | ADMIN, ENCARGADO | Regenera el PIN de terminal del empleado y lo devuelve UNA sola vez | P15, P29 |`
2. **Modificar fila E16** (línea 197): añadir nota al final de la descripción → "Actualiza campos parciales del empleado (PIN de terminal NO se modifica aquí — usar E65)".
3. Verificar que la descripción de E13 ("Genera PIN único y número de empleado automáticos") sigue válida — sí, aplica a creación.

## Risks identified during design

1. **Capability mismatch (E16 en `security-authorization`)**: el cambio no es de autorización sino de surface de input. Decisión del usuario; anotar en archive como nota.
2. **Proposal vs decisión sobre `mensaje`**: el proposal menciona campo `mensaje` en el DTO pero la decisión cerrada lo elimina. Implementación SIGUE la decisión, no el proposal. Sin acción adicional.
3. **`@WebMvcTest` pre-existente roto** (mencionado en proposal): la fase tasks debe decidir si `EmpleadoControllerTest` se excluye del gate o si se configura context mínimo.

## Open Questions

- [ ] ¿El test `EmpleadoControllerTest` debe excluirse del gate de verify igual que `GlobalExceptionHandlerTest`, o se invierte tiempo en arreglar el contexto? → Resolver en sdd-tasks.
