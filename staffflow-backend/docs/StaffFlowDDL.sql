-- =============================================================================
-- StaffFlow — DDL canonico del esquema relacional (MySQL 8.0)
-- =============================================================================
-- Este script reproduce el esquema de base de datos generado por Hibernate
-- a partir de las 7 entidades JPA del paquete com.staffflow.domain.entity
-- y los 7 enumerados del paquete com.staffflow.domain.enums.
--
-- Compatible con MySQL 8.0 y, por extension, con H2 en MODE=MySQL utilizado
-- en el perfil de desarrollo (application-dev.yml).
--
-- Uso previsto:
--   - Documentacion de referencia del esquema para la memoria del TFG
--     (Anexo 12.4 — DDL).
--   - Inicializacion manual del esquema en una base de datos MySQL 8.0
--     vacia cuando se despliega el backend con el perfil mysql
--     (ddl-auto: validate exige que el esquema exista previamente).
--   - Soporte futuro para el script de inicializacion del contenedor MySQL
--     del docker-compose.yml del proyecto.
--
-- Convenciones:
--   - Identificadores en snake_case (alineados con @Column name = "...").
--   - Tipos enumerados materializados con CHECK (...) sobre VARCHAR para
--     reproducir el @Enumerated(EnumType.STRING) del codigo Java.
--   - Las restricciones UNIQUE explicitas del codigo se declaran como
--     UNIQUE KEY con nombre.
--   - Los indices auxiliares declarados con @Index en las entidades JPA se
--     reproducen como KEY con el mismo nombre.
--   - Engine InnoDB (transacciones + claves foraneas).
--   - Charset utf8mb4 con collation utf8mb4_unicode_ci.
--
-- Fuente de verdad: las 7 clases @Entity del paquete domain/entity y los 7
-- @Enumerated del paquete domain/enums. Si una entidad cambia, este script
-- debe regenerarse desde el codigo Java actualizado para mantener la
-- coherencia con el contrato real del backend.
-- =============================================================================

SET FOREIGN_KEY_CHECKS = 0;
SET NAMES utf8mb4;

