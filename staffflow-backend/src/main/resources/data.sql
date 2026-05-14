-- =============================================================================
-- StaffFlow — Datos iniciales para perfil dev (H2 en memoria)
-- Versión: v4 (22/04/2026)
-- Cambios respecto a v3:
--   - fecha_alta de todos los empleados movida a 2026-03-30 para probar
--     el prorrateo de saldos desde cero.
--   - Fichajes y pausas de prueba: 30/03 al 21/04 (22/04 sin fichajes).
--   - Festivos nacionales España 2026 y locales Madrid añadidos en
--     planificacion_ausencias (empleado_id=NULL = festivo global).
--   - Festivos 02/04 y 03/04 (Semana Santa) incluidos con fichaje.
--   - Redondeo asuntos propios cambiado a Math.round en SaldoService.
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
-- Fichajes y pausas: Ana García (emp01, empleadoId=1) y Carlos López
-- (emp02, empleadoId=2), del 30/03 al 21/04/2026.
-- Laura Fernández (encargado01, empleadoId=3) sin fichajes.
-- Hoy (22/04) sin fichajes para probar estado "en curso".
--
-- Saldos: fecha_alta=30/03 → prorrateo automático en crearSaldoInicial():
--   - Vacaciones: ceil(22 × 277/365) = 17 días
--   - Asuntos propios: round(3 × 277/365) = 2 días
--
-- usuario_id referencia:
--   1 = admin
--   2 = encargado01
--   3 = emp01 (Ana)
--   4 = emp02 (Carlos)
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
    'admin',
    '$2a$10$HaOeyYyuQOjcaNZ/zkhOsu/2f.SYeFK3G1XCfWXVAftuRHvKUb9eW',
    'santicastnuevo@gmail.com',
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
-- Ana García (emp01, empleado_id=1): jornada 09:00-17:30
-- Carlos López (emp02, empleado_id=2): jornada 08:00-16:30
-- Laura Fernández (encargado01, empleado_id=3): sin fichajes
-- -----------------------------------------------------------------------------


-- ===== ANA GARCÍA (empleado_id=1) =====

-- 30/03 lun — DIA_LIBRE_COMPENSATORIO (compensación por acuerdo previo)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-30', 'DIA_LIBRE_COMPENSATORIO',
    NULL, NULL, 0, 0, 2, 'Dia libre compensatorio por acuerdo con encargado.', '2026-03-30 00:01:00');

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
    NULL, NULL, 0, 0, 2, 'Jueves Santo — festivo nacional.', '2026-04-02 00:01:00');

-- 03/04 vie — FESTIVO_NACIONAL (Viernes Santo)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-03', 'FESTIVO_NACIONAL',
    NULL, NULL, 0, 0, 2, 'Viernes Santo — festivo nacional.', '2026-04-03 00:01:00');

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
    NULL, NULL, 0, 480, 2, 'Baja medica tramitada por encargado.', '2026-04-07 09:00:00');

-- 08/04 mié — BAJA_MEDICA
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-08', 'BAJA_MEDICA',
    NULL, NULL, 0, 480, 2, 'Continuacion baja medica.', '2026-04-08 09:00:00');

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
    NULL, NULL, 0, 480, 2, 'Permiso retribuido por gestion personal. Aprobado por encargado.', '2026-04-15 00:01:00');

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
    NULL, NULL, 0, 0, 2, NULL, '2026-04-20 00:01:00');

-- 21/04 mar — VACACIONES
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-04-21', 'VACACIONES',
    NULL, NULL, 0, 0, 2, NULL, '2026-04-21 00:01:00');


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
    NULL, NULL, 0, 0, 2, 'Jueves Santo — festivo nacional.', '2026-04-02 00:01:00');

-- 03/04 vie — FESTIVO_NACIONAL (Viernes Santo)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-04-03', 'FESTIVO_NACIONAL',
    NULL, NULL, 0, 0, 2, 'Viernes Santo — festivo nacional.', '2026-04-03 00:01:00');

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
    NULL, NULL, 0, 0, 2, NULL, '2026-04-07 00:01:00');

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
    NULL, NULL, 0, 480, 2, 'Permiso retribuido por cita medica con especialista.', '2026-04-16 00:01:00');

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
    NULL, NULL, 0, 0, 1, 'Jueves Santo — festivo nacional.', '2026-04-02 00:01:00');

-- 03/04 vie — FESTIVO_NACIONAL (Viernes Santo)
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-03', 'FESTIVO_NACIONAL',
    NULL, NULL, 0, 0, 1, 'Viernes Santo — festivo nacional.', '2026-04-03 00:01:00');

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
    NULL, NULL, 0, 480, 1, 'Baja medica tramitada por admin.', '2026-04-07 09:00:00');

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
    NULL, NULL, 0, 0, 1, NULL, '2026-04-13 00:01:00');

