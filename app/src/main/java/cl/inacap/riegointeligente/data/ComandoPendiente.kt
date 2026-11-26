package cl.inacap.riegointeligente.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "comandos_pendientes")
data class ComandoPendiente(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accion: String,
    val creadoEn: Long = System.currentTimeMillis(),
    val sincronizada: Boolean = false,
    val respuesta: String? = null
)
