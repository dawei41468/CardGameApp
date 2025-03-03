package com.example.cardgameapp

import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {
    private lateinit var database: DatabaseReference
    private val cardResourceMap = mapOf(
        "Spades_Ace" to R.drawable.ace_of_spades, "Spades_2" to R.drawable.two_of_spades,
        "Spades_3" to R.drawable.three_of_spades, "Spades_4" to R.drawable.four_of_spades,
        "Spades_5" to R.drawable.five_of_spades, "Spades_6" to R.drawable.six_of_spades,
        "Spades_7" to R.drawable.seven_of_spades, "Spades_8" to R.drawable.eight_of_spades,
        "Spades_9" to R.drawable.nine_of_spades, "Spades_10" to R.drawable.ten_of_spades,
        "Spades_Jack" to R.drawable.jack_of_spades, "Spades_Queen" to R.drawable.queen_of_spades,
        "Spades_King" to R.drawable.king_of_spades,
        "Hearts_Ace" to R.drawable.ace_of_hearts, "Hearts_2" to R.drawable.two_of_hearts,
        "Hearts_3" to R.drawable.three_of_hearts, "Hearts_4" to R.drawable.four_of_hearts,
        "Hearts_5" to R.drawable.five_of_hearts, "Hearts_6" to R.drawable.six_of_hearts,
        "Hearts_7" to R.drawable.seven_of_hearts, "Hearts_8" to R.drawable.eight_of_hearts,
        "Hearts_9" to R.drawable.nine_of_hearts, "Hearts_10" to R.drawable.ten_of_hearts,
        "Hearts_Jack" to R.drawable.jack_of_hearts, "Hearts_Queen" to R.drawable.queen_of_hearts,
        "Hearts_King" to R.drawable.king_of_hearts,
        "Clubs_Ace" to R.drawable.ace_of_clubs, "Clubs_2" to R.drawable.two_of_clubs,
        "Clubs_3" to R.drawable.three_of_clubs, "Clubs_4" to R.drawable.four_of_clubs,
        "Clubs_5" to R.drawable.five_of_clubs, "Clubs_6" to R.drawable.six_of_clubs,
        "Clubs_7" to R.drawable.seven_of_clubs, "Clubs_8" to R.drawable.eight_of_clubs,
        "Clubs_9" to R.drawable.nine_of_clubs, "Clubs_10" to R.drawable.ten_of_clubs,
        "Clubs_Jack" to R.drawable.jack_of_clubs, "Clubs_Queen" to R.drawable.queen_of_clubs,
        "Clubs_King" to R.drawable.king_of_clubs,
        "Diamonds_Ace" to R.drawable.ace_of_diamonds, "Diamonds_2" to R.drawable.two_of_diamonds,
        "Diamonds_3" to R.drawable.three_of_diamonds, "Diamonds_4" to R.drawable.four_of_diamonds,
        "Diamonds_5" to R.drawable.five_of_diamonds, "Diamonds_6" to R.drawable.six_of_diamonds,
        "Diamonds_7" to R.drawable.seven_of_diamonds, "Diamonds_8" to R.drawable.eight_of_diamonds,
        "Diamonds_9" to R.drawable.nine_of_diamonds, "Diamonds_10" to R.drawable.ten_of_diamonds,
        "Diamonds_Jack" to R.drawable.jack_of_diamonds, "Diamonds_Queen" to R.drawable.queen_of_diamonds,
        "Diamonds_King" to R.drawable.king_of_diamonds,
        "Joker_Red" to R.drawable.red_joker, "Joker_Black" to R.drawable.black_joker
    )

    private val viewModel: GameViewModel by viewModels { GameViewModelFactory(database, cardResourceMap) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            database = Firebase.database("https://cardsnow-21673-default-rtdb.asia-southeast1.firebasedatabase.app")
                .reference.child("rooms")
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to connect to server: ${e.message}", Toast.LENGTH_LONG).show()
            finish() // Exit app if Firebase fails
            return
        }
        setContent { CardGameApp(viewModel, this) }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (viewModel.gameState.value.isHost && viewModel.gameState.value.roomCode.isNotEmpty()) { // Updated references
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    database.child(viewModel.gameState.value.roomCode).removeValue().await()
                    println("Room ${viewModel.gameState.value.roomCode} deleted on app destroy")
                } catch (e: Exception) {
                    println("Failed to delete room on destroy: ${e.message}")
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        println("Configuration changed: ${newConfig.screenHeightDp}x${newConfig.screenWidthDp}, orientation=${newConfig.orientation}")
    }
}

class GameViewModelFactory(
    private val database: DatabaseReference,
    private val cardResourceMap: Map<String, Int>
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GameViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GameViewModel(database, cardResourceMap) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}