package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.ui.BabbalApp
import com.example.ui.BabbalViewModel

class MainActivity : FragmentActivity() {

    private val viewModel: BabbalViewModel by viewModels {
        BabbalViewModel.Factory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> viewModel.onAppBackgrounded()
                    Lifecycle.Event.ON_START -> viewModel.onAppForegrounded()
                    else -> Unit
                }
            }
        )

        setContent {
            BabbalApp(viewModel = viewModel)
        }
    }
}
