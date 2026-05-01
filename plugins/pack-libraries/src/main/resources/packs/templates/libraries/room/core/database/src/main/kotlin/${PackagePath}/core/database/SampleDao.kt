package ${Package}.core.database

import androidx.room.Dao
import androidx.room.Query

@Dao
interface SampleDao {
    @Query("SELECT * FROM sampleentity")
    suspend fun all(): List<SampleEntity>
}