-- =============================================================================
-- TABLA: configuracion_empresa
-- Entidad: ConfiguracionEmpresa
-- Tabla singleton (id = 1 siempre). Contiene los datos identificativos
-- y de contacto que aparecen en cabeceras de informes y PDFs firmables
-- (RF-38, RF-39, RF-40).
-- =============================================================================
CREATE TABLE configuracion_empresa (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    nombre_empresa  VARCHAR(100) NOT NULL,
    cif             VARCHAR(20)  NOT NULL,
    direccion       TEXT             NULL,
    email           VARCHAR(100)     NULL,
    telefono        VARCHAR(20)      NULL,
    logo_path       VARCHAR(255)     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_configuracion_empresa_cif (cif)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- =============================================================================
-- TABLA: usuarios
-- Entidad: Usuario
-- Usuario del sistema con acceso a la aplicacion movil y/o web.
-- Todo empleado tiene un usuario asociado, pero no todo usuario tiene
-- un empleado (p.ej. un ADMIN sin ficha de empleado).
-- El rol determina los permisos de acceso (RF-01, RF-02).
-- Los campos reset_token y reset_token_expiry son andamiaje v2.0
-- (siempre NULL en v1: E04 entrega contrasena temporal directa por email).
-- =============================================================================
CREATE TABLE usuarios (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    username            VARCHAR(50)  NOT NULL,
    password_hash       VARCHAR(255) NOT NULL,
    email               VARCHAR(100) NOT NULL,
    rol                 VARCHAR(20)  NOT NULL,
    activo              BIT(1)       NOT NULL DEFAULT 1,
    fecha_creacion      DATETIME(6)  NOT NULL,
    reset_token         VARCHAR(255)     NULL,
    reset_token_expiry  DATETIME(6)      NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usuarios_username (username),
    UNIQUE KEY uk_usuarios_email (email),
    KEY idx_usuarios_activo (activo),
    KEY idx_usuarios_reset_token (reset_token),
    CONSTRAINT chk_usuarios_rol CHECK (rol IN ('ADMIN', 'ENCARGADO', 'EMPLEADO'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- =============================================================================
-- TABLA: empleados
-- Entidad: Empleado
-- Ficha del empleado vinculada a un Usuario del sistema. Relacion 1:1
-- obligatoria (usuario_id UNIQUE NOT NULL). Contiene los datos
-- contractuales y operativos necesarios para el calculo de jornada,
-- vacaciones y asuntos propios (RF-35, RF-36). La categoria es
-- informativa y no afecta a los permisos.
-- pin_terminal CHAR(4) UNIQUE: PIN de 4 digitos para fichar desde el
-- terminal compartido. No se expone en ningun DTO response.
-- codigo_nfc UNIQUE: campo soporte para fichaje por NFC reservado a
-- versiones futuras (no consumido por ningun flujo de fichaje en v1).
-- =============================================================================
CREATE TABLE empleados (
    id                            BIGINT      NOT NULL AUTO_INCREMENT,
    usuario_id                    BIGINT      NOT NULL,
    nombre                        VARCHAR(50) NOT NULL,
    apellido1                     VARCHAR(50) NOT NULL,
    apellido2                     VARCHAR(50)     NULL,
    dni                           VARCHAR(20) NOT NULL,
    numero_empleado               VARCHAR(20) NOT NULL,
    fecha_alta                    DATE        NOT NULL,
    categoria                     VARCHAR(20) NOT NULL,
    jornada_semanal_horas         DOUBLE      NOT NULL DEFAULT 40.0,
    jornada_diaria_minutos        INT         NOT NULL DEFAULT 480,
    dias_vacaciones_anuales       INT         NOT NULL DEFAULT 22,
    dias_asuntos_propios_anuales  INT         NOT NULL DEFAULT 3,
    pin_terminal                  CHAR(4)     NOT NULL,
    codigo_nfc                    VARCHAR(50)     NULL,
    activo                        BIT(1)      NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uk_empleados_usuario_id (usuario_id),
    UNIQUE KEY uk_empleados_dni (dni),
    UNIQUE KEY uk_empleados_numero_empleado (numero_empleado),
    UNIQUE KEY uk_empleados_pin_terminal (pin_terminal),
    UNIQUE KEY uk_empleados_codigo_nfc (codigo_nfc),
    CONSTRAINT fk_empleados_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuarios (id),
    CONSTRAINT chk_empleados_categoria CHECK (
        categoria IN ('OPERARIO', 'ADMINISTRATIVO', 'TECNICO', 'ENCARGADO', 'OTRO')
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- =============================================================================
-- TABLA: fichajes
-- Entidad: Fichaje
-- Registro diario de jornada laboral de un empleado.
-- Un unico fichaje por empleado por dia (RNF-I02, RD-ley 8/2019)
-- materializado mediante UNIQUE (empleado_id, fecha).
-- Inmutable tras su creacion: sin DELETE fisico ni modificacion de datos
-- de jornada una vez cerrada (RNF-L01).
-- =============================================================================
CREATE TABLE fichajes (
    id                       BIGINT      NOT NULL AUTO_INCREMENT,
    empleado_id              BIGINT      NOT NULL,
    fecha                    DATE        NOT NULL,
    tipo                     VARCHAR(30) NOT NULL,
    hora_entrada             DATETIME(6)     NULL,
    hora_salida              DATETIME(6)     NULL,
    total_pausas_minutos     INT         NOT NULL DEFAULT 0,
    jornada_efectiva_minutos INT         NOT NULL DEFAULT 0,
    usuario_id               BIGINT      NOT NULL,
    observaciones            TEXT            NULL,
    fecha_creacion           DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_fichajes_empleado_fecha (empleado_id, fecha),
    CONSTRAINT fk_fichajes_empleado
        FOREIGN KEY (empleado_id) REFERENCES empleados (id),
    CONSTRAINT fk_fichajes_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuarios (id),
    CONSTRAINT chk_fichajes_tipo CHECK (
        tipo IN (
            'NORMAL', 'FESTIVO_NACIONAL', 'FESTIVO_LOCAL', 'VACACIONES',
            'ASUNTO_PROPIO', 'PERMISO_RETRIBUIDO', 'BAJA_MEDICA',
            'DIA_LIBRE_COMPENSATORIO', 'DIA_LIBRE', 'AUSENCIA_INJUSTIFICADA'
        )
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- =============================================================================
-- TABLA: pausas
-- Entidad: Pausa
-- Registro de una pausa dentro de la jornada laboral de un empleado.
-- Una pausa activa se identifica por hora_fin = NULL (E50, E51).
-- Al cerrar la pausa se calcula duracion_minutos con Math.floor,
-- redondeando a la baja para beneficiar al empleado.
-- Sin DELETE fisico (RNF-L01).
-- =============================================================================
CREATE TABLE pausas (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    empleado_id       BIGINT      NOT NULL,
    fecha             DATE        NOT NULL,
    hora_inicio       DATETIME(6) NOT NULL,
    hora_fin          DATETIME(6)     NULL,
    duracion_minutos  INT             NULL,
    tipo_pausa        VARCHAR(20) NOT NULL,
    usuario_id        BIGINT      NOT NULL,
    observaciones     TEXT            NULL,
    fecha_creacion    DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_pausas_empleado_fecha (empleado_id, fecha),
    CONSTRAINT fk_pausas_empleado
        FOREIGN KEY (empleado_id) REFERENCES empleados (id),
    CONSTRAINT fk_pausas_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuarios (id),
    CONSTRAINT chk_pausas_tipo_pausa CHECK (
        tipo_pausa IN ('COMIDA', 'DESCANSO', 'AUSENCIA_RETRIBUIDA', 'OTROS')
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- =============================================================================
-- TABLA: planificacion_ausencias
-- Entidad: PlanificacionAusencia
-- Planificacion anticipada de una ausencia o festivo. Un registro por
-- dia: el proceso nocturno (@Scheduled 23:55) consulta WHERE fecha = HOY
-- AND procesado = FALSE y convierte cada registro en un Fichaje del tipo
-- correspondiente (RF-26).
-- Si empleado_id = NULL el registro es un festivo global que aplica a
-- todos los empleados activos (RF-26).
-- Solo se permite DELETE si procesado = FALSE.
-- UNIQUE (empleado_id, fecha): MySQL trata NULL como valor distinto, lo
-- que permite multiples festivos globales con distintas fechas.
-- =============================================================================
CREATE TABLE planificacion_ausencias (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    empleado_id     BIGINT          NULL,
    fecha           DATE        NOT NULL,
    tipo_ausencia   VARCHAR(25) NOT NULL,
    procesado       BIT(1)      NOT NULL DEFAULT 0,
    usuario_id      BIGINT      NOT NULL,
    observaciones   TEXT            NULL,
    fecha_creacion  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_planificacion_ausencias_empleado_fecha (empleado_id, fecha),
    KEY idx_planificacion_fecha_procesado (fecha, procesado),
    CONSTRAINT fk_planificacion_ausencias_empleado
        FOREIGN KEY (empleado_id) REFERENCES empleados (id),
    CONSTRAINT fk_planificacion_ausencias_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuarios (id),
    CONSTRAINT chk_planificacion_ausencias_tipo CHECK (
        tipo_ausencia IN (
            'FESTIVO_NACIONAL', 'FESTIVO_LOCAL', 'VACACIONES',
            'ASUNTO_PROPIO', 'PERMISO_RETRIBUIDO',
            'DIA_LIBRE_COMPENSATORIO', 'DIA_LIBRE'
        )
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- =============================================================================
-- TABLA: saldos_anuales
-- Entidad: SaldoAnual
-- Saldo anual de vacaciones, asuntos propios y horas de un empleado.
-- Un registro por empleado por anio (UNIQUE (empleado_id, anio)).
-- Lo actualiza el proceso nocturno (@Scheduled) de forma incremental e
-- idempotente: calculado_hasta_fecha evita reprocesar dias ya
-- contabilizados (RF-35, RF-36, RF-53).
-- Los campos pendientesAnioAnterior (vacaciones y asuntos propios) estan
-- previstos para el cierre anual con arrastre de saldos de v2.0; en v1
-- son siempre 0 porque el cierre anual no esta implementado.
-- =============================================================================
CREATE TABLE saldos_anuales (
    id                                            BIGINT        NOT NULL AUTO_INCREMENT,
    empleado_id                                   BIGINT        NOT NULL,
    anio                                          INT           NOT NULL,
    dias_trabajados                               INT           NOT NULL DEFAULT 0,
    dias_baja_medica                              INT           NOT NULL DEFAULT 0,
    dias_permiso_retribuido                       INT           NOT NULL DEFAULT 0,
    dias_ausencia_injustificada                   INT           NOT NULL DEFAULT 0,
    dias_vacaciones_derecho_anio                  INT           NOT NULL,
    dias_vacaciones_pendientes_anio_anterior      INT           NOT NULL DEFAULT 0,
    dias_vacaciones_consumidos                    INT           NOT NULL DEFAULT 0,
    dias_vacaciones_disponibles                   INT           NOT NULL,
    dias_asuntos_propios_derecho_anio             INT           NOT NULL,
    dias_asuntos_propios_pendientes_anterior      INT           NOT NULL DEFAULT 0,
    dias_asuntos_propios_consumidos               INT           NOT NULL DEFAULT 0,
    dias_asuntos_propios_disponibles              INT           NOT NULL,
    horas_ausencia_retribuida                     DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    saldo_horas                                   DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    calculado_hasta_fecha                         DATE              NULL,
    fecha_ultima_modificacion                     DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_saldos_anuales_empleado_anio (empleado_id, anio),
    KEY idx_saldos_anio (anio),
    CONSTRAINT fk_saldos_anuales_empleado
        FOREIGN KEY (empleado_id) REFERENCES empleados (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- FIN DEL SCRIPT
-- =============================================================================
-- Resumen:
--   7 tablas relacionales (configuracion_empresa, usuarios, empleados,
--   fichajes, pausas, planificacion_ausencias, saldos_anuales).
--   7 enumerados materializados como VARCHAR + CHECK:
--     Rol            (3 valores: ADMIN, ENCARGADO, EMPLEADO).
--     CategoriaEmpleado (5 valores).
--     TipoFichaje    (10 valores).
--     TipoPausa      (4 valores).
--     TipoAusencia   (7 valores).
--   Los enumerados EstadoPresencia y EstadoTerminal NO se persisten:
--     se calculan en tiempo de ejecucion a partir de los registros
--     activos del dia.
-- =============================================================================
