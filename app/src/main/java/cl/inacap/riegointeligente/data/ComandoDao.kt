package cl.inacap.riegointeligente.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ComandoDao {

    @Insert
    suspend fun insertar(comando: ComandoPendiente): Long

    @Query("SELECT * FROM comandos_pendientes WHERE sincronizada = 0 ORDER BY creadoEn ASC")
    suspend fun obtenerPendientes(): List<ComandoPendiente>

    @Query("UPDATE comandos_pendientes SET sincronizada = 1, respuesta = :respuesta WHERE id = :id")
    suspend fun marcarSincronizado(id: Long, respuesta: String)

    @Query("SELECT * FROM comandos_pendientes ORDER BY creadoEn DESC LIMIT :limite")
    fun observarHistorial(limite: Int = 20): Flow<List<ComandoPendiente>>

    @Query("SELECT COUNT(*) FROM comandos_pendientes WHERE sincronizada = 0")
    fun contarPendientes(): Flow<Int>
}
