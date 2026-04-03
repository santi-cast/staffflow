package com.staffflow.service;

import com.staffflow.domain.entity.ConfiguracionEmpresa;
import com.staffflow.domain.repository.ConfiguracionEmpresaRepository;
import com.staffflow.dto.request.EmpresaRequest;
import com.staffflow.dto.response.EmpresaResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de gestión de la configuración de empresa.
 *
 * <p>La tabla configuracion_empresa es un singleton: siempre contiene
 * exactamente un registro con id=1. No se crean ni eliminan filas —
 * solo se lee (E06) y se actualiza (E07).
 *
 * <p>Si el registro aún no existe (primera configuración del sistema),
 * E07 lo crea con id=1 mediante save(). Hibernate gestiona el INSERT
 * automáticamente al detectar que el id no existe en BD.
 *
 * @author Santiago Castillo
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmpresaService {

    // Repositorio JPA del singleton configuracion_empresa
    private final ConfiguracionEmpresaRepository configuracionEmpresaRepository;

    // ─── E06 ────────────────────────────────────────────────────────────────

    /**
     * Devuelve la configuración actual de la empresa (E06).
     *
     * <p>Busca el registro con id=1. Si no existe (sistema recién
     * instalado y nunca configurado) lanza IllegalStateException.
     * En producción el sistema debe inicializarse con un INSERT
     * previo antes de ser usado.
     *
     * <p>Alternativa descartada: devolver un EmpresaResponse vacío
     * si no existe. Se rechazó porque un GET que devuelve datos vacíos
     * sin error dificulta detectar que el sistema no está configurado.
     *
     * @return EmpresaResponse con los datos actuales de la empresa
     * @throws IllegalStateException si el registro singleton no existe
     */
    public EmpresaResponse obtenerEmpresa() {
        // El singleton siempre tiene id=1 — convención de diseño (RF-02)
        ConfiguracionEmpresa empresa = configuracionEmpresaRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException(
                        "La configuración de empresa no ha sido inicializada"));

        log.info("Consulta configuracion empresa: {}", empresa.getNombreEmpresa());

        return toEmpresaResponse(empresa);
    }

    // ─── E07 ────────────────────────────────────────────────────────────────

    /**
     * Actualiza la configuración de la empresa (E07).
     *
     * <p>Opera siempre sobre el registro con id=1. Si no existe,
     * lo crea (primera configuración del sistema). Hibernate decide
     * entre INSERT y UPDATE automáticamente según si el id existe.
     *
     * <p>La unicidad del CIF está garantizada por la constraint UNIQUE
     * en BD. Si se intenta guardar un CIF ya usado por otro registro
     * (imposible en un singleton, pero contemplado por contrato),
     * la BD lanzará DataIntegrityViolationException → GlobalExceptionHandler
     * lo convierte en 400.
     *
     * <p>PUT completo: todos los campos del request se aplican al
     * registro. logoPath puede ser null (empresa sin logo configurado).
     *
     * @param request DTO con los nuevos datos de la empresa
     * @return EmpresaResponse con la configuración tras la actualización
     */
    @Transactional
    public EmpresaResponse actualizarEmpresa(EmpresaRequest request) {
        // Cargar o crear el singleton
        // findById devuelve Optional vacío si es la primera configuración
        ConfiguracionEmpresa empresa = configuracionEmpresaRepository.findById(1L)
                .orElse(new ConfiguracionEmpresa());

        // Forzar id=1 — el singleton nunca debe tener otro id
        // Si orElse() devolvió una entidad nueva, setId asegura el INSERT con id=1
        empresa.setId(1L);

        // Aplicar todos los campos del request al registro
        // PUT completo: no hay campos opcionales excepto logoPath
        empresa.setNombreEmpresa(request.getNombreEmpresa());
        empresa.setCif(request.getCif());
        empresa.setDireccion(request.getDireccion());
        empresa.setEmail(request.getEmail());
        empresa.setTelefono(request.getTelefono());

        // logoPath es opcional: puede ser null si la empresa no tiene logo
        empresa.setLogoPath(request.getLogoPath());

        ConfiguracionEmpresa guardada = configuracionEmpresaRepository.save(empresa);

        log.info("Configuracion empresa actualizada: {} (CIF: {})",
                guardada.getNombreEmpresa(), guardada.getCif());

        return toEmpresaResponse(guardada);
    }

    // ─── MAPEO PRIVADO ───────────────────────────────────────────────────────

    /**
     * Convierte una entidad ConfiguracionEmpresa en su DTO de respuesta.
     *
     * <p>Único punto de conversión entidad→DTO en este servicio.
     * Regla de oro: ningún Service devuelve entidades JPA al Controller.
     *
     * @param empresa entidad a convertir
     * @return EmpresaResponse con los datos del registro
     */
    private EmpresaResponse toEmpresaResponse(ConfiguracionEmpresa empresa) {
        return new EmpresaResponse(
                empresa.getId(),
                empresa.getNombreEmpresa(),
                empresa.getCif(),
                empresa.getDireccion(),
                empresa.getEmail(),
                empresa.getTelefono(),
                empresa.getLogoPath()
        );
    }
}
