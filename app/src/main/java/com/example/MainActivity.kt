package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.ShareMeApp
import com.example.ui.ShareMeViewModel
import com.example.ui.theme.ShareMeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appContainer = (application as ShareMeApplication).container

        setContent {
            ShareMeTheme {
                val shareMeViewModel: ShareMeViewModel = viewModel(
                    factory = ShareMeViewModel.factory(appContainer)
                )
                ShareMeApp(viewModel = shareMeViewModel)
            }
        }
    }
}
