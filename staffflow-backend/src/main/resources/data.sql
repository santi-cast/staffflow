-- =============================================================================
-- StaffFlow — Datos iniciales para perfil dev (H2 en memoria)
-- Versión: v5 (18/05/2026)
-- Cambios respecto a v4:
--   - Rango de datos extendido: 30/03 → 17/05/2026 (antes 30/03 → 21/04).
--   - "Hoy" simulado: lun 18/05/2026 sin fichajes (antes 22/04). Esto deja
--     el día abierto para fichar en vivo durante la grabación de la demo.
--   - Añadidos 17 días laborables nuevos por empleado (22/04 → 15/05) con
--     mezcla de NORMAL, BAJA_MEDICA, ASUNTO_PROPIO, PERMISO_RETRIBUIDO,
--     VACACIONES, DIA_LIBRE y horas extra que recuperan parte del saldo
--     negativo acumulado.
--   - Añadidos 24 fichajes DIA_LIBRE de fin de semana (sáb+dom × 3 empleados
--     × 4 fines de semana del rango nuevo + 16/05 cerrado): simulan el
--     descanso semanal que ProcesoCierreDiario crea cada noche del viernes
--     y del sábado.
--   - Festivos 01/05 (Día del Trabajo, nacional) y 15/05 (San Isidro, local
--     Madrid) procesados: 6 fichajes nuevos + procesado=TRUE en las dos
--     planificaciones existentes.
--   - Planificación nueva Laura 04/05 DIA_LIBRE (puente empresa, procesado=TRUE)
--     + fichaje correspondiente.
--   - Planificación existente Carlos 08/05 ASUNTO_PROPIO marcada como
--     procesado=TRUE + fichaje correspondiente.
--   - Saldos recalculados a mano con calculado_hasta_fecha='2026-05-17'
--     siguiendo SaldoService.recalcularParaProceso().
--   - FIX: 20 fichajes históricos del rango 30/03→21/04 corregidos para
--     reflejar la política real del scheduler. ProcesoCierreDiario pone
--     siempre usuario_id=5 (terminal_service) en los fichajes que genera
--     automáticamente (festivos globales, ausencias, vacaciones procesadas,
--     baja médica procesada, etc.). Antes estos fichajes tenían el
--     usuario_id del humano que los planificó, lo que es semánticamente
--     incorrecto (ese usuario_id solo debe vivir en planificacion_ausencias).
--     El bug visualmente no se nota porque InformeService filtra los
--     "candidatos manuales" comprobando si existe planificación previa
--     (existsByEmpleadoIdAndFecha), pero el dato en BD ahora es coherente.
-- =============================================================================
-- POLÍTICA DE usuario_id EN FICHAJES (importante para entender los datos)
-- ---------------------------------------------------------------------------
-- usuario_id en `fichajes` identifica al AUTOR TÉCNICO del registro:
--   - El propio empleado (3 Ana, 4 Carlos, 2 Laura) para fichajes NORMAL
--     hechos por PIN en el terminal.
--   - 1 (admin001) o 2 (usu001) para fichajes creados/corregidos manualmente
--     desde la app de gestión, con observaciones obligatorias.
--   - 5 (terminal_service) para TODO fichaje generado por ProcesoCierreDiario:
--       · AUSENCIA_INJUSTIFICADA del cierre nocturno de un día laborable sin
--         fichaje (Tarea A).
--       · DIA_LIBRE de sábado/domingo creado por Tarea A o Tarea B.
--       · Cualquier fichaje materializado desde planificacion_ausencias por
--         Tarea B (FESTIVO_*, VACACIONES, ASUNTO_PROPIO, PERMISO_RETRIBUIDO,
--         BAJA_MEDICA, DIA_LIBRE_COMPENSATORIO, DIA_LIBRE planificado).
--
-- El autor humano de la decisión (quien planificó el festivo o las vacaciones)
-- vive en `planificacion_ausencias.usuario_id`, NO en `fichajes.usuario_id`.
-- Esta separación permite que InformeService detecte intervenciones manuales
-- reales sin confundirlas con planificaciones procesadas. Detalle técnico
-- completo en B7 §7.1 (Tareas A/B/C) y B12 Anexo 2 (DDL).
-- =============================================================================
-- Se ejecuta automáticamente al arrancar con perfil 'dev' gracias a:
--   spring.jpa.defer-datasource-initialization: true
--   spring.sql.init.mode: always
-- en application-dev.yml.
--
-- No se usan IDs explícitos: H2 los asigna automáticamente con autoincrement.
-- El orden de inserción es importante: usuarios antes que empleados (FK usuario_id).
--
-- Credenciales de prueba (contraseña 'admin1234' para todos):
--   admin001     → ADMIN
--   usu001       → ENCARGADO   (PIN terminal: 3333)
--   usu002       → EMPLEADO    (PIN terminal: 1111)
--   usu003       → EMPLEADO    (PIN terminal: 2222)
--   terminal_service → solo para auditoría interna, nunca para login
--
-- Fichajes y pausas: Ana García (usu002, empleadoId=1), Carlos López
-- (usu003, empleadoId=2) y Laura Fernández (usu001, empleadoId=3),
-- del 30/03 al 17/05/2026.
-- Hoy (18/05) sin fichajes para fichar en vivo durante la grabación.
--
-- Saldos: fecha_alta=30/03 → prorrateo automático en crearSaldoInicial():
--   - Vacaciones: ceil(22 × 277/365) = 17 días
--   - Asuntos propios: round(3 × 277/365) = 2 días
--
-- usuario_id referencia:
--   1 = admin001
--   2 = usu001
--   3 = usu002 (Ana)
--   4 = usu003 (Carlos)
--   5 = terminal_service
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. CONFIGURACION DE EMPRESA (singleton obligatorio)
-- -----------------------------------------------------------------------------
INSERT INTO configuracion_empresa (nombre_empresa, cif, direccion, email, telefono, logo_path)
VALUES (
    'StaffFlow Demo S.L.',
    'B12345678',
    'Calle Gran Via 1, 28013 Madrid',
    'contacto@staffflow.demo',
    '910000001',
    'src/main/resources/static/logo_empresa.png'
);


-- -----------------------------------------------------------------------------
-- 2. USUARIOS
-- -----------------------------------------------------------------------------

INSERT INTO usuarios (username, password_hash, email, rol, activo, fecha_creacion)
VALUES (
    'admin001',
    '$2a$10$HaOeyYyuQOjcaNZ/zkhOsu/2f.SYeFK3G1XCfWXVAftuRHvKUb9eW',
    'santicastnuevo@gmail.com',
    'ADMIN',
    TRUE,
    '2026-01-01 00:00:00'
);

INSERT INTO usuarios (username, password_hash, email, rol, activo, fecha_creacion)
VALUES (
    'usu001',
    '$2a$10$HaOeyYyuQOjcaNZ/zkhOsu/2f.SYeFK3G1XCfWXVAftuRHvKUb9eW',
    'usu001@staffflow.demo',
    'ENCARGADO',
    TRUE,
    '2026-01-01 00:00:00'
);

INSERT INTO usuarios (username, password_hash, email, rol, activo, fecha_creacion)
VALUES (
    'usu002',
    '$2a$10$HaOeyYyuQOjcaNZ/zkhOsu/2f.SYeFK3G1XCfWXVAftuRHvKUb9eW',
    'usu002@staffflow.demo',
    'EMPLEADO',
    TRUE,
    '2026-01-01 00:00:00'
);

INSERT INTO usuarios (username, password_hash, email, rol, activo, fecha_creacion)
VALUES (
    'usu003',
    '$2a$10$HaOeyYyuQOjcaNZ/zkhOsu/2f.SYeFK3G1XCfWXVAftuRHvKUb9eW',
    'usu003@staffflow.demo',
    'EMPLEADO',
    TRUE,
    '2026-01-01 00:00:00'
);

INSERT INTO usuarios (username, password_hash, email, rol, activo, fecha_creacion)
VALUES (
    'terminal_service',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lmuS',
    'terminal@staffflow.internal',
    'ADMIN',
    TRUE,
    '2026-01-01 00:00:00'
);


-- -----------------------------------------------------------------------------
-- 3. EMPLEADOS — fecha_alta = 2026-03-30 para probar prorrateo de saldos
-- -----------------------------------------------------------------------------

INSERT INTO empleados (
    usuario_id, nombre, apellido1, apellido2, dni, numero_empleado,
    fecha_alta, categoria, jornada_semanal_horas, jornada_diaria_minutos,
    dias_vacaciones_anuales, dias_asuntos_propios_anuales,
    pin_terminal, codigo_nfc, activo
) VALUES (
    3, 'Ana', 'Garcia', 'Lopez', '11111111A', 'EMP-001',
    '2026-03-30', 'OPERARIO', 40.00, 480,
    22, 3,
    '1111', NULL, TRUE
);

INSERT INTO empleados (
    usuario_id, nombre, apellido1, apellido2, dni, numero_empleado,
    fecha_alta, categoria, jornada_semanal_horas, jornada_diaria_minutos,
    dias_vacaciones_anuales, dias_asuntos_propios_anuales,
    pin_terminal, codigo_nfc, activo
) VALUES (
    4, 'Carlos', 'Lopez', 'Martinez', '22222222B', 'EMP-002',
    '2026-03-30', 'OPERARIO', 40.00, 480,
    22, 3,
    '2222', NULL, TRUE
);

