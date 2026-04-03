-- =============================================================================
-- StaffFlow — Datos iniciales para perfil dev (H2 en memoria)
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
--   admin        → ADMIN
--   encargado01  → ENCARGADO   (PIN terminal: 3333)
--   emp01        → EMPLEADO    (PIN terminal: 1111)
--   emp02        → EMPLEADO    (PIN terminal: 2222)
--   terminal_service → solo para auditoría interna, nunca para login
--
-- Fichajes y pausas de prueba: Ana García (emp01, empleadoId=1)
-- y Carlos López (emp02, empleadoId=2), todo marzo 2026.
-- Laura Fernández (encargado01, empleadoId=3) sin fichajes: el informe
-- mostrará todo SIN_REGISTRO/DIA_LIBRE para verificar ese caso.
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
    NULL
);


-- -----------------------------------------------------------------------------
-- 2. USUARIOS
-- -----------------------------------------------------------------------------

INSERT INTO usuarios (username, password_hash, email, rol, activo, fecha_creacion)
VALUES (
    'admin',
    '$2a$10$HaOeyYyuQOjcaNZ/zkhOsu/2f.SYeFK3G1XCfWXVAftuRHvKUb9eW',
    'admin@staffflow.demo',
    'ADMIN',
    TRUE,
    '2026-01-01 00:00:00'
);

INSERT INTO usuarios (username, password_hash, email, rol, activo, fecha_creacion)
VALUES (
    'encargado01',
    '$2a$10$HaOeyYyuQOjcaNZ/zkhOsu/2f.SYeFK3G1XCfWXVAftuRHvKUb9eW',
    'encargado01@staffflow.demo',
    'ENCARGADO',
    TRUE,
    '2026-01-01 00:00:00'
);

INSERT INTO usuarios (username, password_hash, email, rol, activo, fecha_creacion)
VALUES (
    'emp01',
    '$2a$10$HaOeyYyuQOjcaNZ/zkhOsu/2f.SYeFK3G1XCfWXVAftuRHvKUb9eW',
    'emp01@staffflow.demo',
    'EMPLEADO',
    TRUE,
    '2026-01-01 00:00:00'
);

INSERT INTO usuarios (username, password_hash, email, rol, activo, fecha_creacion)
VALUES (
    'emp02',
    '$2a$10$HaOeyYyuQOjcaNZ/zkhOsu/2f.SYeFK3G1XCfWXVAftuRHvKUb9eW',
    'emp02@staffflow.demo',
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
-- 3. EMPLEADOS
-- -----------------------------------------------------------------------------

INSERT INTO empleados (
    usuario_id, nombre, apellido1, apellido2, dni, nss,
    fecha_alta, categoria, jornada_semanal_horas, jornada_diaria_minutos,
    dias_vacaciones_anuales, dias_asuntos_propios_anuales,
    pin_terminal, codigo_nfc, activo
) VALUES (
    3, 'Ana', 'Garcia', 'Lopez', '11111111A', '280011111111',
    '2026-01-01', 'OPERARIO', 40, 480,
    22, 3,
    '1111', NULL, TRUE
);

INSERT INTO empleados (
    usuario_id, nombre, apellido1, apellido2, dni, nss,
    fecha_alta, categoria, jornada_semanal_horas, jornada_diaria_minutos,
    dias_vacaciones_anuales, dias_asuntos_propios_anuales,
    pin_terminal, codigo_nfc, activo
) VALUES (
    4, 'Carlos', 'Lopez', 'Martinez', '22222222B', '280022222222',
    '2026-01-01', 'OPERARIO', 40, 480,
    22, 3,
    '2222', NULL, TRUE
);

INSERT INTO empleados (
    usuario_id, nombre, apellido1, apellido2, dni, nss,
    fecha_alta, categoria, jornada_semanal_horas, jornada_diaria_minutos,
    dias_vacaciones_anuales, dias_asuntos_propios_anuales,
    pin_terminal, codigo_nfc, activo
) VALUES (
    2, 'Laura', 'Fernandez', 'Ruiz', '33333333C', '280033333333',
    '2026-01-01', 'ENCARGADO', 40, 480,
    22, 3,
    '3333', NULL, TRUE
);


-- -----------------------------------------------------------------------------
-- 4. FICHAJES DE PRUEBA — MARZO 2026
--
-- Semanas de marzo 2026:
--   Lun 02 - Vie 06  (semana 1)
--   Lun 09 - Vie 13  (semana 2)
--   Lun 16 - Vie 20  (semana 3)
--   Lun 23 - Vie 27  (semana 4)
--   Lun 30 - Mar 31  (semana 5 parcial)
--
-- Fines de semana sin fichaje: el informe los mostrará como DIA_LIBRE.
-- 01/03 (dom): sin fichaje → DIA_LIBRE.
--
-- Tipos usados: NORMAL, VACACIONES, BAJA_MEDICA, FESTIVO_LOCAL,
--   PERMISO_RETRIBUIDO, DIA_LIBRE_COMPENSATORIO, AUSENCIA_INJUSTIFICADA.
--
-- Intervenciones manuales (usuario_id=2, encargado):
--   Ana 05/03: entrada olvidada, corregida por encargado.
--   Carlos 12/03: salida ajustada por encargado.
--   Ana 19/03: ausencia injustificada generada por terminal_service (usuario_id=5).
--
-- usuario_id referencia:
--   1 = admin
--   2 = encargado01
--   3 = emp01 (Ana)
--   4 = emp02 (Carlos)
--   5 = terminal_service
-- -----------------------------------------------------------------------------


-- ===== ANA GARCÍA (empleado_id=1) =====

-- Semana 1 (02-06 mar)
-- 02/03 lun — NORMAL, fichado por Ana desde terminal (usuario_id=3)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-02', 'NORMAL',
    '2026-03-02 09:00:00', '2026-03-02 17:30:00',
    30, 450, 3, NULL, '2026-03-02 17:30:00');

