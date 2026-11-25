
package cl.inacap.riegointeligente.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "usuarios",
    indices = [Index(value = ["correo"], unique = true)]
)
data class Usuario(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val correo: String,

    val passwordHash: String,

    val salt: String,

    val creadoEn: Long = System.currentTimeMillis()
)