INSERT INTO empleados (
    usuario_id, nombre, apellido1, apellido2, dni, numero_empleado,
    fecha_alta, categoria, jornada_semanal_horas, jornada_diaria_minutos,
    dias_vacaciones_anuales, dias_asuntos_propios_anuales,
    pin_terminal, codigo_nfc, activo
) VALUES (
    2, 'Laura', 'Fernandez', 'Ruiz', '33333333C', 'EMP-003',
    '2026-03-30', 'ENCARGADO', 40.00, 480,
    22, 3,
    '3333', NULL, TRUE
);


-- -----------------------------------------------------------------------------
-- 4. FICHAJES DE PRUEBA — 30/03 al 21/04/2026
--
-- Días hábiles:
--   Sem 30/03: lun 30/03, mar 31/03
--   Sem 31/03: mié 01/04
--   02/04 (jue) Jueves Santo — FESTIVO_NACIONAL
--   03/04 (vie) Viernes Santo — FESTIVO_NACIONAL
--   Sem 06/04: lun-vie 06-10/04
--   Sem 13/04: lun-vie 13-17/04
--   Sem 20/04: lun 20/04, mar 21/04
--   22/04 (hoy): SIN FICHAJES
--
-- Ana García (usu002, empleado_id=1): jornada 09:00-17:30
-- Carlos López (usu003, empleado_id=2): jornada 08:00-16:30
-- Laura Fernández (usu001, empleado_id=3): sin fichajes
-- -----------------------------------------------------------------------------


-- ===== ANA GARCÍA (empleado_id=1) =====

-- 30/03 lun — DIA_LIBRE_COMPENSATORIO (compensación por acuerdo previo)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-30', 'DIA_LIBRE_COMPENSATORIO',
    NULL, NULL, 0, 0, 5, 'Dia libre compensatorio por acuerdo con encargado.', '2026-03-30 00:01:00');

-- 31/03 mar — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-31', 'NORMAL',
    '2026-03-31 09:00:00', '2026-03-31 17:30:00',
    30, 480, 3, NULL, '2026-03-31 17:30:00');

-- 01/04 mié — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-01', 'NORMAL',
    '2026-04-01 09:00:00', '2026-04-01 17:30:00',
    30, 480, 3, NULL, '2026-04-01 17:30:00');

-- 02/04 jue — FESTIVO_NACIONAL (Jueves Santo)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-02', 'FESTIVO_NACIONAL',
    NULL, NULL, 0, 0, 5, 'Jueves Santo — festivo nacional.', '2026-04-02 00:01:00');

-- 03/04 vie — FESTIVO_NACIONAL (Viernes Santo)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-03', 'FESTIVO_NACIONAL',
    NULL, NULL, 0, 0, 5, 'Viernes Santo — festivo nacional.', '2026-04-03 00:01:00');

-- 06/04 lun — NORMAL, MANUAL: empleada olvidó fichar entrada, corregido por encargado
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-06', 'NORMAL',
    '2026-04-06 09:00:00', '2026-04-06 17:30:00',
    30, 480, 2, 'Empleada olvidó fichar entrada. Corregido por encargado.', '2026-04-06 17:45:00');

-- 07/04 mar — BAJA_MEDICA
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-07', 'BAJA_MEDICA',
    NULL, NULL, 0, 480, 5, 'Baja medica tramitada por encargado.', '2026-04-07 09:00:00');

-- 08/04 mié — BAJA_MEDICA
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-08', 'BAJA_MEDICA',
    NULL, NULL, 0, 480, 5, 'Continuacion baja medica.', '2026-04-08 09:00:00');

-- 09/04 jue — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-09', 'NORMAL',
    '2026-04-09 09:00:00', '2026-04-09 17:30:00',
    30, 480, 3, NULL, '2026-04-09 17:30:00');

-- 10/04 vie — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-10', 'NORMAL',
    '2026-04-10 09:00:00', '2026-04-10 17:30:00',
    30, 480, 3, NULL, '2026-04-10 17:30:00');

-- 13/04 lun — NORMAL (dos pausas: descanso 15min + comida 30min = 45min)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-13', 'NORMAL',
    '2026-04-13 09:00:00', '2026-04-13 17:30:00',
    45, 465, 3, NULL, '2026-04-13 17:30:00');

-- 14/04 mar — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-14', 'NORMAL',
    '2026-04-14 09:00:00', '2026-04-14 17:30:00',
    30, 480, 3, NULL, '2026-04-14 17:30:00');

-- 15/04 mié — PERMISO_RETRIBUIDO
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-15', 'PERMISO_RETRIBUIDO',
    NULL, NULL, 0, 480, 5, 'Permiso retribuido por gestion personal. Aprobado por encargado.', '2026-04-15 00:01:00');

-- 16/04 jue — NORMAL (pausa con intervención manual)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-16', 'NORMAL',
    '2026-04-16 09:00:00', '2026-04-16 17:30:00',
    30, 480, 3, NULL, '2026-04-16 17:30:00');

-- 17/04 vie — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-17', 'NORMAL',
    '2026-04-17 09:00:00', '2026-04-17 17:30:00',
    30, 480, 3, NULL, '2026-04-17 17:30:00');

-- 20/04 lun — VACACIONES
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-20', 'VACACIONES',
    NULL, NULL, 0, 0, 5, NULL, '2026-04-20 00:01:00');

-- 21/04 mar — VACACIONES
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-21', 'VACACIONES',
    NULL, NULL, 0, 0, 5, NULL, '2026-04-21 00:01:00');

-- ===== ANA GARCÍA — bloque 22/04 → 17/05 (v5) =====
-- Estrategia: recuperar saldo negativo (-8.25h) con varias jornadas extra.
-- Cierre estimado al 17/05: -3.25h (suma de +300 min extras sobre 13 NORMAL).

-- 22/04 mié — NORMAL con horas extra (08:00-18:00, +90 min)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-22', 'NORMAL',
    '2026-04-22 08:00:00', '2026-04-22 18:00:00',
    30, 570, 3, NULL, '2026-04-22 18:00:00');

-- 23/04 jue — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-23', 'NORMAL',
    '2026-04-23 09:00:00', '2026-04-23 17:30:00',
    30, 480, 3, NULL, '2026-04-23 17:30:00');

-- 24/04 vie — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-24', 'NORMAL',
    '2026-04-24 09:00:00', '2026-04-24 17:30:00',
    30, 480, 3, NULL, '2026-04-24 17:30:00');

-- 27/04 lun — NORMAL con horas extra (09:00-18:00, +30 min efectivos)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-27', 'NORMAL',
    '2026-04-27 09:00:00', '2026-04-27 18:00:00',
    30, 510, 3, NULL, '2026-04-27 18:00:00');

-- 28/04 mar — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-28', 'NORMAL',
    '2026-04-28 09:00:00', '2026-04-28 17:30:00',
    30, 480, 3, NULL, '2026-04-28 17:30:00');

-- 29/04 mié — ASUNTO_PROPIO
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-29', 'ASUNTO_PROPIO',
    NULL, NULL, 0, 0, 5, NULL, '2026-04-29 00:01:00');

-- 30/04 jue — NORMAL con horas extra (08:30-18:00, +90 min)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-30', 'NORMAL',
    '2026-04-30 08:30:00', '2026-04-30 18:00:00',
    30, 540, 3, NULL, '2026-04-30 18:00:00');

-- 01/05 vie — FESTIVO_NACIONAL (Día del Trabajo)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-05-01', 'FESTIVO_NACIONAL',
    NULL, NULL, 0, 0, 5, 'Dia del Trabajo — festivo nacional.', '2026-04-30 23:55:00');

-- 04/05 lun — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-05-04', 'NORMAL',
    '2026-05-04 09:00:00', '2026-05-04 17:30:00',
    30, 480, 3, NULL, '2026-05-04 17:30:00');

-- 05/05 mar — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-05-05', 'NORMAL',
    '2026-05-05 09:00:00', '2026-05-05 17:30:00',
    30, 480, 3, NULL, '2026-05-05 17:30:00');

-- 06/05 mié — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-05-06', 'NORMAL',
    '2026-05-06 09:00:00', '2026-05-06 17:30:00',
    30, 480, 3, NULL, '2026-05-06 17:30:00');

-- 07/05 jue — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-05-07', 'NORMAL',
    '2026-05-07 09:00:00', '2026-05-07 17:30:00',
    30, 480, 3, NULL, '2026-05-07 17:30:00');

-- 08/05 vie — NORMAL con horas extra (09:00-18:00, +30 min efectivos)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-05-08', 'NORMAL',
    '2026-05-08 09:00:00', '2026-05-08 18:00:00',
    30, 510, 3, NULL, '2026-05-08 18:00:00');

-- 11/05 lun — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-05-11', 'NORMAL',
    '2026-05-11 09:00:00', '2026-05-11 17:30:00',
    30, 480, 3, NULL, '2026-05-11 17:30:00');

-- 12/05 mar — PERMISO_RETRIBUIDO (gestión personal)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-05-12', 'PERMISO_RETRIBUIDO',
    NULL, NULL, 0, 480, 5, 'Permiso retribuido por gestion personal.', '2026-05-12 00:01:00');

-- 13/05 mié — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-05-13', 'NORMAL',
    '2026-05-13 09:00:00', '2026-05-13 17:30:00',
    30, 480, 3, NULL, '2026-05-13 17:30:00');