-- 03/03 mar — NORMAL (dos pausas: descanso 15min + comida 30min = 45min)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-03', 'NORMAL',
    '2026-03-03 09:00:00', '2026-03-03 17:30:00',
    45, 435, 3, NULL, '2026-03-03 17:30:00');

-- 04/03 mié — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-04', 'NORMAL',
    '2026-03-04 09:00:00', '2026-03-04 17:30:00',
    30, 450, 3, NULL, '2026-03-04 17:30:00');

-- 05/03 jue — NORMAL, MANUAL: empleada olvidó fichar entrada, corregido por encargado
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-05', 'NORMAL',
    '2026-03-05 09:00:00', '2026-03-05 17:30:00',
    30, 450, 2, 'Empleada olvidó fichar entrada. Corregido por encargado.', '2026-03-05 17:45:00');

-- 06/03 vie — FESTIVO_LOCAL (festivo de la Comunidad de Madrid)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-06', 'FESTIVO_LOCAL',
    NULL, NULL,
    0, 0, 2, 'Festivo local Comunidad de Madrid', '2026-03-06 00:01:00');

-- Semana 2 (09-13 mar)
-- 09/03 lun — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-09', 'NORMAL',
    '2026-03-09 09:00:00', '2026-03-09 17:30:00',
    45, 435, 3, NULL, '2026-03-09 17:30:00');

-- 10/03 mar — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-10', 'NORMAL',
    '2026-03-10 09:00:00', '2026-03-10 17:30:00',
    30, 450, 3, NULL, '2026-03-10 17:30:00');

-- 11/03 mié — BAJA_MEDICA
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-11', 'BAJA_MEDICA',
    NULL, NULL,
    0, 0, 2, 'Baja médica por gripe', '2026-03-11 00:01:00');

-- 12/03 jue — BAJA_MEDICA (continúa)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-12', 'BAJA_MEDICA',
    NULL, NULL,
    0, 0, 2, 'Baja médica por gripe (día 2)', '2026-03-12 00:01:00');

-- 13/03 vie — NORMAL (reincorporación)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-13', 'NORMAL',
    '2026-03-13 09:00:00', '2026-03-13 17:30:00',
    30, 450, 3, NULL, '2026-03-13 17:30:00');

-- Semana 3 (16-20 mar)
-- 16/03 lun — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-16', 'NORMAL',
    '2026-03-16 09:00:00', '2026-03-16 17:30:00',
    30, 450, 3, NULL, '2026-03-16 17:30:00');

