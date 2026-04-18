package com.staffflow.android.data.remote.repository

import com.staffflow.android.data.remote.api.InformeApiService
import com.staffflow.android.util.safeApiCall
import okhttp3.ResponseBody

/**
 * Repositorio para los endpoints de informes (E42-E44, E45-E47, E53).
 *
 * Accesible con rol ADMIN o ENCARGADO.
 * Los metodos PDF devuelven ResponseBody para escribir el byte[] a un
 * fichero temporal y abrirlo con Intent ACTION_VIEW via FileProvider.
 * Los metodos HTML devuelven ResponseBody para cargarlo en un WebView
 * (visualizacion en pantalla) o enviarlo a PrintManager (impresion).
 *
 * @param api Instancia de InformeApiService creada por NetworkModule.retrofit.
 */
class InformeRepository(private val api: InformeApiService) {

    /**
     * E42 - Informe de horas de un empleado en HTML (formato=html).
     * P28 tab Horas individual -> boton Imprimir y boton Ver informe.
     */
    suspend fun getInformeHorasHtml(
        empleadoId: Long,
        desde: String,
        hasta: String
    ): Result<ResponseBody> =
        safeApiCall { api.getInformeHorasHtml(empleadoId, desde, hasta) }

    /**
     * E43 - Informe de horas global en HTML (formato=html).
     * P28 tab Horas global -> boton Ver informe.
     */
    suspend fun getInformeHorasGlobalHtml(desde: String, hasta: String): Result<ResponseBody> =
        safeApiCall { api.getInformeHorasGlobalHtml(desde, hasta) }

    /**
     * E44 - Informe de saldos anuales en HTML (formato=html).
     * P28 tab Saldos -> boton Ver saldos.
     */
    suspend fun getInformeSaldosHtml(anio: Int): Result<ResponseBody> =
        safeApiCall { api.getInformeSaldosHtml(anio) }

    /**
     * E45 - PDF informe de horas de un empleado.
     * P28 tab Horas individual -> boton PDF.
     */
    suspend fun getPdfHorasEmpleado(
        empleadoId: Long,
        desde: String,
        hasta: String
    ): Result<ResponseBody> =
        safeApiCall { api.getPdfHorasEmpleado(empleadoId, desde, hasta) }

    /**
     * E46 - PDF informe de horas global de todos los empleados.
     * P28 tab Horas global -> boton PDF.
     */
    suspend fun getPdfHorasGlobal(desde: String, hasta: String): Result<ResponseBody> =
        safeApiCall { api.getPdfHorasGlobal(desde, hasta) }

    /**
     * E47 - PDF informe de saldos anuales.
     * P28 tab Saldos -> boton PDF saldos.
     */
    suspend fun getPdfSaldos(anio: Int): Result<ResponseBody> =
        safeApiCall { api.getPdfSaldos(anio) }

    /**
     * E53 - PDF informe de vacaciones de un empleado.
     * P28 tab Saldos -> boton PDF vacaciones.
     */
    suspend fun getPdfVacaciones(empleadoId: Long, anio: Int): Result<ResponseBody> =
        safeApiCall { api.getPdfVacaciones(empleadoId, anio) }
}
