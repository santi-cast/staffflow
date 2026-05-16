package com.staffflow.controller;

import com.staffflow.dto.request.EmpresaRequest;
import com.staffflow.dto.response.EmpresaResponse;
import com.staffflow.service.EmpresaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Controller de configuración de empresa.
 *
 * <p>Gestiona el singleton de configuración global de la empresa
 * (tabla configuracion_empresa, id=1). Solo accesible por el rol ADMIN.
 *
 * <p>Endpoints:
 *   E06 — GET  /api/v1/empresa  → obtener configuración actual
 *   E07 — PUT  /api/v1/empresa  → actualizar o crear configuración
 *
 * @author Santiago Castillo
 */
@RestController
@RequestMapping("/api/v1/empresa")
@RequiredArgsConstructor
@Tag(name = "Empresa", description = "Configuración global de la empresa (singleton)")
@SecurityRequirement(name = "bearerAuth")
public class EmpresaController {

    // EmpresaService contiene toda la lógica de negocio
    // El controller solo delega — nunca accede directamente a repositorios
    private final EmpresaService empresaService;

    // ─── E06 ─────────────────────────────────────────────────────────────────

    /**
     * Devuelve la configuración actual de la empresa (E06).
     *
     * <p>Solo el rol ADMIN puede consultar la configuración de empresa.
     * ENCARGADO y EMPLEADO reciben 403 Forbidden si intentan acceder.
     *
     * <p>El registro devuelto siempre tiene id=1 (singleton). Si el
     * sistema no ha sido configurado aún (el singleton no existe en
     * BD), el service lanza {@link com.staffflow.exception.NotFoundException}
     * y el cliente recibe 404 Not Found a través de
     * {@code GlobalExceptionHandler}.
     *
     * @return 200 OK con EmpresaResponse | 403 si rol insuficiente |
     *         404 si el singleton no existe todavía
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Obtener configuración de empresa",
            description = "Devuelve el registro singleton de configuración (id=1). Solo ADMIN."
    )
    public ResponseEntity<EmpresaResponse> obtenerEmpresa() {
        // Delega completamente en el servicio
        // El controller no toca repositorios ni entidades JPA directamente
        return ResponseEntity.ok(empresaService.obtenerEmpresa());
    }

    // ─── E07 ─────────────────────────────────────────────────────────────────

    /**
     * Actualiza o crea la configuración de la empresa (E07).
     *
     * <p>Opera siempre sobre id=1. Si el registro no existe (primera
     * configuración), EmpresaService lo crea. Todos los campos excepto
     * logoPath son obligatorios (@NotBlank en EmpresaRequest).
     *
     * <p>Solo el rol ADMIN puede modificar la configuración de empresa.
     *
     * @param request DTO con los nuevos datos validados por @Valid
     * @return 200 OK con EmpresaResponse actualizado | 400 si datos inválidos
     *         | 403 si rol insuficiente
     */
    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Actualizar configuración de empresa",
            description = "PUT completo sobre el singleton id=1. Si no existe, lo crea. Solo ADMIN."
    )
    public ResponseEntity<EmpresaResponse> actualizarEmpresa(
            @Valid @RequestBody EmpresaRequest request) {
        // @Valid activa las validaciones del DTO antes de llegar al servicio
        // Si hay campos @NotBlank vacíos, Spring devuelve 400 automáticamente
        return ResponseEntity.ok(empresaService.actualizarEmpresa(request));
    }
}