-- 14/05 jue — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-05-14', 'NORMAL',
    '2026-05-14 09:00:00', '2026-05-14 17:30:00',
    30, 480, 3, NULL, '2026-05-14 17:30:00');

-- 15/05 vie — FESTIVO_LOCAL (San Isidro, Madrid)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-05-15', 'FESTIVO_LOCAL',
    NULL, NULL, 0, 0, 5, 'San Isidro — festivo local Madrid.', '2026-05-14 23:55:00');


-- ===== CARLOS LÓPEZ (empleado_id=2) =====

-- 30/03 lun — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-30', 'NORMAL',
    '2026-03-30 08:00:00', '2026-03-30 16:30:00',
    30, 480, 4, NULL, '2026-03-30 16:30:00');

-- 31/03 mar — AUSENCIA_INJUSTIFICADA (generada automáticamente por proceso nocturno)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-31', 'AUSENCIA_INJUSTIFICADA',
    NULL, NULL, 0, 0, 5, 'Ausencia injustificada generada automaticamente.', '2026-03-31 23:55:00');

-- 01/04 mié — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-01', 'NORMAL',
    '2026-04-01 08:00:00', '2026-04-01 16:30:00',
    30, 480, 4, NULL, '2026-04-01 16:30:00');

-- 02/04 jue — FESTIVO_NACIONAL (Jueves Santo)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-02', 'FESTIVO_NACIONAL',
    NULL, NULL, 0, 0, 5, 'Jueves Santo — festivo nacional.', '2026-04-02 00:01:00');

-- 03/04 vie — FESTIVO_NACIONAL (Viernes Santo)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-03', 'FESTIVO_NACIONAL',
    NULL, NULL, 0, 0, 5, 'Viernes Santo — festivo nacional.', '2026-04-03 00:01:00');

-- 06/04 lun — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-06', 'NORMAL',
    '2026-04-06 08:00:00', '2026-04-06 16:30:00',
    30, 480, 4, NULL, '2026-04-06 16:30:00');

-- 07/04 mar — ASUNTO_PROPIO
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-07', 'ASUNTO_PROPIO',
    NULL, NULL, 0, 0, 5, NULL, '2026-04-07 00:01:00');

-- 08/04 mié — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-08', 'NORMAL',
    '2026-04-08 08:00:00', '2026-04-08 16:30:00',
    30, 480, 4, NULL, '2026-04-08 16:30:00');

-- 09/04 jue — NORMAL (dos pausas: descanso 15min + comida 30min = 45min)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-09', 'NORMAL',
    '2026-04-09 08:00:00', '2026-04-09 16:30:00',
    45, 465, 4, NULL, '2026-04-09 16:30:00');

-- 10/04 vie — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-10', 'NORMAL',
    '2026-04-10 08:00:00', '2026-04-10 16:30:00',
    30, 480, 4, NULL, '2026-04-10 16:30:00');

-- 13/04 lun — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-13', 'NORMAL',
    '2026-04-13 08:00:00', '2026-04-13 16:30:00',
    30, 480, 4, NULL, '2026-04-13 16:30:00');

-- 14/04 mar — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-14', 'NORMAL',
    '2026-04-14 08:00:00', '2026-04-14 16:30:00',
    30, 480, 4, NULL, '2026-04-14 16:30:00');

-- 15/04 mié — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-15', 'NORMAL',
    '2026-04-15 08:00:00', '2026-04-15 16:30:00',
    30, 480, 4, NULL, '2026-04-15 16:30:00');

-- 16/04 jue — PERMISO_RETRIBUIDO (cita médica con especialista)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-16', 'PERMISO_RETRIBUIDO',
    NULL, NULL, 0, 480, 5, 'Permiso retribuido por cita medica con especialista.', '2026-04-16 00:01:00');

-- 17/04 vie — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-17', 'NORMAL',
    '2026-04-17 08:00:00', '2026-04-17 16:30:00',
    30, 480, 4, NULL, '2026-04-17 16:30:00');

-- 20/04 lun — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-20', 'NORMAL',
    '2026-04-20 08:00:00', '2026-04-20 16:30:00',
    30, 480, 4, NULL, '2026-04-20 16:30:00');

-- 21/04 mar — NORMAL, salida ajustada por encargado
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-21', 'NORMAL',
    '2026-04-21 08:00:00', '2026-04-21 16:30:00',
    30, 480, 2, 'Salida ajustada por encargado.', '2026-04-21 16:45:00');

-- ===== CARLOS LÓPEZ — bloque 22/04 → 17/05 (v5) =====
-- Estrategia: recuperar saldo negativo (-8.25h) con +4h extra antes de
-- las vacaciones. Bloque de vacaciones 11-14/05 (lun-jue) + festivo local
-- 15/05 = semana entera fuera. Cierre estimado al 17/05: -4.25h.

-- 22/04 mié — NORMAL con horas extra (08:00-17:00, +60 min)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-22', 'NORMAL',
    '2026-04-22 08:00:00', '2026-04-22 17:00:00',
    30, 510, 4, NULL, '2026-04-22 17:00:00');

-- 23/04 jue — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-23', 'NORMAL',
    '2026-04-23 08:00:00', '2026-04-23 16:30:00',
    30, 480, 4, NULL, '2026-04-23 16:30:00');

-- 24/04 vie — NORMAL con horas extra (08:00-18:00, +90 min efectivos)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-24', 'NORMAL',
    '2026-04-24 08:00:00', '2026-04-24 18:00:00',
    30, 570, 4, NULL, '2026-04-24 18:00:00');

-- 27/04 lun — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-27', 'NORMAL',
    '2026-04-27 08:00:00', '2026-04-27 16:30:00',
    30, 480, 4, NULL, '2026-04-27 16:30:00');

-- 28/04 mar — BAJA_MEDICA (un día)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-28', 'BAJA_MEDICA',
    NULL, NULL, 0, 480, 5, 'Baja medica de un dia. Parte tramitado por encargado.', '2026-04-28 09:00:00');

-- 29/04 mié — NORMAL (reincorporación)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-29', 'NORMAL',
    '2026-04-29 08:00:00', '2026-04-29 16:30:00',
    30, 480, 4, NULL, '2026-04-29 16:30:00');

-- 30/04 jue — NORMAL con horas extra (08:00-17:00, +60 min)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-30', 'NORMAL',
    '2026-04-30 08:00:00', '2026-04-30 17:00:00',
    30, 510, 4, NULL, '2026-04-30 17:00:00');

-- 01/05 vie — FESTIVO_NACIONAL (Día del Trabajo)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-05-01', 'FESTIVO_NACIONAL',
    NULL, NULL, 0, 0, 5, 'Dia del Trabajo — festivo nacional.', '2026-04-30 23:55:00');

-- 04/05 lun — NORMAL, olvido de salida corregido por encargado
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-05-04', 'NORMAL',
    '2026-05-04 08:00:00', '2026-05-04 16:30:00',
    30, 480, 2, 'Empleado olvido fichar salida. Corregido por encargado.', '2026-05-04 17:15:00');

-- 05/05 mar — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-05-05', 'NORMAL',
    '2026-05-05 08:00:00', '2026-05-05 16:30:00',
    30, 480, 4, NULL, '2026-05-05 16:30:00');

-- 06/05 mié — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-05-06', 'NORMAL',
    '2026-05-06 08:00:00', '2026-05-06 16:30:00',
    30, 480, 4, NULL, '2026-05-06 16:30:00');

-- 07/05 jue — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-05-07', 'NORMAL',
    '2026-05-07 08:00:00', '2026-05-07 16:30:00',
    30, 480, 4, NULL, '2026-05-07 16:30:00');

-- 08/05 vie — ASUNTO_PROPIO (planificación previa procesada)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-05-08', 'ASUNTO_PROPIO',
    NULL, NULL, 0, 0, 5, NULL, '2026-05-08 00:01:00');

-- 11/05 lun — VACACIONES (1/4 del bloque)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-05-11', 'VACACIONES',
    NULL, NULL, 0, 0, 5, 'Vacaciones — bloque 11-14/05.', '2026-05-11 00:01:00');

-- 12/05 mar — VACACIONES (2/4)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-05-12', 'VACACIONES',
    NULL, NULL, 0, 0, 5, 'Vacaciones — bloque 11-14/05.', '2026-05-12 00:01:00');

-- 13/05 mié — VACACIONES (3/4)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-05-13', 'VACACIONES',
    NULL, NULL, 0, 0, 5, 'Vacaciones — bloque 11-14/05.', '2026-05-13 00:01:00');

-- 14/05 jue — VACACIONES (4/4)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-05-14', 'VACACIONES',
    NULL, NULL, 0, 0, 5, 'Vacaciones — bloque 11-14/05.', '2026-05-14 00:01:00');

-- 15/05 vie — FESTIVO_LOCAL (San Isidro, Madrid)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-05-15', 'FESTIVO_LOCAL',
    NULL, NULL, 0, 0, 5, 'San Isidro — festivo local Madrid.', '2026-05-14 23:55:00');


-- ===== LAURA FERNÁNDEZ (empleado_id=3) =====
-- Jornada: 09:00-17:30, 480 min/día.
-- Tipos cubiertos: NORMAL, AUSENCIA_INJUSTIFICADA, FESTIVO_NACIONAL,
--   BAJA_MEDICA, VACACIONES, PERMISO_RETRIBUIDO, DIA_LIBRE_COMPENSATORIO,
--   ASUNTO_PROPIO.
-- Saldo acumulado al cierre 21/04: -840 min (-14.00h).

