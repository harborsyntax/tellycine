package app.tellycine.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.tellycine.AppContainer
import app.tellycine.container

/** Creates a ViewModel with access to the [AppContainer] and its [SavedStateHandle]. */
@Composable
inline fun <reified VM : ViewModel> tellycineViewModel(
    key: String? = null,
    crossinline create: (AppContainer, SavedStateHandle) -> VM,
): VM {
    val container = LocalContext.current.container
    return viewModel(
        key = key,
        factory = viewModelFactory {
            initializer { create(container, createSavedStateHandle()) }
        },
    )
}
