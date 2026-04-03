package com.staffflow.service;

import com.staffflow.domain.repository.UsuarioRepository;
import com.staffflow.dto.request.UsuarioPatchRequest;
import com.staffflow.dto.request.UsuarioRequest;
import com.staffflow.dto.response.MensajeResponse;
import com.staffflow.dto.response.UsuarioResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Servicio de gestión de usuarios del sistema.
 *
 * <p>Cubre los endpoints E08-E12 (Grupo 3). CRUD de usuarios accesible
 * exclusivamente por el rol ADMIN. Las bajas son lógicas: activo=false,
 * nunca DELETE físico (decisión nº4).</p>
 *
 * <p>Las contraseñas se almacenan cifradas con BCrypt (RNF-S01). El campo
 * password_hash nunca se expone en ningún response.</p>
 *
 * @author Santiago Castillo
 * @see com.staffflow.domain.repository.UsuarioRepository
 */
@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;

    /**
     * Crea un nuevo usuario en el sistema (E08).
     *
     * <p>Username y email deben ser únicos en BD. La contraseña se cifra
     * con BCrypt antes de persistir. Devuelve HTTP 409 si username o email
     * ya existen.</p>
     *
     * @param request datos del nuevo usuario
     * @return usuario creado con id asignado
     */
    public UsuarioResponse crear(UsuarioRequest request) {
        // TODO: implementar en Bloque 4
        throw new UnsupportedOperationException("Pendiente de implementar en Bloque 4");
    }

    /**
     * Lista todos los usuarios con filtros opcionales (E09).
     *
     * <p>Los parámetros rol y activo son opcionales y combinables.
     * Sin parámetros devuelve todos los usuarios.</p>
     *
     * @param rol    filtro por rol (nullable)
     * @param activo filtro por estado activo (nullable)
     * @return lista de usuarios que cumplen los filtros
     */
    public List<UsuarioResponse> listar(String rol, Boolean activo) {
        // TODO: implementar en Bloque 4
        throw new UnsupportedOperationException("Pendiente de implementar en Bloque 4");
    }

    /**
     * Devuelve el detalle de un usuario concreto (E10).
     *
     * <p>No incluye el campo password_hash. Devuelve HTTP 404
     * si el usuario no existe.</p>
     *
     * @param id id del usuario
     * @return datos del usuario sin información sensible
     */
    public UsuarioResponse obtenerPorId(Long id) {
        // TODO: implementar en Bloque 4
        throw new UnsupportedOperationException("Pendiente de implementar en Bloque 4");
    }

    /**
     * Actualiza los datos editables de un usuario (E11).
     *
     * <p>Solo actualiza los campos con valor no nulo en el request.
     * La contraseña no se gestiona por este endpoint: usar E03 o E05.
     * Devuelve HTTP 409 si el nuevo username o email ya existen.</p>
     *
     * @param id      id del usuario a actualizar
     * @param request campos a actualizar (todos opcionales)
     * @return usuario actualizado completo
     */
    public UsuarioResponse actualizar(Long id, UsuarioPatchRequest request) {
        // TODO: implementar en Bloque 4
        throw new UnsupportedOperationException("Pendiente de implementar en Bloque 4");
    }

    /**
     * Desactiva un usuario (baja lógica: activo=false) (E12).
     *
     * <p>No ejecuta SQL DELETE. El historial de auditoría queda intacto.
     * El usuario desactivado no puede hacer login. Devuelve HTTP 404
     * si el usuario no existe.</p>
     *
     * @param id id del usuario a desactivar
     * @return mensaje de confirmación
     */
    public MensajeResponse desactivar(Long id) {
        // TODO: implementar en Bloque 4
        throw new UnsupportedOperationException("Pendiente de implementar en Bloque 4");
    }
}