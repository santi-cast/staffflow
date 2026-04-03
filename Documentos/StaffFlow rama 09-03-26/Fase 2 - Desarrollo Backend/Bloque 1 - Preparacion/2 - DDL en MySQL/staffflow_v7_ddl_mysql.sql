-- ============================================================
-- StaffFlow v1.0 — Script DDL + datos de prueba MySQL 8.0
-- TFG DAM 2025/2026 — iLERNA — Santiago
-- Versión: v7 (22/03/2026) — DIA_LIBRE añadido a TipoFichaje y TipoAusencia
-- Cambios respecto a v5:
--   - Columna nss renombrada a numero_empleado en tabla empleados.
--     Constraint uq_empleados_nss renombrada a uq_empleados_numero_empleado.
--     El campo se usa como identificador interno de empleado en cabeceras
--     de PDFs firmables (RF-40). D-030.
--   - Datos de prueba incluidos al final del script (equivalentes
--     al data.sql v2 que Spring Boot carga en H2 automaticamente).
-- ============================================================
-- Este script hace en MySQL exactamente lo mismo que data.sql
-- hace en H2: crea el esquema completo e inserta los datos de
-- prueba. Ejecutar una sola vez tras instalar MySQL.
--
-- Ejecutar en orden. Las FK exigen que las tablas padre
-- existan antes que las tablas hijo.
-- Orden: configuracion_empresa → usuarios → empleados →
--        planificacion_ausencias → fichajes → pausas → saldos_anuales
--
-- Credenciales de prueba (contraseña 'admin1234' para todos):
--   admin            → ADMIN
--   encargado01      → ENCARGADO   (PIN terminal: 3333)
--   emp01            → EMPLEADO    (PIN terminal: 1111)
--   emp02            → EMPLEADO    (PIN terminal: 2222)
--   terminal_service → solo para auditoria interna, nunca para login
--
-- Fichajes y pausas de prueba: Ana Garcia (emp01, empleado_id=1)
-- y Carlos Lopez (emp02, empleado_id=2), todo marzo 2026.
-- Laura Fernandez (encargado01, empleado_id=3) sin fichajes.
--
-- NOTA: Para verificar E44 (informe saldos) ejecutar primero
-- POST /api/v1/test/cierre-diario tras arrancar el backend.
-- ============================================================

