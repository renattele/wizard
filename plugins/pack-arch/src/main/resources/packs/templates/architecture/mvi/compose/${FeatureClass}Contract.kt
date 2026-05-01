package ${Package}.feature.${FeaturePackage}.presentation

data class ${FeatureClass}${ArchitectureStateSuffix}(
    val title: String = "${FeatureClass}",
)

sealed interface ${FeatureClass}${ArchitectureIntentSuffix}

sealed interface ${FeatureClass}${ArchitectureEffectSuffix}
