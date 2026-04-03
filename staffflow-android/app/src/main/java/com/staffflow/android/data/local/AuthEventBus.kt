package com.staffflow.android.data.local

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Eventos de autenticacion emitidos desde fuera del ciclo de vida de la UI.
 */
sealed class AuthEvent {
    /** El servidor devolvio HTTP 401: el token ha expirado o es invalido. */
    object SessionExpired : AuthEvent()
}

/**
 * Bus de eventos de autenticacion para comunicar el AuthInterceptor con MainActivity.
 *
 * El AuthInterceptor (OkHttp) corre en un hilo de red. Cuando detecta un HTTP 401
 * en un endpoint autenticado, llama a AuthEventBus.post(AuthEvent.SessionExpired).
 * MainActivity colecciona el flow y ejecuta el flujo de cierre de sesion:
 *   DataStore.clear() -> hideDrawer() -> navigate(loginFragment) -> Snackbar.
 *
 * Se usa MutableSharedFlow con extraBufferCapacity=1 para que el interceptor
 * pueda emitir sin suspender (tryEmit) aunque MainActivity no este escuchando
 * en ese instante exacto.
 */
object AuthEventBus {

    private val _events = MutableSharedFlow<AuthEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<AuthEvent> = _events.asSharedFlow()

    /** Emite un evento sin suspender. Seguro de llamar desde un hilo de red. */
    fun post(event: AuthEvent) {
        _events.tryEmit(event)
    }
}
