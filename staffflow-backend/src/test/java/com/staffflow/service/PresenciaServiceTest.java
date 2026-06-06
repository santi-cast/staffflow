package com.staffflow.service;

import com.staffflow.domain.entity.Empleado;
import com.staffflow.domain.entity.Fichaje;
import com.staffflow.domain.entity.Pausa;
import com.staffflow.domain.entity.PlanificacionAusencia;
import com.staffflow.domain.enums.EstadoPresencia;
import com.staffflow.domain.enums.TipoFichaje;
import com.staffflow.domain.enums.TipoPausa;
import com.staffflow.domain.repository.EmpleadoRepository;
import com.staffflow.domain.repository.FichajeRepository;
import com.staffflow.domain.repository.PausaRepository;
import com.staffflow.domain.repository.PlanificacionAusenciaRepository;
import com.staffflow.dto.response.DetallePresenciaResponse;
import com.staffflow.dto.response.ParteDiarioResponse;
import com.staffflow.dto.response.SinJustificarResponse;
import com.staffflow.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de PresenciaService — cubre los tres endpoints publicos
 * del servicio (E35 obtenerParteDiario, E36 obtenerSinJustificar,
 * E37 obtenerMiPresencia).
 *
 * <p>Estrategia: Mockito puro sin contexto Spring, mockeando los cuatro
 * repositorios que el servicio consume (EmpleadoRepository,
 * FichajeRepository, PausaRepository, PlanificacionAusenciaRepository).
 * No se inyecta {@code Clock}: PresenciaService nunca llama a
 * {@code LocalDate.now()}; la fecha llega siempre como parametro desde el
 * controlador, que es quien resuelve "por defecto hoy". Por eso aqui el
 * SUT se construye con {@code @InjectMocks} sin riesgo de NPE por un Clock
 * sin inicializar.
 *
 * <p>El nucleo de la prueba es el helper privado {@code clasificarEmpleado},
 * ejercitado de forma indirecta a traves de E35 para cubrir las seis ramas
 * de {@link EstadoPresencia} por orden de prioridad, mas la rama de festivo
 * global (ausencia con empleado = null). E36 y E37 reutilizan la misma
 * clasificacion.</p>
 *
 * @author Santiago Castillo
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PresenciaService — E35, E36, E37")
class PresenciaServiceTest {

    @Mock
    private EmpleadoRepository empleadoRepository;
    @Mock
    private FichajeRepository fichajeRepository;
    @Mock
    private PausaRepository pausaRepository;
    @Mock
    private PlanificacionAusenciaRepository ausenciaRepository;

    @InjectMocks
    private PresenciaService presenciaService;

    private LocalDate fecha;

    @BeforeEach
    void setUp() {
        fecha = LocalDate.of(2026, 1, 15);
    }

    // ---------------------------------------------------------------
    // Helpers de construccion de entidades de apoyo
    // ---------------------------------------------------------------

    private Empleado empleado(Long id, String nombre, String apellido1, String apellido2) {
        Empleado emp = new Empleado();
        emp.setId(id);
        emp.setNombre(nombre);
        emp.setApellido1(apellido1);
        emp.setApellido2(apellido2);
        emp.setFechaAlta(LocalDate.of(2025, 1, 1));
        return emp;
    }

    private Fichaje fichaje(Long id, Empleado emp, LocalDateTime entrada, LocalDateTime salida,
                            TipoFichaje tipo, Integer jornadaEfectivaMinutos) {
        Fichaje f = new Fichaje();
        f.setId(id);
        f.setEmpleado(emp);
        f.setHoraEntrada(entrada);
        f.setHoraSalida(salida);
        f.setTipo(tipo);
        f.setJornadaEfectivaMinutos(jornadaEfectivaMinutos);
        return f;
    }

    private Pausa pausa(Long id, Empleado emp, LocalDateTime inicio, LocalDateTime fin,
                        TipoPausa tipo, Integer duracionMinutos) {
        Pausa p = new Pausa();
        p.setId(id);
        p.setEmpleado(emp);
        p.setHoraInicio(inicio);
        p.setHoraFin(fin);
        p.setTipoPausa(tipo);
        p.setDuracionMinutos(duracionMinutos);
        return p;
    }

    private PlanificacionAusencia ausencia(Long id, Empleado emp) {
        PlanificacionAusencia a = new PlanificacionAusencia();
        a.setId(id);
        a.setEmpleado(emp);
        return a;
    }

