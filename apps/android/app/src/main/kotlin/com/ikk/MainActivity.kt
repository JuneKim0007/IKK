package com.ikk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.ikk.ui.generated.HomeLayoutGenerated

/**
 * Renders the layout generated from the current contract.
 *
 * `HomeLayoutGenerated` is written by `packages/codegen` and refreshed by
 * `make android-sync` — never edited here. See
 * docs/architecture/frontend-android.md §5.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HomeLayoutGenerated(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}
