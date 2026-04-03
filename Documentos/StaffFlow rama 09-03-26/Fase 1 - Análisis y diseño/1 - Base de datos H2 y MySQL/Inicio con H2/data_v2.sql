-- =============================================================================
-- StaffFlow — Datos iniciales para perfil dev (H2 en memoria)
-- Versión: v2 (21/03/2026)
-- Cambios respecto a v1:
--   - Columna nss renombrada a numero_empleado en INSERT de empleados.
--     Valores de prueba actualizados a formato EMP-001, EMP-002, EMP-003.
--     Alineado con DDL v6 y entidad Empleado (D-030).
--   - logo_path en configuracion_empresa apunta a
--     src/main/resources/static/logo_empresa.png (imagen de prueba).
--   - Nota sobre POST /api/v1/test/cierre-diario añadida en cabecera.
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
--
-- NOTA: Para verificar E44 (informe saldos) es necesario ejecutar primero
-- POST /api/v1/test/cierre-diario. Los saldos_anuales no se precargan
-- aquí porque en producción los genera ProcesoCierreDiario cada noche.
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
    usuario_id, nombre, apellido1, apellido2, dni, numero_empleado,
    fecha_alta, categoria, jornada_semanal_horas, jornada_diaria_minutos,
    dias_vacaciones_anuales, dias_asuntos_propios_anuales,
    pin_terminal, codigo_nfc, activo
) VALUES (
    3, 'Ana', 'Garcia', 'Lopez', '11111111A', 'EMP-001',
    '2026-01-01', 'OPERARIO', 40, 480,
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
    '2026-01-01', 'OPERARIO', 40, 480,
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
    NULL, NULL, 0, 0, 2, 'Festivo local Comunidad de Madrid', '2026-03-06 08:00:00');

-- Semana 2 (09-13 mar)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-09', 'NORMAL',
    '2026-03-09 09:00:00', '2026-03-09 17:30:00',
    30, 450, 3, NULL, '2026-03-09 17:30:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-10', 'NORMAL',
    '2026-03-10 09:00:00', '2026-03-10 17:30:00',
    30, 450, 3, NULL, '2026-03-10 17:30:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-11', 'BAJA_MEDICA',
    NULL, NULL, 0, 0, 2, 'Baja médica tramitada por encargado.', '2026-03-11 09:00:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-12', 'BAJA_MEDICA',
    NULL, NULL, 0, 0, 2, 'Continuación baja médica.', '2026-03-12 09:00:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-13', 'NORMAL',
    '2026-03-13 09:00:00', '2026-03-13 17:30:00',
    30, 450, 3, NULL, '2026-03-13 17:30:00');

-- Semana 3 (16-20 mar)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-16', 'NORMAL',
    '2026-03-16 09:00:00', '2026-03-16 17:30:00',
    30, 450, 3, NULL, '2026-03-16 17:30:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-17', 'NORMAL',
    '2026-03-17 09:00:00', '2026-03-17 17:30:00',
    30, 450, 3, NULL, '2026-03-17 17:30:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-18', 'NORMAL',
    '2026-03-18 09:00:00', '2026-03-18 17:30:00',
    30, 450, 3, NULL, '2026-03-18 17:30:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-19', 'AUSENCIA_INJUSTIFICADA',
    NULL, NULL, 0, 0, 5, 'Ausencia injustificada generada automáticamente.', '2026-03-19 23:55:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-20', 'NORMAL',
    '2026-03-20 09:00:00', '2026-03-20 17:30:00',
    30, 450, 3, NULL, '2026-03-20 17:30:00');

-- Semana 4 (23-27 mar) — VACACIONES
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-23', 'VACACIONES', NULL, NULL, 0, 0, 2, NULL, '2026-03-23 00:01:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-24', 'VACACIONES', NULL, NULL, 0, 0, 2, NULL, '2026-03-24 00:01:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-25', 'VACACIONES', NULL, NULL, 0, 0, 2, NULL, '2026-03-25 00:01:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-26', 'VACACIONES', NULL, NULL, 0, 0, 2, NULL, '2026-03-26 00:01:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-27', 'VACACIONES', NULL, NULL, 0, 0, 2, NULL, '2026-03-27 00:01:00');

-- Semana 5 (30-31 mar)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-30', 'DIA_LIBRE_COMPENSATORIO',
    NULL, NULL, 0, 0, 2, 'Compensacion por horas extra semana del 2 de marzo', '2026-03-30 00:01:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-31', 'NORMAL',
    '2026-03-31 09:00:00', '2026-03-31 17:30:00',
    30, 450, 3, NULL, '2026-03-31 17:30:00');


