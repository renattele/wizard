package ${Package}.feature.${FeaturePackage}.presentation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun ${FeatureClass}Screen(
    state: ${FeatureClass}${ArchitectureStateSuffix},
    onEvent: (${FeatureClass}${ArchitectureEventSuffix}) -> Unit = {},
) {
    onEvent
    Text(text = state.title)
}
