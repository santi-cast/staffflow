package com.staffflow.service;

import com.staffflow.domain.entity.ConfiguracionEmpresa;
import com.staffflow.domain.repository.ConfiguracionEmpresaRepository;
import com.staffflow.dto.request.EmpresaRequest;
import com.staffflow.dto.response.EmpresaResponse;
import com.staffflow.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de EmpresaService.
 *
 * <p>Verifica los dos endpoints publicos del servicio (E06 y E07) sobre
 * la tabla singleton configuracion_empresa (id = 1 siempre):
 * <ul>
 *   <li>E06 obtenerEmpresa: lectura del singleton, 404 si no existe.</li>
 *   <li>E07 actualizarEmpresa: update si existe, insert con id=1 si no.</li>
 * </ul>
 *
 * <p>Service sin dependencias temporales ni de seguridad: Mockito puro
 * directo, sin Clock ni SecurityContextHolder.
 *
 * @author Santiago Castillo
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmpresaService — configuracion singleton de empresa")
class EmpresaServiceTest {

    @Mock private ConfiguracionEmpresaRepository configuracionEmpresaRepository;

    @InjectMocks
    private EmpresaService empresaService;

    private ConfiguracionEmpresa singletonExistente;

    @BeforeEach
    void setUp() {
        singletonExistente = new ConfiguracionEmpresa(
                1L,
                "ACME S.L.",
                "B12345678",
                "Calle Mayor 1, Madrid",
                "contacto@acme.com",
                "910000000",
                "logos/acme.png"
        );
    }

    // ─── E06 ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("E06 obtenerEmpresa - GET /api/v1/empresa")
    class ObtenerEmpresa {

        @Test
        @DisplayName("devuelve EmpresaResponse mapeado cuando el singleton existe")
        void devuelveResponseCuandoSingletonExiste() {
            when(configuracionEmpresaRepository.findById(1L))
                    .thenReturn(Optional.of(singletonExistente));

            EmpresaResponse response = empresaService.obtenerEmpresa();

            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getNombreEmpresa()).isEqualTo("ACME S.L.");
            assertThat(response.getCif()).isEqualTo("B12345678");
            assertThat(response.getDireccion()).isEqualTo("Calle Mayor 1, Madrid");
            assertThat(response.getEmail()).isEqualTo("contacto@acme.com");
            assertThat(response.getTelefono()).isEqualTo("910000000");
            assertThat(response.getLogoPath()).isEqualTo("logos/acme.png");
        }

        @Test
        @DisplayName("lanza NotFoundException cuando el singleton id=1 no existe")
        void lanzaNotFoundCuandoSingletonNoExiste() {
            when(configuracionEmpresaRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> empresaService.obtenerEmpresa())
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("no ha sido inicializada");

            verify(configuracionEmpresaRepository, never()).save(any());
        }

