package com.staffflow.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Tests de arquitectura de la capa de servicio.
 *
 * <p>Estos tests no verifican comportamiento sino convenciones estructurales
 * que son fáciles de violar accidentalmente y caras de detectar en revisión.
 * Cierran la deuda DEBT-05 del SDD {@code backend-hardening-high-issues}:
 * tras la migración de {@code IllegalStateException} a {@link com.staffflow.exception.NotFoundException}
 * en paths de "no encontrado", queremos un guardarraíl que detecte regresiones
 * automáticamente.</p>
 *
 * <p><strong>Regla activa:</strong> ningún servicio puede lanzar
 * {@code IllegalStateException} excepto las clases explícitamente listadas
 * en la whitelist. ISE en services se reserva para errores genuinos de
 * estado interno (5xx) — los casos legítimos actuales son:</p>
 * <ul>
 *   <li>{@code PdfService} — errores de iText7 al generar PDFs (I/O fallido,
 *       documento corrupto). El cliente no puede recuperarse: 5xx es correcto.</li>
 *   <li>{@code SaldoService#obtenerMiSaldo} — años fuera del rango contractual
 *       del empleado (alta futura o futuro). Es estado inválido del request,
 *       no recurso ausente. Documentado en GlobalExceptionHandler.</li>
 * </ul>
 *
 * <p><strong>Cómo extender la whitelist:</strong> añadir una clase requiere
 * justificarla aquí mismo en este Javadoc. Si la causa real es "el recurso
 * no existe en BD", la respuesta correcta NO es ampliar la whitelist sino
 * usar {@link com.staffflow.exception.NotFoundException}.</p>
 *
 * @author Santiago Castillo
 */
@DisplayName("Arquitectura — capa de servicio")
class ServiceLayerArchitectureTest {

    /**
     * Paquete raíz de la capa de servicio. Importamos todo el árbol bajo
     * {@code com.staffflow.service} excluyendo tests para que el escaneo
     * sea estable entre ejecuciones.
     */
    private static final String SERVICE_PACKAGE = "com.staffflow.service..";

    /**
     * Clases que tienen permiso explícito de lanzar IllegalStateException.
     * Cualquier adición a esta lista debe documentarse en el Javadoc de la
     * clase de test (ver párrafo "Cómo extender la whitelist").
     */
    private static final String[] WHITELIST_ISE = {
            "com.staffflow.service.PdfService",
            "com.staffflow.service.SaldoService"
    };

    @Test
    @DisplayName("Ningún servicio (fuera de whitelist) lanza IllegalStateException — usar NotFoundException o ConflictException")
    void servicios_no_lanzan_IllegalStateException_excepto_whitelist() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(SERVICE_PACKAGE)
                .and().doNotHaveFullyQualifiedName(WHITELIST_ISE[0])
                .and().doNotHaveFullyQualifiedName(WHITELIST_ISE[1])
                .should().dependOnClassesThat().haveFullyQualifiedName("java.lang.IllegalStateException")
                .because("ISE en services indica regresión del patrón viejo. "
                        + "Para 'no encontrado' usar NotFoundException (404), "
                        + "para conflictos usar ConflictException (409). "
                        + "ISE se reserva para errores internos genuinos (5xx) — "
                        + "ver Javadoc de ServiceLayerArchitectureTest para casos legítimos.");

        rule.check(new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.staffflow.service"));
    }
}
