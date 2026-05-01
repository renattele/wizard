package ${Package}.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class SampleEntity(
    @PrimaryKey val id: String,
    val title: String,
)
