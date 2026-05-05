package com.example.bitacora

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import java.io.File
import java.io.FileOutputStream
import java.util.*

// --- MODELOS DE DATOS ---
// Representación de una Orden de Trabajo completa para Firebase
data class OrdenTrabajo(
    val numeroOrden: String? = null,
    val hectareas: String? = null,
    val trabajo: String? = null,
    val fundo: String? = null,
    val sector: String? = null,
    val ltAgua: String? = null,
    val tiempos: List<FilaTiempo>? = null, // Relación uno a muchos: Una orden, varios registros de tiempo
    val quimicos: List<FilaQuimico>? = null, // Relación uno a muchos: Una orden, varios químicos
    val observaciones: String? = null
)

// Modelos para las filas dinámicas de las tablas
data class FilaTiempo(val fecha: String? = null, val inicio: String? = null, val fin: String? = null)
data class FilaQuimico(val nombre: String? = null, val dosis: String? = null, val cantidad: String? = null, val ocupados: String? = null, val saldo: String? = null)

class OrdenTrabajoActivity : AppCompatActivity() {
    private lateinit var database: DatabaseReference
    private lateinit var tvNumOrden: TextView
    private lateinit var etHectareas: EditText
    private lateinit var etTrabajo: EditText
    private lateinit var etFundo: EditText
    private lateinit var etSector: EditText
    private lateinit var etLtAgua: EditText
    private lateinit var etObservaciones: EditText
    private lateinit var contenedorTiempos: LinearLayout
    private lateinit var contenedorQuimicos: LinearLayout
    private lateinit var btnGuardar: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
        enableEdgeToEdge()
        setContentView(R.layout.activity_orden_trabajo)

        // Configuración de bordes
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        database = FirebaseDatabase.getInstance().getReference("OrdenesDeTrabajo")

        // Vincular Vistas
        tvNumOrden = findViewById(R.id.numeroOrden)
        etHectareas = findViewById(R.id.etHectareas)
        etTrabajo = findViewById(R.id.etTrabajo)
        etFundo = findViewById(R.id.etFundo)
        etSector = findViewById(R.id.etSector)
        etLtAgua = findViewById(R.id.etLtAgua)
        etObservaciones = findViewById(R.id.etObservaciones)
        contenedorTiempos = findViewById(R.id.contenedorTiempos)
        contenedorQuimicos = findViewById(R.id.contenedorQuimicos)

        contenedorTiempos.removeAllViews()
        contenedorQuimicos.removeAllViews()

        val btnAgregarTiempo = findViewById<Button>(R.id.btnAgregarTiempo)
        val btnAgregarQuimico = findViewById<Button>(R.id.btnAgregarQuimico)
        btnGuardar = findViewById(R.id.btnGuardarOrden)

        // --- LÓGICA DE NAVEGACIÓN ---
        val idRecibido = intent.getStringExtra("ID_REGISTRO")
        val modoVisualizacion = intent.getBooleanExtra("MODO_VISUALIZACION", false)

        if (idRecibido != null) {
            // Si recibimos un ID, cargamos la data existente para visualizar o editar
            cargarDatosParaVisualizar(idRecibido)
            if (modoVisualizacion) {
                btnGuardar.text = "ACTUALIZAR Y COMPARTIR PDF" // Nuevo Texto
                btnGuardar.setBackgroundColor(Color.parseColor("#1A237E")) // Azul para editar
            }
        } else {
            // Si no hay ID, generamos automáticamente el siguiente número de orden
            obtenerUltimoNumeroYConfigurar()
        }

        // --- MANEJO DE TABLAS DINÁMICAS ---
        // Estos botones permiten al usuario añadir filas infinitas según lo requiera la faena
        btnAgregarTiempo.setOnClickListener { agregarFilaTiempo(null) }
        btnAgregarQuimico.setOnClickListener { agregarFilaQuimico(null) }

