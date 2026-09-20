package com.ikk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.ikk.data.DefaultGreetingRepository
import com.ikk.ui.GreetingScreen

class MainActivity : ComponentActivity() {

    // TODO: replace with dependency injection once there is more than one
    // dependency to wire.
    private val repository = DefaultGreetingRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GreetingScreen(message = repository.greetingFor("World"))
                }
            }
        }
    }
}
