package com.example.bitacora

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.graphics.Color

// Adaptador para gestionar la visualización de registros en el RecyclerView
class HistorialAdapter(private var lista: List<ItemHistorial>) :
    RecyclerView.Adapter<HistorialAdapter.ViewHolder>() {

    // Referencia a los componentes visuales de cada fila (item_historial)
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val indicador: View = view.findViewById(R.id.viewTipo)
        val id: TextView = view.findViewById(R.id.tvId)
        val detalle: TextView = view.findViewById(R.id.tvDetalle)
        val fecha: TextView = view.findViewById(R.id.tvFecha)
        val valor: TextView = view.findViewById(R.id.tvValor)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_historial, parent, false)
        return ViewHolder(view)
    }

    // Une los datos del objeto ItemHistorial con la vista (UI)
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = lista[position]
        holder.id.text = item.id
        holder.detalle.text = item.resumen
        holder.fecha.text = item.fecha
        holder.valor.text = item.valor

        // Abre el detalle según el tipo de registro
        holder.itemView.setOnClickListener {
            val context = holder.itemView.context
            val intent = if (item.tipo == "OT") {
                Intent(context, OrdenTrabajoActivity::class.java)
            } else {
                Intent(context, MantencionActivity::class.java)
            }

            // Usamos trim() para evitar espacios fantasmas que rompen la carga
            // Quitamos los prefijos para que Firebase pueda encontrar la clave pura
            val idParaCargar = item.id.replace("OT-", "").replace("MANT-", "").trim()

            intent.putExtra("ID_REGISTRO", idParaCargar)
            intent.putExtra("MODO_VISUALIZACION", true) // sin edición
            context.startActivity(intent)
        }

        // UNIFICACIÓN DE COLORES INDUSTRIALES
        if (item.tipo == "OT") {
            holder.indicador.setBackgroundColor(Color.parseColor("#FFD600")) // Amarillo
            holder.valor.setTextColor(Color.parseColor("#FFD600"))
            holder.id.setTextColor(Color.parseColor("#FFD600"))
        } else {
            holder.indicador.setBackgroundColor(Color.parseColor("#E0E0E0")) // Plata/Blanco
            holder.valor.setTextColor(Color.parseColor("#E0E0E0"))
            holder.id.setTextColor(Color.parseColor("#E0E0E0"))
        }
    }

    override fun getItemCount() = lista.size

    // Función para actualizar la lista cuando filtramos
    fun actualizarLista(nuevaLista: List<ItemHistorial>) {
        lista = nuevaLista
        notifyDataSetChanged()
    }
}