-- 30/03 lun — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-03-30', 'NORMAL',
    '2026-03-30 09:00:00', '2026-03-30 17:30:00',
    30, 480, 2, NULL, '2026-03-30 17:30:00');

-- 31/03 mar — NORMAL con horas extra (08:30-18:30, +90 min saldo)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-03-31', 'NORMAL',
    '2026-03-31 08:30:00', '2026-03-31 18:30:00',
    30, 570, 2, NULL, '2026-03-31 18:30:00');

-- 01/04 mié — AUSENCIA_INJUSTIFICADA (generada automáticamente)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-01', 'AUSENCIA_INJUSTIFICADA',
    NULL, NULL, 0, 0, 5, 'Ausencia injustificada generada automaticamente.', '2026-04-01 23:55:00');

-- 02/04 jue — FESTIVO_NACIONAL (Jueves Santo)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-02', 'FESTIVO_NACIONAL',
    NULL, NULL, 0, 0, 5, 'Jueves Santo — festivo nacional.', '2026-04-02 00:01:00');

-- 03/04 vie — FESTIVO_NACIONAL (Viernes Santo)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-03', 'FESTIVO_NACIONAL',
    NULL, NULL, 0, 0, 5, 'Viernes Santo — festivo nacional.', '2026-04-03 00:01:00');

-- 06/04 lun — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-06', 'NORMAL',
    '2026-04-06 09:00:00', '2026-04-06 17:30:00',
    30, 480, 2, NULL, '2026-04-06 17:30:00');

-- 07/04 mar — BAJA_MEDICA
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-07', 'BAJA_MEDICA',
    NULL, NULL, 0, 480, 5, 'Baja medica tramitada por admin.', '2026-04-07 09:00:00');

-- 08/04 mié — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-08', 'NORMAL',
    '2026-04-08 09:00:00', '2026-04-08 17:30:00',
    30, 480, 2, NULL, '2026-04-08 17:30:00');

-- 09/04 jue — NORMAL con ligero retraso (09:15-17:30, -15 min saldo)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-09', 'NORMAL',
    '2026-04-09 09:15:00', '2026-04-09 17:30:00',
    30, 465, 2, NULL, '2026-04-09 17:30:00');

-- 10/04 vie — NORMAL salida anticipada (09:00-17:00, -30 min saldo)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-10', 'NORMAL',
    '2026-04-10 09:00:00', '2026-04-10 17:00:00',
    30, 450, 2, NULL, '2026-04-10 17:00:00');

-- 13/04 lun — VACACIONES
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-13', 'VACACIONES',
    NULL, NULL, 0, 0, 5, NULL, '2026-04-13 00:01:00');

-- 14/04 mar — VACACIONES
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-14', 'VACACIONES',
    NULL, NULL, 0, 0, 5, NULL, '2026-04-14 00:01:00');

-- 15/04 mié — PERMISO_RETRIBUIDO
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-15', 'PERMISO_RETRIBUIDO',
    NULL, NULL, 0, 480, 5, 'Permiso retribuido por asunto familiar.', '2026-04-15 00:01:00');

-- 16/04 jue — NORMAL con horas extra (08:00-18:00, dos pausas, +75 min saldo)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-16', 'NORMAL',
    '2026-04-16 08:00:00', '2026-04-16 18:00:00',
    45, 555, 2, NULL, '2026-04-16 18:00:00');

-- 17/04 vie — DIA_LIBRE_COMPENSATORIO
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-17', 'DIA_LIBRE_COMPENSATORIO',
    NULL, NULL, 0, 0, 5, 'Dia libre compensatorio por horas extra del 16/04.', '2026-04-17 00:01:00');

-- 20/04 lun — ASUNTO_PROPIO
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-20', 'ASUNTO_PROPIO',
    NULL, NULL, 0, 0, 5, NULL, '2026-04-20 00:01:00');

-- 21/04 mar — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-21', 'NORMAL',
    '2026-04-21 09:00:00', '2026-04-21 17:30:00',
    30, 480, 2, NULL, '2026-04-21 17:30:00');

-- ===== LAURA FERNÁNDEZ — bloque 22/04 → 17/05 (v5) =====
-- Estrategia: recuperar saldo negativo (-14h) con muchas horas extra.
-- Incluye un caso particular: día 24/04 con 3 pausas (DESCANSO+COMIDA+DESCANSO)
-- y un día 04/05 como DIA_LIBRE planificado por encargada (puente de empresa).
-- Cierre estimado al 17/05: -3h (suma +660 min sobre saldo previo).

-- 22/04 mié — NORMAL con horas extra (08:30-19:00, +120 min)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-22', 'NORMAL',
    '2026-04-22 08:30:00', '2026-04-22 19:00:00',
    30, 600, 2, NULL, '2026-04-22 19:00:00');

-- 23/04 jue — NORMAL con horas extra (08:30-19:00, +120 min)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-23', 'NORMAL',
    '2026-04-23 08:30:00', '2026-04-23 19:00:00',
    30, 600, 2, NULL, '2026-04-23 19:00:00');

-- 24/04 vie — NORMAL (09:00-18:00 = 540 totales, 3 pausas: 15+30+15=60min, 480 efectivos)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-24', 'NORMAL',
    '2026-04-24 09:00:00', '2026-04-24 18:00:00',
    60, 480, 2, NULL, '2026-04-24 18:00:00');

-- 27/04 lun — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-27', 'NORMAL',
    '2026-04-27 09:00:00', '2026-04-27 17:30:00',
    30, 480, 2, NULL, '2026-04-27 17:30:00');

-- 28/04 mar — NORMAL con horas extra (08:30-18:30, +90 min)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-28', 'NORMAL',
    '2026-04-28 08:30:00', '2026-04-28 18:30:00',
    30, 570, 2, NULL, '2026-04-28 18:30:00');

-- 29/04 mié — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-29', 'NORMAL',
    '2026-04-29 09:00:00', '2026-04-29 17:30:00',
    30, 480, 2, NULL, '2026-04-29 17:30:00');

-- 30/04 jue — NORMAL (09:00-18:00 = 540 totales, +30 min efectivos)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-30', 'NORMAL',
    '2026-04-30 09:00:00', '2026-04-30 18:00:00',
    30, 510, 2, NULL, '2026-04-30 18:00:00');

-- 01/05 vie — FESTIVO_NACIONAL (Día del Trabajo)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-05-01', 'FESTIVO_NACIONAL',
    NULL, NULL, 0, 0, 5, 'Dia del Trabajo — festivo nacional.', '2026-04-30 23:55:00');

-- 04/05 lun — DIA_LIBRE (puente empresa, planificado por encargada)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-05-04', 'DIA_LIBRE',
    NULL, NULL, 0, 0, 5, 'Dia libre por puente de empresa. Planificado por encargada.', '2026-05-04 00:01:00');

-- 05/05 mar — NORMAL (09:00-18:00 = 540 totales, +30 min efectivos)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-05-05', 'NORMAL',
    '2026-05-05 09:00:00', '2026-05-05 18:00:00',
    30, 510, 2, NULL, '2026-05-05 18:00:00');

-- 06/05 mié — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-05-06', 'NORMAL',
    '2026-05-06 09:00:00', '2026-05-06 17:30:00',
    30, 480, 2, NULL, '2026-05-06 17:30:00');

-- 07/05 jue — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-05-07', 'NORMAL',
    '2026-05-07 09:00:00', '2026-05-07 17:30:00',
    30, 480, 2, NULL, '2026-05-07 17:30:00');

-- 08/05 vie — NORMAL (09:00-18:30 = 570 totales, +60 min efectivos)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-05-08', 'NORMAL',
    '2026-05-08 09:00:00', '2026-05-08 18:30:00',
    30, 540, 2, NULL, '2026-05-08 18:30:00');

-- 11/05 lun — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-05-11', 'NORMAL',
    '2026-05-11 09:00:00', '2026-05-11 17:30:00',
    30, 480, 2, NULL, '2026-05-11 17:30:00');

-- 12/05 mar — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-05-12', 'NORMAL',
    '2026-05-12 09:00:00', '2026-05-12 17:30:00',
    30, 480, 2, NULL, '2026-05-12 17:30:00');

-- 13/05 mié — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-05-13', 'NORMAL',
    '2026-05-13 09:00:00', '2026-05-13 17:30:00',
    30, 480, 2, NULL, '2026-05-13 17:30:00');

-- 14/05 jue — NORMAL (09:00-18:00 = 540 totales, +30 min efectivos)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-05-14', 'NORMAL',
    '2026-05-14 09:00:00', '2026-05-14 18:00:00',
    30, 510, 2, NULL, '2026-05-14 18:00:00');

-- 15/05 vie — FESTIVO_LOCAL (San Isidro, Madrid)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-05-15', 'FESTIVO_LOCAL',
    NULL, NULL, 0, 0, 5, 'San Isidro — festivo local Madrid.', '2026-05-14 23:55:00');

-- ===== DIA_LIBRE DE FIN DE SEMANA (cierre nocturno preventivo) =====
-- Generados por ProcesoCierreDiario Tarea B cada viernes/sábado a las 23:55.
-- Cubre todo el rango 30/03 → 17/05: 7 fines de semana = 14 días × 3 empleados
-- = 42 fichajes (incluye también los 6 días del rango histórico 04-05/04,
-- 11-12/04, 18-19/04 que el data.sql v4 había dejado sin DIA_LIBRE).

