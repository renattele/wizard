package ${Package}

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import ${Package}.feature.${StartFeaturePackage}.presentation.${StartFeatureClass}Fragment

class MainActivity : AppCompatActivity(R.layout.activity_main) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.content, ${StartFeatureClass}Fragment())
                .commit()
        }
    }
}
