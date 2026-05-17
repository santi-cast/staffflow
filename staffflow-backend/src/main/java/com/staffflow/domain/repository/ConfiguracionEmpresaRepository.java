package com.staffflow.domain.repository;

import com.staffflow.domain.entity.ConfiguracionEmpresa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio para la configuración global de la empresa.
 *
 * <p>La tabla configuracion_empresa es un singleton: la convención del sistema es
 * que exista exactamente una fila con id=1. E07 (PUT /empresa) opera con semántica
 * de upsert sobre ese id: si la fila no existe (primera configuración tras la
 * instalación), {@code save()} hace INSERT con id=1; si existe, UPDATE. No se
 * eliminan registros en tiempo de ejecución.</p>
 *
 * <p>No se necesitan métodos custom: findById(1L) y save() son suficientes
 * para todas las operaciones de EmpresaService.</p>
 *
 * @author Santiago Castillo
 * @see com.staffflow.domain.entity.ConfiguracionEmpresa
 */
@Repository
public interface ConfiguracionEmpresaRepository extends JpaRepository<ConfiguracionEmpresa, Long> {
}