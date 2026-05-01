package ${Package}.feature.${FeaturePackage}.presentation

interface ${FeatureClass}${ArchitectureContractSuffix}View {
    fun render(title: String)
}

interface ${FeatureClass}${ArchitecturePresenterSuffix} {
    fun bind(view: ${FeatureClass}${ArchitectureContractSuffix}View)
}
