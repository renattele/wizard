package ${Package}.core.common

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

private val Context.userPreferencesDataStore by preferencesDataStore(name = "user_preferences")

object UserPreferencesStore {
    val themeKey = stringPreferencesKey("theme")

    fun storeName(context: Context): String = context.userPreferencesDataStore.toString()
}
