package com.staffflow.android.data.remote.api

import com.staffflow.android.data.local.AuthEvent
import com.staffflow.android.data.local.AuthEventBus
import okhttp3.Interceptor
import okhttp3.OkHttpClient
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
 * BASE_URL apunta al emulador Android (10.0.2.2 = localhost del PC).
 * Para produccion configurar via BuildConfig.
 */
object NetworkModule {

    private const val BASE_URL = "http://10.0.2.2:8080/api/v1/"

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

        if (response.code == 401) {
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

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
}