-- ===== CARLOS LÓPEZ (empleado_id=2) =====

-- Semana 1 (02-06 mar)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-02', 'NORMAL',
    '2026-03-02 08:00:00', '2026-03-02 16:30:00',
    30, 450, 4, NULL, '2026-03-02 16:30:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-03', 'NORMAL',
    '2026-03-03 08:00:00', '2026-03-03 16:30:00',
    30, 450, 4, NULL, '2026-03-03 16:30:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-04', 'ASUNTO_PROPIO',
    NULL, NULL, 0, 0, 2, NULL, '2026-03-04 00:01:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-05', 'NORMAL',
    '2026-03-05 08:00:00', '2026-03-05 16:30:00',
    30, 450, 4, NULL, '2026-03-05 16:30:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-06', 'FESTIVO_LOCAL',
    NULL, NULL, 0, 0, 2, 'Festivo local Comunidad de Madrid', '2026-03-06 08:00:00');

-- Semana 2 (09-13 mar)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-09', 'NORMAL',
    '2026-03-09 08:00:00', '2026-03-09 16:30:00',
    30, 450, 4, NULL, '2026-03-09 16:30:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-10', 'NORMAL',
    '2026-03-10 08:00:00', '2026-03-10 16:30:00',
    30, 450, 4, NULL, '2026-03-10 16:30:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-11', 'NORMAL',
    '2026-03-11 08:00:00', '2026-03-11 16:30:00',
    45, 435, 4, NULL, '2026-03-11 16:30:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-12', 'NORMAL',
    '2026-03-12 08:00:00', '2026-03-12 16:30:00',
    30, 450, 2, 'Salida ajustada por encargado.', '2026-03-12 16:45:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-13', 'NORMAL',
    '2026-03-13 08:00:00', '2026-03-13 16:30:00',
    30, 450, 4, NULL, '2026-03-13 16:30:00');

-- Semana 3 (16-20 mar)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-16', 'NORMAL',
    '2026-03-16 08:00:00', '2026-03-16 16:30:00',
    30, 450, 4, NULL, '2026-03-16 16:30:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-17', 'NORMAL',
    '2026-03-17 08:00:00', '2026-03-17 16:30:00',
    30, 450, 4, NULL, '2026-03-17 16:30:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-18', 'NORMAL',
    '2026-03-18 08:00:00', '2026-03-18 16:30:00',
    30, 450, 4, NULL, '2026-03-18 16:30:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-19', 'PERMISO_RETRIBUIDO',
    NULL, NULL, 0, 0, 2, 'Permiso retribuido por cita medica con especialista', '2026-03-19 00:01:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-20', 'NORMAL',
    '2026-03-20 08:00:00', '2026-03-20 16:30:00',
    30, 450, 4, NULL, '2026-03-20 16:30:00');

-- Semana 4 (23-27 mar)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-23', 'NORMAL',
    '2026-03-23 08:00:00', '2026-03-23 16:30:00',
    30, 450, 4, NULL, '2026-03-23 16:30:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-24', 'NORMAL',
    '2026-03-24 08:00:00', '2026-03-24 16:30:00',
    30, 450, 4, NULL, '2026-03-24 16:30:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-25', 'NORMAL',
    '2026-03-25 08:00:00', '2026-03-25 16:30:00',
    30, 450, 4, NULL, '2026-03-25 16:30:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-26', 'NORMAL',
    '2026-03-26 08:00:00', '2026-03-26 16:30:00',
    30, 450, 4, NULL, '2026-03-26 16:30:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-27', 'NORMAL',
    '2026-03-27 08:00:00', '2026-03-27 16:30:00',
    30, 450, 4, NULL, '2026-03-27 16:30:00');

-- Semana 5 (30-31 mar)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-30', 'NORMAL',
    '2026-03-30 08:00:00', '2026-03-30 16:30:00',
    30, 450, 4, NULL, '2026-03-30 16:30:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-31', 'AUSENCIA_INJUSTIFICADA',
    NULL, NULL, 0, 0, 5, 'Ausencia injustificada generada automáticamente.', '2026-03-31 23:55:00');


