package com.deniskrr.exam.ui

import androidx.compose.Model

sealed class Screen {
    object MySection : Screen()
}

@Model
object Navigator {
    var currentScreen: Screen = Screen.MySection
}

fun navigateTo(destination: Screen) {
    Navigator.currentScreen = destination
}
