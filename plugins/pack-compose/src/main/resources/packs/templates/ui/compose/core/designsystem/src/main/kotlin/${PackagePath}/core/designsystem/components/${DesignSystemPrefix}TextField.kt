package ${Package}.core.designsystem.components

import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable

@Composable
fun ${DesignSystemPrefix}TextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { androidx.compose.material3.Text(text = label) },
    )
}
