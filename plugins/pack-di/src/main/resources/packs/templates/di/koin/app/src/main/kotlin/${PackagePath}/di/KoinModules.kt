package ${Package}.di

import ${Package}.core.common.DefaultDispatcherProvider
import ${Package}.core.common.DispatcherProvider
${FeatureRepositoryImports}
import org.koin.dsl.module

val appModules = listOf(
    module {
        single<DispatcherProvider> { DefaultDispatcherProvider }
${FeatureKoinBindings}
    },
)
