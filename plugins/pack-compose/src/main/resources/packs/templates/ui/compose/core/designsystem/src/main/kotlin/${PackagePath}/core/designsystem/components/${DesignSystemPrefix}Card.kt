package ${Package}.core.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable

@Composable
fun ${DesignSystemPrefix}Card(content: @Composable () -> Unit) {
    Card {
        Column {
            content()
        }
    }
}