-- Sáb 04/04
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-04', 'DIA_LIBRE', NULL, NULL, 0, 0, 5,
    'Dia libre generado automaticamente por ProcesoCierreDiario (SATURDAY)', '2026-04-03 23:55:00');
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-04', 'DIA_LIBRE', NULL, NULL, 0, 0, 5,
    'Dia libre generado automaticamente por ProcesoCierreDiario (SATURDAY)', '2026-04-03 23:55:00');
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-04', 'DIA_LIBRE', NULL, NULL, 0, 0, 5,
    'Dia libre generado automaticamente por ProcesoCierreDiario (SATURDAY)', '2026-04-03 23:55:00');

-- Dom 05/04
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-05', 'DIA_LIBRE', NULL, NULL, 0, 0, 5,
    'Dia libre generado automaticamente por ProcesoCierreDiario (SUNDAY)', '2026-04-04 23:55:00');
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-05', 'DIA_LIBRE', NULL, NULL, 0, 0, 5,
    'Dia libre generado automaticamente por ProcesoCierreDiario (SUNDAY)', '2026-04-04 23:55:00');
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-05', 'DIA_LIBRE', NULL, NULL, 0, 0, 5,
    'Dia libre generado automaticamente por ProcesoCierreDiario (SUNDAY)', '2026-04-04 23:55:00');

-- Sáb 11/04
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-11', 'DIA_LIBRE', NULL, NULL, 0, 0, 5,
    'Dia libre generado automaticamente por ProcesoCierreDiario (SATURDAY)', '2026-04-10 23:55:00');
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-11', 'DIA_LIBRE', NULL, NULL, 0, 0, 5,
    'Dia libre generado automaticamente por ProcesoCierreDiario (SATURDAY)', '2026-04-10 23:55:00');
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-11', 'DIA_LIBRE', NULL, NULL, 0, 0, 5,
    'Dia libre generado automaticamente por ProcesoCierreDiario (SATURDAY)', '2026-04-10 23:55:00');

-- Dom 12/04
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-12', 'DIA_LIBRE', NULL, NULL, 0, 0, 5,
    'Dia libre generado automaticamente por ProcesoCierreDiario (SUNDAY)', '2026-04-11 23:55:00');
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-12', 'DIA_LIBRE', NULL, NULL, 0, 0, 5,
    'Dia libre generado automaticamente por ProcesoCierreDiario (SUNDAY)', '2026-04-11 23:55:00');
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-12', 'DIA_LIBRE', NULL, NULL, 0, 0, 5,
    'Dia libre generado automaticamente por ProcesoCierreDiario (SUNDAY)', '2026-04-11 23:55:00');

-- Sáb 18/04
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-18', 'DIA_LIBRE', NULL, NULL, 0, 0, 5,
    'Dia libre generado automaticamente por ProcesoCierreDiario (SATURDAY)', '2026-04-17 23:55:00');
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-18', 'DIA_LIBRE', NULL, NULL, 0, 0, 5,
    'Dia libre generado automaticamente por ProcesoCierreDiario (SATURDAY)', '2026-04-17 23:55:00');
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-18', 'DIA_LIBRE', NULL, NULL, 0, 0, 5,
    'Dia libre generado automaticamente por ProcesoCierreDiario (SATURDAY)', '2026-04-17 23:55:00');

-- Dom 19/04
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-19', 'DIA_LIBRE', NULL, NULL, 0, 0, 5,
    'Dia libre generado automaticamente por ProcesoCierreDiario (SUNDAY)', '2026-04-18 23:55:00');
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-19', 'DIA_LIBRE', NULL, NULL, 0, 0, 5,
    'Dia libre generado automaticamente por ProcesoCierreDiario (SUNDAY)', '2026-04-18 23:55:00');
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-19', 'DIA_LIBRE', NULL, NULL, 0, 0, 5,
    'Dia libre generado automaticamente por ProcesoCierreDiario (SUNDAY)', '2026-04-18 23:55:00');

-- Sáb 25/04
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-25', 'DIA_LIBRE', NULL, NULL, 0, 0, 5,
    'Dia libre generado automaticamente por ProcesoCierreDiario (SATURDAY)', '2026-04-24 23:55:00');
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-25', 'DIA_LIBRE', NULL, NULL, 0, 0, 5,
    'Dia libre generado automaticamente por ProcesoCierreDiario (SATURDAY)', '2026-04-24 23:55:00');
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-25', 'DIA_LIBRE', NULL, NULL, 0, 0, 5,
    'Dia libre generado automaticamente por ProcesoCierreDiario (SATURDAY)', '2026-04-24 23:55:00');

-- Dom 26/04
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-26', 'DIA_LIBRE', NULL, NULL, 0, 0, 5,
    'Dia libre generado automaticamente por ProcesoCierreDiario (SUNDAY)', '2026-04-25 23:55:00');
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-26', 'DIA_LIBRE', NULL, NULL, 0, 0, 5,
    'Dia libre generado automaticamente por ProcesoCierreDiario (SUNDAY)', '2026-04-25 23:55:00');
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-26', 'DIA_LIBRE', NULL, NULL, 0, 0, 5,
    'Dia libre generado automaticamente por ProcesoCierreDiario (SUNDAY)', '2026-04-25 23:55:00');

-- Sáb 02/05
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-05-02', 'DIA_LIBRE', NULL, NULL, 0, 0, 5,
    'Dia libre generado automaticamente por ProcesoCierreDiario (SATURDAY)', '2026-05-01 23:55:00');
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-05-02', 'DIA_LIBRE', NULL, NULL, 0, 0, 5,
    'Dia libre generado automaticamente por ProcesoCierreDiario (SATURDAY)', '2026-05-01 23:55:00');
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-05-02', 'DIA_LIBRE', NULL, NULL, 0, 0, 5,
    'Dia libre generado automaticamente por ProcesoCierreDiario (SATURDAY)', '2026-05-01 23:55:00');

-- Dom 03/05
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-05-03', 'DIA_LIBRE', NULL, NULL, 0, 0, 5,
    'Dia libre generado automaticamente por ProcesoCierreDiario (SUNDAY)', '2026-05-02 23:55:00');
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-05-03', 'DIA_LIBRE', NULL, NULL, 0, 0, 5,
    'Dia libre generado automaticamente por ProcesoCierreDiario (SUNDAY)', '2026-05-02 23:55:00');
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-05-03', 'DIA_LIBRE', NULL, NULL, 0, 0, 5,
    'Dia libre generado automaticamente por ProcesoCierreDiario (SUNDAY)', '2026-05-02 23:55:00');

-- Sáb 09/05
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-05-09', 'DIA_LIBRE', NULL, NULL, 0, 0, 5,
    'Dia libre generado automaticamente por ProcesoCierreDiario (SATURDAY)', '2026-05-08 23:55:00');
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-05-09', 'DIA_LIBRE', NULL, NULL, 0, 0, 5,
    'Dia libre generado automaticamente por ProcesoCierreDiario (SATURDAY)', '2026-05-08 23:55:00');
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-05-09', 'DIA_LIBRE', NULL, NULL, 0, 0, 5,
    'Dia libre generado automaticamente por ProcesoCierreDiario (SATURDAY)', '2026-05-08 23:55:00');

-- Dom 10/05
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-05-10', 'DIA_LIBRE', NULL, NULL, 0, 0, 5,
    'Dia libre generado automaticamente por ProcesoCierreDiario (SUNDAY)', '2026-05-09 23:55:00');
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-05-10', 'DIA_LIBRE', NULL, NULL, 0, 0, 5,
    'Dia libre generado automaticamente por ProcesoCierreDiario (SUNDAY)', '2026-05-09 23:55:00');
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-05-10', 'DIA_LIBRE', NULL, NULL, 0, 0, 5,
    'Dia libre generado automaticamente por ProcesoCierreDiario (SUNDAY)', '2026-05-09 23:55:00');

-- Sáb 16/05
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-05-16', 'DIA_LIBRE', NULL, NULL, 0, 0, 5,
    'Dia libre generado automaticamente por ProcesoCierreDiario (SATURDAY)', '2026-05-15 23:55:00');
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-05-16', 'DIA_LIBRE', NULL, NULL, 0, 0, 5,
    'Dia libre generado automaticamente por ProcesoCierreDiario (SATURDAY)', '2026-05-15 23:55:00');
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-05-16', 'DIA_LIBRE', NULL, NULL, 0, 0, 5,
    'Dia libre generado automaticamente por ProcesoCierreDiario (SATURDAY)', '2026-05-15 23:55:00');

-- Dom 17/05
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-05-17', 'DIA_LIBRE', NULL, NULL, 0, 0, 5,
    'Dia libre generado automaticamente por ProcesoCierreDiario (SUNDAY)', '2026-05-16 23:55:00');
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-05-17', 'DIA_LIBRE', NULL, NULL, 0, 0, 5,
    'Dia libre generado automaticamente por ProcesoCierreDiario (SUNDAY)', '2026-05-16 23:55:00');
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-05-17', 'DIA_LIBRE', NULL, NULL, 0, 0, 5,
    'Dia libre generado automaticamente por ProcesoCierreDiario (SUNDAY)', '2026-05-16 23:55:00');


