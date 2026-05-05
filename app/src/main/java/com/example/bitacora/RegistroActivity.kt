package com.example.bitacora

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

// Clase encargada de consolidar y visualizar el historial mensual de trabajos
class RegistroActivity : AppCompatActivity() {
    private lateinit var database: DatabaseReference
    private lateinit var tablaResumen: TableLayout
    private lateinit var tvMesSeleccionado: TextView
    private var mesActual = 0
    private var anioActual = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
        enableEdgeToEdge()
        setContentView(R.layout.activity_registro)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        database = FirebaseDatabase.getInstance().reference
        tablaResumen = findViewById(R.id.tablaResumen)
        tvMesSeleccionado = findViewById(R.id.tvMesSeleccionado)
        var btnGenerarReporteMensual = findViewById<Button>(R.id.btnGenerarReporteMensual)

        // Configurar fecha actual por defecto
        val calendar = java.util.Calendar.getInstance()
        mesActual = calendar.get(java.util.Calendar.MONTH)
        anioActual = calendar.get(java.util.Calendar.YEAR)
        actualizarEtiquetaMes()

        // Listener para filtrar datos por mes/año mediante un DatePickerDialog personalizado
        findViewById<Button>(R.id.btnSeleccionarMes).setOnClickListener {
            mostrarSelectorMes()
        }

        btnGenerarReporteMensual.setOnClickListener { generarPdfMensual() }

