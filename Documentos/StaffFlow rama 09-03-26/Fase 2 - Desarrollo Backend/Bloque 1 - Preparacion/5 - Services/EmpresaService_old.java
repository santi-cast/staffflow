package com.staffflow.service;

import com.staffflow.domain.repository.ConfiguracionEmpresaRepository;
import com.staffflow.dto.request.EmpresaRequest;
import com.staffflow.dto.response.EmpresaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Servicio de configuración de empresa.
 *
 * <p>Cubre los endpoints E06-E07 (Grupo 2). Gestiona el registro singleton
 * de la tabla configuracion_empresa, que siempre tiene exactamente una fila
 * con id=1. Solo accesible por el rol ADMIN.</p>
 *
 * @author Santiago Castillo
 * @see com.staffflow.domain.repository.ConfiguracionEmpresaRepository
 */
@Service
@RequiredArgsConstructor
public class EmpresaService {

    private final ConfiguracionEmpresaRepository empresaRepository;

    /**
     * Devuelve la configuración actual de la empresa (E06).
     *
     * <p>Busca siempre el registro con id=1. Si no existe lanza
     * ResourceNotFoundException → HTTP 404.</p>
     *
     * @return datos de configuración de la empresa
     */
    public EmpresaResponse obtener() {
        // TODO: implementar en Bloque 3
        throw new UnsupportedOperationException("Pendiente de implementar en Bloque 3");
    }

    /**
     * Actualiza la configuración de la empresa (E07).
     *
     * <p>Opera siempre sobre el registro con id=1. Si no existe lo crea
     * (primera configuración). Todos los campos son opcionales: solo se
     * actualizan los campos enviados con valor no nulo.</p>
     *
     * @param request datos de empresa a actualizar
     * @return configuración actualizada completa
     */
    public EmpresaResponse actualizar(EmpresaRequest request) {
        // TODO: implementar en Bloque 3
        throw new UnsupportedOperationException("Pendiente de implementar en Bloque 3");
    }
}