package com.staffflow.exception;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manejador global de excepciones de la API REST.
 *
 * <p>Centraliza la conversión de excepciones Java en respuestas HTTP
 * con el formato de error definido en el contrato de la API:
 * { error, campo?, timestamp, path }
 *
 * <p>Sin este handler, Spring devolvería su formato de error por defecto
 * (Whitelabel Error Page o JSON genérico) que no sigue el contrato de
 * StaffFlow y expone información interna del servidor.
 *
 * <p>Alternativa descartada: manejar excepciones en cada controller.
 * Se rechazó porque duplica código y dispersa la lógica de error.
 * @ControllerAdvice centraliza todo en un solo punto.
 *
 * @author Santiago Castillo
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ─── 400 BAD REQUEST ─────────────────────────────────────────────────────

    /**
     * Maneja errores de validación de @Valid en los DTOs de request.
     *
     * <p>Spring lanza MethodArgumentNotValidException cuando un campo
     * @NotBlank, @Email, @Size, @NotNull, etc. no supera la validación.
     * Este handler recoge el primer error del primer campo fallido
     * y lo devuelve en el cuerpo de la respuesta.
     *
     * <p>El campo "campo" del body identifica qué atributo del DTO
     * falló, lo que permite al cliente Android mostrar el error
     * directamente en el campo correspondiente del formulario.
     *
     * @param ex excepción con la lista de errores de validación
     * @param request información de la petición HTTP
     * @return 400 con { error, campo, timestamp, path }
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(
            MethodArgumentNotValidException ex, WebRequest request) {

        String campo = ex.getBindingResult().getFieldErrors().isEmpty()
                ? "desconocido"
                : ex.getBindingResult().getFieldErrors().get(0).getField();

        String mensaje = ex.getBindingResult().getFieldErrors().isEmpty()
                ? "Datos de entrada inválidos"
                : ex.getBindingResult().getFieldErrors().get(0).getDefaultMessage();

        log.warn("Validacion fallida en campo '{}': {}", campo, mensaje);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildError("Validación fallida: " + mensaje, campo, request));
    }

    /**
     * Maneja errores de negocio que resultan en datos inválidos (400).
     *
     * <p>IllegalArgumentException se usa en los servicios para señalar
     * condiciones de error controladas: contraseña incorrecta (E03),
     * token de recuperación inválido o expirado (E05), etc.
     *
     * @param ex excepción con el mensaje descriptivo del error
     * @param request información de la petición HTTP
     * @return 400 con { error, timestamp, path }
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException ex, WebRequest request) {

        log.warn("Argumento ilegal: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildError(ex.getMessage(), null, request));
    }

    /**
     * Maneja violaciones de constraints de integridad de BD (400).
     *
     * <p>DataIntegrityViolationException la lanza Hibernate cuando se
     * viola una constraint UNIQUE o NOT NULL en la BD sin que el servicio
     * lo haya detectado antes. El mensaje genérico evita exponer nombres
     * de constraints internas de la BD al cliente.
     *
     * @param ex excepción de integridad de datos
     * @param request información de la petición HTTP
     * @return 400 con { error, timestamp, path }
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(
            DataIntegrityViolationException ex, WebRequest request) {

        log.warn("Violacion integridad BD: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildError("El dato enviado viola una restricción de unicidad", null, request));
    }

    // ─── 401 UNAUTHORIZED ────────────────────────────────────────────────────

    /**
     * Maneja credenciales incorrectas en el login (401).
     *
     * <p>El mensaje es genérico intencionadamente: no revela si fue
     * el username o la contraseña lo incorrecto (RNF-S04).
     *
     * @param ex excepción de credenciales inválidas
     * @param request información de la petición HTTP
     * @return 401 con { error, timestamp, path }
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(
            BadCredentialsException ex, WebRequest request) {

        log.warn("Intento de login fallido: credenciales incorrectas");

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(buildError("Credenciales incorrectas", null, request));
    }

    // ─── 403 FORBIDDEN ───────────────────────────────────────────────────────

    /**
     * Maneja accesos denegados por rol insuficiente (403).
     *
     * <p>AccessDeniedException la lanza Spring Security cuando un usuario
     * autenticado intenta acceder a un endpoint para el que no tiene
     * el rol requerido (@PreAuthorize).
     *
     * @param ex excepción de acceso denegado
     * @param request información de la petición HTTP
     * @return 403 con { error, timestamp, path }
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(
            AccessDeniedException ex, WebRequest request) {

        log.warn("Acceso denegado: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(buildError("No tiene permisos para realizar esta acción", null, request));
    }

    // ─── 404 NOT FOUND ───────────────────────────────────────────────────────

    /**
     * Maneja recursos no encontrados en la base de datos (404).
     *
     * <p>{@link NotFoundException} es la excepción de dominio estándar para
     * señalar que el recurso solicitado no existe. Sustituye el uso incorrecto
     * de {@link IllegalStateException} como señal de "not found".
     *
     * <p>El mensaje de la excepción describe el tipo de recurso y el criterio
     * de búsqueda (ej: "Empleado no encontrado con id: 42") y se devuelve
     * directamente al cliente en el campo "error" del cuerpo de la respuesta.
     *
     * @param ex excepción con el mensaje descriptivo del recurso ausente
     * @param request información de la petición HTTP
     * @return 404 con { error, timestamp, path }
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(
            NotFoundException ex, WebRequest request) {

        log.warn("Recurso no encontrado: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildError(ex.getMessage(), null, request));
    }

    /**
     * Maneja EntityNotFoundException de JPA (404).
     *
     * <p>Nota: el handler de {@link IllegalStateException} fue eliminado en
     * la tarea de hardening ISE-01 (SDD backend-hardening-high-issues).
     * Las 9 ISE intencionales que permanecen en el código (7 en PdfService
     * por errores de iText7, 2 en SaldoService por años fuera del rango
     * valido del empleado) caen al manejador genérico
     * {@link #handleGenericException} → HTTP 500, lo que es la semántica
     * correcta para errores de estado interno.</p>
     *
     * <p>jakarta.persistence.EntityNotFoundException la lanza TerminalService
     * cuando el PIN introducido no corresponde a ningun empleado registrado.
     * Se mapea a 404 para que el terminal pueda distinguir PIN incorrecto
     * de bloqueo (423) o error interno (500).
     *
     * @param ex excepcion con el mensaje descriptivo
     * @param request informacion de la peticion HTTP
     * @return 404 con { error, timestamp, path }
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleEntityNotFound(
            EntityNotFoundException ex, WebRequest request) {

        log.warn("Entidad no encontrada: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildError(ex.getMessage(), null, request));
    }

    // ─── 409 CONFLICT ────────────────────────────────────────────────────────

    /**
     * Maneja conflictos de rango de ausencias (409).
     *
     * <p>RangoConflictException la lanza AusenciaService.crearRango() cuando
     * hay ausencias ya planificadas (procesado=false) en algún día del rango
     * y sobrescribir=false. Devuelve la lista de fechas conflictivas para
     * que Android muestre un AlertDialog de confirmación.</p>
     *
     * @param ex excepción con la lista de fechas conflictivas
     * @param request información de la petición HTTP
     * @return 409 con { error, fechasConflictivas, timestamp, path }
     */
    @ExceptionHandler(RangoConflictException.class)
    public ResponseEntity<Map<String, Object>> handleRangoConflict(
            RangoConflictException ex, WebRequest request) {

        log.warn("Conflicto de rango ausencias: {}", ex.getFechasConflictivas());

        Map<String, Object> body = buildError(ex.getMessage(), null, request);
        body.put("fechasConflictivas", ex.getFechasConflictivas());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    /**
     * Maneja conflictos de unicidad en datos del dominio (409).
     *
     * <p>ConflictException la lanzan los servicios cuando detectan
     * de forma preventiva que un dato ya existe en otro registro:
     * username, email, DNI, PIN, CIF, etc.
     *
     * <p>Se usa validación preventiva en el servicio (existsBy...)
     * en lugar de dejar explotar DataIntegrityViolationException,
     * lo que permite devolver un mensaje claro al cliente Android
     * con el dato concreto que genera el conflicto.
     *
     * @param ex excepción con el mensaje descriptivo del conflicto
     * @param request información de la petición HTTP
     * @return 409 con { error, timestamp, path }
     */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(
            ConflictException ex, WebRequest request) {

        log.warn("Conflicto de unicidad: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(buildError(ex.getMessage(), null, request));
    }

    // ─── 423 LOCKED ──────────────────────────────────────────────────────────

    /**
     * Maneja el bloqueo temporal del PIN del terminal (423).
     *
     * <p>HTTP 423 Locked se usa cuando un dispositivo ha superado
     * los 5 intentos fallidos de PIN y está bloqueado 30 segundos
     * (RNF-S05). El bloqueo es por dispositivo, no por empleado
     * (decisión de diseño nº16).
     *
     * @param ex excepción de PIN bloqueado
     * @param request información de la petición HTTP
     * @return 423 con { error, timestamp, path }
     */
    @ExceptionHandler(PinBloqueadoException.class)
    public ResponseEntity<Map<String, Object>> handlePinBloqueado(
            PinBloqueadoException ex, WebRequest request) {

        log.warn("PIN bloqueado: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.LOCKED)
                .body(buildError(ex.getMessage(), null, request));
    }

    // ─── 501 NOT IMPLEMENTED ─────────────────────────────────────────────────

    /**
     * Maneja funcionalidades no implementadas en v1.0 (501).
     *
     * <p>UnsupportedOperationException se usa en los servicios para marcar
     * endpoints definidos en el contrato pero pendientes de implementación
     * en versiones futuras (E19, E20).</p>
     *
     * @param ex excepción con el mensaje descriptivo de la feature
     * @param request información de la petición HTTP
     * @return 501 con { error, timestamp, path }
     */
    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<Map<String, Object>> handleUnsupportedOperation(
            UnsupportedOperationException ex, WebRequest request) {

        log.info("Funcionalidad no implementada: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(buildError("Funcionalidad no disponible en v1.0", null, request));
    }

    // ─── 500 INTERNAL SERVER ERROR ───────────────────────────────────────────

    /**
     * Manejador de último recurso para excepciones no controladas (500).
     *
     * <p>El mensaje genérico es intencionado: no se exponen detalles
     * internos al cliente por seguridad. El log.error() registra
     * el stack trace completo para diagnóstico interno.
     *
     * @param ex excepción no controlada
     * @param request información de la petición HTTP
     * @return 500 con { error, timestamp, path }
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(
            Exception ex, WebRequest request) {

        log.error("Error interno no controlado", ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildError("Error interno del servidor", null, request));
    }

    // ─── HELPER PRIVADO ──────────────────────────────────────────────────────

    /**
     * Construye el mapa de error con el formato del contrato de la API.
     *
     * <p>Formato: { error: String, campo?: String, timestamp: LocalDateTime, path: String }
     * LinkedHashMap mantiene el orden de inserción de las claves.
     * El campo "campo" solo se incluye si no es null.
     *
     * @param mensaje descripción del error
     * @param campo   nombre del campo problemático (puede ser null)
     * @param request petición HTTP para extraer el path
     * @return mapa con el body de error en formato contrato
     */
    private Map<String, Object> buildError(String mensaje, String campo, WebRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", mensaje);

        if (campo != null) {
            body.put("campo", campo);
        }

        body.put("timestamp", LocalDateTime.now());
        body.put("path", request.getDescription(false).replace("uri=", ""));

        return body;
    }
}
