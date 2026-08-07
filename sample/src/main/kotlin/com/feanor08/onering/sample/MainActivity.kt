package com.feanor08.onering.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.feanor08.onering.sample.theme.OneRingSampleTheme

/**
 * Sample host activity. Renders a placeholder screen until the OneRing
 * composable (Phase 2 onward) is wired in as the library is built out.
 */
public class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OneRingSampleTheme {
                SampleApp()
            }
        }
    }
}

@Composable
private fun SampleApp() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Scaffold { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text("one-ring sample — ring UI lands as later phases build out the library")
            }
        }
    }
}
