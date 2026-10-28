package com.example.nexonote.ui.transfer

import androidx.lifecycle.ViewModelProvider

/** Factories shared by MainActivity and the navigation graph. */
data class TransferFactories(
    val export: ViewModelProvider.Factory,
    val importer: ViewModelProvider.Factory,
    val backup: ViewModelProvider.Factory,
    val restore: (String?) -> ViewModelProvider.Factory,
)
