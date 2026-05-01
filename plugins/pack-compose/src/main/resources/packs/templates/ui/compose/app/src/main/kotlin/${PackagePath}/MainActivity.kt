package ${Package}

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import ${Package}.core.designsystem.theme.${DesignSystemPrefix}Theme
import ${Package}.navigation.AppNavigation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ${DesignSystemPrefix}Theme {
                AppNavigation()
            }
        }
    }
}
