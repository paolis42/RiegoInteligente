package cl.inacap.riegointeligente.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lecturas")
data class Lectura(
    val valor: Float,
    val umbral: Float,
    val actuador: Boolean,
    @PrimaryKey val ts: Long,
    val sincronizada: Boolean = true
)