-- -----------------------------------------------------------------------------
-- 5. PAUSAS DE PRUEBA — ANA GARCÍA (empleado_id=1)
-- -----------------------------------------------------------------------------

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-31',
    '2026-03-31 13:30:00', '2026-03-31 14:00:00', 30,
    'COMIDA', 3, NULL, '2026-03-31 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-01',
    '2026-04-01 13:30:00', '2026-04-01 14:00:00', 30,
    'COMIDA', 3, NULL, '2026-04-01 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-06',
    '2026-04-06 13:30:00', '2026-04-06 14:00:00', 30,
    'COMIDA', 3, NULL, '2026-04-06 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-09',
    '2026-04-09 13:30:00', '2026-04-09 14:00:00', 30,
    'COMIDA', 3, NULL, '2026-04-09 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-10',
    '2026-04-10 13:30:00', '2026-04-10 14:00:00', 30,
    'COMIDA', 3, NULL, '2026-04-10 14:00:00');

-- 13/04 — dos pausas: descanso + comida
INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-13',
    '2026-04-13 10:00:00', '2026-04-13 10:15:00', 15,
    'DESCANSO', 3, NULL, '2026-04-13 10:15:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-13',
    '2026-04-13 13:30:00', '2026-04-13 14:00:00', 30,
    'COMIDA', 3, NULL, '2026-04-13 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-14',
    '2026-04-14 13:30:00', '2026-04-14 14:00:00', 30,
    'COMIDA', 3, NULL, '2026-04-14 14:00:00');

-- 16/04 — inicio y fin de pausa corregidos por encargado
INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-16',
    '2026-04-16 14:00:00', '2026-04-16 14:30:00', 30,
    'COMIDA', 2, 'Inicio y fin de pausa corregidos por encargado.', '2026-04-16 14:35:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-17',
    '2026-04-17 13:30:00', '2026-04-17 14:00:00', 30,
    'COMIDA', 3, NULL, '2026-04-17 14:00:00');

-- Bloque 22/04 → 14/05 — 1 COMIDA por dia NORMAL

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-22',
    '2026-04-22 13:30:00', '2026-04-22 14:00:00', 30,
    'COMIDA', 3, NULL, '2026-04-22 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-23',
    '2026-04-23 13:30:00', '2026-04-23 14:00:00', 30,
    'COMIDA', 3, NULL, '2026-04-23 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-24',
    '2026-04-24 13:30:00', '2026-04-24 14:00:00', 30,
    'COMIDA', 3, NULL, '2026-04-24 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-27',
    '2026-04-27 13:30:00', '2026-04-27 14:00:00', 30,
    'COMIDA', 3, NULL, '2026-04-27 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-28',
    '2026-04-28 13:30:00', '2026-04-28 14:00:00', 30,
    'COMIDA', 3, NULL, '2026-04-28 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-30',
    '2026-04-30 13:00:00', '2026-04-30 13:30:00', 30,
    'COMIDA', 3, NULL, '2026-04-30 13:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-05-04',
    '2026-05-04 13:30:00', '2026-05-04 14:00:00', 30,
    'COMIDA', 3, NULL, '2026-05-04 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-05-05',
    '2026-05-05 13:30:00', '2026-05-05 14:00:00', 30,
    'COMIDA', 3, NULL, '2026-05-05 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-05-06',
    '2026-05-06 13:30:00', '2026-05-06 14:00:00', 30,
    'COMIDA', 3, NULL, '2026-05-06 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-05-07',
    '2026-05-07 13:30:00', '2026-05-07 14:00:00', 30,
    'COMIDA', 3, NULL, '2026-05-07 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-05-08',
    '2026-05-08 13:30:00', '2026-05-08 14:00:00', 30,
    'COMIDA', 3, NULL, '2026-05-08 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-05-11',
    '2026-05-11 13:30:00', '2026-05-11 14:00:00', 30,
    'COMIDA', 3, NULL, '2026-05-11 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-05-13',
    '2026-05-13 13:30:00', '2026-05-13 14:00:00', 30,
    'COMIDA', 3, NULL, '2026-05-13 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-05-14',
    '2026-05-14 13:30:00', '2026-05-14 14:00:00', 30,
    'COMIDA', 3, NULL, '2026-05-14 14:00:00');


-- -----------------------------------------------------------------------------
-- 6. PAUSAS DE PRUEBA — CARLOS LÓPEZ (empleado_id=2)
-- -----------------------------------------------------------------------------

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-30',
    '2026-03-30 14:00:00', '2026-03-30 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-30 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-01',
    '2026-04-01 14:00:00', '2026-04-01 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-04-01 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-06',
    '2026-04-06 14:00:00', '2026-04-06 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-04-06 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-08',
    '2026-04-08 14:00:00', '2026-04-08 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-04-08 14:30:00');

-- 09/04 — dos pausas: descanso + comida
INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-09',
    '2026-04-09 10:00:00', '2026-04-09 10:15:00', 15,
    'DESCANSO', 4, NULL, '2026-04-09 10:15:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-09',
    '2026-04-09 14:00:00', '2026-04-09 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-04-09 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-10',
    '2026-04-10 14:00:00', '2026-04-10 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-04-10 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-13',
    '2026-04-13 14:00:00', '2026-04-13 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-04-13 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-14',
    '2026-04-14 14:00:00', '2026-04-14 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-04-14 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-15',
    '2026-04-15 14:00:00', '2026-04-15 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-04-15 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-17',
    '2026-04-17 14:00:00', '2026-04-17 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-04-17 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-20',
    '2026-04-20 14:00:00', '2026-04-20 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-04-20 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-21',
    '2026-04-21 14:00:00', '2026-04-21 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-04-21 14:30:00');

-- Bloque 22/04 → 14/05 — 1 COMIDA por dia NORMAL

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-22',
    '2026-04-22 14:00:00', '2026-04-22 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-04-22 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-23',
    '2026-04-23 14:00:00', '2026-04-23 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-04-23 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-24',
    '2026-04-24 14:00:00', '2026-04-24 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-04-24 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-27',
    '2026-04-27 14:00:00', '2026-04-27 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-04-27 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-29',
    '2026-04-29 14:00:00', '2026-04-29 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-04-29 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-30',
    '2026-04-30 14:00:00', '2026-04-30 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-04-30 14:30:00');

-- 04/05 — pausa con hora_fin corregida por encargado (mismo dia que olvido de salida)
INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-05-04',
    '2026-05-04 14:00:00', '2026-05-04 14:30:00', 30,
    'COMIDA', 2, 'Hora de fin de pausa corregida por encargado.', '2026-05-04 17:15:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-05-05',
    '2026-05-05 14:00:00', '2026-05-05 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-05-05 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-05-06',
    '2026-05-06 14:00:00', '2026-05-06 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-05-06 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-05-07',
    '2026-05-07 14:00:00', '2026-05-07 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-05-07 14:30:00');


-- -----------------------------------------------------------------------------
-- 7. PAUSAS DE PRUEBA — LAURA FERNÁNDEZ (empleado_id=3)
-- -----------------------------------------------------------------------------

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-03-30',
    '2026-03-30 13:30:00', '2026-03-30 14:00:00', 30,
    'COMIDA', 2, NULL, '2026-03-30 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-03-31',
    '2026-03-31 13:30:00', '2026-03-31 14:00:00', 30,
    'COMIDA', 2, NULL, '2026-03-31 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-06',
    '2026-04-06 13:30:00', '2026-04-06 14:00:00', 30,
    'COMIDA', 2, NULL, '2026-04-06 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-08',
    '2026-04-08 13:30:00', '2026-04-08 14:00:00', 30,
    'COMIDA', 2, NULL, '2026-04-08 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-09',
    '2026-04-09 13:30:00', '2026-04-09 14:00:00', 30,
    'COMIDA', 2, NULL, '2026-04-09 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-10',
    '2026-04-10 13:00:00', '2026-04-10 13:30:00', 30,
    'COMIDA', 2, NULL, '2026-04-10 13:30:00');

-- 16/04 — dos pausas: descanso + comida
INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-16',
    '2026-04-16 10:30:00', '2026-04-16 10:45:00', 15,
    'DESCANSO', 2, NULL, '2026-04-16 10:45:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-16',
    '2026-04-16 14:00:00', '2026-04-16 14:30:00', 30,
    'COMIDA', 2, NULL, '2026-04-16 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-21',
    '2026-04-21 13:30:00', '2026-04-21 14:00:00', 30,
    'COMIDA', 2, NULL, '2026-04-21 14:00:00');

-- Bloque 22/04 → 14/05

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-22',
    '2026-04-22 13:30:00', '2026-04-22 14:00:00', 30,
    'COMIDA', 2, NULL, '2026-04-22 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-23',
    '2026-04-23 13:30:00', '2026-04-23 14:00:00', 30,
    'COMIDA', 2, NULL, '2026-04-23 14:00:00');

