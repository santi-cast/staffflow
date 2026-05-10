package com.staffflow.android

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupActionBarWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.staffflow.android.data.local.AuthEvent
import com.staffflow.android.data.local.AuthEventBus
import com.staffflow.android.data.local.SessionManager
import com.staffflow.android.data.remote.api.NetworkModule
import com.staffflow.android.databinding.ActivityMainBinding
import com.staffflow.android.domain.model.Rol
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Unica Activity de la app (Single Activity + Navigation Component).
 *
 * Responsabilidades:
 *   1. Configurar Toolbar, NavController, DrawerLayout y NavigationUI.
 *   2. Mostrar u ocultar los grupos del Drawer segun el rol del usuario.
 *   3. Bloquear el Drawer en la zona publica (Terminal, Login, Recovery).
 *   4. Gestionar la sesion expirada (HTTP 401): limpiar DataStore,
 *      ocultar Drawer, navegar a LoginFragment y mostrar Snackbar.
 *   5. Al arrancar, si hay JWT valido en DataStore, navegar directamente
 *      al destino inicial por rol sin pasar por el login (Decision 24-C).
 *
 * La navegacion post-login se delega a LoginFragment, que llama a
 * navigateToInitialDestination(rol) tras un login exitoso (E01).
 *
 * Destinos iniciales por rol:
 *   ADMIN     -> parteDiarioFragment (P17)
 *   ENCARGADO -> parteDiarioFragment (P17)
 *   EMPLEADO  -> miHoyFragment       (P12)
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration

    private val sessionManager by lazy { SessionManager.getInstance(this) }
    private var nombreUsuario: String? = null

    private val fmtReloj = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy · HH:mm", Locale("es"))

    /**
     * Destinos de nivel superior: muestran el icono de hamburguesa en lugar
     * del boton Atras. Son los puntos de entrada de cada flujo.
     */
    private val topLevelDestinations = setOf(
        R.id.terminalFragment,
        R.id.loginFragment,
        // Empleado
        R.id.miHoyFragment,
        R.id.misFichajesFragment,
        R.id.misAusenciasFragment,
        R.id.miSaldoFragment,
        R.id.miPerfilFragment,
        // Encargado
        R.id.parteDiarioFragment,
        R.id.empleadosFragment,
        R.id.resumenSemanalFragment,
        R.id.ausenciasFragment,
        R.id.saldosGlobalesFragment,
        R.id.informesFragment,
        // Admin
        R.id.usuariosFragment,
        R.id.empresaFragment,
        // Ajustes (todos los roles)
        R.id.cambiarPasswordFragment
    )

    /**
     * Zona publica: el Drawer se bloquea y no se muestra icono de navegacion.
     * El usuario no debe poder abrir el menu lateral sin estar autenticado.
     */
    private val publicDestinations = setOf(
        R.id.terminalFragment,
        R.id.confirmacionFragment,
        R.id.tipoPausaFragment,
        R.id.loginFragment,
        R.id.recoveryFragment,
        R.id.resetPasswordFragment
    )

    /**
     * Pantallas kiosk: la Toolbar se oculta porque el layout ya tiene su propio titulo.
     * Son pantallas de zona publica sin navegacion estructural.
     */
    private val kioskDestinations = setOf(
        R.id.terminalFragment,
        R.id.confirmacionFragment,
        R.id.tipoPausaFragment
    )

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------

    override fun attachBaseContext(newBase: android.content.Context) {
        // Forzar locale español independientemente del idioma del dispositivo.
        // MaterialDatePicker, AlertDialog y cualquier recurso de sistema usan
        // este contexto para resolver cadenas de idioma.
        val locale = java.util.Locale("es", "ES")
        java.util.Locale.setDefault(locale)
        val config = newBase.resources.configuration
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false) // se activa por destino en setupDestinationListener

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        navController = navHostFragment.navController

        appBarConfiguration = AppBarConfiguration(topLevelDestinations, binding.drawerLayout)
        setupActionBarWithNavController(navController, appBarConfiguration)

        setupDrawerItemListener()
        setupDestinationListener()
        collectAuthEvents()
        checkExistingSession()
        iniciarReloj()
    }

    override fun onSupportNavigateUp(): Boolean =
        NavigationUI.navigateUp(navController, appBarConfiguration) || super.onSupportNavigateUp()

    // ------------------------------------------------------------------
    // Reloj del toolbar
    // ------------------------------------------------------------------

    private fun iniciarReloj() {
        lifecycleScope.launch {
            // Sincronizar al inicio del próximo minuto exacto
            val msHastaProximoMinuto = 60_000L - (System.currentTimeMillis() % 60_000L)
            binding.tvReloj.text = LocalDateTime.now().format(fmtReloj)
            delay(msHastaProximoMinuto)
            while (true) {
                binding.tvReloj.text = LocalDateTime.now().format(fmtReloj)
                delay(60_000L)
            }
        }
    }

    // ------------------------------------------------------------------
    // Configuracion del Drawer
    // ------------------------------------------------------------------

    /**
     * Actualiza la cabecera del Drawer y la visibilidad de los grupos
     * segun el rol del usuario autenticado.
     * Llamar tras el login y al arrancar con sesion valida.
     */
    fun refreshDrawerMenu() {
        lifecycleScope.launch {
            val rol      = sessionManager.getRol()
            val username = sessionManager.getUsername() ?: ""
            nombreUsuario = sessionManager.getNombre() ?: username
            binding.tvToolbarNombre.text = " · $nombreUsuario"
            binding.tvToolbarNombre.visibility = View.VISIBLE

            val header = binding.navigationView.getHeaderView(0)
            header.findViewById<TextView>(R.id.tvHeaderUsername).text = username
            header.findViewById<TextView>(R.id.tvHeaderRol).text = rol?.name ?: ""

            val menu = binding.navigationView.menu
            when (rol) {
                Rol.EMPLEADO -> {
                    menu.setGroupVisible(R.id.group_empleado,  true)
                    menu.setGroupVisible(R.id.group_encargado, false)
                    menu.setGroupVisible(R.id.group_admin,     false)
                    menu.setGroupVisible(R.id.group_ajustes,   true)
                }
                Rol.ENCARGADO -> {
                    menu.setGroupVisible(R.id.group_empleado,  true)
                    menu.setGroupVisible(R.id.group_encargado, true)
                    menu.setGroupVisible(R.id.group_admin,     false)
                    menu.setGroupVisible(R.id.group_ajustes,   true)
                }
                Rol.ADMIN -> {
                    menu.setGroupVisible(R.id.group_empleado,  false)
                    menu.setGroupVisible(R.id.group_encargado, true)
                    menu.setGroupVisible(R.id.group_admin,     true)
                    menu.setGroupVisible(R.id.group_ajustes,   true)
                }
                null -> hideDrawerMenu()
            }
        }
    }

    /** Oculta todos los grupos del Drawer. Llamar al cerrar sesion o en 401. */
    fun hideDrawerMenu() {
        val menu = binding.navigationView.menu
        menu.setGroupVisible(R.id.group_empleado,  false)
        menu.setGroupVisible(R.id.group_encargado, false)
        menu.setGroupVisible(R.id.group_admin,     false)
        menu.setGroupVisible(R.id.group_ajustes,   false)
    }

    // ------------------------------------------------------------------
    // Navegacion
    // ------------------------------------------------------------------

    /**
     * Navega al destino inicial segun el rol, limpiando el back stack
     * para que el boton Atras no vuelva al terminal ni al login.
     * Llamado desde LoginFragment tras un login exitoso (E01).
     */
    fun navigateToInitialDestination(rol: Rol) {
        val destination = when (rol) {
            Rol.ADMIN     -> R.id.parteDiarioFragment
            Rol.ENCARGADO -> R.id.parteDiarioFragment
            Rol.EMPLEADO  -> R.id.miHoyFragment
        }
        val navOptions = NavOptions.Builder()
            .setPopUpTo(R.id.terminalFragment, inclusive = true)
            .build()
        navController.navigate(destination, null, navOptions)
    }

    // ------------------------------------------------------------------
    // Listeners
    // ------------------------------------------------------------------

    private fun setupDestinationListener() {
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id in kioskDestinations) {
                supportActionBar?.hide()
            } else {
                supportActionBar?.show()
            }
            if (destination.id in publicDestinations) {
                binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                binding.toolbar.navigationIcon = null
                binding.tvToolbarNombre.visibility = View.GONE
                binding.tvReloj.visibility = View.GONE
                supportActionBar?.setDisplayShowTitleEnabled(false)
            } else {
                binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
                nombreUsuario?.let {
                    binding.tvToolbarNombre.text = " · $it"
                    binding.tvToolbarNombre.visibility = View.VISIBLE
                }
                binding.tvReloj.visibility = View.VISIBLE
                // Mostrar el label del destino (definido en nav_graph.xml) como titulo
                supportActionBar?.setDisplayShowTitleEnabled(true)
            }
        }
    }

    private fun setupDrawerItemListener() {
        binding.navigationView.setNavigationItemSelectedListener { item ->
            if (item.itemId == R.id.nav_cerrar_sesion) {
                showLogoutDialog()
                true
            } else {
                val handled = NavigationUI.onNavDestinationSelected(item, navController)
                if (handled) binding.drawerLayout.closeDrawer(GravityCompat.START)
                handled
            }
        }
    }

    // ------------------------------------------------------------------
    // Sesion
    // ------------------------------------------------------------------

    /**
     * Al arrancar, comprueba si hay token en DataStore.
     * Si existe, configura el Drawer y navega al destino inicial por rol
     * sin pasar por el login (Decision 24-C).
     *
     * Para la URL base del backend:
     *   1. Si hay BASE_URL en DataStore (configurada previamente) -> usarla directamente.
     *   2. Si NO hay BASE_URL guardada -> sondear /api/health en orden:
     *        a. http://10.0.2.2:8080  (emulador Android Studio)
     *        b. http://127.0.0.1:8080  (demo standalone: backend en la misma tablet)
     *      Si alguno responde 2xx, persistir esa baseUrl e inicializar NetworkModule.
     *      Si ninguno responde, no se hace nada: LoginViewModel detectara el fallo
     *      en el primer login y mostrara el dialogo manual de "configurar IP".
     *
     * Nota: el terminal (P01) es SIEMPRE la pantalla de inicio, independientemente
     * de si hay sesion activa. La sesion se carga en memoria para que el
     * AuthInterceptor la use y para que LoginFragment pueda saltar el formulario
     * si el token sigue siendo valido, pero no se navega automaticamente.
     */
    private fun checkExistingSession() {
        lifecycleScope.launch {
            val baseUrl = sessionManager.getBaseUrl()
            if (baseUrl != null) {
                NetworkModule.init(baseUrl)
            } else {
                autoDetectBackendBaseUrl()
            }
            val token = sessionManager.getToken()
            if (token != null) {
                NetworkModule.authToken = token
                refreshDrawerMenu()
            }
        }
    }

    /**
     * Sondeo de descubrimiento de IP en el primer arranque.
     *
     * Orden 10.0.2.2 -> 127.0.0.1 elegido conscientemente: el emulador es el
     * caso mas frecuente en desarrollo y evaluacion del TFG. Si falla, se prueba
     * el loopback (demo standalone con backend en Termux en la misma tablet).
     *
     * Tiempo peor caso: ~3 s (2 timeouts de 1.5 s). Solo ocurre una vez en la
     * vida del dispositivo: tras el primer sondeo exitoso la baseUrl queda
     * persistida en DataStore y los arranques posteriores la leen directamente.
     */
    private suspend fun autoDetectBackendBaseUrl() {
        val candidates = listOf("10.0.2.2", "127.0.0.1")
        for (host in candidates) {
            if (NetworkModule.probeHealth(host)) {
                val baseUrl = "http://$host:8080/api/v1/"
                sessionManager.saveBaseUrl(baseUrl)
                NetworkModule.init(baseUrl)
                return
            }
        }
        // Ningun host respondio: se mantiene la baseUrl por defecto del NetworkModule.
        // El primer login fallara y LoginViewModel mostrara el dialogo manual de IP.
    }

    /**
     * Gestiona un HTTP 401 recibido en cualquier endpoint autenticado.
     * Limpia DataStore, oculta el Drawer, navega al login y muestra Snackbar.
     */
    private fun handleSessionExpired() {
        lifecycleScope.launch {
            sessionManager.clear()
            NetworkModule.authToken = null
            hideDrawerMenu()
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            navController.navigate(
                R.id.loginFragment,
                null,
                NavOptions.Builder()
                    .setPopUpTo(0, inclusive = true)
                    .build()
            )
            Snackbar.make(binding.root, "La sesión ha caducado", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun collectAuthEvents() {
        lifecycleScope.launch {
            AuthEventBus.events.collect { event ->
                when (event) {
                    is AuthEvent.SessionExpired -> handleSessionExpired()
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Dialogo de cierre de sesion
    // ------------------------------------------------------------------

    /**
     * Confirmacion antes de cerrar sesion (Decision 26).
     * El boton destructivo es setNegativeButton, que en Material Design 3
     * aparece en rojo para indicar accion destructiva.
     */
    private fun showLogoutDialog() {
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        MaterialAlertDialogBuilder(this)
            .setTitle("Cerrar sesión")
            .setMessage("¿Seguro que quieres cerrar sesión?")
            .setPositiveButton("Cancelar", null)
            .setNegativeButton("Cerrar sesión") { _, _ ->
                lifecycleScope.launch {
                    sessionManager.clear()
                    NetworkModule.authToken = null
                    hideDrawerMenu()
                    navController.navigate(
                        R.id.terminalFragment,
                        null,
                        NavOptions.Builder()
                            .setPopUpTo(0, inclusive = true)
                            .build()
                    )
                }
            }
            .show()
    }
}