        btnGuardar.setOnClickListener {
            val idOrdenActual = if (idRecibido != null) idRecibido else tvNumOrden.text.toString().filter { it.isDigit() }.trim()

            if (validarCamposCriticos()) {
                guardarOrdenConID(idOrdenActual)
            }
        }
    }

    // Validación de negocio: Evita registros incompletos que son críticos para la auditoría agrícola
    private fun validarCamposCriticos(): Boolean {
        // Campos de texto obligatorios
        if (etFundo.text.trim().isEmpty()) { etFundo.error = "Campo obligatorio"; etFundo.requestFocus(); return false }
        if (etTrabajo.text.trim().isEmpty()) { etTrabajo.error = "Campo obligatorio"; etTrabajo.requestFocus(); return false }
        if (etHectareas.text.trim().isEmpty()) { etHectareas.error = "Campo obligatorio"; etHectareas.requestFocus(); return false }

        // Verificamos que las tablas no estén vacías (Debe haber al menos un registro de tiempo y un químico)
        if (contenedorTiempos.childCount == 0) {
            Toast.makeText(this, "Faltó agregar registro de tiempos", Toast.LENGTH_LONG).show()
            return false
        }
        if (contenedorQuimicos.childCount == 0) {
            Toast.makeText(this, "Faltó agregar control de químicos", Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }

    // --- INFLADO DINÁMICO DE VISTAS ---
    // Crea una nueva fila en la tabla de tiempos y configura los pickers de fecha/hora
    private fun agregarFilaTiempo(datos: FilaTiempo?) {
        val fila = LayoutInflater.from(this).inflate(R.layout.item_fila_tiempo, null)
        val etF = fila.findViewById<EditText>(R.id.etFecha)
        val etI = fila.findViewById<EditText>(R.id.etInicio)
        val etT = fila.findViewById<EditText>(R.id.etTermino)

        if (datos != null) {
            etF.setText(datos.fecha); etI.setText(datos.inicio); etT.setText(datos.fin)
        } else {
            mostrarCalendario(etF); mostrarReloj(etI); mostrarReloj(etT)
        }
        contenedorTiempos.addView(fila)
    }

    private fun agregarFilaQuimico(datos: FilaQuimico?) {
        val fila = LayoutInflater.from(this).inflate(R.layout.item_fila_quimico, null)
        if (datos != null) {
            fila.findViewById<EditText>(R.id.etQuimico).setText(datos.nombre)
            fila.findViewById<EditText>(R.id.etDosis).setText(datos.dosis)
            fila.findViewById<EditText>(R.id.etCantidad).setText(datos.cantidad)
            fila.findViewById<EditText>(R.id.etOcupados).setText(datos.ocupados)
            fila.findViewById<EditText>(R.id.etSaldo).setText(datos.saldo)
        }
        contenedorQuimicos.addView(fila)
    }

    // Recolecta toda la información de la UI, incluyendo las tablas dinámicas, y la envía a Firebase
    private fun guardarOrdenConID(nOrden: String) {
        val nOrden = tvNumOrden.text.toString().filter { it.isDigit() }.trim()
        if (nOrden.isEmpty()) { Toast.makeText(this, "Error de ID",
            Toast.LENGTH_SHORT).show();
            return
        }

        // Mapeo manual de las vistas dinámicas a objetos de Kotlin
        val listaTiempos = mutableListOf<FilaTiempo>()
        for (i in 0 until contenedorTiempos.childCount) {
            val f = contenedorTiempos.getChildAt(i)
            listaTiempos.add(FilaTiempo(
                f.findViewById<EditText>(R.id.etFecha).text.toString(),
                f.findViewById<EditText>(R.id.etInicio).text.toString(),
                f.findViewById<EditText>(R.id.etTermino).text.toString()
            ))
        }

        val listaQuimicos = mutableListOf<FilaQuimico>()
        for (i in 0 until contenedorQuimicos.childCount) {
            val f = contenedorQuimicos.getChildAt(i)
            listaQuimicos.add(FilaQuimico(
                f.findViewById<EditText>(R.id.etQuimico).text.toString(),
                f.findViewById<EditText>(R.id.etDosis).text.toString(),
                f.findViewById<EditText>(R.id.etCantidad).text.toString(),
                f.findViewById<EditText>(R.id.etOcupados).text.toString(),
                f.findViewById<EditText>(R.id.etSaldo).text.toString()
            ))
        }

        val orden = OrdenTrabajo(nOrden, etHectareas.text.toString(), etTrabajo.text.toString(),
            etFundo.text.toString(), etSector.text.toString(), etLtAgua.text.toString(),
            listaTiempos, listaQuimicos, etObservaciones.text.toString())

        database.child(nOrden).setValue(orden).addOnSuccessListener {
            Toast.makeText(this, "Orden $nOrden guardada con éxito", Toast.LENGTH_SHORT).show()
            generarPDF()
        }
    }

    private fun cargarDatosParaVisualizar(id: String) {
        database.child(id).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val orden = snapshot.getValue(OrdenTrabajo::class.java)
                orden?.let {
                    tvNumOrden.text = "N° Orden: ${it.numeroOrden}"

                    // Aseguramos que los campos NO estén bloqueados para poder editar
                    etFundo.isEnabled = true; etSector.isEnabled = true
                    etTrabajo.isEnabled = true; etHectareas.isEnabled = true
                    etLtAgua.isEnabled = true; etObservaciones.isEnabled = true

                    etFundo.setText(it.fundo)
                    etSector.setText(it.sector)
                    etTrabajo.setText(it.trabajo)
                    etHectareas.setText(it.hectareas)
                    etLtAgua.setText(it.ltAgua)
                    etObservaciones.setText(it.observaciones)

                    // Limpiamos antes de cargar filas viejas
                    contenedorTiempos.removeAllViews()
                    contenedorQuimicos.removeAllViews()

                    it.tiempos?.forEach { t -> agregarFilaTiempo(t) }
                    it.quimicos?.forEach { q -> agregarFilaQuimico(q) }
                }
            } else {
                Toast.makeText(this, "Registro $id no encontrado", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- GENERACIÓN DE PDF COMPLEJO ---
    // Dibuja un reporte profesional que incluye encabezados y tablas de datos dinámicos
    private fun generarPDF() {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint()
        val titlePaint = Paint().apply {
            textSize = 18f; isFakeBoldText = true; color = Color.rgb(25, 118, 210)
        }
        val headerPaint = Paint().apply { textSize = 12f; isFakeBoldText = true }

        var y = 50f
        // Dibujo de encabezados principales
        canvas.drawText("ORDEN DE TRABAJO", 200f, y, titlePaint)
        paint.textSize = 12f
        canvas.drawText(tvNumOrden.text.toString(), 430f, y, paint)
        y += 40f
        canvas.drawText("Fundo: ${etFundo.text} | Sector: ${etSector.text}", 40f, y, paint)
        y += 20f
        canvas.drawText("Trabajo: ${etTrabajo.text} | Has: ${etHectareas.text}", 40f, y, paint)
        y += 30f
        canvas.drawLine(40f, y, 550f, y, paint)
        y += 20f

        // Antes de dibujar el texto de "REGISTRO DE TIEMPOS"
        paint.color = Color.LTGRAY
        paint.alpha = 50 // Transparencia
        canvas.drawRect(40f, y - 15f, 550f, y + 5f, paint) // Un fondo gris clarito
        paint.alpha = 255
        paint.color = Color.BLACK
        canvas.drawText("REGISTRO DE TIEMPOS", 40f, y, headerPaint)

        // --- TABLA DE TIEMPOS ---
        canvas.drawText("REGISTRO DE TIEMPOS", 40f, y, headerPaint)

        // Dibujo de Tabla de Tiempos con formato de lista
        y += 20f
        for (i in 0 until contenedorTiempos.childCount) {
            val fila = contenedorTiempos.getChildAt(i)
            val fecha = fila.findViewById<EditText>(R.id.etFecha).text.toString()
            val inicio = fila.findViewById<EditText>(R.id.etInicio).text.toString()
            val fin = fila.findViewById<EditText>(R.id.etTermino).text.toString()
            // ...Extracción de datos de cada fila dinámica para el dibujo
            canvas.drawText("• $fecha | Inicio: $inicio | Término: $fin", 50f, y, paint)
            y += 18f
        }

        y += 20f
        canvas.drawLine(40f, y, 300f, y, paint)
        y += 20f

        // --- TABLA DE QUÍMICOS ---
        canvas.drawText("CONTROL DE QUÍMICOS", 40f, y, headerPaint)
        y += 20f
        for (i in 0 until contenedorQuimicos.childCount) {
            val fila = contenedorQuimicos.getChildAt(i)
            val q = fila.findViewById<EditText>(R.id.etQuimico).text.toString()
            val d = fila.findViewById<EditText>(R.id.etDosis).text.toString()
            val c = fila.findViewById<EditText>(R.id.etCantidad).text.toString()
            canvas.drawText("• $q ($d) | Cant: $c", 50f, y, paint)
            y += 18f
        }

        y += 30f
        if (etObservaciones.text.isNotEmpty()) {
            canvas.drawText("OBSERVACIONES:", 40f, y, headerPaint)
            y += 20f
            val lineas = etObservaciones.text.toString().chunked(80)
            for (linea in lineas) {
                canvas.drawText(linea, 50f, y, paint)
                y += 18f
            }
        }

        doc.finishPage(page)

        // --- NOMBRE DE ARCHIVO PREDETERMINADO ---
        val nOrden = tvNumOrden.text.toString().filter { it.isDigit() }
        val nombreArchivo = "OT_$nOrden.pdf" // Ejemplo: OT_3.pdf
        val file = File(getExternalFilesDir(null), nombreArchivo)

        try {
            doc.writeTo(FileOutputStream(file))
            doc.close()
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, nombreArchivo)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Compartir Orden"))
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun mostrarCalendario(et: EditText) {
        et.setOnClickListener {
            val c = Calendar.getInstance()
            DatePickerDialog(this, { _, y, m, d -> et.setText("$d/${m+1}/$y") },
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
        }
    }

    private fun mostrarReloj(et: EditText) {
        et.setOnClickListener {
            val c = Calendar.getInstance()
            TimePickerDialog(this, { _, h, m -> et.setText(String.format("%02d:%02d", h, m)) },
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
        }
    }

    private fun obtenerUltimoNumeroYConfigurar() {
        database.limitToLast(1).get().addOnSuccessListener { snapshot ->
            var proximo = 1
            if (snapshot.exists()) {
                for (child in snapshot.children) {
                    val num = child.child("numeroOrden").value.toString()
                    proximo = (num.toIntOrNull() ?: 0) + 1
                }
            }
            tvNumOrden.text = "N° Orden: $proximo"
        }
    }
}