-- 24/04 — tres pausas: DESCANSO + COMIDA + DESCANSO (caso especial)
INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-24',
    '2026-04-24 10:30:00', '2026-04-24 10:45:00', 15,
    'DESCANSO', 2, NULL, '2026-04-24 10:45:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-24',
    '2026-04-24 13:30:00', '2026-04-24 14:00:00', 30,
    'COMIDA', 2, NULL, '2026-04-24 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-24',
    '2026-04-24 16:30:00', '2026-04-24 16:45:00', 15,
    'DESCANSO', 2, NULL, '2026-04-24 16:45:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-27',
    '2026-04-27 13:30:00', '2026-04-27 14:00:00', 30,
    'COMIDA', 2, NULL, '2026-04-27 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-28',
    '2026-04-28 13:30:00', '2026-04-28 14:00:00', 30,
    'COMIDA', 2, NULL, '2026-04-28 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-29',
    '2026-04-29 13:30:00', '2026-04-29 14:00:00', 30,
    'COMIDA', 2, NULL, '2026-04-29 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-30',
    '2026-04-30 13:30:00', '2026-04-30 14:00:00', 30,
    'COMIDA', 2, NULL, '2026-04-30 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-05-05',
    '2026-05-05 13:30:00', '2026-05-05 14:00:00', 30,
    'COMIDA', 2, NULL, '2026-05-05 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-05-06',
    '2026-05-06 13:30:00', '2026-05-06 14:00:00', 30,
    'COMIDA', 2, NULL, '2026-05-06 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-05-07',
    '2026-05-07 13:30:00', '2026-05-07 14:00:00', 30,
    'COMIDA', 2, NULL, '2026-05-07 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-05-08',
    '2026-05-08 13:30:00', '2026-05-08 14:00:00', 30,
    'COMIDA', 2, NULL, '2026-05-08 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-05-11',
    '2026-05-11 13:30:00', '2026-05-11 14:00:00', 30,
    'COMIDA', 2, NULL, '2026-05-11 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-05-12',
    '2026-05-12 13:30:00', '2026-05-12 14:00:00', 30,
    'COMIDA', 2, NULL, '2026-05-12 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-05-13',
    '2026-05-13 13:30:00', '2026-05-13 14:00:00', 30,
    'COMIDA', 2, NULL, '2026-05-13 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-05-14',
    '2026-05-14 13:30:00', '2026-05-14 14:00:00', 30,
    'COMIDA', 2, NULL, '2026-05-14 14:00:00');


-- -----------------------------------------------------------------------------
-- 8. PLANIFICACION DE AUSENCIAS
--
-- Festivos globales: empleado_id=NULL → proceso nocturno crea fichaje
-- para todos los empleados activos (RF-26).
--
-- Festivos nacionales España 2026:
--   01/01 (jue) Año Nuevo            — pasado, procesado=TRUE
--   06/01 (mar) Reyes Magos          — pasado, procesado=TRUE
--   02/04 (jue) Jueves Santo         — pasado, procesado=TRUE
--   03/04 (vie) Viernes Santo        — pasado, procesado=TRUE
--   01/05 (vie) Día del Trabajo      — futuro
--   12/10 (lun) Fiesta Nacional      — futuro
--   01/11 (dom) Todos los Santos     — futuro
--   08/12 (mar) Inmaculada Concepción— futuro
--   25/12 (vie) Navidad              — futuro
--
-- Festivos locales Madrid 2026:
--   19/03 (jue) San José             — pasado, procesado=TRUE
--   15/05 (vie) San Isidro           — futuro
--   09/11 (lun) Nuestra Sra. Almudena— futuro
--
-- Nota: 15/08 (Asunción, sáb), 02/05 (Dos de Mayo, sáb) y
--       25/07 (Santiago Apóstol, sáb) caen en fin de semana en 2026.
-- -----------------------------------------------------------------------------

-- ── FESTIVOS PASADOS (procesado=TRUE) ─────────────────────────────────────

-- 01/01 Año Nuevo (festivo nacional — antes del rango de fichajes)
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado,
    usuario_id, observaciones, fecha_creacion)
VALUES (NULL, '2026-01-01', 'FESTIVO_NACIONAL', TRUE,
    1, 'Año Nuevo — festivo nacional.', '2025-12-20 09:00:00');

-- 06/01 Reyes Magos (festivo nacional — antes del rango de fichajes)
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado,
    usuario_id, observaciones, fecha_creacion)
VALUES (NULL, '2026-01-06', 'FESTIVO_NACIONAL', TRUE,
    1, 'Epifanía del Señor (Reyes Magos) — festivo nacional.', '2025-12-20 09:00:00');

-- 19/03 San José (festivo local Madrid — antes del rango de fichajes)
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado,
    usuario_id, observaciones, fecha_creacion)
VALUES (NULL, '2026-03-19', 'FESTIVO_LOCAL', TRUE,
    1, 'San José — festivo local Comunidad de Madrid.', '2026-01-10 09:00:00');

-- 02/04 Jueves Santo (dentro del rango de fichajes)
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado,
    usuario_id, observaciones, fecha_creacion)
VALUES (NULL, '2026-04-02', 'FESTIVO_NACIONAL', TRUE,
    1, 'Jueves Santo — festivo nacional.', '2026-01-10 09:00:00');

-- 03/04 Viernes Santo (dentro del rango de fichajes)
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado,
    usuario_id, observaciones, fecha_creacion)
VALUES (NULL, '2026-04-03', 'FESTIVO_NACIONAL', TRUE,
    1, 'Viernes Santo — festivo nacional.', '2026-01-10 09:00:00');

-- ── AUSENCIAS INDIVIDUALES PASADAS (procesado=TRUE) ───────────────────────

-- Ana 30/03 — DIA_LIBRE_COMPENSATORIO
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado,
    usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-30', 'DIA_LIBRE_COMPENSATORIO', TRUE,
    2, 'Dia libre compensatorio por acuerdo con encargado.', '2026-03-15 09:00:00');

-- Ana 15/04 — PERMISO_RETRIBUIDO
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado,
    usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-15', 'PERMISO_RETRIBUIDO', TRUE,
    2, 'Permiso retribuido por gestion personal. Aprobado por encargado.', '2026-04-14 17:00:00');

-- Ana 20/04 — VACACIONES
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado,
    usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-20', 'VACACIONES', TRUE, 2, NULL, '2026-04-01 09:00:00');

-- Ana 21/04 — VACACIONES
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado,
    usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-21', 'VACACIONES', TRUE, 2, NULL, '2026-04-01 09:00:00');

-- Carlos 07/04 — ASUNTO_PROPIO
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado,
    usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-07', 'ASUNTO_PROPIO', TRUE, 2, NULL, '2026-04-03 09:00:00');

-- Carlos 16/04 — PERMISO_RETRIBUIDO
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado,
    usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-16', 'PERMISO_RETRIBUIDO', TRUE,
    2, 'Permiso retribuido por cita medica con especialista.', '2026-04-14 09:00:00');

-- Laura 13/04 — VACACIONES
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado,
    usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-13', 'VACACIONES', TRUE, 1, NULL, '2026-04-01 09:00:00');

-- Laura 14/04 — VACACIONES
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado,
    usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-14', 'VACACIONES', TRUE, 1, NULL, '2026-04-01 09:00:00');

-- Laura 15/04 — PERMISO_RETRIBUIDO
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado,
    usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-15', 'PERMISO_RETRIBUIDO', TRUE,
    1, 'Permiso retribuido por asunto familiar.', '2026-04-14 09:00:00');

-- Laura 17/04 — DIA_LIBRE_COMPENSATORIO
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado,
    usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-17', 'DIA_LIBRE_COMPENSATORIO', TRUE,
    1, 'Dia libre compensatorio por horas extra del 16/04.', '2026-04-16 18:00:00');

-- Laura 20/04 — ASUNTO_PROPIO
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado,
    usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-20', 'ASUNTO_PROPIO', TRUE, 1, NULL, '2026-04-18 09:00:00');

-- ── FESTIVOS FUTUROS (procesado=FALSE) ────────────────────────────────────

-- 01/05 Día del Trabajo (nacional) — procesado en cierre del 30/04
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado,
    usuario_id, observaciones, fecha_creacion)
VALUES (NULL, '2026-05-01', 'FESTIVO_NACIONAL', TRUE,
    1, 'Día del Trabajo — festivo nacional.', '2026-01-10 09:00:00');

-- 15/05 San Isidro (local Madrid) — procesado en cierre del 14/05
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado,
    usuario_id, observaciones, fecha_creacion)
VALUES (NULL, '2026-05-15', 'FESTIVO_LOCAL', TRUE,
    1, 'San Isidro — festivo local Madrid.', '2026-01-10 09:00:00');

-- 12/10 Fiesta Nacional de España
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado,
    usuario_id, observaciones, fecha_creacion)
VALUES (NULL, '2026-10-12', 'FESTIVO_NACIONAL', FALSE,
    1, 'Fiesta Nacional de España — festivo nacional.', '2026-01-10 09:00:00');

-- 01/11 Todos los Santos
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado,
    usuario_id, observaciones, fecha_creacion)
VALUES (NULL, '2026-11-01', 'FESTIVO_NACIONAL', FALSE,
    1, 'Todos los Santos — festivo nacional.', '2026-01-10 09:00:00');

-- 09/11 Nuestra Señora de la Almudena (local Madrid)
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado,
    usuario_id, observaciones, fecha_creacion)
VALUES (NULL, '2026-11-09', 'FESTIVO_LOCAL', FALSE,
    1, 'Nuestra Señora de la Almudena — festivo local Madrid.', '2026-01-10 09:00:00');

-- 08/12 Inmaculada Concepción
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado,
    usuario_id, observaciones, fecha_creacion)
VALUES (NULL, '2026-12-08', 'FESTIVO_NACIONAL', FALSE,
    1, 'Inmaculada Concepción — festivo nacional.', '2026-01-10 09:00:00');

-- 25/12 Navidad
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado,
    usuario_id, observaciones, fecha_creacion)
