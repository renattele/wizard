package ${Package}.feature.${FeaturePackage}.presentation

data class ${FeatureClass}${ArchitectureStateSuffix}(
    val title: String = "${FeatureClass}",
)

sealed interface ${FeatureClass}${ArchitectureEventSuffix}

sealed interface ${FeatureClass}${ArchitectureEffectSuffix}
