package ${Package}.di

import ${Package}.core.common.DefaultDispatcherProvider
import org.koin.dsl.module

val appModules = listOf(
    module {
        single { DefaultDispatcherProvider }
    },
)