-- 17/03 mar — PERMISO_RETRIBUIDO (fue a votar por la mañana, llegó tarde)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-17', 'NORMAL',
    '2026-03-17 11:00:00', '2026-03-17 17:30:00',
    30, 360, 2, '2h de permiso retribuido por ejercicio del voto. Entrada ajustada por encargado.', '2026-03-17 17:30:00');

-- 18/03 mié — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-18', 'NORMAL',
    '2026-03-18 09:00:00', '2026-03-18 17:30:00',
    30, 450, 3, NULL, '2026-03-18 17:30:00');

-- 19/03 jue — AUSENCIA_INJUSTIFICADA generada automáticamente por terminal_service
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-19', 'AUSENCIA_INJUSTIFICADA',
    NULL, NULL,
    0, 0, 5, 'Generado automaticamente por ProcesoCierreDiario', '2026-03-19 23:55:00');

-- 20/03 vie — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-20', 'NORMAL',
    '2026-03-20 09:00:00', '2026-03-20 17:30:00',
    30, 450, 3, NULL, '2026-03-20 17:30:00');

-- Semana 4 (23-27 mar)
-- 23/03 lun — VACACIONES
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-23', 'VACACIONES',
    NULL, NULL,
    0, 0, 2, NULL, '2026-03-23 00:01:00');

-- 24/03 mar — VACACIONES
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-24', 'VACACIONES',
    NULL, NULL,
    0, 0, 2, NULL, '2026-03-24 00:01:00');

-- 25/03 mié — VACACIONES
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-25', 'VACACIONES',
    NULL, NULL,
    0, 0, 2, NULL, '2026-03-25 00:01:00');

-- 26/03 jue — VACACIONES
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-26', 'VACACIONES',
    NULL, NULL,
    0, 0, 2, NULL, '2026-03-26 00:01:00');

-- 27/03 vie — VACACIONES
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-27', 'VACACIONES',
    NULL, NULL,
    0, 0, 2, NULL, '2026-03-27 00:01:00');

-- Semana 5 (30-31 mar)
-- 30/03 lun — DIA_LIBRE_COMPENSATORIO
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-30', 'DIA_LIBRE_COMPENSATORIO',
    NULL, NULL,
    0, 0, 2, 'Compensación por horas extra semana del 2 de marzo', '2026-03-30 00:01:00');

-- 31/03 mar — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-31', 'NORMAL',
    '2026-03-31 09:00:00', '2026-03-31 17:30:00',
    30, 450, 3, NULL, '2026-03-31 17:30:00');


-- ===== CARLOS LÓPEZ (empleado_id=2) =====

-- Semana 1 (02-06 mar)
-- 02/03 lun — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-02', 'NORMAL',
    '2026-03-02 08:45:00', '2026-03-02 17:15:00',
    30, 450, 4, NULL, '2026-03-02 17:15:00');

-- 03/03 mar — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-03', 'NORMAL',
    '2026-03-03 08:45:00', '2026-03-03 17:15:00',
    30, 450, 4, NULL, '2026-03-03 17:15:00');

-- 04/03 mié — ASUNTO_PROPIO
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-04', 'ASUNTO_PROPIO',
    NULL, NULL,
    0, 0, 2, NULL, '2026-03-04 00:01:00');

-- 05/03 jue — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-05', 'NORMAL',
    '2026-03-05 08:45:00', '2026-03-05 17:15:00',
    30, 450, 4, NULL, '2026-03-05 17:15:00');

-- 06/03 vie — FESTIVO_LOCAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-06', 'FESTIVO_LOCAL',
    NULL, NULL,
    0, 0, 2, 'Festivo local Comunidad de Madrid', '2026-03-06 00:01:00');

-- Semana 2 (09-13 mar)
-- 09/03 lun — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-09', 'NORMAL',
    '2026-03-09 08:45:00', '2026-03-09 17:15:00',
    30, 450, 4, NULL, '2026-03-09 17:15:00');

-- 10/03 mar — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-10', 'NORMAL',
    '2026-03-10 08:45:00', '2026-03-10 17:15:00',
    30, 450, 4, NULL, '2026-03-10 17:15:00');

