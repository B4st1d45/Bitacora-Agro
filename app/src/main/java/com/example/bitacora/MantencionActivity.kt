package com.example.bitacora

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

// Clase de datos para representar filas dinámicas de repuestos o insumos extras
data class FilaSimple(val tipo: String, val estado: String)

class MantencionActivity : AppCompatActivity() {
    private lateinit var database: DatabaseReference
    private lateinit var tvNumMantencion: TextView
    private var idActual: String? = null // Variable para manejar el ID puro sin texto

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
        enableEdgeToEdge()
        setContentView(R.layout.activity_mantencion)

        // Configuración inicial de la referencia a Firebase
        database = FirebaseDatabase.getInstance().getReference("Mantenciones")
        tvNumMantencion = findViewById(R.id.tvNumMantencion)
        val btnGuardar = findViewById<Button>(R.id.btnGuardarMantencion)
        val etFecha = findViewById<EditText>(R.id.etFechaMantencion)
        val btnAddAceite = findViewById<Button>(R.id.btnAgregarAceite)
        val contenedorAceite = findViewById<LinearLayout>(R.id.contenedorAceite)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // --- LÓGICA DE CARGA O NUEVO REGISTRO ---
        // Determinamos si venimos del Historial (Visualización) o si es un registro nuevo
        val idRecibido = intent.getStringExtra("ID_REGISTRO")
        val modoVisualizacion = intent.getBooleanExtra("MODO_VISUALIZACION", false)

        if (modoVisualizacion && idRecibido != null) {
            // Modo Lectura/Edición: Cargamos datos de un registro existente
            idActual = idRecibido.trim()
            tvNumMantencion.text = "N° Mantención: $idActual"
            cargarDatosExistentes(idActual!!)

            // Configurar botón para actualizar
            btnGuardar.text = "ACTUALIZAR Y COMPARTIR PDF"
            btnGuardar.setBackgroundColor(Color.parseColor("#0D47A1")) // Azul industrial
            btnGuardar.visibility = View.VISIBLE
        } else {
            // Modo Nuevo: Calculamos el siguiente número correlativo automáticamente
            obtenerCorrelativo()
            btnGuardar.text = "GUARDAR Y COMPARTIR PDF"
        }

        // --- LISTENERS ---
        btnGuardar.setOnClickListener {
            if (validarCamposMantencion()) {
                guardarEnFirebase()
            }
        }

