package ${Package}.di

import ${Package}.core.common.DefaultDispatcherProvider
import ${Package}.core.common.DispatcherProvider
${FeatureRepositoryImports}
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
object AppModule {
    @Provides
    @Singleton
    fun provideDispatchers(): DispatcherProvider = DefaultDispatcherProvider

${FeatureDaggerProviderMethods}
}
