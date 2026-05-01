package ${Package}.feature.${FeaturePackage}.presentation

interface ${FeatureClass}View {
    fun render(title: String)
}

interface ${FeatureClass}Presenter {
    fun bind(view: ${FeatureClass}View)
}
