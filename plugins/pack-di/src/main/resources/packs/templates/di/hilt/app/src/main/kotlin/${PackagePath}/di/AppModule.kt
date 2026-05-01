package ${Package}.di

import ${Package}.core.common.DefaultDispatcherProvider
import ${Package}.core.common.DispatcherProvider
${FeatureRepositoryImports}
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDispatchers(): DispatcherProvider = DefaultDispatcherProvider

${FeatureHiltProviderMethods}
}
