package com.staffflow.android.data.remote.api

import com.staffflow.android.data.local.AuthEvent
import com.staffflow.android.data.local.AuthEventBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Modulo de red de StaffFlow.
 *
 * Proporciona el singleton de Retrofit configurado con:
 *   - AuthInterceptor: adjunta el token JWT en cada peticion autenticada
 *     y detecta respuestas HTTP 401 para notificar el cierre de sesion.
 *   - HttpLoggingInterceptor: registra cuerpo completo de requests y
 *     responses en logcat (nivel BODY, solo para depuracion).
 *
 * authToken se establece desde MainActivity tras leer el DataStore al
 * arrancar la app y desde LoginFragment tras un login exitoso (E01).
 * Se pone a null al cerrar sesion o procesar un 401.
 *
 * BASE_URL apunta al servidor backend de StaffFlow.
 * Para produccion configurar via BuildConfig.
 */
object NetworkModule {

    // EMULADOR Android Studio: usar 10.0.2.2 (alias del host desde el emulador)
    // TABLET REAL (red local): cambiar a la IP del PC en la red, ej: "http://192.168.1.15:8080/api/v1/"
    private const val DEFAULT_BASE_URL = "http://10.0.2.2:8080/api/v1/"

    /** IP extraida de la base URL actual, sin esquema ni puerto. Solo la parte de host. */
    var currentIp: String = "10.0.2.2"
        private set

    /**
     * Token JWT activo. Lo escribe MainActivity o LoginFragment.
     * El AuthInterceptor lo lee sincrónicamente desde el hilo de red.
     */
    @Volatile
    var authToken: String? = null

    // ------------------------------------------------------------------
    // Interceptores
    // ------------------------------------------------------------------

    /**
     * Interceptor de autenticacion.
     *
     * - Si authToken no es null, añade el header Authorization: Bearer {token}.
     * - Si la respuesta es HTTP 401, emite AuthEvent.SessionExpired al bus
     *   para que MainActivity limpie la sesion y navegue al login.
     */
    private val authInterceptor = Interceptor { chain ->
        val request = authToken?.let { token ->
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } ?: chain.request()

        val response = chain.proceed(request)

        // Solo disparar SessionExpired en rutas autenticadas.
        // /auth/login devuelve 401 por credenciales incorrectas — no es sesion caducada.
        if (response.code == 401 && !request.url.encodedPath.contains("/auth/login")) {
            AuthEventBus.post(AuthEvent.SessionExpired)
        }

        response
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    // ------------------------------------------------------------------
    // Cliente HTTP y Retrofit
    // ------------------------------------------------------------------

    private fun buildClient() = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile
    var retrofit: Retrofit = buildRetrofit(DEFAULT_BASE_URL)
        private set

    private fun buildRetrofit(baseUrl: String) = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(buildClient())
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    /**
     * Reconstruye el cliente Retrofit con una nueva URL base.
     * Llamar desde MainActivity al arrancar (si hay URL guardada en DataStore)
     * o desde LoginViewModel al guardar una nueva IP.
     *
     * @param baseUrl URL completa, ej: "http://192.168.1.107:8080/api/v1/"
     */
    fun init(baseUrl: String) {
        currentIp = baseUrl
            .removePrefix("http://")
            .substringBefore(":")
        retrofit = buildRetrofit(baseUrl)
    }

    // ------------------------------------------------------------------
    // Auto-deteccion de IP del backend (sondeo /api/health)
    // ------------------------------------------------------------------

    /**
     * Cliente HTTP dedicado al sondeo de descubrimiento de IP.
     * Timeout corto (1500 ms) para no colgar la UX si el host no responde.
     * NO compartir con el cliente principal (que tiene 30 s justificados
     * para latencias de red real).
     */
    private val probeClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(1500, TimeUnit.MILLISECONDS)
            .readTimeout(1500, TimeUnit.MILLISECONDS)
            .writeTimeout(1500, TimeUnit.MILLISECONDS)
            .build()
    }

    /**
     * Sondea GET http://{host}:8080/api/health para descubrir si el backend
     * esta accesible en ese host. Devuelve true si la respuesta es 2xx.
     *
     * El endpoint /api/health (E56) es publico (sin auth), por lo que no
     * requiere token JWT. Pensado para ejecutarse en el primer arranque
     * de la app cuando no hay BASE_URL guardada en DataStore.
     *
     * @param host host a sondear sin esquema ni puerto (ej: "10.0.2.2", "127.0.0.1")
     * @return true si /api/health responde 2xx, false si falla, da timeout o devuelve error
     */
    suspend fun probeHealth(host: String): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("http://$host:8080/api/health")
            .get()
            .build()
        runCatching {
            probeClient.newCall(request).execute().use { it.isSuccessful }
        }.getOrElse { false }
    }
}
