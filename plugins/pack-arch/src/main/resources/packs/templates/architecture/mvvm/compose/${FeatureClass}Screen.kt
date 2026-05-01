package ${Package}.feature.${FeaturePackage}.presentation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun ${FeatureClass}Screen(
    state: ${FeatureClass}UiState,
    onEvent: (${FeatureClass}UiEvent) -> Unit = {},
) {
    onEvent
    Text(text = state.title)
}
