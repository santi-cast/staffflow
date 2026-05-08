package com.staffflow.exception;

import java.time.LocalDate;
import java.util.List;

/**
 * Excepción lanzada cuando crearRango detecta ausencias ya planificadas
 * (procesado=false) en algún día del rango solicitado y sobrescribir=false.
 *
 * Devuelve HTTP 409 con la lista de fechas conflictivas para que Android
 * pueda mostrar un AlertDialog pidiendo confirmación al usuario.
 *
 * @author Santiago Castillo
 */
public class RangoConflictException extends RuntimeException {

    private final List<LocalDate> fechasConflictivas;

    public RangoConflictException(List<LocalDate> fechasConflictivas) {
        super("Hay ausencias ya planificadas en las fechas indicadas");
        this.fechasConflictivas = fechasConflictivas;
    }

    public List<LocalDate> getFechasConflictivas() {
        return fechasConflictivas;
    }
}
