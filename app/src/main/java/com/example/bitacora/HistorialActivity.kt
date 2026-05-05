package com.example.bitacora

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.FirebaseDatabase


class HistorialActivity : AppCompatActivity() {
    private lateinit var rvHistorial: RecyclerView
    private lateinit var adapter: HistorialAdapter
    private var listaCompleta = mutableListOf<ItemHistorial>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
        setContentView(R.layout.activity_historial)

        val btnTodos = findViewById<Button>(R.id.btnFiltroTodos)
        val btnOT = findViewById<Button>(R.id.btnFiltroOT)
        val btnMant = findViewById<Button>(R.id.btnFiltroMant)

        rvHistorial = findViewById(R.id.rvHistorial)
        rvHistorial.layoutManager = LinearLayoutManager(this)
        adapter = HistorialAdapter(listaCompleta)
        rvHistorial.adapter = adapter

        cargarDatosDesdeFirebase()

        // Filtro TODOS
        btnTodos.setOnClickListener {
            resaltarBoton(btnTodos, listOf(btnOT, btnMant))
            adapter.actualizarLista(listaCompleta)
        }

        // Filtro TRABAJOS (OT)
        btnOT.setOnClickListener {
            resaltarBoton(btnOT, listOf(btnTodos, btnMant))
            val filtrada = listaCompleta.filter { it.tipo == "OT" }
            adapter.actualizarLista(filtrada)
        }

        // Filtro TALLER (MANT)
        btnMant.setOnClickListener {
            resaltarBoton(btnMant, listOf(btnTodos, btnOT))
            val filtrada = listaCompleta.filter { it.tipo == "MANT" }
            adapter.actualizarLista(filtrada)
        }

        // queda el de "Todos" marcado por defecto al entrar
        resaltarBoton(btnTodos, listOf(btnOT, btnMant))
    }

    private fun resaltarBoton(seleccionado: Button, otros: List<Button>) {
        // seleccionado se pone AMARILLO con letras NEGRAS (Vibe Maquinaria)
        seleccionado.setBackgroundColor(android.graphics.Color.parseColor("#FFD600"))
        seleccionado.setTextColor(android.graphics.Color.BLACK)

        // GRIS OSCURO con letras BLANCAS
        otros.forEach {
            it.setBackgroundColor(android.graphics.Color.parseColor("#333333"))
            it.setTextColor(android.graphics.Color.WHITE)
        }
    }

    private fun cargarDatosDesdeFirebase() {
        val db = FirebaseDatabase.getInstance()

        // 1. Cargar OTs
        db.getReference("OrdenesDeTrabajo").get().addOnSuccessListener { snapshot ->
            for (ds in snapshot.children) {
                val ot = ds.getValue(OrdenTrabajo::class.java)
                val idLimpio = ds.key ?: "" // Este será el número "1", "2"...

                listaCompleta.add(ItemHistorial(
                    id = "OT-$idLimpio", // Lo que se muestra en la lista
                    tipo = "OT",
                    fecha = ot?.tiempos?.firstOrNull()?.fecha ?: "",
                    resumen = "Fundo: ${ot?.fundo ?: "S/N"}",
                    valor = "${ot?.hectareas ?: "0"} Has"
                ))
            }
            ordenarYMostrar()
        }

        // 2. Cargar Mantenciones
        db.getReference("Mantenciones").get().addOnSuccessListener { snapshot ->
            for (ds in snapshot.children) { // este for recorre cada uno de los hijos (ds) uno por uno (registro de mant)
                val idM = ds.key ?: ""
                val fecha = ds.child("fecha").value.toString()
                val horometro = ds.child("horometro").value.toString()

                listaCompleta.add(ItemHistorial(  // convierte datos sueltos de la bd en un objeto ItemHistorial que la lista (RecyclerView) entiende
                    id = "MANT-$idM",
                    tipo = "MANT",
                    fecha = fecha,
                    resumen = "Mantenimiento Sprayer",
                    valor = "$horometro hrs"
                ))
            }
            ordenarYMostrar()
        }
    }

    private fun ordenarYMostrar() {
        // Ordenar por fecha
        listaCompleta.sortByDescending { it.id }
        adapter.actualizarLista(listaCompleta)
    }
}