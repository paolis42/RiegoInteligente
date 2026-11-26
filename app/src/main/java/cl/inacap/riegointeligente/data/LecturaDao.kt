package cl.inacap.riegointeligente.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LecturaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(lectura: Lectura)

    @Query("SELECT * FROM lecturas ORDER BY ts DESC LIMIT :limite")
    fun observarRecientes(limite: Int = 30): Flow<List<Lectura>>

    @Query("SELECT * FROM lecturas ORDER BY ts DESC LIMIT 1")
    suspend fun obtenerUltima(): Lectura?
}