    /** Localiza la fila de detalle de un empleado por su id. */
    private DetallePresenciaResponse filaDe(ParteDiarioResponse parte, Long empleadoId) {
        return parte.getDetalle().stream()
                .filter(d -> d.getEmpleadoId().equals(empleadoId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No hay fila para el empleado " + empleadoId));
    }

    // ===============================================================
    // E35 — obtenerParteDiario
    // ===============================================================

    @Nested
    @DisplayName("E35 obtenerParteDiario")
    class ObtenerParteDiario {

        @Test
        @DisplayName("clasifica EN_PAUSA cuando hay fichaje abierto y pausa activa")
        void clasificaEnPausa() {
            Empleado emp = empleado(1L, "Ana", "Alvarez", "Ruiz");
            Fichaje f = fichaje(100L, emp, LocalDateTime.of(2026, 1, 15, 9, 0), null,
                    TipoFichaje.NORMAL, 0);
            Pausa p = pausa(200L, emp, LocalDateTime.of(2026, 1, 15, 11, 0), null,
                    TipoPausa.DESCANSO, null);

            when(empleadoRepository.findByActivoTrueAndFechaAltaLessThanEqual(fecha))
                    .thenReturn(List.of(emp));
            when(fichajeRepository.findByFechaWithEmpleado(fecha)).thenReturn(List.of(f));
            when(pausaRepository.findPausasActivasByFecha(fecha)).thenReturn(List.of(p));
            when(ausenciaRepository.findByFechaAndProcesadoFalse(fecha)).thenReturn(List.of());
            when(pausaRepository.findByFechaWithEmpleado(fecha)).thenReturn(List.of(p));

            ParteDiarioResponse parte = presenciaService.obtenerParteDiario(fecha);

            DetallePresenciaResponse fila = filaDe(parte, 1L);
            assertThat(fila.getEstado()).isEqualTo(EstadoPresencia.EN_PAUSA);
            assertThat(fila.getPausaActiva()).isTrue();
            assertThat(fila.getPausas()).hasSize(1);
            // EN_PAUSA suma tanto a enPausa como a trabajando (entrada sin salida)
            assertThat(parte.getEnPausa()).isEqualTo(1);
            assertThat(parte.getTrabajando()).isEqualTo(1);
        }

        @Test
        @DisplayName("clasifica JORNADA_COMPLETADA cuando hay entrada y salida")
        void clasificaJornadaCompletada() {
            Empleado emp = empleado(1L, "Ana", "Alvarez", "Ruiz");
            Fichaje f = fichaje(100L, emp, LocalDateTime.of(2026, 1, 15, 9, 0),
                    LocalDateTime.of(2026, 1, 15, 17, 0), TipoFichaje.NORMAL, 420);

            when(empleadoRepository.findByActivoTrueAndFechaAltaLessThanEqual(fecha))
                    .thenReturn(List.of(emp));
            when(fichajeRepository.findByFechaWithEmpleado(fecha)).thenReturn(List.of(f));
            when(pausaRepository.findPausasActivasByFecha(fecha)).thenReturn(List.of());
            when(ausenciaRepository.findByFechaAndProcesadoFalse(fecha)).thenReturn(List.of());
            when(pausaRepository.findByFechaWithEmpleado(fecha)).thenReturn(List.of());

            ParteDiarioResponse parte = presenciaService.obtenerParteDiario(fecha);

            DetallePresenciaResponse fila = filaDe(parte, 1L);
            assertThat(fila.getEstado()).isEqualTo(EstadoPresencia.JORNADA_COMPLETADA);
            assertThat(fila.getJornadaEfectivaMinutos()).isEqualTo(420);
            assertThat(parte.getJornadaCompletada()).isEqualTo(1);
            assertThat(parte.getTrabajando()).isZero();
        }

        @Test
        @DisplayName("clasifica JORNADA_INICIADA cuando hay entrada sin salida ni pausa")
        void clasificaJornadaIniciada() {
            Empleado emp = empleado(1L, "Ana", "Alvarez", "Ruiz");
            Fichaje f = fichaje(100L, emp, LocalDateTime.of(2026, 1, 15, 9, 0), null,
                    TipoFichaje.NORMAL, 0);

            when(empleadoRepository.findByActivoTrueAndFechaAltaLessThanEqual(fecha))
                    .thenReturn(List.of(emp));
            when(fichajeRepository.findByFechaWithEmpleado(fecha)).thenReturn(List.of(f));
            when(pausaRepository.findPausasActivasByFecha(fecha)).thenReturn(List.of());
            when(ausenciaRepository.findByFechaAndProcesadoFalse(fecha)).thenReturn(List.of());
            when(pausaRepository.findByFechaWithEmpleado(fecha)).thenReturn(List.of());

            ParteDiarioResponse parte = presenciaService.obtenerParteDiario(fecha);

            DetallePresenciaResponse fila = filaDe(parte, 1L);
            assertThat(fila.getEstado()).isEqualTo(EstadoPresencia.JORNADA_INICIADA);
            assertThat(parte.getTrabajando()).isEqualTo(1);
            assertThat(parte.getEnPausa()).isZero();
        }

        @Test
        @DisplayName("clasifica AUSENCIA_REGISTRADA cuando el fichaje no tiene hora de entrada")
        void clasificaAusenciaRegistrada() {
            Empleado emp = empleado(1L, "Ana", "Alvarez", "Ruiz");
            Fichaje f = fichaje(100L, emp, null, null, TipoFichaje.VACACIONES, 0);

            when(empleadoRepository.findByActivoTrueAndFechaAltaLessThanEqual(fecha))
                    .thenReturn(List.of(emp));
            when(fichajeRepository.findByFechaWithEmpleado(fecha)).thenReturn(List.of(f));
            when(pausaRepository.findPausasActivasByFecha(fecha)).thenReturn(List.of());
            when(ausenciaRepository.findByFechaAndProcesadoFalse(fecha)).thenReturn(List.of());
            when(pausaRepository.findByFechaWithEmpleado(fecha)).thenReturn(List.of());

            ParteDiarioResponse parte = presenciaService.obtenerParteDiario(fecha);

            DetallePresenciaResponse fila = filaDe(parte, 1L);
            assertThat(fila.getEstado()).isEqualTo(EstadoPresencia.AUSENCIA_REGISTRADA);
            assertThat(parte.getAusencias()).isEqualTo(1);
        }

        @Test
        @DisplayName("clasifica AUSENCIA_PLANIFICADA individual cuando no hay fichaje pero si ausencia")
        void clasificaAusenciaPlanificadaIndividual() {
            Empleado emp = empleado(1L, "Ana", "Alvarez", "Ruiz");
            PlanificacionAusencia a = ausencia(300L, emp);

            when(empleadoRepository.findByActivoTrueAndFechaAltaLessThanEqual(fecha))
                    .thenReturn(List.of(emp));
            when(fichajeRepository.findByFechaWithEmpleado(fecha)).thenReturn(List.of());
            when(pausaRepository.findPausasActivasByFecha(fecha)).thenReturn(List.of());
            when(ausenciaRepository.findByFechaAndProcesadoFalse(fecha)).thenReturn(List.of(a));
            when(pausaRepository.findByFechaWithEmpleado(fecha)).thenReturn(List.of());

            ParteDiarioResponse parte = presenciaService.obtenerParteDiario(fecha);

            DetallePresenciaResponse fila = filaDe(parte, 1L);
            assertThat(fila.getEstado()).isEqualTo(EstadoPresencia.AUSENCIA_PLANIFICADA);
            assertThat(fila.getAusenciaId()).isEqualTo(300L);
            assertThat(parte.getAusencias()).isEqualTo(1);
        }

        @Test
        @DisplayName("clasifica SIN_JUSTIFICAR cuando no hay fichaje ni ausencia ni festivo")
        void clasificaSinJustificar() {
            Empleado emp = empleado(1L, "Ana", "Alvarez", "Ruiz");

            when(empleadoRepository.findByActivoTrueAndFechaAltaLessThanEqual(fecha))
                    .thenReturn(List.of(emp));
            when(fichajeRepository.findByFechaWithEmpleado(fecha)).thenReturn(List.of());
            when(pausaRepository.findPausasActivasByFecha(fecha)).thenReturn(List.of());
            when(ausenciaRepository.findByFechaAndProcesadoFalse(fecha)).thenReturn(List.of());
            when(pausaRepository.findByFechaWithEmpleado(fecha)).thenReturn(List.of());

            ParteDiarioResponse parte = presenciaService.obtenerParteDiario(fecha);

            DetallePresenciaResponse fila = filaDe(parte, 1L);
            assertThat(fila.getEstado()).isEqualTo(EstadoPresencia.SIN_JUSTIFICAR);
            assertThat(parte.getSinJustificar()).isEqualTo(1);
        }

        @Test
        @DisplayName("festivo global marca AUSENCIA_PLANIFICADA a los empleados sin fichaje")
        void festivoGlobalMarcaPlanificada() {
            Empleado emp = empleado(1L, "Ana", "Alvarez", "Ruiz");
            // Ausencia con empleado = null => festivo global
            PlanificacionAusencia festivo = ausencia(400L, null);

            when(empleadoRepository.findByActivoTrueAndFechaAltaLessThanEqual(fecha))
                    .thenReturn(List.of(emp));
            when(fichajeRepository.findByFechaWithEmpleado(fecha)).thenReturn(List.of());
            when(pausaRepository.findPausasActivasByFecha(fecha)).thenReturn(List.of());
            when(ausenciaRepository.findByFechaAndProcesadoFalse(fecha)).thenReturn(List.of(festivo));
            when(pausaRepository.findByFechaWithEmpleado(fecha)).thenReturn(List.of());

            ParteDiarioResponse parte = presenciaService.obtenerParteDiario(fecha);

            DetallePresenciaResponse fila = filaDe(parte, 1L);
            assertThat(fila.getEstado()).isEqualTo(EstadoPresencia.AUSENCIA_PLANIFICADA);
            // El festivo global no es individual: ausenciaId queda null
            assertThat(fila.getAusenciaId()).isNull();
            assertThat(parte.getSinJustificar()).isZero();
            assertThat(parte.getAusencias()).isEqualTo(1);
        }

        @Test
        @DisplayName("ordena el detalle por apellido1, apellido2 y nombre")
        void ordenaAlfabeticamente() {
            Empleado c = empleado(1L, "Carlos", "Zapata", null);
            Empleado a = empleado(2L, "Ana", "Alvarez", "Bravo");
            Empleado b = empleado(3L, "Beatriz", "Alvarez", "Acosta");

            when(empleadoRepository.findByActivoTrueAndFechaAltaLessThanEqual(fecha))
                    .thenReturn(List.of(c, a, b));
            when(fichajeRepository.findByFechaWithEmpleado(fecha)).thenReturn(List.of());
            when(pausaRepository.findPausasActivasByFecha(fecha)).thenReturn(List.of());
            when(ausenciaRepository.findByFechaAndProcesadoFalse(fecha)).thenReturn(List.of());
            when(pausaRepository.findByFechaWithEmpleado(fecha)).thenReturn(List.of());

            ParteDiarioResponse parte = presenciaService.obtenerParteDiario(fecha);

            // Alvarez Acosta (Beatriz) < Alvarez Bravo (Ana) < Zapata (Carlos)
            assertThat(parte.getDetalle())
                    .extracting(DetallePresenciaResponse::getEmpleadoId)
                    .containsExactly(3L, 2L, 1L);
        }

        @Test
        @DisplayName("devuelve contadores en cero y detalle vacio cuando no hay empleados operativos")
        void empleadosVacios() {
            when(empleadoRepository.findByActivoTrueAndFechaAltaLessThanEqual(fecha))
                    .thenReturn(List.of());
            when(fichajeRepository.findByFechaWithEmpleado(fecha)).thenReturn(List.of());
            when(pausaRepository.findPausasActivasByFecha(fecha)).thenReturn(List.of());
            when(ausenciaRepository.findByFechaAndProcesadoFalse(fecha)).thenReturn(List.of());
            when(pausaRepository.findByFechaWithEmpleado(fecha)).thenReturn(List.of());

            ParteDiarioResponse parte = presenciaService.obtenerParteDiario(fecha);

            assertThat(parte.getTotalEmpleados()).isZero();
            assertThat(parte.getTrabajando()).isZero();
            assertThat(parte.getEnPausa()).isZero();
            assertThat(parte.getAusencias()).isZero();
            assertThat(parte.getSinJustificar()).isZero();
            assertThat(parte.getJornadaCompletada()).isZero();
            assertThat(parte.getDetalle()).isEmpty();
        }

        @Test
        @DisplayName("agrega contadores correctos con una mezcla de estados")
        void mezclaDeEstados() {
            Empleado e1 = empleado(1L, "Ana", "Alvarez", null);       // JORNADA_COMPLETADA
            Empleado e2 = empleado(2L, "Bea", "Bravo", null);         // JORNADA_INICIADA
            Empleado e3 = empleado(3L, "Cris", "Cano", null);         // EN_PAUSA
            Empleado e4 = empleado(4L, "Dan", "Diaz", null);          // AUSENCIA_PLANIFICADA
            Empleado e5 = empleado(5L, "Eva", "Esteban", null);       // SIN_JUSTIFICAR

            Fichaje f1 = fichaje(101L, e1, LocalDateTime.of(2026, 1, 15, 9, 0),
                    LocalDateTime.of(2026, 1, 15, 17, 0), TipoFichaje.NORMAL, 480);
            Fichaje f2 = fichaje(102L, e2, LocalDateTime.of(2026, 1, 15, 9, 0), null,
                    TipoFichaje.NORMAL, 0);
            Fichaje f3 = fichaje(103L, e3, LocalDateTime.of(2026, 1, 15, 9, 0), null,
                    TipoFichaje.NORMAL, 0);
            Pausa p3 = pausa(203L, e3, LocalDateTime.of(2026, 1, 15, 11, 0), null,
                    TipoPausa.DESCANSO, null);
            PlanificacionAusencia a4 = ausencia(304L, e4);

            when(empleadoRepository.findByActivoTrueAndFechaAltaLessThanEqual(fecha))
                    .thenReturn(List.of(e1, e2, e3, e4, e5));
            when(fichajeRepository.findByFechaWithEmpleado(fecha)).thenReturn(List.of(f1, f2, f3));
            when(pausaRepository.findPausasActivasByFecha(fecha)).thenReturn(List.of(p3));
            when(ausenciaRepository.findByFechaAndProcesadoFalse(fecha)).thenReturn(List.of(a4));
            when(pausaRepository.findByFechaWithEmpleado(fecha)).thenReturn(List.of(p3));

            ParteDiarioResponse parte = presenciaService.obtenerParteDiario(fecha);

            assertThat(parte.getTotalEmpleados()).isEqualTo(5);
            assertThat(parte.getJornadaCompletada()).isEqualTo(1);
            // trabajando = JORNADA_INICIADA + EN_PAUSA
            assertThat(parte.getTrabajando()).isEqualTo(2);
            assertThat(parte.getEnPausa()).isEqualTo(1);
            assertThat(parte.getAusencias()).isEqualTo(1);
            assertThat(parte.getSinJustificar()).isEqualTo(1);
        }
    }

    // ===============================================================
    // E36 — obtenerSinJustificar
    // ===============================================================

    @Nested
    @DisplayName("E36 obtenerSinJustificar")
    class ObtenerSinJustificar {

        @Test
        @DisplayName("devuelve solo los empleados en estado SIN_JUSTIFICAR")
        void devuelveSoloSinJustificar() {
            Empleado fichado = empleado(1L, "Ana", "Alvarez", null);     // JORNADA_INICIADA
            Empleado sinNada = empleado(2L, "Bea", "Bravo", "Cano");     // SIN_JUSTIFICAR
            Fichaje f = fichaje(101L, fichado, LocalDateTime.of(2026, 1, 15, 9, 0), null,
                    TipoFichaje.NORMAL, 0);

            when(empleadoRepository.findByActivoTrueAndFechaAltaLessThanEqual(fecha))
                    .thenReturn(List.of(fichado, sinNada));
            when(fichajeRepository.findByFechaWithEmpleado(fecha)).thenReturn(List.of(f));
            when(pausaRepository.findPausasActivasByFecha(fecha)).thenReturn(List.of());
            when(ausenciaRepository.findByFechaAndProcesadoFalse(fecha)).thenReturn(List.of());
            when(pausaRepository.findByFechaWithEmpleado(fecha)).thenReturn(List.of());

            List<SinJustificarResponse> lista = presenciaService.obtenerSinJustificar(fecha);

            assertThat(lista).hasSize(1);
            SinJustificarResponse item = lista.get(0);
            assertThat(item.getEmpleadoId()).isEqualTo(2L);
            assertThat(item.getNombre()).isEqualTo("Bea");
            assertThat(item.getApellido1()).isEqualTo("Bravo");
            assertThat(item.getApellido2()).isEqualTo("Cano");
        }

        @Test
        @DisplayName("devuelve lista vacia cuando hay festivo global")
        void festivoGlobalListaVacia() {
            Empleado emp = empleado(1L, "Ana", "Alvarez", null);
            PlanificacionAusencia festivo = ausencia(400L, null);

            when(empleadoRepository.findByActivoTrueAndFechaAltaLessThanEqual(fecha))
                    .thenReturn(List.of(emp));
            when(fichajeRepository.findByFechaWithEmpleado(fecha)).thenReturn(List.of());
            when(pausaRepository.findPausasActivasByFecha(fecha)).thenReturn(List.of());
            when(ausenciaRepository.findByFechaAndProcesadoFalse(fecha)).thenReturn(List.of(festivo));
            when(pausaRepository.findByFechaWithEmpleado(fecha)).thenReturn(List.of());

            List<SinJustificarResponse> lista = presenciaService.obtenerSinJustificar(fecha);

            assertThat(lista).isEmpty();
        }
    }

    // ===============================================================
    // E37 — obtenerMiPresencia
    // ===============================================================

    @Nested
    @DisplayName("E37 obtenerMiPresencia")
    class ObtenerMiPresencia {

        @Test
        @DisplayName("resuelve el empleado por username y devuelve su detalle con pausas del dia")
        void devuelveDetallePropio() {
            Empleado emp = empleado(1L, "Ana", "Alvarez", "Ruiz");
            Fichaje f = fichaje(100L, emp, LocalDateTime.of(2026, 1, 15, 9, 0), null,
                    TipoFichaje.NORMAL, 0);
            Pausa p = pausa(200L, emp, LocalDateTime.of(2026, 1, 15, 11, 0), null,
                    TipoPausa.DESCANSO, null);

            when(empleadoRepository.findByUsuarioUsername("ana"))
                    .thenReturn(Optional.of(emp));
            when(fichajeRepository.findByEmpleadoIdAndFecha(1L, fecha))
                    .thenReturn(Optional.of(f));
            when(pausaRepository.findByEmpleadoIdAndFechaAndHoraFinIsNull(1L, fecha))
                    .thenReturn(Optional.of(p));
            when(ausenciaRepository.findByFechaAndProcesadoFalse(fecha)).thenReturn(List.of());
            when(pausaRepository.findByEmpleadoIdAndFecha(1L, fecha)).thenReturn(List.of(p));

            DetallePresenciaResponse detalle = presenciaService.obtenerMiPresencia("ana", fecha);

            assertThat(detalle.getEmpleadoId()).isEqualTo(1L);
            // fichaje abierto + pausa activa => EN_PAUSA
            assertThat(detalle.getEstado()).isEqualTo(EstadoPresencia.EN_PAUSA);
            assertThat(detalle.getPausaActiva()).isTrue();
            assertThat(detalle.getPausas()).hasSize(1);
        }

        @Test
        @DisplayName("clasifica SIN_JUSTIFICAR cuando el empleado no tiene ningun registro")
        void detallePropioSinJustificar() {
            Empleado emp = empleado(1L, "Ana", "Alvarez", "Ruiz");

            when(empleadoRepository.findByUsuarioUsername("ana"))
                    .thenReturn(Optional.of(emp));
            when(fichajeRepository.findByEmpleadoIdAndFecha(1L, fecha))
                    .thenReturn(Optional.empty());
            when(pausaRepository.findByEmpleadoIdAndFechaAndHoraFinIsNull(1L, fecha))
                    .thenReturn(Optional.empty());
            when(ausenciaRepository.findByFechaAndProcesadoFalse(fecha)).thenReturn(List.of());
            when(pausaRepository.findByEmpleadoIdAndFecha(1L, fecha)).thenReturn(List.of());

            DetallePresenciaResponse detalle = presenciaService.obtenerMiPresencia("ana", fecha);

            assertThat(detalle.getEstado()).isEqualTo(EstadoPresencia.SIN_JUSTIFICAR);
            assertThat(detalle.getPausas()).isEmpty();
        }

        @Test
        @DisplayName("lanza NotFoundException cuando el username no tiene perfil de empleado")
        void usernameSinEmpleado() {
            when(empleadoRepository.findByUsuarioUsername("fantasma"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> presenciaService.obtenerMiPresencia("fantasma", fecha))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("no tiene perfil de empleado");
        }
    }
}
