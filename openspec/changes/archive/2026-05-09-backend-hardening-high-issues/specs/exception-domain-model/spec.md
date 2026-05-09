# Exception Domain Model Specification

## Purpose

Introduce `NotFoundException` como excepción de dominio estándar para los casos en que
un recurso solicitado no existe en la base de datos, eliminando el abuso de
`IllegalStateException` para semántica de "no encontrado".

## Requirements

### Requirement: NotFoundException class exists

El sistema MUST proporcionar una clase `NotFoundException` en el paquete
`com.staffflow.exception`, que extienda `RuntimeException` y acepte un mensaje String.

#### Scenario: clase creada correctamente

- GIVEN el paquete `com.staffflow.exception`
- WHEN se compila el proyecto
- THEN `NotFoundException` existe, extiende `RuntimeException` y tiene un constructor `(String message)`

---

### Requirement: GlobalExceptionHandler maps NotFoundException to HTTP 404

El sistema MUST manejar `NotFoundException` en `GlobalExceptionHandler` y devolver
HTTP 404 con el cuerpo `{ error, timestamp, path }` (formato contrato existente).

#### Scenario: recurso no encontrado devuelve 404

- GIVEN un endpoint que lanza `NotFoundException("Empleado no encontrado con id: 99")`
- WHEN el cliente realiza la petición
- THEN la respuesta HTTP es 404
- AND el cuerpo contiene `{ "error": "Empleado no encontrado con id: 99", "timestamp": ..., "path": ... }`

---

### Requirement: GlobalExceptionHandler no longer maps IllegalStateException to 404

El sistema MUST reasignar `IllegalStateException` de HTTP 404 a HTTP 500, o eliminar
su handler y dejar que caiga al manejador genérico (`Exception.class` → 500).

Rationale: `IllegalStateException` es una excepción estándar de Java que señala estado
inválido del objeto, no ausencia de recurso. Mapearla a 404 enmascara errores reales
de lógica interna. El remapeo a 500 hace visible el problema en logs sin exponer
detalles internos al cliente.

(Previously: `IllegalStateException` → HTTP 404, usado como señal de "not found".)

#### Scenario: ISE de estado inválido devuelve 500

- GIVEN una excepción `IllegalStateException("Error generando PDF: ...")` lanzada por `PdfService`
- WHEN el cliente recibe la respuesta
- THEN el código HTTP es 500
- AND el cuerpo contiene `{ "error": "Error interno del servidor", ... }` (mensaje genérico)

#### Scenario: ISE de duplicado devuelve 409

- GIVEN que `EmpleadoService.actualizar()` detecta PIN duplicado y lanza `ConflictException`
  (los dos casos de ISE en líneas 316 y 323 de EmpleadoService son semántica de conflicto,
  no de estado inválido — DEBEN migrarse a `ConflictException`, no a `NotFoundException`)
- WHEN el cliente intenta asignar el PIN duplicado
- THEN la respuesta HTTP es 409

---

### Requirement: Service code uses NotFoundException for entity-not-found cases

El sistema MUST reemplazar todos los usos de `IllegalStateException` en la capa de
servicio donde la semántica sea "entidad no encontrada en base de datos" (patrón
`orElseThrow(() -> new IllegalStateException(...))` con mensajes del tipo
"X no encontrado", "X no existe", "X no tiene perfil de empleado").

Los usos que NO son "not found" DEBEN tratarse según su semántica real:
- Error de generación de PDF (`PdfService`) → mantiene `IllegalStateException` o excepción runtime propia; no es un recurso ausente
- Registros singleton inexistentes (`EmpresaService`) → `NotFoundException` (la empresa no existe en BD)
- Datos de saldo inválidos (`SaldoService` líneas 340-344) → evaluar si es datos inconsistentes (500) o recurso ausente (404)

#### Scenario: service distinguishes not-found from invalid-state

- GIVEN `AusenciaService.obtenerPorId(99L)` donde el id no existe en BD
- WHEN el servicio ejecuta `ausenciaRepository.findById(99L).orElseThrow(...)`
- THEN lanza `NotFoundException("Ausencia no encontrada con id: 99")`
- AND el handler devuelve HTTP 404

- GIVEN `PdfService.generarInformePDF(...)` donde iText7 lanza una excepción interna
- WHEN el servicio captura la excepción y la re-lanza
- THEN lanza `IllegalStateException("Error generando el informe PDF: ...")` (sin cambio)
- AND el handler devuelve HTTP 500

- GIVEN `EmpleadoService.actualizar(id, request)` donde el PIN ya existe en otro registro
- WHEN el servicio detecta el duplicado
- THEN lanza `ConflictException("El PIN de terminal '1234' ya está registrado")`
- AND el handler devuelve HTTP 409

---

### Requirement: NotFoundException messages are in neutral Spanish

El sistema MUST usar español neutral (no argentino/rioplatense) en todos los mensajes
de `NotFoundException`.

#### Scenario: mensaje correcto

- GIVEN cualquier `NotFoundException` lanzada en la capa de servicio
- WHEN se inspecciona el mensaje
- THEN no contiene voseo ni expresiones regionales
- AND el mensaje describe el tipo de recurso y el criterio de búsqueda (ej. id, username)
