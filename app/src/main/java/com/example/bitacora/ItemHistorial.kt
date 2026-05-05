package com.example.bitacora

data class ItemHistorial(
    val id: String = "",
    val tipo: String = "", // "OT" o "MANT"
    val fecha: String = "",
    val resumen: String = "",
    val valor: String = ""
)