        cargarDatosMensuales()
    }

    private fun actualizarEtiquetaMes() {
        val meses = arrayOf("Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
            "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre")
        tvMesSeleccionado.text = "${meses[mesActual]} $anioActual"
    }

    private fun mostrarSelectorMes() {
        // Un DatePickerDialog sencillo que solo use mes y año
        val dpd = android.app.DatePickerDialog(this, { _, year, month, _ ->
            mesActual = month
            anioActual = year
            actualizarEtiquetaMes()
            cargarDatosMensuales() // Recargar al cambiar
        }, anioActual, mesActual, 1)
        dpd.show()
    }

    // Lógica de filtrado: Determina si un registro de Firebase pertenece al periodo seleccionado
    private fun perteneceAlMes(fecha: String): Boolean {
        if (fecha.isEmpty() || !fecha.contains("/")) return false

        return try {
            val partes = fecha.split("/")
            if (partes.size >= 3) {
                val m = partes[1].toInt() - 1  // Ajuste de base 0 para meses en Kotlin
                val a = partes[2].toInt()
                m == mesActual && a == anioActual
            } else false
        } catch (e: Exception) {
            false // Si la fecha está mal escrita, simplemente la ignora y no se cae
        }
    }

    // Generación dinámica de UI: Crea filas en el TableLayout con estilos diferenciados
    private fun agregarFilaATabla(id: String, fecha: String, fundo: String, trabajo: String, valor: String, esMantencion: Boolean) {
        val fila = TableRow(this)
        fila.setPadding(0, 10, 0, 10)

        val lpID = TableRow.LayoutParams(0, TableLayout.LayoutParams.WRAP_CONTENT, 0.4f)
        val lpFecha = TableRow.LayoutParams(0, TableLayout.LayoutParams.WRAP_CONTENT, 0.8f)
        val lpFundo = TableRow.LayoutParams(0, TableLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        val lpTrabajo = TableRow.LayoutParams(0, TableLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        val lpValor = TableRow.LayoutParams(0, TableLayout.LayoutParams.WRAP_CONTENT, 0.6f)

        // Estilización visual para diferenciar mantenimientos de órdenes de trabajo
        if (esMantencion) {
            fila.setBackgroundColor(Color.parseColor("#01052e"))
        }
        val colorTexto = if (esMantencion) Color.parseColor("#95a1a6") else Color.WHITE

        fun crearTextView(texto: String, params: TableRow.LayoutParams, gravity: Int = android.view.Gravity.START): TextView {
            return TextView(this).apply {
                text = texto
                layoutParams = params
                textSize = 11f
                setTextColor(colorTexto) // Aplicamos el color aquí
                this.gravity = gravity
                if (esMantencion) setTypeface(null, android.graphics.Typeface.BOLD)
            }
        }

        fila.addView(crearTextView(id, lpID))
        fila.addView(crearTextView(fecha, lpFecha))
        fila.addView(crearTextView(fundo, lpFundo))
        fila.addView(crearTextView(trabajo, lpTrabajo))
        fila.addView(crearTextView(valor, lpValor, android.view.Gravity.END))

        tablaResumen.addView(fila)
    }

    // --- CARGA MULTIFUENTE ---
    // Consulta dos nodos distintos en Firebase y unifica los resultados en una sola vista
    private fun cargarDatosMensuales() {
        // Limpiar tabla (excepto el encabezado que es la fila 0)
        val childCount = tablaResumen.childCount
        if (childCount > 1) {
            tablaResumen.removeViews(1, childCount - 1)
        }

        // 1. Carga de Órdenes de Trabajo
        database.child("OrdenesDeTrabajo").get().addOnSuccessListener { snapshot ->
            for (ds in snapshot.children) {
                val orden = ds.getValue(OrdenTrabajo::class.java)
                val fecha = orden?.tiempos?.firstOrNull()?.fecha ?: ""
                if (perteneceAlMes(fecha)) {
                    agregarFilaATabla(
                        id = "OT-${orden?.numeroOrden}",
                        fecha = fecha,
                        fundo = orden?.fundo ?: "",
                        trabajo = orden?.trabajo ?: "",
                        valor = orden?.hectareas ?: "0",
                        esMantencion = false)
                }
            }
        }

        // 2. Carga de Mantenciones (Consolidación de datos)
        database.child("Mantenciones").get().addOnSuccessListener { snapshot ->
            for (ds in snapshot.children) {
                val fecha = ds.child("fecha").value.toString()
                if (perteneceAlMes(fecha)) {
                    val numM = ds.child("numeroMantencion").value.toString()
                    val horometro = ds.child("horometro").value.toString()

                    agregarFilaATabla(
                        id = "M-$numM",
                        fecha = fecha,
                        fundo = "TALLER",
                        trabajo = "MANTENCIÓN",
                        valor = "$horometro hrs",
                        esMantencion = true)
                }
            }
        }
    }

    // --- REPORTE EJECUTIVO PDF ---
    // Genera un documento consolidado calculando totales acumulados en tiempo real
    private fun generarPdfMensual() {
        val doc = android.graphics.pdf.PdfDocument()
        val paint = android.graphics.Paint()
        val titlePaint = android.graphics.Paint()

        val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        var y = 50f
        val xInicio = 40f

        // --- ENCABEZADO ---
        titlePaint.textSize = 18f
        titlePaint.isFakeBoldText = true
        titlePaint.color = android.graphics.Color.rgb(25, 118, 210)
        canvas.drawText("RESUMEN MENSUAL DE TRABAJOS", xInicio, y, titlePaint)

        paint.textSize = 11f
        paint.color = android.graphics.Color.BLACK
        canvas.drawText("Período: ${tvMesSeleccionado.text}", 400f, y, paint)

        y += 10f
        canvas.drawLine(xInicio, y, 545f, y, paint)
        y += 25f

        // --- ENCABEZADOS DE TABLA ---
        paint.isFakeBoldText = true
        canvas.drawText("Folio", xInicio, y, paint)
        canvas.drawText("Fecha", xInicio + 40, y, paint)
        canvas.drawText("Fundo / Sector", xInicio + 110, y, paint)
        canvas.drawText("Trabajo", xInicio + 260, y, paint)
        canvas.drawText("Has/Hrs", xInicio + 460, y, paint)

        y += 10f
        canvas.drawLine(xInicio, y, 555f, y, paint)
        y += 20f
        paint.isFakeBoldText = false

        // --- CONTENIDO DE LA TABLA ---
        var totalHas = 0.0 // Acumulador para el cálculo de productividad mensual

        // Itera sobre los elementos de la tabla visual para construir el documento físico
        for (i in 1 until tablaResumen.childCount) {
            val fila = tablaResumen.getChildAt(i) as TableRow

            val folio = (fila.getChildAt(0) as TextView).text.toString()
            val fecha   = (fila.getChildAt(1) as TextView).text.toString()
            val fundo   = (fila.getChildAt(2) as TextView).text.toString()
            val trabajo = (fila.getChildAt(3) as TextView).text.toString()
            val valorStr = (fila.getChildAt(4) as TextView).text.toString()

            // Lógica de cálculo: Solo sumamos hectáreas (OT), no horómetros (Mantenciones)
            if (folio.startsWith("MANT")) {
                paint.color = android.graphics.Color.GRAY
                paint.textSkewX = -0.25f // Inclinación para cursiva
            } else {
                paint.color = android.graphics.Color.BLACK
                paint.textSkewX = 0f    // Volver a normal (IMPORTANTE)
                totalHas += valorStr.replace(",", ".").toDoubleOrNull() ?: 0.0
            }

            canvas.drawText(folio, xInicio, y, paint)
            canvas.drawText(fecha, xInicio + 50, y, paint)
            canvas.drawText(fundo, xInicio + 120, y, paint)
            canvas.drawText(trabajo, xInicio + 270, y, paint)
            canvas.drawText(valorStr, xInicio + 460, y, paint)

            y += 20f

            if (y > 780) break
        }

        paint.textSkewX = 0f
        paint.color = android.graphics.Color.BLACK

        y += 10f
        canvas.drawLine(xInicio, y, 555f, y, paint)
        y += 25f

        paint.isFakeBoldText = true
        paint.textSize = 13f

        // Pie de página con el resumen de métricas clave (Total Hectáreas)
        canvas.drawText("TOTAL HECTÁREAS TRABAJADAS: ", xInicio + 200, y, paint)
        canvas.drawText(String.format("%.2f Has.", totalHas), xInicio + 440, y, paint)

        doc.finishPage(page)

        // --- GUARDAR Y COMPARTIR ---
        val nombreArchivo = "Resumen_${tvMesSeleccionado.text.toString().replace(" ", "_")}.pdf"
        val file = java.io.File(getExternalFilesDir(null), nombreArchivo)

        try {
            doc.writeTo(java.io.FileOutputStream(file))
            doc.close()
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this, "${applicationContext.packageName}.provider", file)
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(android.content.Intent.createChooser(intent, "Compartir Resumen Mensual"))
        } catch (e: Exception) { e.printStackTrace()}
    }
}