CREATE DATABASE IF NOT EXISTS staffflow
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE staffflow;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS saldos_anuales;
DROP TABLE IF EXISTS pausas;
DROP TABLE IF EXISTS fichajes;
DROP TABLE IF EXISTS planificacion_ausencias;
DROP TABLE IF EXISTS empleados;
DROP TABLE IF EXISTS usuarios;
DROP TABLE IF EXISTS configuracion_empresa;
SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- TABLA 1 — configuracion_empresa
-- Singleton: id siempre = 1.
-- ============================================================
CREATE TABLE configuracion_empresa (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    nombre_empresa   VARCHAR(100)  NOT NULL,
    cif              VARCHAR(20)   NOT NULL,
    direccion        TEXT          NULL,
    email            VARCHAR(100)  NULL,
    telefono         VARCHAR(20)   NULL,
    logo_path        VARCHAR(255)  NULL,

    CONSTRAINT pk_configuracion_empresa PRIMARY KEY (id),
    CONSTRAINT uq_configuracion_empresa_cif UNIQUE (cif)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- TABLA 2 — usuarios
-- Autenticación y control de roles.
-- ============================================================
CREATE TABLE usuarios (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    username            VARCHAR(50)   NOT NULL,
    password_hash       VARCHAR(255)  NOT NULL,
    email               VARCHAR(100)  NOT NULL,
    rol                 ENUM('ADMIN','ENCARGADO','EMPLEADO') NOT NULL,
    activo              BOOLEAN       NOT NULL DEFAULT TRUE,
    fecha_creacion      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reset_token         VARCHAR(255)  NULL,
    reset_token_expiry  DATETIME      NULL,

    CONSTRAINT pk_usuarios PRIMARY KEY (id),
    CONSTRAINT uq_usuarios_username UNIQUE (username),
    CONSTRAINT uq_usuarios_email    UNIQUE (email),

    INDEX idx_usuarios_activo      (activo),
    INDEX idx_usuarios_reset_token (reset_token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- TABLA 3 — empleados
-- Perfil laboral. Relación 1:1 con usuarios.
-- ============================================================
CREATE TABLE empleados (
    id                            BIGINT       NOT NULL AUTO_INCREMENT,
    usuario_id                    BIGINT       NOT NULL,
    nombre                        VARCHAR(50)  NOT NULL,
    apellido1                     VARCHAR(50)  NOT NULL,
    apellido2                     VARCHAR(50)  NULL,
    dni                           VARCHAR(20)  NOT NULL,
    numero_empleado               VARCHAR(20)  NOT NULL,
    fecha_alta                    DATE         NOT NULL,
    categoria                     ENUM('OPERARIO','ADMINISTRATIVO','TECNICO',
                                       'ENCARGADO','OTRO') NOT NULL,
    jornada_semanal_horas         INT          NOT NULL DEFAULT 40,
    jornada_diaria_minutos        INT          NOT NULL DEFAULT 480,
    dias_vacaciones_anuales       INT          NOT NULL DEFAULT 22,
    dias_asuntos_propios_anuales  INT          NOT NULL DEFAULT 3,
    pin_terminal                  CHAR(4)      NOT NULL,
    codigo_nfc                    VARCHAR(50)  NULL,
    activo                        BOOLEAN      NOT NULL DEFAULT TRUE,

    CONSTRAINT pk_empleados PRIMARY KEY (id),
    CONSTRAINT uq_empleados_usuario_id      UNIQUE (usuario_id),
    CONSTRAINT uq_empleados_dni             UNIQUE (dni),
    CONSTRAINT uq_empleados_numero_empleado UNIQUE (numero_empleado),
    CONSTRAINT uq_empleados_pin             UNIQUE (pin_terminal),
    CONSTRAINT uq_empleados_nfc             UNIQUE (codigo_nfc),

    -- Índice crítico: búsqueda por PIN en cada fichaje (RNF-R03 < 100ms P95)
    -- El UNIQUE ya crea el índice; se documenta aquí por claridad.
    INDEX idx_empleados_activo (activo),
    INDEX idx_empleados_dni    (dni),

    CONSTRAINT fk_empleados_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuarios (id)
        ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- TABLA 4 — planificacion_ausencias
-- Ausencias futuras y festivos globales (empleado_id NULL).
-- ============================================================
CREATE TABLE planificacion_ausencias (
    id              BIGINT    NOT NULL AUTO_INCREMENT,
    empleado_id     BIGINT    NULL,           -- NULL = festivo global
    fecha           DATE      NOT NULL,
    tipo_ausencia   ENUM('FESTIVO_NACIONAL','FESTIVO_LOCAL','VACACIONES',
                         'ASUNTO_PROPIO','PERMISO_RETRIBUIDO',
                         'DIA_LIBRE_COMPENSATORIO','DIA_LIBRE') NOT NULL,
    procesado       BOOLEAN   NOT NULL DEFAULT FALSE,
    usuario_id      BIGINT    NOT NULL,
    observaciones   TEXT      NULL,
    fecha_creacion  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_planificacion_ausencias PRIMARY KEY (id),
    CONSTRAINT uq_planificacion_empleado_fecha UNIQUE (empleado_id, fecha),

    INDEX idx_planificacion_fecha_procesado (fecha, procesado),

    CONSTRAINT fk_planificacion_empleado
        FOREIGN KEY (empleado_id) REFERENCES empleados (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_planificacion_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuarios (id)
        ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- TABLA 5 — fichajes
-- ============================================================
CREATE TABLE fichajes (
    id                        BIGINT    NOT NULL AUTO_INCREMENT,
    empleado_id               BIGINT    NOT NULL,
    fecha                     DATE      NOT NULL,
    tipo                      ENUM('NORMAL','FESTIVO_NACIONAL','FESTIVO_LOCAL',
                                   'VACACIONES','ASUNTO_PROPIO',
                                   'PERMISO_RETRIBUIDO','BAJA_MEDICA',
                                   'DIA_LIBRE_COMPENSATORIO','DIA_LIBRE',
                                   'AUSENCIA_INJUSTIFICADA') NOT NULL,
    hora_entrada              DATETIME  NULL,
    hora_salida               DATETIME  NULL,
    total_pausas_minutos      INT       NOT NULL DEFAULT 0,
    jornada_efectiva_minutos  INT       NOT NULL DEFAULT 0,
    usuario_id                BIGINT    NOT NULL,
    observaciones             TEXT      NULL,
    fecha_creacion            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_fichajes PRIMARY KEY (id),
    CONSTRAINT uq_fichajes_empleado_fecha UNIQUE (empleado_id, fecha),

    CONSTRAINT fk_fichajes_empleado
        FOREIGN KEY (empleado_id) REFERENCES empleados (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_fichajes_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuarios (id)
        ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- TABLA 6 — pausas
-- ============================================================
CREATE TABLE pausas (
    id                 BIGINT    NOT NULL AUTO_INCREMENT,
    empleado_id        BIGINT    NOT NULL,
    fecha              DATE      NOT NULL,
    hora_inicio        DATETIME  NOT NULL,
    hora_fin           DATETIME  NULL,
    duracion_minutos   INT       NULL,
    tipo_pausa         ENUM('COMIDA','DESCANSO','AUSENCIA_RETRIBUIDA','OTROS') NOT NULL,
    usuario_id         BIGINT    NOT NULL,
    observaciones      TEXT      NULL,
    fecha_creacion     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_pausas PRIMARY KEY (id),

    INDEX idx_pausas_empleado_fecha (empleado_id, fecha),

    CONSTRAINT fk_pausas_empleado
        FOREIGN KEY (empleado_id) REFERENCES empleados (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_pausas_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuarios (id)
        ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- TABLA 7 — saldos_anuales
-- ============================================================
CREATE TABLE saldos_anuales (
    id                                       BIGINT        NOT NULL AUTO_INCREMENT,
    empleado_id                              BIGINT        NOT NULL,
    anio                                     INT           NOT NULL,

    dias_trabajados                          INT           NOT NULL DEFAULT 0,
    dias_baja_medica                         INT           NOT NULL DEFAULT 0,
    dias_permiso_retribuido                  INT           NOT NULL DEFAULT 0,
    dias_ausencia_injustificada              INT           NOT NULL DEFAULT 0,

    dias_vacaciones_derecho_anio             INT           NOT NULL,
    dias_vacaciones_pendientes_anio_anterior INT           NOT NULL DEFAULT 0,
    dias_vacaciones_consumidos               INT           NOT NULL DEFAULT 0,
    dias_vacaciones_disponibles              INT           NOT NULL,

    dias_asuntos_propios_derecho_anio        INT           NOT NULL,
    dias_asuntos_propios_pendientes_anterior INT           NOT NULL DEFAULT 0,
    dias_asuntos_propios_consumidos          INT           NOT NULL DEFAULT 0,
    dias_asuntos_propios_disponibles         INT           NOT NULL,

    horas_ausencia_retribuida                DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    saldo_horas                              DECIMAL(10,2) NOT NULL DEFAULT 0.00,

    calculado_hasta_fecha                    DATE          NULL,
    fecha_ultima_modificacion                TIMESTAMP     NOT NULL
                                             DEFAULT CURRENT_TIMESTAMP
                                             ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_saldos_anuales PRIMARY KEY (id),
    CONSTRAINT uq_saldos_empleado_anio UNIQUE (empleado_id, anio),

    INDEX idx_saldos_anio (anio),

    CONSTRAINT fk_saldos_empleado
        FOREIGN KEY (empleado_id) REFERENCES empleados (id)
        ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- DATOS DE PRUEBA
-- Equivalentes al data.sql v2 cargado automaticamente en H2.
-- ============================================================

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
VALUES ('admin',
    '$2a$10$HaOeyYyuQOjcaNZ/zkhOsu/2f.SYeFK3G1XCfWXVAftuRHvKUb9eW',
    'admin@staffflow.demo', 'ADMIN', TRUE, '2026-01-01 00:00:00');

INSERT INTO usuarios (username, password_hash, email, rol, activo, fecha_creacion)
VALUES ('encargado01',
    '$2a$10$HaOeyYyuQOjcaNZ/zkhOsu/2f.SYeFK3G1XCfWXVAftuRHvKUb9eW',
    'encargado01@staffflow.demo', 'ENCARGADO', TRUE, '2026-01-01 00:00:00');

INSERT INTO usuarios (username, password_hash, email, rol, activo, fecha_creacion)
VALUES ('emp01',
    '$2a$10$HaOeyYyuQOjcaNZ/zkhOsu/2f.SYeFK3G1XCfWXVAftuRHvKUb9eW',
    'emp01@staffflow.demo', 'EMPLEADO', TRUE, '2026-01-01 00:00:00');

INSERT INTO usuarios (username, password_hash, email, rol, activo, fecha_creacion)
VALUES ('emp02',
    '$2a$10$HaOeyYyuQOjcaNZ/zkhOsu/2f.SYeFK3G1XCfWXVAftuRHvKUb9eW',
    'emp02@staffflow.demo', 'EMPLEADO', TRUE, '2026-01-01 00:00:00');

INSERT INTO usuarios (username, password_hash, email, rol, activo, fecha_creacion)
VALUES ('terminal_service',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lmuS',
    'terminal@staffflow.internal', 'ADMIN', TRUE, '2026-01-01 00:00:00');

-- -----------------------------------------------------------------------------
-- 3. EMPLEADOS
-- usuario_id referencia: 1=admin 2=encargado01 3=emp01(Ana) 4=emp02(Carlos) 5=terminal_service
-- -----------------------------------------------------------------------------

INSERT INTO empleados (usuario_id, nombre, apellido1, apellido2, dni, numero_empleado,
    fecha_alta, categoria, jornada_semanal_horas, jornada_diaria_minutos,
    dias_vacaciones_anuales, dias_asuntos_propios_anuales, pin_terminal, codigo_nfc, activo)
VALUES (3, 'Ana', 'Garcia', 'Lopez', '11111111A', 'EMP-001',
    '2026-01-01', 'OPERARIO', 40, 480, 22, 3, '1111', NULL, TRUE);

INSERT INTO empleados (usuario_id, nombre, apellido1, apellido2, dni, numero_empleado,
    fecha_alta, categoria, jornada_semanal_horas, jornada_diaria_minutos,
    dias_vacaciones_anuales, dias_asuntos_propios_anuales, pin_terminal, codigo_nfc, activo)
VALUES (4, 'Carlos', 'Lopez', 'Martinez', '22222222B', 'EMP-002',
    '2026-01-01', 'OPERARIO', 40, 480, 22, 3, '2222', NULL, TRUE);

INSERT INTO empleados (usuario_id, nombre, apellido1, apellido2, dni, numero_empleado,
    fecha_alta, categoria, jornada_semanal_horas, jornada_diaria_minutos,
    dias_vacaciones_anuales, dias_asuntos_propios_anuales, pin_terminal, codigo_nfc, activo)
VALUES (2, 'Laura', 'Fernandez', 'Ruiz', '33333333C', 'EMP-003',
    '2026-01-01', 'ENCARGADO', 40, 480, 22, 3, '3333', NULL, TRUE);

-- -----------------------------------------------------------------------------
-- 4. FICHAJES DE PRUEBA — MARZO 2026
-- empleado_id referencia: 1=Ana 2=Carlos 3=Laura (sin fichajes)
-- -----------------------------------------------------------------------------

-- ===== ANA GARCIA (empleado_id=1) =====

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-02', 'NORMAL', '2026-03-02 09:00:00', '2026-03-02 17:30:00',
    30, 450, 3, NULL, '2026-03-02 17:30:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-03', 'NORMAL', '2026-03-03 09:00:00', '2026-03-03 17:30:00',
    45, 435, 3, NULL, '2026-03-03 17:30:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-04', 'NORMAL', '2026-03-04 09:00:00', '2026-03-04 17:30:00',
    30, 450, 3, NULL, '2026-03-04 17:30:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-05', 'NORMAL', '2026-03-05 09:00:00', '2026-03-05 17:30:00',
    30, 450, 2, 'Empleada olvido fichar entrada. Corregido por encargado.', '2026-03-05 17:45:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-06', 'FESTIVO_LOCAL', NULL, NULL, 0, 0,
    2, 'Festivo local Comunidad de Madrid', '2026-03-06 08:00:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-09', 'NORMAL', '2026-03-09 09:00:00', '2026-03-09 17:30:00',
    30, 450, 3, NULL, '2026-03-09 17:30:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-10', 'NORMAL', '2026-03-10 09:00:00', '2026-03-10 17:30:00',
    30, 450, 3, NULL, '2026-03-10 17:30:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-11', 'BAJA_MEDICA', NULL, NULL, 0, 0,
    2, 'Baja medica tramitada por encargado.', '2026-03-11 09:00:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-12', 'BAJA_MEDICA', NULL, NULL, 0, 0,
    2, 'Continuacion baja medica.', '2026-03-12 09:00:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-13', 'NORMAL', '2026-03-13 09:00:00', '2026-03-13 17:30:00',
    30, 450, 3, NULL, '2026-03-13 17:30:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-16', 'NORMAL', '2026-03-16 09:00:00', '2026-03-16 17:30:00',
    30, 450, 3, NULL, '2026-03-16 17:30:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-17', 'NORMAL', '2026-03-17 09:00:00', '2026-03-17 17:30:00',
    30, 450, 3, NULL, '2026-03-17 17:30:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-18', 'NORMAL', '2026-03-18 09:00:00', '2026-03-18 17:30:00',
    30, 450, 3, NULL, '2026-03-18 17:30:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-19', 'AUSENCIA_INJUSTIFICADA', NULL, NULL, 0, 0,
    5, 'Ausencia injustificada generada automaticamente.', '2026-03-19 23:55:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-20', 'NORMAL', '2026-03-20 09:00:00', '2026-03-20 17:30:00',
    30, 450, 3, NULL, '2026-03-20 17:30:00');

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

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-30', 'DIA_LIBRE_COMPENSATORIO', NULL, NULL, 0, 0,
    2, 'Compensacion por horas extra semana del 2 de marzo', '2026-03-30 00:01:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-31', 'NORMAL', '2026-03-31 09:00:00', '2026-03-31 17:30:00',
    30, 450, 3, NULL, '2026-03-31 17:30:00');

-- ===== CARLOS LOPEZ (empleado_id=2) =====

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-02', 'NORMAL', '2026-03-02 08:00:00', '2026-03-02 16:30:00',
    30, 450, 4, NULL, '2026-03-02 16:30:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-03', 'NORMAL', '2026-03-03 08:00:00', '2026-03-03 16:30:00',
    30, 450, 4, NULL, '2026-03-03 16:30:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-04', 'ASUNTO_PROPIO', NULL, NULL, 0, 0, 2, NULL, '2026-03-04 00:01:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-05', 'NORMAL', '2026-03-05 08:00:00', '2026-03-05 16:30:00',
    30, 450, 4, NULL, '2026-03-05 16:30:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-06', 'FESTIVO_LOCAL', NULL, NULL, 0, 0,
    2, 'Festivo local Comunidad de Madrid', '2026-03-06 08:00:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-09', 'NORMAL', '2026-03-09 08:00:00', '2026-03-09 16:30:00',
    30, 450, 4, NULL, '2026-03-09 16:30:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-10', 'NORMAL', '2026-03-10 08:00:00', '2026-03-10 16:30:00',
    30, 450, 4, NULL, '2026-03-10 16:30:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-11', 'NORMAL', '2026-03-11 08:00:00', '2026-03-11 16:30:00',
    45, 435, 4, NULL, '2026-03-11 16:30:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-12', 'NORMAL', '2026-03-12 08:00:00', '2026-03-12 16:30:00',
    30, 450, 2, 'Salida ajustada por encargado.', '2026-03-12 16:45:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-13', 'NORMAL', '2026-03-13 08:00:00', '2026-03-13 16:30:00',
    30, 450, 4, NULL, '2026-03-13 16:30:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-16', 'NORMAL', '2026-03-16 08:00:00', '2026-03-16 16:30:00',
    30, 450, 4, NULL, '2026-03-16 16:30:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-17', 'NORMAL', '2026-03-17 08:00:00', '2026-03-17 16:30:00',
    30, 450, 4, NULL, '2026-03-17 16:30:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-18', 'NORMAL', '2026-03-18 08:00:00', '2026-03-18 16:30:00',
    30, 450, 4, NULL, '2026-03-18 16:30:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-19', 'PERMISO_RETRIBUIDO', NULL, NULL, 0, 0,
    2, 'Permiso retribuido por cita medica con especialista', '2026-03-19 00:01:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-20', 'NORMAL', '2026-03-20 08:00:00', '2026-03-20 16:30:00',
    30, 450, 4, NULL, '2026-03-20 16:30:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-23', 'NORMAL', '2026-03-23 08:00:00', '2026-03-23 16:30:00',
    30, 450, 4, NULL, '2026-03-23 16:30:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-24', 'NORMAL', '2026-03-24 08:00:00', '2026-03-24 16:30:00',
    30, 450, 4, NULL, '2026-03-24 16:30:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-25', 'NORMAL', '2026-03-25 08:00:00', '2026-03-25 16:30:00',
    30, 450, 4, NULL, '2026-03-25 16:30:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-26', 'NORMAL', '2026-03-26 08:00:00', '2026-03-26 16:30:00',
    30, 450, 4, NULL, '2026-03-26 16:30:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-27', 'NORMAL', '2026-03-27 08:00:00', '2026-03-27 16:30:00',
    30, 450, 4, NULL, '2026-03-27 16:30:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-30', 'NORMAL', '2026-03-30 08:00:00', '2026-03-30 16:30:00',
    30, 450, 4, NULL, '2026-03-30 16:30:00');

INSERT INTO fichajes (empleado_id, fecha, tipo, hora_entrada, hora_salida,
    total_pausas_minutos, jornada_efectiva_minutos, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-31', 'AUSENCIA_INJUSTIFICADA', NULL, NULL, 0, 0,
    5, 'Ausencia injustificada generada automaticamente.', '2026-03-31 23:55:00');

-- -----------------------------------------------------------------------------
-- 5. PAUSAS DE PRUEBA — ANA GARCIA (empleado_id=1)
-- -----------------------------------------------------------------------------

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-02', '2026-03-02 13:30:00', '2026-03-02 14:00:00', 30,
    'COMIDA', 3, NULL, '2026-03-02 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-03', '2026-03-03 10:00:00', '2026-03-03 10:15:00', 15,
    'DESCANSO', 3, NULL, '2026-03-03 10:15:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-03', '2026-03-03 13:30:00', '2026-03-03 14:00:00', 30,
    'COMIDA', 3, NULL, '2026-03-03 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-04', '2026-03-04 13:30:00', '2026-03-04 14:00:00', 30,
    'COMIDA', 3, NULL, '2026-03-04 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-05', '2026-03-05 13:30:00', '2026-03-05 14:00:00', 30,
    'COMIDA', 3, NULL, '2026-03-05 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-09', '2026-03-09 13:30:00', '2026-03-09 14:00:00', 30,
    'COMIDA', 3, NULL, '2026-03-09 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-10', '2026-03-10 13:30:00', '2026-03-10 14:00:00', 30,
    'COMIDA', 3, NULL, '2026-03-10 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-13', '2026-03-13 13:30:00', '2026-03-13 14:00:00', 30,
    'COMIDA', 3, NULL, '2026-03-13 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-16', '2026-03-16 13:30:00', '2026-03-16 14:00:00', 30,
    'COMIDA', 3, NULL, '2026-03-16 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-17', '2026-03-17 13:30:00', '2026-03-17 14:00:00', 30,
    'COMIDA', 3, NULL, '2026-03-17 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-18', '2026-03-18 14:00:00', '2026-03-18 14:30:00', 30,
    'COMIDA', 2, 'Inicio y fin de pausa corregidos por encargado.', '2026-03-18 14:35:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-20', '2026-03-20 13:30:00', '2026-03-20 14:00:00', 30,
    'COMIDA', 3, NULL, '2026-03-20 14:00:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-31', '2026-03-31 13:30:00', '2026-03-31 14:00:00', 30,
    'COMIDA', 3, NULL, '2026-03-31 14:00:00');

-- -----------------------------------------------------------------------------
-- 6. PLANIFICACION DE AUSENCIAS
-- -----------------------------------------------------------------------------

INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado,
    usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-06', 'FESTIVO_LOCAL', TRUE,
    2, 'Festivo local Comunidad de Madrid', '2026-03-05 09:00:00');

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

INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado,
    usuario_id, observaciones, fecha_creacion)
VALUES (1, '2026-03-30', 'DIA_LIBRE_COMPENSATORIO', TRUE,
    2, 'Compensacion por horas extra semana del 2 de marzo', '2026-03-15 09:00:00');

INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado,
    usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-04', 'ASUNTO_PROPIO', TRUE, 2, NULL, '2026-03-03 09:00:00');

INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado,
    usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-06', 'FESTIVO_LOCAL', TRUE,
    2, 'Festivo local Comunidad de Madrid', '2026-03-05 09:00:00');

INSERT INTO planificacion_ausencias (empleado_id, fecha, tipo_ausencia, procesado,
    usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-19', 'PERMISO_RETRIBUIDO', TRUE,
    2, 'Permiso retribuido por cita medica con especialista', '2026-03-18 09:00:00');

-- -----------------------------------------------------------------------------
-- 7. PAUSAS DE PRUEBA — CARLOS LOPEZ (empleado_id=2)
-- -----------------------------------------------------------------------------

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-02', '2026-03-02 14:00:00', '2026-03-02 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-02 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-03', '2026-03-03 14:00:00', '2026-03-03 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-03 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-05', '2026-03-05 14:00:00', '2026-03-05 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-05 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-09', '2026-03-09 14:00:00', '2026-03-09 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-09 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-10', '2026-03-10 14:00:00', '2026-03-10 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-10 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-11', '2026-03-11 10:00:00', '2026-03-11 10:15:00', 15,
    'DESCANSO', 4, NULL, '2026-03-11 10:15:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-11', '2026-03-11 14:00:00', '2026-03-11 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-11 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-12', '2026-03-12 14:00:00', '2026-03-12 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-12 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-13', '2026-03-13 14:00:00', '2026-03-13 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-13 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-16', '2026-03-16 14:00:00', '2026-03-16 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-16 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-17', '2026-03-17 14:00:00', '2026-03-17 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-17 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-18', '2026-03-18 14:00:00', '2026-03-18 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-18 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-20', '2026-03-20 14:00:00', '2026-03-20 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-20 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-23', '2026-03-23 14:00:00', '2026-03-23 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-23 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-24', '2026-03-24 14:00:00', '2026-03-24 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-24 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-25', '2026-03-25 14:00:00', '2026-03-25 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-25 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-26', '2026-03-26 14:00:00', '2026-03-26 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-26 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-27', '2026-03-27 14:00:00', '2026-03-27 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-27 14:30:00');

INSERT INTO pausas (empleado_id, fecha, hora_inicio, hora_fin, duracion_minutos,
    tipo_pausa, usuario_id, observaciones, fecha_creacion)
VALUES (2, '2026-03-30', '2026-03-30 14:00:00', '2026-03-30 14:30:00', 30,
    'COMIDA', 4, NULL, '2026-03-30 14:30:00');

-- ============================================================
-- FIN DEL SCRIPT
-- ============================================================