-- 14/04 mar — VACACIONES
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-14', 'VACACIONES',
    NULL, NULL, 0, 0, 1, NULL, '2026-04-14 00:01:00');

-- 15/04 mié — PERMISO_RETRIBUIDO
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-15', 'PERMISO_RETRIBUIDO',
    NULL, NULL, 0, 480, 1, 'Permiso retribuido por asunto familiar.', '2026-04-15 00:01:00');

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
    NULL, NULL, 0, 0, 1, 'Dia libre compensatorio por horas extra del 16/04.', '2026-04-17 00:01:00');

-- 20/04 lun — ASUNTO_PROPIO
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-20', 'ASUNTO_PROPIO',
    NULL, NULL, 0, 0, 1, NULL, '2026-04-20 00:01:00');

-- 21/04 mar — NORMAL
INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (3, '2026-04-21', 'NORMAL',
    '2026-04-21 09:00:00', '2026-04-21 17:30:00',
    30, 480, 2, NULL, '2026-04-21 17:30:00');


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


-- -----------------------------------------------------------------------------
-- 8. PLANIFICACION DE AUSENCIAS
--
-- Festivos globales: empleado_id=NULL → proceso nocturno crea fichaje
-- para todos los empleados activos (RF-26, decisión nº7).
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

-- 01/05 Día del Trabajo (nacional)
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado,
    usuario_id, observaciones, fecha_creacion)
VALUES (NULL, '2026-05-01', 'FESTIVO_NACIONAL', FALSE,
    1, 'Día del Trabajo — festivo nacional.', '2026-01-10 09:00:00');

-- 15/05 San Isidro (local Madrid)
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado,
    usuario_id, observaciones, fecha_creacion)
VALUES (NULL, '2026-05-15', 'FESTIVO_LOCAL', FALSE,
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
-- de la sección 4. calculado_hasta_fecha = 21/04/2026 (último fichaje).
--
-- Prorrateo (fecha_alta=30/03, diasRestantes=277, diasAnio=365):
--   Vacaciones: ceil(22×277/365) = 17
--   Asuntos propios: round(3×277/365) = 2
--
-- Ana García (emp_id=1):
--   NORMAL×9 (480 min c/u)=0, NORMAL×1 (465 min)=-15 min, DLC×1=-480 min
--   BAJA_MEDICA×2, PERMISO_RETRIBUIDO×1, VACACIONES×2
--   saldo_horas = (-480-15)/60 = -8.25 h
--
-- Carlos López (emp_id=2):
--   NORMAL×10 (480 min c/u salvo 09/04 465 min), AUSENCIA_INJUSTIFICADA×1
--   PERMISO_RETRIBUIDO×1, ASUNTO_PROPIO×1
--   saldo_horas = (-480-15)/60 = -8.25 h
--
-- Laura Fernández (emp_id=3):
--   NORMAL×6 (480×4, 570 31/03, 555 16/04, 465 09/04, 450 10/04)
--   AUSENCIA_INJUSTIFICADA×1, BAJA_MEDICA×1, PERMISO_RETRIBUIDO×1
--   DLC×1, VACACIONES×2, ASUNTO_PROPIO×1
--   saldo_horas = (90-480-15-30+75-480)/60 = -840/60 = -14.00 h
--   (nota: comentario anterior "+2h" era incorrecto, calculado antes
--    de definir la lógica final de recalcularParaProceso)
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
 12, 2, 1, 0,
 17, 0, 2, 15,
 2, 0, 0, 2,
 0.00, -8.25,
 '2026-04-21', '2026-04-21 17:30:00'),
-- Carlos López
(2, 2026,
 13, 0, 1, 1,
 17, 0, 0, 17,
 2, 0, 1, 1,
 0.00, -8.25,
 '2026-04-21', '2026-04-21 16:30:00'),
-- Laura Fernández
(3, 2026,
 10, 1, 1, 1,
 17, 0, 2, 15,
 2, 0, 1, 1,
 0.00, -14.00,
 '2026-04-21', '2026-04-21 17:30:00');


-- ── AUSENCIAS FUTURAS INDIVIDUALES ────────────────────────────────────────

-- ANA GARCIA — asuntos propios (mayo y junio)
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-05-22', 'ASUNTO_PROPIO', FALSE, 2, NULL, '2026-04-18 09:00:00');
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-06-12', 'ASUNTO_PROPIO', FALSE, 2, NULL, '2026-04-18 09:00:00');

-- CARLOS LOPEZ — asunto propio (mayo)
INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-05-08', 'ASUNTO_PROPIO', FALSE, 2, NULL, '2026-04-18 09:00:00');

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
