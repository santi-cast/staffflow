package com.staffflow.domain.repository;

import com.staffflow.domain.entity.Empleado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio para los perfiles de empleado.
 *
 * <p>Un empleado siempre está vinculado a exactamente un usuario (relación
 * 1:1, FK usuario_id UNIQUE). ADMIN no tiene perfil de empleado.</p>
 *
 * <p>Las bajas son lógicas: activo=false impide el acceso al terminal
 * pero conserva el historial completo (decisión nº4).</p>
 *
 * @author Santiago Castillo
 * @see com.staffflow.domain.entity.Empleado
 */
@Repository
public interface EmpleadoRepository extends JpaRepository<Empleado, Long> {

    /**
     * Busca el perfil de empleado vinculado a un usuario concreto.
     *
     * <p>Lo usa EmpleadoService en E17 (GET /empleados/me) para resolver
     * el perfil del usuario autenticado a partir del id extraído del JWT.
     * Si el usuario es ADMIN devuelve Optional.empty() porque ADMIN no
     * tiene perfil de empleado.</p>
     *
     * @param usuarioId id del usuario vinculado
     * @return Optional con el empleado, vacío si no existe
     */
    Optional<Empleado> findByUsuarioId(Long usuarioId);

    /**
     * Devuelve todos los empleados con el estado activo indicado.
     *
     * <p>Lo usa PresenciaService en E35 (GET /presencia/parte-diario)
     * para obtener la lista de empleados activos sobre la que calcular
     * el estado de presencia en tiempo real.</p>
     *
     * @param activo true para empleados activos, false para bajas lógicas
     * @return lista de empleados con el estado indicado
     */
    List<Empleado> findByActivo(boolean activo);

    /**
     * Busca un empleado por su PIN de terminal.
     *
     * <p>Lo usa TerminalService en E48-E51 (/terminal/*) para identificar
     * al empleado que está fichando. El PIN es de 4 dígitos, único en BD.
     * Si el PIN no existe devuelve Optional.empty() → HTTP 401.
     * El bloqueo por intentos fallidos lo gestiona TerminalService,
     * no este repositorio (decisión nº16).</p>
     *
     * @param pinTerminal PIN de 4 dígitos del terminal
     * @return Optional con el empleado, vacío si el PIN no existe
     */
    Optional<Empleado> findByPinTerminal(String pinTerminal);
}