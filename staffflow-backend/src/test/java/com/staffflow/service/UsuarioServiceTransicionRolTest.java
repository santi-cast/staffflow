package com.staffflow.service;

import com.staffflow.domain.entity.Usuario;
import com.staffflow.domain.enums.Rol;
import com.staffflow.domain.repository.EmpleadoRepository;
import com.staffflow.domain.repository.UsuarioRepository;
import com.staffflow.dto.request.UsuarioPatchRequest;
import com.staffflow.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios del guard de transición de rol en UsuarioService.actualizar() (E11).
 *
 * Verifica la invariante rol↔empleado:
 *   - ADMIN puro (sin empleado) no puede cambiar de rol.
 *   - Usuario con empleado asociado no puede ser promovido a ADMIN.
 *   - ENCARGADO ↔ EMPLEADO (ambos con empleado) está permitido.
 *   - Casos donde el guard no debe activarse (rol null, rol sin cambio).
 *
 * No usa {@code @SpringBootTest} (deuda M-036: wiring JWT roto en integración).
 * Usa Mockito puro con {@code @ExtendWith(MockitoExtension.class)}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UsuarioService — guard transición de rol (E11)")
class UsuarioServiceTransicionRolTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private EmpleadoRepository empleadoRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UsuarioService usuarioService;

    private static final long USUARIO_ID = 10L;

    private Usuario usuarioAdmin;
    private Usuario usuarioEmpleado;
    private Usuario usuarioEncargado;

    @BeforeEach
    void setUp() {
        usuarioAdmin = new Usuario();
        usuarioAdmin.setId(USUARIO_ID);
        usuarioAdmin.setRol(Rol.ADMIN);
        usuarioAdmin.setEmail("admin@staffflow.com");
        usuarioAdmin.setActivo(true);

        usuarioEmpleado = new Usuario();
        usuarioEmpleado.setId(USUARIO_ID);
        usuarioEmpleado.setRol(Rol.EMPLEADO);
        usuarioEmpleado.setEmail("empleado@staffflow.com");
        usuarioEmpleado.setActivo(true);

        usuarioEncargado = new Usuario();
        usuarioEncargado.setId(USUARIO_ID);
        usuarioEncargado.setRol(Rol.ENCARGADO);
        usuarioEncargado.setEmail("encargado@staffflow.com");
        usuarioEncargado.setActivo(true);
    }

    // -------------------------------------------------------------------------
    // Casos que deben lanzar ConflictException (guard activo)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Transiciones prohibidas → ConflictException")
    class TransicionesProhibidas {

        @Test
        @DisplayName("ADMIN puro intenta cambiar a ENCARGADO → ConflictException")
        void adminPuroACencargadoLanzaConflict() {
            when(usuarioRepository.findById(USUARIO_ID))
                    .thenReturn(Optional.of(usuarioAdmin));
            when(empleadoRepository.existsByUsuarioId(USUARIO_ID))
                    .thenReturn(false);

            UsuarioPatchRequest request = new UsuarioPatchRequest();
            request.setRol(Rol.ENCARGADO);

            assertThatThrownBy(() -> usuarioService.actualizar(USUARIO_ID, request))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Un usuario ADMIN no puede cambiar de rol");

            verify(usuarioRepository, never()).save(any());
        }

        @Test
        @DisplayName("ADMIN puro intenta cambiar a EMPLEADO → ConflictException")
        void adminPuroAEmpleadoLanzaConflict() {
            when(usuarioRepository.findById(USUARIO_ID))
                    .thenReturn(Optional.of(usuarioAdmin));
            when(empleadoRepository.existsByUsuarioId(USUARIO_ID))
                    .thenReturn(false);

            UsuarioPatchRequest request = new UsuarioPatchRequest();
            request.setRol(Rol.EMPLEADO);

            assertThatThrownBy(() -> usuarioService.actualizar(USUARIO_ID, request))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Un usuario ADMIN no puede cambiar de rol");

            verify(usuarioRepository, never()).save(any());
        }

        @Test
        @DisplayName("EMPLEADO con empleado asociado intenta cambiar a ADMIN → ConflictException")
        void empleadoConEmpleadoAAdminLanzaConflict() {
            when(usuarioRepository.findById(USUARIO_ID))
                    .thenReturn(Optional.of(usuarioEmpleado));
            when(empleadoRepository.existsByUsuarioId(USUARIO_ID))
                    .thenReturn(true);

            UsuarioPatchRequest request = new UsuarioPatchRequest();
            request.setRol(Rol.ADMIN);

            assertThatThrownBy(() -> usuarioService.actualizar(USUARIO_ID, request))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Un usuario con empleado asociado no puede ser promovido a ADMIN");

            verify(usuarioRepository, never()).save(any());
        }

        @Test
        @DisplayName("ENCARGADO con empleado asociado intenta cambiar a ADMIN → ConflictException")
        void encargadoConEmpleadoAAdminLanzaConflict() {
            when(usuarioRepository.findById(USUARIO_ID))
                    .thenReturn(Optional.of(usuarioEncargado));
            when(empleadoRepository.existsByUsuarioId(USUARIO_ID))
                    .thenReturn(true);

            UsuarioPatchRequest request = new UsuarioPatchRequest();
            request.setRol(Rol.ADMIN);

            assertThatThrownBy(() -> usuarioService.actualizar(USUARIO_ID, request))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Un usuario con empleado asociado no puede ser promovido a ADMIN");

            verify(usuarioRepository, never()).save(any());
        }
    }

    // -------------------------------------------------------------------------
    // Casos permitidos → llama save()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Transiciones permitidas → llama save()")
    class TransicionesPermitidas {

        @Test
        @DisplayName("EMPLEADO con empleado asociado cambia a ENCARGADO → llama save()")
        void empleadoAEncargadoLlamaSave() {
            when(usuarioRepository.findById(USUARIO_ID))
                    .thenReturn(Optional.of(usuarioEmpleado));
            when(empleadoRepository.existsByUsuarioId(USUARIO_ID))
                    .thenReturn(true);
            when(usuarioRepository.save(any())).thenReturn(usuarioEmpleado);

            UsuarioPatchRequest request = new UsuarioPatchRequest();
            request.setRol(Rol.ENCARGADO);

            usuarioService.actualizar(USUARIO_ID, request);

            verify(usuarioRepository).save(any());
        }

        @Test
        @DisplayName("ENCARGADO con empleado asociado cambia a EMPLEADO → llama save()")
        void encargadoAEmpleadoLlamaSave() {
            when(usuarioRepository.findById(USUARIO_ID))
                    .thenReturn(Optional.of(usuarioEncargado));
            when(empleadoRepository.existsByUsuarioId(USUARIO_ID))
                    .thenReturn(true);
            when(usuarioRepository.save(any())).thenReturn(usuarioEncargado);

            UsuarioPatchRequest request = new UsuarioPatchRequest();
            request.setRol(Rol.EMPLEADO);

            usuarioService.actualizar(USUARIO_ID, request);

            verify(usuarioRepository).save(any());
        }

        @Test
        @DisplayName("PATCH sin campo rol (null) → no valida guard, llama save()")
        void patchSinRolNoValidaGuard() {
            when(usuarioRepository.findById(USUARIO_ID))
                    .thenReturn(Optional.of(usuarioEmpleado));
            when(usuarioRepository.save(any())).thenReturn(usuarioEmpleado);

            UsuarioPatchRequest request = new UsuarioPatchRequest();
            // request.rol queda null

            usuarioService.actualizar(USUARIO_ID, request);

            verify(empleadoRepository, never()).existsByUsuarioId(any());
            verify(usuarioRepository).save(any());
        }

        @Test
        @DisplayName("PATCH con mismo rol que el actual → no valida guard, llama save()")
        void patchConMismoRolNoValidaGuard() {
            when(usuarioRepository.findById(USUARIO_ID))
                    .thenReturn(Optional.of(usuarioEmpleado));
            when(usuarioRepository.save(any())).thenReturn(usuarioEmpleado);

            UsuarioPatchRequest request = new UsuarioPatchRequest();
            request.setRol(Rol.EMPLEADO); // mismo que el actual

            usuarioService.actualizar(USUARIO_ID, request);

            verify(empleadoRepository, never()).existsByUsuarioId(any());
            verify(usuarioRepository).save(any());
        }
    }
}