-- -----------------------------------------------------------------------------
-- 5. PAUSAS DE PRUEBA — ANA GARCÍA (empleado_id=1)
-- -----------------------------------------------------------------------------

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-02',
    '2026-03-02 13:30:00', '2026-03-02 14:00:00', 30,
    'COMIDA', 3, NULL, '2026-03-02 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-03',
    '2026-03-03 10:00:00', '2026-03-03 10:15:00', 15,
    'DESCANSO', 3, NULL, '2026-03-03 10:15:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-03',
    '2026-03-03 13:30:00', '2026-03-03 14:00:00', 30,
    'COMIDA', 3, NULL, '2026-03-03 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-04',
    '2026-03-04 13:30:00', '2026-03-04 14:00:00', 30,
    'COMIDA', 3, NULL, '2026-03-04 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-05',
    '2026-03-05 13:30:00', '2026-03-05 14:00:00', 30,
    'COMIDA', 3, NULL, '2026-03-05 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-09',
    '2026-03-09 13:30:00', '2026-03-09 14:00:00', 30,
    'COMIDA', 3, NULL, '2026-03-09 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-10',
    '2026-03-10 13:30:00', '2026-03-10 14:00:00', 30,
    'COMIDA', 3, NULL, '2026-03-10 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-13',
    '2026-03-13 13:30:00', '2026-03-13 14:00:00', 30,
    'COMIDA', 3, NULL, '2026-03-13 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-16',
    '2026-03-16 13:30:00', '2026-03-16 14:00:00', 30,
    'COMIDA', 3, NULL, '2026-03-16 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-17',
    '2026-03-17 13:30:00', '2026-03-17 14:00:00', 30,
    'COMIDA', 3, NULL, '2026-03-17 14:00:00');

-- 18/03 — pausa con intervención manual en inicio y fin
INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-18',
    '2026-03-18 14:00:00', '2026-03-18 14:30:00', 30,
    'COMIDA', 2, 'Inicio y fin de pausa corregidos por encargado.', '2026-03-18 14:35:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-20',
    '2026-03-20 13:30:00', '2026-03-20 14:00:00', 30,
    'COMIDA', 3, NULL, '2026-03-20 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-31',
    '2026-03-31 13:30:00', '2026-03-31 14:00:00', 30,
    'COMIDA', 3, NULL, '2026-03-31 14:00:00');


-- -----------------------------------------------------------------------------
-- 6. PLANIFICACION DE AUSENCIAS
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
VALUES (1, '2026-03-23', 'VACACIONES', TRUE, 2, NULL, '2026-03-10 09:00:00');

INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado,
    usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-24', 'VACACIONES', TRUE, 2, NULL, '2026-03-10 09:00:00');

INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado,
    usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-25', 'VACACIONES', TRUE, 2, NULL, '2026-03-10 09:00:00');

INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado,
    usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-26', 'VACACIONES', TRUE, 2, NULL, '2026-03-10 09:00:00');

INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado,
    usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-27', 'VACACIONES', TRUE, 2, NULL, '2026-03-10 09:00:00');

-- 30/03 — DIA_LIBRE_COMPENSATORIO planificado
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado,
    usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-30', 'DIA_LIBRE_COMPENSATORIO', TRUE,
    2, 'Compensacion por horas extra semana del 2 de marzo', '2026-03-15 09:00:00');


-- CARLOS LOPEZ (empleado_id=2)

-- 04/03 — ASUNTO_PROPIO planificado
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado,
    usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-04', 'ASUNTO_PROPIO', TRUE, 2, NULL, '2026-03-03 09:00:00');

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


-- -----------------------------------------------------------------------------
-- 7. PAUSAS DE PRUEBA — CARLOS LÓPEZ (empleado_id=2)
-- -----------------------------------------------------------------------------

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-02',
    '2026-03-02 14:00:00', '2026-03-02 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-02 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-03',
    '2026-03-03 14:00:00', '2026-03-03 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-03 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-05',
    '2026-03-05 14:00:00', '2026-03-05 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-05 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-09',
    '2026-03-09 14:00:00', '2026-03-09 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-09 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-10',
    '2026-03-10 14:00:00', '2026-03-10 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-10 14:30:00');

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

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-12',
    '2026-03-12 14:00:00', '2026-03-12 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-12 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-13',
    '2026-03-13 14:00:00', '2026-03-13 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-13 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-16',
    '2026-03-16 14:00:00', '2026-03-16 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-16 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-17',
    '2026-03-17 14:00:00', '2026-03-17 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-17 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-18',
    '2026-03-18 14:00:00', '2026-03-18 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-18 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-20',
    '2026-03-20 14:00:00', '2026-03-20 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-20 14:30:00');

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

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-30',
    '2026-03-30 14:00:00', '2026-03-30 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-30 14:30:00');
