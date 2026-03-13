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
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. CONFIGURACION DE EMPRESA (singleton obligatorio)
-- Sin este registro E06 GET /empresa devuelve 404.
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
-- Hash BCrypt de 'admin1234' (factor coste 10, generado con el PasswordEncoder
-- del proyecto). terminal_service usa hash distinto: su password nunca se usa.
-- Orden de inserción: admin(1), encargado01(2), emp01(3), emp02(4), terminal_service(5).
-- Los IDs son asignados por H2 en este orden garantizado.
-- -----------------------------------------------------------------------------

-- id=1 ADMIN — acceso total, login en Swagger y app Android
INSERT INTO usuarios (username, password_hash, email, rol, activo, fecha_creacion)
VALUES (
    'admin',
    '$2a$10$HaOeyYyuQOjcaNZ/zkhOsu/2f.SYeFK3G1XCfWXVAftuRHvKUb9eW',
    'admin@staffflow.demo',
    'ADMIN',
    TRUE,
    '2026-01-01 00:00:00'
);

-- id=2 ENCARGADO — gestión de fichajes, pausas, ausencias, informes
INSERT INTO usuarios (username, password_hash, email, rol, activo, fecha_creacion)
VALUES (
    'encargado01',
    '$2a$10$HaOeyYyuQOjcaNZ/zkhOsu/2f.SYeFK3G1XCfWXVAftuRHvKUb9eW',
    'encargado01@staffflow.demo',
    'ENCARGADO',
    TRUE,
    '2026-01-01 00:00:00'
);

-- id=3 EMPLEADO 1 — Ana García, PIN terminal 1111
INSERT INTO usuarios (username, password_hash, email, rol, activo, fecha_creacion)
VALUES (
    'emp01',
    '$2a$10$HaOeyYyuQOjcaNZ/zkhOsu/2f.SYeFK3G1XCfWXVAftuRHvKUb9eW',
    'emp01@staffflow.demo',
    'EMPLEADO',
    TRUE,
    '2026-01-01 00:00:00'
);

-- id=4 EMPLEADO 2 — Carlos López, PIN terminal 2222
INSERT INTO usuarios (username, password_hash, email, rol, activo, fecha_creacion)
VALUES (
    'emp02',
    '$2a$10$HaOeyYyuQOjcaNZ/zkhOsu/2f.SYeFK3G1XCfWXVAftuRHvKUb9eW',
    'emp02@staffflow.demo',
    'EMPLEADO',
    TRUE,
    '2026-01-01 00:00:00'
);

-- id=5 USUARIO SISTEMA TERMINAL
-- Auditoría de fichajes y pausas creados por terminal PIN (D-021 Opción B).
-- No tiene perfil de empleado. Nunca se usa para login.
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
-- usuario_id referencia el orden de inserción de usuarios arriba:
--   encargado01 → usuario_id=2
--   emp01       → usuario_id=3
--   emp02       → usuario_id=4
-- El encargado también tiene perfil de empleado para el cómputo de jornada.
-- jornada_semanal_horas=40, jornada_diaria_minutos=480 (jornada completa estándar).
-- dias_vacaciones_anuales=22, dias_asuntos_propios_anuales=3 (convenio tipo).
-- -----------------------------------------------------------------------------

-- Empleado 1 — Ana García (emp01, usuario_id=3), PIN=1111
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

-- Empleado 2 — Carlos López (emp02, usuario_id=4), PIN=2222
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

-- Empleado 3 — Laura Fernández (encargado01, usuario_id=2), PIN=3333
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
