package com.feanor08.onering.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.feanor08.onering.sample.theme.OneRingSampleTheme
import com.feanor08.onering.sample.ui.ShowcaseApp

/** Hosts the [ShowcaseApp] -- a browsable catalog of one-ring capability demos. */
public class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OneRingSampleTheme {
                ShowcaseApp()
            }
        }
    }
}