-- 11/03 mié — NORMAL (dos pausas: descanso 15min + comida 30min = 45min)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-11', 'NORMAL',
    '2026-03-11 08:45:00', '2026-03-11 17:15:00',
    45, 435, 4, NULL, '2026-03-11 17:15:00');

-- 12/03 jue — NORMAL, MANUAL: salida ajustada por encargado
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-12', 'NORMAL',
    '2026-03-12 08:45:00', '2026-03-12 17:30:00',
    30, 465, 2, 'Salida ajustada por encargado. Empleado olvidó fichar salida.', '2026-03-12 18:00:00');

-- 13/03 vie — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-13', 'NORMAL',
    '2026-03-13 08:45:00', '2026-03-13 17:15:00',
    30, 450, 4, NULL, '2026-03-13 17:15:00');

-- Semana 3 (16-20 mar)
-- 16/03 lun — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-16', 'NORMAL',
    '2026-03-16 08:45:00', '2026-03-16 17:15:00',
    30, 450, 4, NULL, '2026-03-16 17:15:00');

-- 17/03 mar — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-17', 'NORMAL',
    '2026-03-17 08:45:00', '2026-03-17 17:15:00',
    30, 450, 4, NULL, '2026-03-17 17:15:00');

-- 18/03 mié — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-18', 'NORMAL',
    '2026-03-18 08:45:00', '2026-03-18 17:15:00',
    30, 450, 4, NULL, '2026-03-18 17:15:00');

-- 19/03 jue — PERMISO_RETRIBUIDO (cita médica especialista)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-19', 'PERMISO_RETRIBUIDO',
    NULL, NULL,
    0, 0, 2, 'Permiso retribuido por cita médica con especialista', '2026-03-19 00:01:00');

-- 20/03 vie — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-20', 'NORMAL',
    '2026-03-20 08:45:00', '2026-03-20 17:15:00',
    30, 450, 4, NULL, '2026-03-20 17:15:00');

-- Semana 4 (23-27 mar)
-- 23/03 lun — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-23', 'NORMAL',
    '2026-03-23 08:45:00', '2026-03-23 17:15:00',
    30, 450, 4, NULL, '2026-03-23 17:15:00');

-- 24/03 mar — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-24', 'NORMAL',
    '2026-03-24 08:45:00', '2026-03-24 17:15:00',
    30, 450, 4, NULL, '2026-03-24 17:15:00');

-- 25/03 mié — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-25', 'NORMAL',
    '2026-03-25 08:45:00', '2026-03-25 17:15:00',
    30, 450, 4, NULL, '2026-03-25 17:15:00');

-- 26/03 jue — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-26', 'NORMAL',
    '2026-03-26 08:45:00', '2026-03-26 17:15:00',
    30, 450, 4, NULL, '2026-03-26 17:15:00');

-- 27/03 vie — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-27', 'NORMAL',
    '2026-03-27 08:45:00', '2026-03-27 17:15:00',
    30, 450, 4, NULL, '2026-03-27 17:15:00');

-- Semana 5 (30-31 mar)
-- 30/03 lun — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-30', 'NORMAL',
    '2026-03-30 08:45:00', '2026-03-30 17:15:00',
    30, 450, 4, NULL, '2026-03-30 17:15:00');

-- 31/03 mar — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-31', 'NORMAL',
    '2026-03-31 08:45:00', '2026-03-31 17:15:00',
    30, 450, 4, NULL, '2026-03-31 17:15:00');


-- -----------------------------------------------------------------------------
-- 5. PAUSAS DE PRUEBA — MARZO 2026
--
-- Solo para días con jornada NORMAL y hora_entrada/hora_salida registradas.
-- No se insertan pausas para días de ausencia, festivos ni vacaciones.
-- Algunos días tienen dos pausas para probar el caso de filas múltiples.
-- Una pausa manual (usuario_id=2) para probar el asterisco en pausas.
-- -----------------------------------------------------------------------------

-- ANA GARCÍA (empleado_id=1)

-- 02/03 — pausa comida (normal, por empleada)
INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-02',
    '2026-03-02 14:00:00', '2026-03-02 14:30:00', 30,
    'COMIDA', 3, NULL, '2026-03-02 14:30:00');

