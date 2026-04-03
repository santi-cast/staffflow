package com.staffflow.exception;

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

        // Obtener el primer error de campo — en caso de múltiples errores
        // se devuelve solo el primero para no sobrecargar la respuesta
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
     * <p>Se mapea a 400 y no a 500 porque es un error del cliente
     * (datos incorrectos), no un error del servidor.
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
     * viola una constraint UNIQUE o NOT NULL en la BD: username duplicado,
     * email duplicado, CIF duplicado (E07), PIN duplicado, etc.
     *
     * <p>Se mapea a 400 porque el dato enviado por el cliente viola
     * una regla de unicidad del dominio.
     *
     * <p>El mensaje genérico evita exponer nombres de constraints
     * internas de la BD al cliente.
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
     * <p>BadCredentialsException la lanza Spring Security cuando
     * AuthenticationManager no puede autenticar al usuario: username
     * inexistente, contraseña incorrecta, o usuario inactivo.
     *
     * <p>El mensaje es genérico intencionadamente (no revela si fue
     * el username o la contraseña lo incorrecto — RNF-S04).
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
     * el rol requerido (@PreAuthorize). Ejemplo: EMPLEADO intentando
     * acceder a GET /api/v1/empresa (solo ADMIN).
     *
     * <p>Este handler complementa al Http403ForbiddenEntryPoint de
     * SecurityConfig, que maneja el caso de usuario no autenticado.
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
     * Maneja recursos no encontrados en BD (404).
     *
     * <p>IllegalStateException se usa en los servicios cuando un recurso
     * solicitado por id no existe en la BD: empleado no encontrado,
     * usuario no encontrado, configuración no inicializada, etc.
     *
     * <p>Se usa IllegalStateException (no una excepción custom) para
     * mantener el código simple en la fase actual del proyecto.
     * En una versión futura se podría crear ResourceNotFoundException.
     *
     * @param ex excepción con el mensaje descriptivo del recurso ausente
     * @param request información de la petición HTTP
     * @return 404 con { error, timestamp, path }
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(
            IllegalStateException ex, WebRequest request) {

        log.warn("Recurso no encontrado: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildError(ex.getMessage(), null, request));
    }

    // ─── 423 LOCKED ──────────────────────────────────────────────────────────

    /**
     * Maneja el bloqueo temporal del PIN del terminal (423).
     *
     * <p>HTTP 423 Locked se usa cuando un dispositivo ha superado
     * los 5 intentos fallidos de PIN y está bloqueado 30 segundos
     * (RNF-S05). El bloqueo es por dispositivo, no por empleado.
     *
     * <p>Se usa una excepción personalizada RuntimeException con
     * mensaje específico. En Bloque 5 se puede crear PinBloqueadoException
     * si se considera necesario para mayor claridad.
     *
     * <p>Nota: 423 no es un código estándar de Spring Security —
     * se lanza manualmente desde TerminalService cuando detecta
     * el bloqueo (Bloque 5).
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

    // ─── 500 INTERNAL SERVER ERROR ───────────────────────────────────────────

    /**
     * Manejador de último recurso para excepciones no controladas (500).
     *
     * <p>Captura cualquier excepción no manejada por los handlers
     * anteriores. Evita que Spring devuelva stack traces o mensajes
     * internos al cliente.
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

        // Log completo con stack trace para diagnóstico — NO se expone al cliente
        log.error("Error interno no controlado", ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildError("Error interno del servidor", null, request));
    }

    // ─── HELPER PRIVADO ──────────────────────────────────────────────────────

    /**
     * Construye el mapa de error con el formato del contrato de la API.
     *
     * <p>Formato definido en StaffFlow_Endpoints_v3.docx:
     * { error: String, campo?: String, timestamp: LocalDateTime, path: String }
     *
     * <p>LinkedHashMap mantiene el orden de inserción de las claves,
     * lo que produce un JSON con las claves en el orden definido
     * (error → campo → timestamp → path).
     *
     * <p>El campo "campo" solo se incluye si no es null — evita
     * contaminar la respuesta con "campo: null" cuando el error
     * no está asociado a un campo concreto.
     *
     * @param mensaje descripción del error
     * @param campo   nombre del campo problemático (puede ser null)
     * @param request petición HTTP para extraer el path
     * @return mapa con el body de error en formato contrato
     */
    private Map<String, Object> buildError(String mensaje, String campo, WebRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", mensaje);

        // "campo" solo se incluye cuando es relevante (errores de validación de campo)
        if (campo != null) {
            body.put("campo", campo);
        }

        body.put("timestamp", LocalDateTime.now());

        // WebRequest.getDescription(false) devuelve "uri=/api/v1/..." sin el host
        body.put("path", request.getDescription(false).replace("uri=", ""));

        return body;
    }
}
