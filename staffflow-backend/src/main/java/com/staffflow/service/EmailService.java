package com.staffflow.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Servicio de envío de correo electrónico via Gmail SMTP.
 *
 * <p>Credenciales configuradas por variables de entorno:
 * <ul>
 *   <li>{@code MAIL_USERNAME} — dirección Gmail del remitente</li>
 *   <li>{@code MAIL_PASSWORD} — App Password de Google (16 caracteres)</li>
 * </ul>
 * </p>
 *
 * <p>El envío es {@code @Async} para no bloquear el hilo HTTP del endpoint E04.
 * Si el envío falla se loguea el error pero no se propaga al cliente (el
 * endpoint E04 ya respondió 200 antes de que se dispare el correo).</p>
 *
 * <p><b>v1.0 — no operativo:</b> en v1 el flujo real de recuperación es el
 * de {@link #enviarPasswordTemporal(String, String)}, que entrega una
 * contraseña temporal de 8 caracteres en claro por correo. El envío de un
 * token UUID de un solo uso con caducidad de 30 minutos pertenece al
 * andamiaje reservado para v2.0 (ver memoria TFG, bloque B10 Vías Futuras →
 * Reset password con token UUID).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${staffflow.mail.from}")
    private String from;

    /**
     * Envía el correo de recuperación de contraseña con la contraseña
     * temporal generada en E04.
     *
     * <p><b>v1.0 — no operativo:</b> en v1 este flujo entrega una contraseña
     * temporal de 8 caracteres por email (E04). El token UUID de 30 minutos
     * descrito a continuación pertenece al andamiaje reservado para v2.0
     * (ver memoria TFG, bloque B10 Vías Futuras → Reset password con token UUID).</p>
     *
     * <p>El cuerpo del correo es HTML y muestra la contraseña temporal en
     * claro, indicando al usuario que la cambie desde Ajustes una vez
     * dentro de la aplicación.</p>
     *
     * @param destinatario      email del usuario al que se envía el correo
     * @param passwordTemporal  contraseña temporal de 8 caracteres
     *                          alfanuméricos generada en E04 (se incrusta
     *                          en el cuerpo del correo en claro)
     */
    @Async
    public void enviarPasswordTemporal(String destinatario, String passwordTemporal) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(from);
            helper.setTo(destinatario);
            helper.setSubject("StaffFlow — Tu contraseña temporal");
            helper.setText(buildHtml(passwordTemporal), true);

            mailSender.send(message);
            log.info("[EMAIL] Contraseña temporal enviada a {}", destinatario);

        } catch (MessagingException e) {
            log.error("[EMAIL] Error al enviar contraseña temporal a {}: {}", destinatario, e.getMessage());
        }
    }

    private String buildHtml(String passwordTemporal) {
        return """
                <html>
                <body style="font-family: sans-serif; color: #333; max-width: 480px; margin: auto; padding: 24px;">
                  <h2 style="color: #1a1a1a;">Tu contraseña temporal</h2>
                  <p>Hemos recibido una solicitud de recuperación de contraseña para tu cuenta en <strong>StaffFlow</strong>.</p>
                  <p>Usa esta contraseña temporal para iniciar sesión:</p>
                  <p style="background: #f5f5f5; padding: 16px; border-radius: 6px;
                             font-family: monospace; font-size: 28px; letter-spacing: 4px;
                             text-align: center;">
                    %s
                  </p>
                  <p>Una vez dentro, ve a <strong>Ajustes → Cambiar contraseña</strong> para establecer una nueva.</p>
                  <p style="color: #888; font-size: 13px;">
                    Si no has solicitado este cambio, ignora este correo.
                  </p>
                </body>
                </html>
                """.formatted(passwordTemporal);
    }
}
