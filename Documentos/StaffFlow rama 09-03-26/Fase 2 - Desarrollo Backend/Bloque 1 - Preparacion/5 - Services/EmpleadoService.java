package com.staffflow.service;

import com.staffflow.domain.repository.EmpleadoRepository;
import com.staffflow.domain.repository.UsuarioRepository;
import com.staffflow.dto.request.EmpleadoPatchRequest;
import com.staffflow.dto.request.EmpleadoRequest;
import com.staffflow.dto.response.EmpleadoResponse;
import com.staffflow.dto.response.MensajeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Servicio de gestión de perfiles de empleado.
 *
 * <p>Cubre los endpoints E13-E21 (Grupo 4). ADMIN y ENCARGADO acceden a
 * todos los empleados. EMPLEADO solo accede a sus propios datos mediante /me.
 * Las bajas son lógicas: activo=false, nunca DELETE físico (decisión nº4).</p>
 *
 * <p>La relación usuario-empleado es 1:1 e inmutable: una vez vinculados
 * no se puede cambiar el usuarioId. ADMIN nunca tiene perfil de empleado.</p>
 *
 * @author Santiago Castillo
 * @see com.staffflow.domain.repository.EmpleadoRepository
 */
@Service
@RequiredArgsConstructor
public class EmpleadoService {

    private final EmpleadoRepository empleadoRepository;
    private final UsuarioRepository usuarioRepository;

    /**
     * Crea el perfil laboral de un empleado vinculándolo a un usuario (E13).
     *
     * <p>El usuarioId debe existir y no tener ya un perfil de empleado.
     * El pinTerminal debe ser único en todo el sistema (RNF-R03).
     * Devuelve HTTP 404 si el usuarioId no existe, HTTP 409 si
     * DNI, NSS, PIN o NFC ya están registrados.</p>
     *
     * @param request datos del nuevo perfil de empleado
     * @return empleado creado con id asignado
     */
    public EmpleadoResponse crear(EmpleadoRequest request) {
        // TODO: implementar en Bloque 4
        throw new UnsupportedOperationException("Pendiente de implementar en Bloque 4");
    }

    /**
     * Lista empleados con filtros opcionales y combinables (E14).
     *
     * <p>El parámetro q busca simultáneamente en nombre, apellido1,
     * apellido2 y dni (RF-14). Sin parámetros devuelve todos los activos.</p>
     *
     * @param activo    filtro por estado (nullable, defecto: true)
     * @param q         búsqueda por nombre o DNI (nullable)
     * @param categoria filtro por categoría laboral (nullable)
     * @return lista de empleados que cumplen los filtros
     */
    public List<EmpleadoResponse> listar(Boolean activo, String q, String categoria) {
        // TODO: implementar en Bloque 4
        throw new UnsupportedOperationException("Pendiente de implementar en Bloque 4");
    }

    /**
     * Devuelve el perfil completo de un empleado concreto (E15).
     *
     * <p>ADMIN recibe el pinTerminal. ENCARGADO no (el controller
     * filtra el campo según el rol del token JWT).</p>
     *
     * @param id id del empleado
     * @return perfil completo del empleado
     */
    public EmpleadoResponse obtenerPorId(Long id) {
        // TODO: implementar en Bloque 4
        throw new UnsupportedOperationException("Pendiente de implementar en Bloque 4");
    }

    /**
     * Actualiza el perfil laboral de un empleado (E16).
     *
     * <p>Solo actualiza los campos con valor no nulo en el request.
     * El usuarioId no puede modificarse. El pinTerminal debe seguir
     * siendo único. Devuelve HTTP 409 si PIN, DNI o NSS ya existen.</p>
     *
     * @param id      id del empleado a actualizar
     * @param request campos a actualizar (todos opcionales)
     * @return empleado actualizado completo
     */
    public EmpleadoResponse actualizar(Long id, EmpleadoPatchRequest request) {
        // TODO: implementar en Bloque 4
        throw new UnsupportedOperationException("Pendiente de implementar en Bloque 4");
    }

    /**
     * Desactiva un empleado (baja lógica: activo=false) (E17).
     *
     * <p>El empleado desactivado no puede fichar desde el terminal ni
     * aparece en listados por defecto. Su historial queda intacto.</p>
     *
     * @param id id del empleado a desactivar
     * @return mensaje de confirmación
     */
    public MensajeResponse darDeBaja(Long id) {
        // TODO: implementar en Bloque 4
        throw new UnsupportedOperationException("Pendiente de implementar en Bloque 4");
    }

    /**
     * Reactiva un empleado previamente desactivado (E18).
     *
     * <p>Restaura activo=true. El empleado recupera inmediatamente la
     * capacidad de fichar con su PIN. Devuelve HTTP 409 si ya estaba activo.</p>
     *
     * @param id id del empleado a reactivar
     * @return mensaje de confirmación
     */
    public MensajeResponse reactivar(Long id) {
        // TODO: implementar en Bloque 4
        throw new UnsupportedOperationException("Pendiente de implementar en Bloque 4");
    }

    /**
     * Devuelve el perfil del empleado autenticado (E21 — /me).
     *
     * <p>Usa el usuarioId extraído del JWT para localizar el perfil de
     * empleado vinculado. Si el usuario autenticado es ADMIN devuelve
     * HTTP 404 porque ADMIN no tiene perfil de empleado.</p>
     *
     * @param usuarioId id del usuario autenticado (extraído del JWT)
     * @return perfil del empleado autenticado sin pinTerminal
     */
    public EmpleadoResponse obtenerMiPerfil(Long usuarioId) {
        // TODO: implementar en Bloque 4
        throw new UnsupportedOperationException("Pendiente de implementar en Bloque 4");
    }
}