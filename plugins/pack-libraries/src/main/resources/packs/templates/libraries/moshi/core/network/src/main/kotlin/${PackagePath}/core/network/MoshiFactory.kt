package ${Package}.core.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object MoshiFactory {
    val instance: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
}
