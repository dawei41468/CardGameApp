package com.example.cardgameapp

import androidx.compose.runtime.Composable
import com.example.cardgameapp.ui.screens.GameRoomScreen
import com.example.cardgameapp.ui.screens.LobbyScreen
import com.example.cardgameapp.ui.screens.RoomCreationScreen
import com.example.cardgameapp.ui.screens.RoomJoinScreen
import com.example.cardgameapp.ui.screens.SplashScreen

@Composable
fun CardGameApp(viewModel: GameViewModel, activity: MainActivity) {
    val gameState = viewModel.gameState
    when (gameState.value.screen) {
        "splash" -> SplashScreen { viewModel.navigateToHome() }
        "home" -> LobbyScreen(viewModel::navigateToCreate, viewModel::navigateToJoin)
        "create" -> RoomCreationScreen(viewModel)
        "join" -> RoomJoinScreen(viewModel)
        "room" -> GameRoomScreen(viewModel, activity)
    }
}