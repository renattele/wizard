package ${Package}.feature.${FeaturePackage}.presentation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun ${FeatureClass}Screen(
    state: ${FeatureClass}State,
    dispatch: (${FeatureClass}Intent) -> Unit = {},
) {
    dispatch
    Text(text = state.title)
}
