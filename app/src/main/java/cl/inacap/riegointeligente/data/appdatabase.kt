
package cl.inacap.riegointeligente.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Usuario::class, Lectura::class, ComandoPendiente::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun usuarioDao(): UsuarioDao
    abstract fun lecturaDao(): LecturaDao
    abstract fun comandoDao(): ComandoDao

    companion object {

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun obtener(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instancia = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "riego_inteligente_db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()

                INSTANCE = instancia
                instancia
            }
        }
    }
}
