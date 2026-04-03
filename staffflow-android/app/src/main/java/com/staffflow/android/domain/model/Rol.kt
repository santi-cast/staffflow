package com.staffflow.android.domain.model

/**
 * Roles de usuario del sistema StaffFlow.
 *
 * Determina las pantallas accesibles y el destino inicial tras el login:
 *   ADMIN     -> P13 EmpleadosFragment
 *   ENCARGADO -> P17 ParteDiarioFragment
 *   EMPLEADO  -> P09 MiSaldoFragment
 *
 * El rol se recibe en LoginResponse y se persiste en Preferences DataStore
 * para construir el Drawer dinamico en MainActivity.
 */
enum class Rol {
    ADMIN, ENCARGADO, EMPLEADO
}
