-- =============================================================================
-- Datos iniciales del sistema StaffFlow
-- Este script se ejecuta automáticamente al arrancar con perfil 'dev' (H2).
-- Spring Boot lo carga después de que Hibernate crea el esquema (ddl-auto: create-drop).
-- En MySQL (perfil 'mysql') estos datos se insertan manualmente una sola vez.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Usuario de servicio para auditoría de terminal (E48-E51)
-- Necesario porque fichajes.usuario_id y pausas.usuario_id son NOT NULL (RNF-L01).
-- El terminal no tiene sesión JWT: usa este usuario como auditoría de servicio.
-- Password hash de 'terminal_service_2026' con BCrypt (nunca se usa para login).
-- Decisión de diseño registrada en D-021 (Opción B: usuario sistema).
-- -----------------------------------------------------------------------------
INSERT INTO usuarios (username, password_hash, email, rol, activo, fecha_creacion)
VALUES (
    'terminal_service',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lmuS',
    'terminal@staffflow.internal',
    'ADMIN',
    TRUE,
    NOW()
);
