package com.staffflow.domain.repository;

import com.staffflow.domain.entity.ConfiguracionEmpresa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio para la configuración global de la empresa.
 *
 * <p>La tabla configuracion_empresa es un singleton: siempre existe exactamente
 * una fila con id=1. No se crean ni eliminan registros en tiempo de ejecución,
 * solo se actualiza el existente (E07 PUT /empresa).</p>
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