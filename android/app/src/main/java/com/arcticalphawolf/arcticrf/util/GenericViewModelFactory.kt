package com.arcticalphawolf.arcticrf.util

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/** Small helper so each feature ViewModel can take constructor args without a Dagger/Hilt setup. */
class GenericViewModelFactory<T : ViewModel>(private val creator: () -> T) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <U : ViewModel> create(modelClass: Class<U>): U = creator() as U
}