        // Selector de fecha nativo para estandarizar el formato de entrada
        etFecha.setOnClickListener {
            val c = java.util.Calendar.getInstance()
            android.app.DatePickerDialog(this, { _, y, m, d ->
                etFecha.setText("$d/${m + 1}/$y")
            }, c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH)).show()
        }

        // Permite agregar dinámicamente campos de texto para repuestos adicionales
        btnAddAceite.setOnClickListener {
            val fila = LayoutInflater.from(this).inflate(R.layout.item_fila_simple, null)
            contenedorAceite.addView(fila)
        }
    }

    // Recupera la información de Firebase y puebla los formularios y CheckBoxes
    private fun cargarDatosExistentes(id: String) {
        database.child(id).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                findViewById<EditText>(R.id.etFechaMantencion).setText(snapshot.child("fecha").value.toString())
                findViewById<EditText>(R.id.etHorometro).setText(snapshot.child("horometro").value.toString())
                findViewById<EditText>(R.id.etObsMantencion).setText(snapshot.child("observaciones").value.toString())

                findViewById<CheckBox>(R.id.cbAceiteMotor).isChecked = snapshot.child("aceiteMotor").value as? Boolean ?: false
                findViewById<CheckBox>(R.id.cbAceiteHidraulico).isChecked = snapshot.child("aceiteHidraulico").value as? Boolean ?: false
                findViewById<CheckBox>(R.id.cbEngrase).isChecked = snapshot.child("engrase").value as? Boolean ?: false

                // Reconstrucción dinámica de la lista de repuestos extras
                val extras = snapshot.child("repuestosExtras").children
                val contenedor = findViewById<LinearLayout>(R.id.contenedorAceite)
                contenedor.removeAllViews()

                for (item in extras) {
                    val fila = LayoutInflater.from(this).inflate(R.layout.item_fila_simple, null)
                    val sp = fila.findViewById<Spinner>(R.id.spTipoMantencion)
                    val et = fila.findViewById<EditText>(R.id.etDetalleAceite)

                    et.setText(item.child("estado").value.toString())
                    val tipo = item.child("tipo").value.toString()

                    val adapter = sp.adapter
                    for (i in 0 until adapter.count) {
                        if (adapter.getItem(i).toString() == tipo) {
                            sp.setSelection(i)
                            break
                        }
                    }
                    contenedor.addView(fila)
                }
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Error al cargar datos", Toast.LENGTH_SHORT).show()
        }
    }

    // Consulta el último registro para generar un ID incremental (autoincrement manual)
    private fun obtenerCorrelativo() {
        database.limitToLast(1).get().addOnSuccessListener { snapshot ->
            var proximo = 1
            if (snapshot.exists()) {
                for (child in snapshot.children) {
                    val num = child.child("numeroMantencion").value.toString()
                    proximo = (num.toIntOrNull() ?: 0) + 1
                }
            }
            idActual = proximo.toString()
            tvNumMantencion.text = "N° Mantención: $idActual"
        }
    }

    // Organiza los datos en un HashMap y los sube a la nube
    private fun guardarEnFirebase() {
        // Usamos el ID limpio para evitar crear nodos duplicados
        val idFinal = idActual ?: tvNumMantencion.text.toString().filter { it.isDigit() }

        val fecha = findViewById<EditText>(R.id.etFechaMantencion).text.toString()
        val horometro = findViewById<EditText>(R.id.etHorometro).text.toString()
        val obs = findViewById<EditText>(R.id.etObsMantencion).text.toString()

        val aceiteMotor = findViewById<CheckBox>(R.id.cbAceiteMotor).isChecked
        val aceiteHidraulico = findViewById<CheckBox>(R.id.cbAceiteHidraulico).isChecked
        val engrase = findViewById<CheckBox>(R.id.cbEngrase).isChecked

        // Mapeo de la lista dinámica de repuestos extras
        val listaExtras = mutableListOf<FilaSimple>()
        val contenedor = findViewById<LinearLayout>(R.id.contenedorAceite)

        for (i in 0 until contenedor.childCount) {
            val fila = contenedor.getChildAt(i)
            val tipo = fila.findViewById<Spinner>(R.id.spTipoMantencion).selectedItem.toString()
            val detalle = fila.findViewById<EditText>(R.id.etDetalleAceite).text.toString()

            if (tipo != "Seleccione tipo..." && detalle.isNotEmpty()) {
                listaExtras.add(FilaSimple(tipo, detalle))
            }
        }

        val mantencion = HashMap<String, Any>()
        mantencion["numeroMantencion"] = idFinal
        mantencion["fecha"] = fecha
        mantencion["horometro"] = horometro
        mantencion["aceiteMotor"] = aceiteMotor
        mantencion["aceiteHidraulico"] = aceiteHidraulico
        mantencion["engrase"] = engrase
        mantencion["repuestosExtras"] = listaExtras
        mantencion["observaciones"] = obs

        database.child(idFinal).setValue(mantencion).addOnSuccessListener {
            Toast.makeText(this, "Registro Guardado en Firebase", Toast.LENGTH_SHORT).show()
            generarPdfMantencion() // Tras guardar con éxito, iniciamos la exportación
        }.addOnFailureListener {
            Toast.makeText(this, "Error al guardar: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun validarCamposMantencion(): Boolean {
        val etFecha = findViewById<EditText>(R.id.etFechaMantencion)
        val etHorometro = findViewById<EditText>(R.id.etHorometro)
        if (etFecha.text.trim().isEmpty()) { etFecha.error = "Obligatorio"; return false }
        if (etHorometro.text.trim().isEmpty()) { etHorometro.error = "Obligatorio"; return false }
        return true
    }

    // --- GENERACIÓN DE REPORTE PDF ---
    // Utiliza la API nativa de Android para dibujar un reporte técnico formal
    private fun generarPdfMantencion() {
        val doc = android.graphics.pdf.PdfDocument()
        val paint = android.graphics.Paint()
        val titlePaint = android.graphics.Paint()
        val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        var y = 50f
        val xInicio = 50f

        titlePaint.textSize = 20f
        titlePaint.isFakeBoldText = true
        titlePaint.color = android.graphics.Color.rgb(25, 118, 210)
        canvas.drawText("REPORTE TÉCNICO DE MANTENCIÓN", xInicio, y, titlePaint)

        paint.textSize = 12f
        canvas.drawText(tvNumMantencion.text.toString(), 430f, y, paint)
        y += 10f
        canvas.drawLine(xInicio, y, 545f, y, paint)
        y += 30f

        val fecha = findViewById<EditText>(R.id.etFechaMantencion).text.toString()
        val horometro = findViewById<EditText>(R.id.etHorometro).text.toString()
        canvas.drawText("FECHA: $fecha", xInicio, y, paint)
        canvas.drawText("HORÓMETRO: $horometro hrs", 300f, y, paint)
        y += 40f

        paint.isFakeBoldText = true
        canvas.drawText("TAREAS REALIZADAS:", xInicio, y, paint)
        y += 20f
        paint.isFakeBoldText = false

        val tareas = listOf(
            "Aceite/Filtro Motor" to findViewById<CheckBox>(R.id.cbAceiteMotor).isChecked,
            "Aceite/Filtro Hidráulico" to findViewById<CheckBox>(R.id.cbAceiteHidraulico).isChecked,
            "Engrase General" to findViewById<CheckBox>(R.id.cbEngrase).isChecked
        )

        for (t in tareas) {
            val status = if (t.second) "[X]" else "[  ]"
            canvas.drawText("$status ${t.first}", xInicio + 10, y, paint)
            y += 20f
        }

        y += 20f
        paint.isFakeBoldText = true
        canvas.drawText("OBSERVACIONES:", xInicio, y, paint)
        y += 20f
        paint.isFakeBoldText = false
        val obs = findViewById<EditText>(R.id.etObsMantencion).text.toString()
        obs.chunked(80).forEach {
            canvas.drawText(it, xInicio + 10, y, paint)
            y += 18f
        }

        doc.finishPage(page)

        // Guardado local y apertura del selector de compartir (WhatsApp, Email, etc.)
        val nombreArchivo = "MANT_${idActual}_${fecha.replace("/", "-")}.pdf"
        val file = java.io.File(getExternalFilesDir(null), nombreArchivo)

        try {
            doc.writeTo(java.io.FileOutputStream(file))
            doc.close()
            // FileProvider permite compartir el archivo de forma segura con apps externas
            val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Enviar Reporte"))
        } catch (e: Exception) {
            Toast.makeText(this, "Error PDF", Toast.LENGTH_SHORT).show()
        }
    }
}