-- 03/03 — pausa comida + pausa descanso (dos pausas mismo día)
INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-03',
    '2026-03-03 10:15:00', '2026-03-03 10:30:00', 15,
    'DESCANSO', 3, NULL, '2026-03-03 10:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-03',
    '2026-03-03 14:00:00', '2026-03-03 14:30:00', 30,
    'COMIDA', 3, NULL, '2026-03-03 14:30:00');

-- 04/03 — pausa comida
INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-04',
    '2026-03-04 14:00:00', '2026-03-04 14:30:00', 30,
    'COMIDA', 3, NULL, '2026-03-04 14:30:00');

-- 05/03 — pausa comida (el fichaje es manual pero la pausa la cerró ella)
INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-05',
    '2026-03-05 14:00:00', '2026-03-05 14:30:00', 30,
    'COMIDA', 3, NULL, '2026-03-05 14:30:00');

-- 09/03 — pausa comida + descanso (45min total en fichaje)
INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-09',
    '2026-03-09 10:00:00', '2026-03-09 10:15:00', 15,
    'DESCANSO', 3, NULL, '2026-03-09 10:15:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-09',
    '2026-03-09 14:00:00', '2026-03-09 14:30:00', 30,
    'COMIDA', 3, NULL, '2026-03-09 14:30:00');

-- 10/03 — pausa comida
INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-10',
    '2026-03-10 14:00:00', '2026-03-10 14:30:00', 30,
    'COMIDA', 3, NULL, '2026-03-10 14:30:00');

-- 13/03 — pausa comida
INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-13',
    '2026-03-13 14:00:00', '2026-03-13 14:30:00', 30,
    'COMIDA', 3, NULL, '2026-03-13 14:30:00');

-- 16/03 — pausa comida
INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-16',
    '2026-03-16 14:00:00', '2026-03-16 14:30:00', 30,
    'COMIDA', 3, NULL, '2026-03-16 14:30:00');

-- 17/03 — pausa comida (fichaje manual, pausa normal)
INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-17',
    '2026-03-17 14:00:00', '2026-03-17 14:30:00', 30,
    'COMIDA', 3, NULL, '2026-03-17 14:30:00');

-- 18/03 — pausa comida, MANUAL: fin de pausa cerrado por encargado
INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-18',
    '2026-03-18 14:00:00', '2026-03-18 14:30:00', 30,
    'COMIDA', 2, 'Fin de pausa cerrado manualmente. Empleada olvidó fichar vuelta de comida.', '2026-03-18 14:30:00');

-- 20/03 — pausa comida
INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-20',
    '2026-03-20 14:00:00', '2026-03-20 14:30:00', 30,
    'COMIDA', 3, NULL, '2026-03-20 14:30:00');

-- 31/03 — pausa comida
INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-31',
    '2026-03-31 14:00:00', '2026-03-31 14:30:00', 30,
    'COMIDA', 3, NULL, '2026-03-31 14:30:00');


-- -----------------------------------------------------------------------------
-- 6. PLANIFICACION DE AUSENCIAS
--
-- Registros de planificacion_ausencias para los dias que fueron planificados
-- con antelacion y materializados por ProcesoCierreDiario (procesado=true).
-- Necesarios para que el workaround D-028 en InformeService funcione
-- correctamente: si existe planificacion para un empleado y fecha,
-- el fichaje NO se marca como intervencion manual aunque lo crease el encargado.
--
-- Ausencias NO planificables (no tienen registro aqui):
--   BAJA_MEDICA: TipoAusencia no incluye BAJA_MEDICA (baja inesperada).
--   AUSENCIA_INJUSTIFICADA: la genera ProcesoCierreDiario automaticamente.
--   NORMAL con correccion manual: el empleado debia fichar el solo.
--
-- usuario_id=2 (encargado01) es quien planifica las ausencias.
-- procesado=true: ya fueron materializadas en fichajes por ProcesoCierreDiario.
-- -----------------------------------------------------------------------------

-- ANA GARCIA (empleado_id=1)

-- 06/03 — FESTIVO_LOCAL planificado
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado,
    usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-06', 'FESTIVO_LOCAL', TRUE,
    2, 'Festivo local Comunidad de Madrid', '2026-03-05 09:00:00');

