    // ---------------------------------------------------------------
    // Método añadido en Bloque 6 Tarea 2 (PresenciaService E35-E37)
    // ---------------------------------------------------------------

    /**
     * Devuelve todas las ausencias planificadas de una fecha concreta
     * que aún no han sido procesadas por el proceso diario.
     *
     * Usado por PresenciaService para determinar qué empleados tienen
     * estado AUSENCIA_PLANIFICADA al construir el parte diario (E35-E37).
     * Una ausencia planificada con procesado=false significa que el proceso
     * nocturno (@Scheduled 00:01) aún no la ha convertido en fichaje,
     * por lo que el empleado no tendrá fichaje para ese día todavía.
     *
     * Incluye ausencias globales (empleado IS NULL — festivos nacionales/locales)
     * porque también determinan el estado de los empleados ese día.
     * El service comprueba si hay un festivo global antes de clasificar
     * a empleados sin fichaje como SIN_JUSTIFICAR.
     *
     * LEFT JOIN FETCH es necesario porque empleado puede ser null
     * en el caso de festivos globales (RF-26).
     *
     * @param fecha fecha a consultar (normalmente hoy)
     * @return lista de ausencias planificadas pendientes para esa fecha,
     *         incluyendo festivos globales (empleado = null)
     */
    @Query("SELECT a FROM PlanificacionAusencia a LEFT JOIN FETCH a.empleado " +
           "WHERE a.fecha = :fecha AND a.procesado = false")
    List<PlanificacionAusencia> findByFechaAndProcesadoFalse(@Param("fecha") LocalDate fecha);
