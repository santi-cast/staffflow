package com.staffflow.controller;

import com.staffflow.dto.request.UsuarioPatchRequest;
import com.staffflow.dto.request.UsuarioRequest;
import com.staffflow.dto.response.MensajeResponse;
import com.staffflow.dto.response.UsuarioResponse;
import com.staffflow.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller REST para la gestión de usuarios del sistema.
 *
 * Cubre los endpoints E08-E12 del Grupo 3 (Gestión de Usuarios).
 * Ruta base: /api/v1/usuarios
 *
 * Todos los endpoints de este controller son exclusivos del rol ADMIN.
 * ENCARGADO y EMPLEADO reciben HTTP 403 en cualquier intento de acceso.
 *
 * Responsabilidad única (SRP, decisión D del documento de endpoints):
 *   - Recibe la petición HTTP y extrae parámetros.
 *   - Valida el body con @Valid (Bean Validation — HTTP 400 automático).
 *   - Delega toda la lógica en UsuarioService.
 *   - Devuelve la respuesta con el código HTTP correcto.
 *   - Sin lógica de negocio en ningún método (RNF-M01).
 *
 * RF cubiertos: RF-03, RF-04, RF-05, RF-06, RF-07.
 */
@RestController
@RequestMapping("/api/v1/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    // ----------------------------------------------------------------
    // E08 — POST /api/v1/usuarios
    // RF-03: Crear usuario
    // ----------------------------------------------------------------

    /**
     * Crea un nuevo usuario en el sistema.
     *
     * El body se valida con @Valid antes de llegar al service.
     * La respuesta incluye los datos del usuario creado sin password_hash.
     *
     * Códigos HTTP:
     *   201 Created      → usuario creado correctamente
     *   400 Bad Request  → datos de entrada inválidos (Bean Validation)
     *   403 Forbidden    → rol insuficiente
     *   409 Conflict     → username o email ya existen
     *
     * @param request body JSON con username, email, password y rol
     * @return 201 Created con UsuarioResponse
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UsuarioResponse> crear(@Valid @RequestBody UsuarioRequest request) {
        UsuarioResponse response = usuarioService.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ----------------------------------------------------------------
    // E09 — GET /api/v1/usuarios
    // RF-04: Listar usuarios con filtros opcionales
    // ----------------------------------------------------------------

    /**
     * Lista todos los usuarios del sistema con filtros opcionales y combinables.
     *
     * Sin query params devuelve todos los usuarios (activos e inactivos).
     * Los parámetros son opcionales y combinables:
     *   ?rol=ADMIN|ENCARGADO|EMPLEADO
     *   ?activo=true|false
     *   ?rol=ENCARGADO&activo=true  (ambos simultáneamente)
     *
     * Códigos HTTP:
     *   200 OK          → lista devuelta (puede ser lista vacía [])
     *   403 Forbidden   → rol insuficiente
     *
     * @param rol    filtro por rol — opcional
     * @param activo filtro por estado activo/inactivo — opcional
     * @return 200 OK con lista de UsuarioResponse
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UsuarioResponse>> listar(
            @RequestParam(required = false) String rol,
            @RequestParam(required = false) Boolean activo) {
        return ResponseEntity.ok(usuarioService.listar(rol, activo));
    }

    // ----------------------------------------------------------------
    // E10 — GET /api/v1/usuarios/{id}
    // RF-05: Consultar detalle de usuario
    // ----------------------------------------------------------------

    /**
     * Devuelve el detalle completo de un usuario concreto.
     *
     * No incluye password_hash en la respuesta (RNF-S01).
     *
     * Códigos HTTP:
     *   200 OK          → usuario encontrado y devuelto
     *   403 Forbidden   → rol insuficiente
     *   404 Not Found   → usuario con el id indicado no existe
     *
     * @param id ID del usuario (path variable)
     * @return 200 OK con UsuarioResponse
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UsuarioResponse> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioService.obtenerPorId(id));
    }

    // ----------------------------------------------------------------
    // E11 — PATCH /api/v1/usuarios/{id}
    // RF-06: Editar usuario
    // ----------------------------------------------------------------

    /**
     * Actualiza los datos editables de un usuario: username, email y rol.
     *
     * La contraseña y el campo activo no se modifican por este endpoint.
     * Solo actualiza los campos enviados con valor no nulo (PATCH semántico).
     *
     * Códigos HTTP:
     *   200 OK          → usuario actualizado correctamente
     *   400 Bad Request → datos de entrada inválidos
     *   403 Forbidden   → rol insuficiente
     *   404 Not Found   → usuario no encontrado
     *   409 Conflict    → username o email ya existen en otro usuario
     *
     * @param id      ID del usuario a actualizar (path variable)
     * @param request body JSON con los campos a actualizar (todos opcionales)
     * @return 200 OK con UsuarioResponse actualizado
     */
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UsuarioResponse> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody UsuarioPatchRequest request) {
        return ResponseEntity.ok(usuarioService.actualizar(id, request));
    }

    // ----------------------------------------------------------------
    // E12 — DELETE /api/v1/usuarios/{id}
    // RF-07: Desactivar usuario (baja lógica)
    // ----------------------------------------------------------------

    /**
     * Desactiva un usuario aplicando baja lógica (activo = false).
     *
     * No ejecuta SQL DELETE. El registro permanece en BD con activo=false.
     * El historial de auditoría queda intacto (decisión nº4).
     * El usuario desactivado no puede hacer login.
     *
     * Nota: el verbo HTTP es DELETE pero la semántica es baja lógica,
     * no eliminación física. La respuesta es 200 OK con mensaje de
     * confirmación (no 204 No Content) para que Android pueda mostrar
     * el mensaje al usuario. El único 204 del sistema es E32 (ausencias).
     *
     * Códigos HTTP:
     *   200 OK          → usuario desactivado correctamente
     *   403 Forbidden   → rol insuficiente
     *   404 Not Found   → usuario no encontrado
     *
     * @param id ID del usuario a desactivar (path variable)
     * @return 200 OK con MensajeResponse
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MensajeResponse> desactivar(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioService.desactivar(id));
    }
}
