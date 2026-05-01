package ${Package}.feature.${FeaturePackage}.presentation

class ${FeatureClass}${ArchitecturePresenterSuffix}Impl : ${FeatureClass}${ArchitecturePresenterSuffix} {
    override fun bind(view: ${FeatureClass}${ArchitectureContractSuffix}View) {
        view.render("${FeatureClass}")
    }
}
