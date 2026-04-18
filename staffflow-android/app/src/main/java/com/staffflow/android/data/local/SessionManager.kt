package com.staffflow.android.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.staffflow.android.domain.model.Rol
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Extension para crear el DataStore una sola vez por proceso. */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

/**
 * Gestor de sesion persistente basado en Preferences DataStore.
 *
 * Almacena token JWT, rol, username y empleadoId tras el login (E01).
 * Los datos se leen al arrancar la app para:
 *   - Navegar directamente al destino inicial por rol (Decision 24-C).
 *   - Configurar los grupos visibles del Drawer en MainActivity.
 *   - Adjuntar el token en el header Authorization de cada llamada
 *     autenticada (NetworkModule.authToken).
 *
 * Al cerrar sesion o detectar un 401, clear() borra todos los datos
 * y MainActivity navega al LoginFragment.
 *
 * Singleton: se instancia una sola vez via getInstance(context).
 */
class SessionManager private constructor(context: Context) {

    private val dataStore = context.applicationContext.dataStore

    private object Keys {
        val TOKEN       = stringPreferencesKey("token")
        val ROL         = stringPreferencesKey("rol")
        val USERNAME    = stringPreferencesKey("username")
        val EMPLEADO_ID = longPreferencesKey("empleado_id")
        val NOMBRE      = stringPreferencesKey("nombre")
    }

    // ------------------------------------------------------------------
    // Escritura
    // ------------------------------------------------------------------

    suspend fun saveSession(token: String, rol: Rol, username: String, empleadoId: Long?, nombre: String?) {
        dataStore.edit { prefs ->
            prefs[Keys.TOKEN]    = token
            prefs[Keys.ROL]      = rol.name
            prefs[Keys.USERNAME] = username
            if (empleadoId != null) prefs[Keys.EMPLEADO_ID] = empleadoId
            if (nombre != null) prefs[Keys.NOMBRE] = nombre
        }
    }

    /** Elimina todos los datos de sesion. Llamar al cerrar sesion o en 401. */
    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    // ------------------------------------------------------------------
    // Lectura
    // ------------------------------------------------------------------

    suspend fun getToken(): String? =
        dataStore.data.map { it[Keys.TOKEN] }.first()

    suspend fun getRol(): Rol? =
        dataStore.data.map { prefs ->
            prefs[Keys.ROL]?.let { runCatching { Rol.valueOf(it) }.getOrNull() }
        }.first()

    suspend fun getUsername(): String? =
        dataStore.data.map { it[Keys.USERNAME] }.first()

    suspend fun getEmpleadoId(): Long? =
        dataStore.data.map { it[Keys.EMPLEADO_ID] }.first()

    suspend fun getNombre(): String? =
        dataStore.data.map { it[Keys.NOMBRE] }.first()

    // ------------------------------------------------------------------
    // Singleton
    // ------------------------------------------------------------------

    companion object {
        @Volatile private var instance: SessionManager? = null

        fun getInstance(context: Context): SessionManager =
            instance ?: synchronized(this) {
                instance ?: SessionManager(context).also { instance = it }
            }
    }
}