VALUES (NULL, '2026-12-25', 'FESTIVO_NACIONAL', FALSE,
    1, 'Navidad — festivo nacional.', '2026-01-10 09:00:00');

-- -----------------------------------------------------------------------------
-- 9. SALDOS ANUALES 2026
--
-- Calculados a mano siguiendo recalcularParaProceso() sobre los fichajes
-- de la sección 4. calculado_hasta_fecha = 17/05/2026 (último día cerrado
-- por el scheduler nocturno simulado antes del "hoy" 18/05).
--
-- Prorrateo (fecha_alta=30/03, diasRestantes=277, diasAnio=365):
--   Vacaciones: ceil(22×277/365) = 17
--   Asuntos propios: round(3×277/365) = 2
--
-- Reglas de SaldoService.recalcularParaProceso() aplicadas:
--   NORMAL: dias_trab+1, saldo += (efectivos - 480 jornadaDiaria).
--   FESTIVO_NACIONAL / FESTIVO_LOCAL / DIA_LIBRE: totalmente neutro.
--   BAJA_MEDICA: dias_trab+1, dias_baja+1, saldo neutro.
--   PERMISO_RETRIBUIDO: dias_trab+1, dias_permiso+1, saldo neutro.
--   VACACIONES: vac_consum+1, saldo neutro.
--   ASUNTO_PROPIO: ap_consum+1, saldo neutro.
--   DIA_LIBRE_COMPENSATORIO: saldo -= 480 (consume horas acumuladas).
--   AUSENCIA_INJUSTIFICADA: dias_aus_inj+1, saldo -= 480.
--
-- Ana García (emp_id=1):
--   Hasta 21/04: NORMAL×10 (9×480 + 1×465) = -15, DLC×1 = -480, total -495 min.
--   22/04→14/05: NORMAL×13 con extras 22/04 +90, 27/04 +30, 30/04 +60,
--                08/05 +30. Total +210 min. ASUNTO_PROPIO×1, PERMISO×1.
--   Festivos 01/05 NAC + 15/05 LOC: neutros.
--   DIA_LIBRE finde × 8: neutros.
--   Total: -495 + 210 = -285 min = -4.75 h.
--
-- Carlos López (emp_id=2):
--   Hasta 21/04: NORMAL×10 (9×480 + 1×465) = -15, AUSENCIA_INJ×1 = -480, total -495.
--   22/04→07/05: NORMAL×10 con extras 22/04 +30, 24/04 +90, 30/04 +30.
--                Total +150 min. BAJA_MEDICA×1.
--   ASUNTO_PROPIO 08/05 + VACACIONES×4 (11-14/05) + festivos: neutros.
--   DIA_LIBRE finde × 8: neutros.
--   Total: -495 + 150 = -345 min = -5.75 h.
--
-- Laura Fernández (emp_id=3):
--   Hasta 21/04: saldo previo -840 min (NORMAL×6 con 31/03 +90, 09/04 -15,
--                10/04 -30, 16/04 +75; AUSENCIA_INJ×1 -480; DLC×1 -480).
--   22/04→14/05: NORMAL×15 con extras 22/04 +120, 23/04 +120, 28/04 +90,
--                30/04 +30, 05/05 +30, 08/05 +60, 14/05 +30. Total +480.
--   DIA_LIBRE planificado 04/05, festivos 01/05+15/05, finde × 8: neutros.
--   Total: -840 + 480 = -360 min = -6.00 h.
-- -----------------------------------------------------------------------------

INSERT INTO saldos_anuales (
    empleado_id, anio,
    dias_trabajados, dias_baja_medica, dias_permiso_retribuido, dias_ausencia_injustificada,
    dias_vacaciones_derecho_anio, dias_vacaciones_pendientes_anio_anterior,
    dias_vacaciones_consumidos, dias_vacaciones_disponibles,
    dias_asuntos_propios_derecho_anio, dias_asuntos_propios_pendientes_anterior,
    dias_asuntos_propios_consumidos, dias_asuntos_propios_disponibles,
    horas_ausencia_retribuida, saldo_horas,
    calculado_hasta_fecha, fecha_ultima_modificacion
) VALUES
-- Ana García
(1, 2026,
 27, 2, 2, 0,
 17, 0, 2, 15,
 2, 0, 1, 1,
 0.00, -4.75,
 '2026-05-17', '2026-05-17 23:55:00'),
-- Carlos López
(2, 2026,
 24, 1, 1, 1,
 17, 0, 4, 13,
 2, 0, 2, 0,
 0.00, -5.75,
 '2026-05-17', '2026-05-17 23:55:00'),
-- Laura Fernández
(3, 2026,
 25, 1, 1, 1,
 17, 0, 2, 15,
 2, 0, 1, 1,
 0.00, -6.00,
 '2026-05-17', '2026-05-17 23:55:00');


-- ── AUSENCIAS FUTURAS INDIVIDUALES ────────────────────────────────────────

-- ANA GARCIA — asuntos propios (mayo y junio)
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-05-22', 'ASUNTO_PROPIO', FALSE, 2, NULL, '2026-04-18 09:00:00');
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-06-12', 'ASUNTO_PROPIO', FALSE, 2, NULL, '2026-04-18 09:00:00');

-- CARLOS LOPEZ — asunto propio (mayo) — procesado en cierre del 07/05
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-05-08', 'ASUNTO_PROPIO', TRUE, 2, NULL, '2026-04-18 09:00:00');

-- LAURA FERNANDEZ — dia libre puente empresa (mayo) — planificado por encargada
-- y procesado en cierre del 03/05. Es la unica planificacion de tipo DIA_LIBRE
-- individual del rango: muestra que la empresa puede planificar un dia
-- de cierre puntual (puente) para un empleado concreto.
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-05-04', 'DIA_LIBRE', TRUE, 2, 'Dia libre por puente de empresa. Planificado por encargada.', '2026-04-25 10:00:00');

-- LAURA FERNANDEZ — asunto propio (junio)
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-06-05', 'ASUNTO_PROPIO', FALSE, 2, NULL, '2026-04-18 09:00:00');

-- ANA GARCIA — vacaciones de verano (21-31 julio, lun-vie, excl. fin de semana 25-26/07)
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-07-21', 'VACACIONES', FALSE, 2, 'Vacaciones de verano 2026', '2026-04-15 10:00:00');
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-07-22', 'VACACIONES', FALSE, 2, 'Vacaciones de verano 2026', '2026-04-15 10:00:00');
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-07-23', 'VACACIONES', FALSE, 2, 'Vacaciones de verano 2026', '2026-04-15 10:00:00');
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-07-24', 'VACACIONES', FALSE, 2, 'Vacaciones de verano 2026', '2026-04-15 10:00:00');
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-07-27', 'VACACIONES', FALSE, 2, 'Vacaciones de verano 2026', '2026-04-15 10:00:00');
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-07-28', 'VACACIONES', FALSE, 2, 'Vacaciones de verano 2026', '2026-04-15 10:00:00');
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-07-29', 'VACACIONES', FALSE, 2, 'Vacaciones de verano 2026', '2026-04-15 10:00:00');
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-07-30', 'VACACIONES', FALSE, 2, 'Vacaciones de verano 2026', '2026-04-15 10:00:00');
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-07-31', 'VACACIONES', FALSE, 2, 'Vacaciones de verano 2026', '2026-04-15 10:00:00');

-- CARLOS LOPEZ — vacaciones de verano (3-14 agosto, lun-vie, excl. fin de semana 8-9/08)
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-08-03', 'VACACIONES', FALSE, 2, 'Vacaciones de verano 2026', '2026-04-15 10:00:00');
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-08-04', 'VACACIONES', FALSE, 2, 'Vacaciones de verano 2026', '2026-04-15 10:00:00');
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-08-05', 'VACACIONES', FALSE, 2, 'Vacaciones de verano 2026', '2026-04-15 10:00:00');
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-08-06', 'VACACIONES', FALSE, 2, 'Vacaciones de verano 2026', '2026-04-15 10:00:00');
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-08-07', 'VACACIONES', FALSE, 2, 'Vacaciones de verano 2026', '2026-04-15 10:00:00');
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-08-10', 'VACACIONES', FALSE, 2, 'Vacaciones de verano 2026', '2026-04-15 10:00:00');
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-08-11', 'VACACIONES', FALSE, 2, 'Vacaciones de verano 2026', '2026-04-15 10:00:00');
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-08-12', 'VACACIONES', FALSE, 2, 'Vacaciones de verano 2026', '2026-04-15 10:00:00');
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-08-13', 'VACACIONES', FALSE, 2, 'Vacaciones de verano 2026', '2026-04-15 10:00:00');
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-08-14', 'VACACIONES', FALSE, 2, 'Vacaciones de verano 2026', '2026-04-15 10:00:00');

-- LAURA FERNANDEZ — vacaciones de verano (17-21 agosto)
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-08-17', 'VACACIONES', FALSE, 2, 'Vacaciones de verano 2026', '2026-04-15 10:00:00');
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-08-18', 'VACACIONES', FALSE, 2, 'Vacaciones de verano 2026', '2026-04-15 10:00:00');
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-08-19', 'VACACIONES', FALSE, 2, 'Vacaciones de verano 2026', '2026-04-15 10:00:00');
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-08-20', 'VACACIONES', FALSE, 2, 'Vacaciones de verano 2026', '2026-04-15 10:00:00');
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-08-21', 'VACACIONES', FALSE, 2, 'Vacaciones de verano 2026', '2026-04-15 10:00:00');
