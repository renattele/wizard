package ${Package}.navigation

data class FeatureDestination(
    val route: String,
    val title: String,
)

val destinations = listOf(
${FeatureXmlDestinations}
)