        @Test
        @DisplayName("preserva logoPath null en la respuesta cuando la empresa no tiene logo")
        void preservaLogoPathNulo() {
            singletonExistente.setLogoPath(null);
            when(configuracionEmpresaRepository.findById(1L))
                    .thenReturn(Optional.of(singletonExistente));

            EmpresaResponse response = empresaService.obtenerEmpresa();

            assertThat(response.getLogoPath()).isNull();
            assertThat(response.getNombreEmpresa()).isEqualTo("ACME S.L.");
        }
    }

    // ─── E07 ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("E07 actualizarEmpresa - PUT /api/v1/empresa")
    class ActualizarEmpresa {

        @Test
        @DisplayName("actualiza el singleton existente y devuelve los datos guardados")
        void actualizaSingletonExistente() {
            EmpresaRequest request = buildRequest(
                    "Nueva Razon S.A.",
                    "A87654321",
                    "Avenida Nueva 42, Barcelona",
                    "info@nueva.com",
                    "930000000",
                    "logos/nueva.png"
            );

            when(configuracionEmpresaRepository.findById(1L))
                    .thenReturn(Optional.of(singletonExistente));
            when(configuracionEmpresaRepository.save(any(ConfiguracionEmpresa.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            EmpresaResponse response = empresaService.actualizarEmpresa(request);

            ArgumentCaptor<ConfiguracionEmpresa> captor =
                    ArgumentCaptor.forClass(ConfiguracionEmpresa.class);
            verify(configuracionEmpresaRepository).save(captor.capture());

            ConfiguracionEmpresa guardada = captor.getValue();
            assertThat(guardada.getId()).isEqualTo(1L);
            assertThat(guardada.getNombreEmpresa()).isEqualTo("Nueva Razon S.A.");
            assertThat(guardada.getCif()).isEqualTo("A87654321");
            assertThat(guardada.getDireccion()).isEqualTo("Avenida Nueva 42, Barcelona");
            assertThat(guardada.getEmail()).isEqualTo("info@nueva.com");
            assertThat(guardada.getTelefono()).isEqualTo("930000000");
            assertThat(guardada.getLogoPath()).isEqualTo("logos/nueva.png");

            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getNombreEmpresa()).isEqualTo("Nueva Razon S.A.");
            assertThat(response.getCif()).isEqualTo("A87654321");
        }

        @Test
        @DisplayName("crea el singleton con id=1 cuando no existe (primera configuracion)")
        void creaSingletonCuandoNoExiste() {
            EmpresaRequest request = buildRequest(
                    "Primera Config S.L.",
                    "B11111111",
                    "Calle Inicio 1",
                    "init@primera.com",
                    "911111111",
                    null
            );

            when(configuracionEmpresaRepository.findById(1L)).thenReturn(Optional.empty());
            when(configuracionEmpresaRepository.save(any(ConfiguracionEmpresa.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            EmpresaResponse response = empresaService.actualizarEmpresa(request);

            ArgumentCaptor<ConfiguracionEmpresa> captor =
                    ArgumentCaptor.forClass(ConfiguracionEmpresa.class);
            verify(configuracionEmpresaRepository).save(captor.capture());

            ConfiguracionEmpresa guardada = captor.getValue();
            // El service fuerza id=1 incluso cuando la entidad es nueva,
            // garantizando que Hibernate haga INSERT con id=1 (singleton).
            assertThat(guardada.getId()).isEqualTo(1L);
            assertThat(guardada.getNombreEmpresa()).isEqualTo("Primera Config S.L.");
            assertThat(guardada.getCif()).isEqualTo("B11111111");

            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getNombreEmpresa()).isEqualTo("Primera Config S.L.");
        }

        @Test
        @DisplayName("preserva logoPath null en la entidad cuando el request no lleva logo")
        void preservaLogoPathNuloEnUpdate() {
            EmpresaRequest request = buildRequest(
                    "Sin Logo S.L.",
                    "B22222222",
                    "Calle Sin Logo 1",
                    "sinlogo@empresa.com",
                    "922222222",
                    null
            );

            when(configuracionEmpresaRepository.findById(1L))
                    .thenReturn(Optional.of(singletonExistente));
            when(configuracionEmpresaRepository.save(any(ConfiguracionEmpresa.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            EmpresaResponse response = empresaService.actualizarEmpresa(request);

            ArgumentCaptor<ConfiguracionEmpresa> captor =
                    ArgumentCaptor.forClass(ConfiguracionEmpresa.class);
            verify(configuracionEmpresaRepository).save(captor.capture());

            // PUT completo: si el request trae logoPath null, la entidad
            // queda con null aunque previamente tuviera un valor.
            assertThat(captor.getValue().getLogoPath()).isNull();
            assertThat(response.getLogoPath()).isNull();
        }

        @Test
        @DisplayName("invoca save exactamente una vez")
        void invocaSaveUnaVez() {
            EmpresaRequest request = buildRequest(
                    "Una Vez S.L.",
                    "B33333333",
                    "Calle Una 1",
                    "una@vez.com",
                    "933333333",
                    "logos/una.png"
            );

            when(configuracionEmpresaRepository.findById(1L))
                    .thenReturn(Optional.of(singletonExistente));
            when(configuracionEmpresaRepository.save(any(ConfiguracionEmpresa.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            empresaService.actualizarEmpresa(request);

            verify(configuracionEmpresaRepository).save(any(ConfiguracionEmpresa.class));
        }
    }

    // ─── HELPERS ─────────────────────────────────────────────────────────────

    private EmpresaRequest buildRequest(String nombre, String cif, String direccion,
                                        String email, String telefono, String logoPath) {
        EmpresaRequest request = new EmpresaRequest();
        request.setNombreEmpresa(nombre);
        request.setCif(cif);
        request.setDireccion(direccion);
        request.setEmail(email);
        request.setTelefono(telefono);
        request.setLogoPath(logoPath);
        return request;
    }
}