-- 23-27/03 — VACACIONES planificadas
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado,
    usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-23', 'VACACIONES', TRUE,
    2, NULL, '2026-03-10 09:00:00');

INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado,
    usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-24', 'VACACIONES', TRUE,
    2, NULL, '2026-03-10 09:00:00');

INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado,
    usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-25', 'VACACIONES', TRUE,
    2, NULL, '2026-03-10 09:00:00');

INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado,
    usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-26', 'VACACIONES', TRUE,
    2, NULL, '2026-03-10 09:00:00');

INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado,
    usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-27', 'VACACIONES', TRUE,
    2, NULL, '2026-03-10 09:00:00');

-- 30/03 — DIA_LIBRE_COMPENSATORIO planificado
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado,
    usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-30', 'DIA_LIBRE_COMPENSATORIO', TRUE,
    2, 'Compensacion por horas extra semana del 2 de marzo', '2026-03-15 09:00:00');


-- CARLOS LOPEZ (empleado_id=2)

-- 04/03 — ASUNTO_PROPIO planificado
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado,
    usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-04', 'ASUNTO_PROPIO', TRUE,
    2, NULL, '2026-03-03 09:00:00');

-- 06/03 — FESTIVO_LOCAL planificado
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado,
    usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-06', 'FESTIVO_LOCAL', TRUE,
    2, 'Festivo local Comunidad de Madrid', '2026-03-05 09:00:00');

-- 19/03 — PERMISO_RETRIBUIDO planificado
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado,
    usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-19', 'PERMISO_RETRIBUIDO', TRUE,
    2, 'Permiso retribuido por cita medica con especialista', '2026-03-18 09:00:00');


-- CARLOS LOPEZ (empleado_id=2)

-- 02/03 — pausa comida
INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-02',
    '2026-03-02 14:00:00', '2026-03-02 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-02 14:30:00');

-- 03/03 — pausa comida
INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-03',
    '2026-03-03 14:00:00', '2026-03-03 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-03 14:30:00');

-- 05/03 — pausa comida
INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-05',
    '2026-03-05 14:00:00', '2026-03-05 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-05 14:30:00');

-- 09/03 — pausa comida
INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-09',
    '2026-03-09 14:00:00', '2026-03-09 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-09 14:30:00');

-- 10/03 — pausa comida
INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-10',
    '2026-03-10 14:00:00', '2026-03-10 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-10 14:30:00');

-- 11/03 — pausa comida + descanso
INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-11',
    '2026-03-11 10:00:00', '2026-03-11 10:15:00', 15,
    'DESCANSO', 4, NULL, '2026-03-11 10:15:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-11',
    '2026-03-11 14:00:00', '2026-03-11 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-11 14:30:00');

-- 12/03 — pausa comida (fichaje manual)
INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-12',
    '2026-03-12 14:00:00', '2026-03-12 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-12 14:30:00');

-- 13/03 — pausa comida
INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-13',
    '2026-03-13 14:00:00', '2026-03-13 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-13 14:30:00');

-- 16/03 — pausa comida
INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-16',
    '2026-03-16 14:00:00', '2026-03-16 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-16 14:30:00');

-- 17/03 — pausa comida
INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-17',
    '2026-03-17 14:00:00', '2026-03-17 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-17 14:30:00');

-- 18/03 — pausa comida
INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-18',
    '2026-03-18 14:00:00', '2026-03-18 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-18 14:30:00');

-- 20/03 — pausa comida
INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-20',
    '2026-03-20 14:00:00', '2026-03-20 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-20 14:30:00');

-- 23/03 al 27/03 — pausas normales (semana completa)
INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-23',
    '2026-03-23 14:00:00', '2026-03-23 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-23 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-24',
    '2026-03-24 14:00:00', '2026-03-24 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-24 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-25',
    '2026-03-25 14:00:00', '2026-03-25 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-25 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-26',
    '2026-03-26 14:00:00', '2026-03-26 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-26 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-27',
    '2026-03-27 14:00:00', '2026-03-27 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-27 14:30:00');

-- 30/03 y 31/03
INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-30',
    '2026-03-30 14:00:00', '2026-03-30 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-30 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-31',
    '2026-03-31 14:00:00', '2026-03-31 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-31 14:30:00');
