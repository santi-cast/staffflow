-- ============================================================
-- StaffFlow v1.0 — Script DDL MySQL 8.0
-- TFG DAM 2025/2026 — iLERNA — Santiago
-- ============================================================
-- Ejecutar en orden. Las FK exigen que las tablas padre
-- existan antes que las tablas hijo.
-- Orden: configuracion_empresa → usuarios → empleados →
--        planificacion_ausencias → fichajes → pausas → saldos_anuales
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
    id               INT           NOT NULL AUTO_INCREMENT,
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
    id                  INT           NOT NULL AUTO_INCREMENT,
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
    id                            INT          NOT NULL AUTO_INCREMENT,
    usuario_id                    INT          NOT NULL,
    nombre                        VARCHAR(50)  NOT NULL,
    apellido1                     VARCHAR(50)  NOT NULL,
    apellido2                     VARCHAR(50)  NULL,
    dni                           VARCHAR(20)  NOT NULL,
    nss                           VARCHAR(20)  NOT NULL,
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
    CONSTRAINT uq_empleados_usuario_id  UNIQUE (usuario_id),
    CONSTRAINT uq_empleados_dni         UNIQUE (dni),
    CONSTRAINT uq_empleados_nss         UNIQUE (nss),
    CONSTRAINT uq_empleados_pin         UNIQUE (pin_terminal),
    CONSTRAINT uq_empleados_nfc         UNIQUE (codigo_nfc),

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
    id              INT       NOT NULL AUTO_INCREMENT,
    empleado_id     INT       NULL,           -- NULL = festivo global
    fecha           DATE      NOT NULL,
    tipo_ausencia   ENUM('FESTIVO_NACIONAL','FESTIVO_LOCAL','VACACIONES',
                         'ASUNTO_PROPIO','PERMISO_RETRIBUIDO',
                         'DIA_LIBRE_COMPENSATORIO') NOT NULL,
    procesado       BOOLEAN   NOT NULL DEFAULT FALSE,
    usuario_id      INT       NOT NULL,
    observaciones   TEXT      NULL,
    fecha_creacion  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_planificacion_ausencias PRIMARY KEY (id),
    -- 1 ausencia por empleado por fecha (NULL se trata como valor distinto en MySQL)
    CONSTRAINT uq_planificacion_empleado_fecha UNIQUE (empleado_id, fecha),

    -- Índice crítico: proceso diario 00:01 → WHERE fecha = HOY AND procesado = FALSE
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
-- 1 registro por empleado por día. Tabla central del sistema.
-- Sin DELETE (RNF-L01 / RD-ley 8/2019).
-- ============================================================
CREATE TABLE fichajes (
    id                        INT       NOT NULL AUTO_INCREMENT,
    empleado_id               INT       NOT NULL,
    fecha                     DATE      NOT NULL,
    tipo                      ENUM('NORMAL','FESTIVO_NACIONAL','FESTIVO_LOCAL',
                                   'VACACIONES','ASUNTO_PROPIO',
                                   'PERMISO_RETRIBUIDO','BAJA_MEDICA',
                                   'DIA_LIBRE_COMPENSATORIO',
                                   'AUSENCIA_INJUSTIFICADA') NOT NULL,
    hora_entrada              DATETIME  NULL,
    hora_salida               DATETIME  NULL,
    total_pausas_minutos      INT       NOT NULL DEFAULT 0,
    jornada_efectiva_minutos  INT       NOT NULL DEFAULT 0,
    usuario_id                INT       NOT NULL,
    observaciones             TEXT      NULL,
    fecha_creacion            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_fichajes PRIMARY KEY (id),
    -- 1 fichaje por empleado por día (RNF-I02 / RD-ley 8/2019)
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
-- Múltiples pausas por empleado por día.
-- hora_fin NULL = pausa activa en curso.
-- Sin DELETE (RNF-L01 / RD-ley 8/2019).
-- ============================================================
CREATE TABLE pausas (
    id                 INT       NOT NULL AUTO_INCREMENT,
    empleado_id        INT       NOT NULL,
    fecha              DATE      NOT NULL,
    hora_inicio        DATETIME  NOT NULL,
    hora_fin           DATETIME  NULL,
    duracion_minutos   INT       NULL,        -- NULL hasta que se cierra la pausa
    tipo_pausa         ENUM('COMIDA','DESCANSO','AUSENCIA_RETRIBUIDA','OTROS') NOT NULL,
    usuario_id         INT       NOT NULL,
    observaciones      TEXT      NULL,
    fecha_creacion     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_pausas PRIMARY KEY (id),

    -- Índice de rendimiento: historial de pausas por empleado y fecha
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
-- 1 registro por empleado por año.
-- Se actualiza por el proceso de cierre diario (23:30)
-- y se reinicia por el cierre anual (31 dic).
-- ============================================================
CREATE TABLE saldos_anuales (
    id                                       INT           NOT NULL AUTO_INCREMENT,
    empleado_id                              INT           NOT NULL,
    anio                                     INT           NOT NULL,

    -- Contadores de días por tipo
    dias_trabajados                          INT           NOT NULL DEFAULT 0,
    dias_baja_medica                         INT           NOT NULL DEFAULT 0,
    dias_permiso_retribuido                  INT           NOT NULL DEFAULT 0,
    dias_ausencia_injustificada              INT           NOT NULL DEFAULT 0,

    -- Vacaciones
    dias_vacaciones_derecho_anio             INT           NOT NULL,
    dias_vacaciones_pendientes_anio_anterior INT           NOT NULL DEFAULT 0,
    dias_vacaciones_consumidos               INT           NOT NULL DEFAULT 0,
    dias_vacaciones_disponibles              INT           NOT NULL,

    -- Asuntos propios
    dias_asuntos_propios_derecho_anio        INT           NOT NULL,
    dias_asuntos_propios_pendientes_anterior INT           NOT NULL DEFAULT 0,
    dias_asuntos_propios_consumidos          INT           NOT NULL DEFAULT 0,
    dias_asuntos_propios_disponibles         INT           NOT NULL,

    -- Horas
    horas_ausencia_retribuida                DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    saldo_horas                              DECIMAL(10,2) NOT NULL DEFAULT 0.00,

    -- Control de proceso
    calculado_hasta_fecha                    DATE          NULL,
    fecha_ultima_modificacion                TIMESTAMP     NOT NULL
                                             DEFAULT CURRENT_TIMESTAMP
                                             ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_saldos_anuales PRIMARY KEY (id),
    -- 1 registro por empleado por año
    CONSTRAINT uq_saldos_empleado_anio UNIQUE (empleado_id, anio),

    INDEX idx_saldos_anio (anio),

    CONSTRAINT fk_saldos_empleado
        FOREIGN KEY (empleado_id) REFERENCES empleados (id)
        ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- FIN DEL SCRIPT
-- ============================================================
