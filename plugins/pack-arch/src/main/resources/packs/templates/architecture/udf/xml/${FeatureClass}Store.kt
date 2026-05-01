package ${Package}.feature.${FeaturePackage}.presentation

import androidx.lifecycle.ViewModel

class ${FeatureClass}${ArchitectureStoreSuffix} : ViewModel() {
    val state: ${FeatureClass}${ArchitectureStateSuffix} = ${FeatureClass}${ArchitectureStateSuffix}()

    fun dispatch(event: ${FeatureClass}${ArchitectureEventSuffix}) = event
}
