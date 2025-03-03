package com.example.cardgameapp

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class GameState(
    val screen: String = "splash",
    val roomCode: String = "",
    val playerName: String = "",
    val numDecks: Int = 1,
    val includeJokers: Boolean = false,
    val hostName: String = "Host",
    val players: List<String> = emptyList(),
    val isHost: Boolean = false,
    val gameStarted: Boolean = false,
    val myHand: List<Card> = emptyList(),
    val table: List<List<Card>> = emptyList(),
    val discardPile: List<Card> = emptyList(),
    val selectedCards: Map<String, Boolean> = emptyMap(),
    val deckEmpty: Boolean = false,
    val deckSize: Int = 0,
    val otherPlayersHandSizes: Map<String, Int> = emptyMap(),
    val canRecall: Boolean = false,
    val showMenu: Boolean = false,
    val isLoadingGeneral: Boolean = false,
    val isPlayingCards: Boolean = false,
    val isDrawingCard: Boolean = false,
    val errorMessage: String = "",
    val errorType: ErrorType = ErrorType.NONE,
    val successMessage: String = "",
    val isConnected: Boolean = true,
    val showNewHostDialog: Boolean = false
)

enum class ErrorType {
    NONE, TRANSIENT, CRITICAL
}

class GameViewModel(
    private val database: DatabaseReference,
    private val cardResourceMap: Map<String, Int>
) : ViewModel() {
    private var _gameState = mutableStateOf(GameState())
    val gameState: State<GameState> = _gameState

    private val connectionListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val connected = snapshot.getValue(Boolean::class.java) ?: false
            println("Firebase .info/connected snapshot: $connected (key: ${snapshot.key}, exists: ${snapshot.exists()})")
            println("Previous isConnected: ${_gameState.value.isConnected}")
            if (connected != _gameState.value.isConnected) {
                _gameState.value = _gameState.value.copy(isConnected = connected)
                println("Network status changed to: ${if (connected) "Connected" else "Disconnected"}")
                if (connected && _gameState.value.roomCode.isNotEmpty()) {
                    rejoinRoom()
                }
            } else {
                println("Network status unchanged: ${if (connected) "Connected" else "Disconnected"}")
            }
        }

        override fun onCancelled(error: DatabaseError) {
            println("Connection listener cancelled: ${error.message}")
            showError("Connection check failed: ${error.message}", ErrorType.TRANSIENT)
        }
    }

    init {
        println("Initializing GameViewModel - Attaching connection listener")
        database.root.child(".info/connected").addValueEventListener(connectionListener)
    }

    override fun onCleared() {
        super.onCleared()
        println("GameViewModel cleared - Removing connection listener")
        database.root.child(".info/connected").removeEventListener(connectionListener)
        roomRef.removeEventListener(roomListener)
    }

    fun setHostName(newHostName: String) {
        _gameState.value = _gameState.value.copy(hostName = newHostName)
    }

    fun setNumDecks(newNumDecks: Int) {
        _gameState.value = _gameState.value.copy(numDecks = newNumDecks)
    }

    fun setIncludeJokers(newIncludeJokers: Boolean) {
        _gameState.value = _gameState.value.copy(includeJokers = newIncludeJokers)
    }

    private val roomListener: ValueEventListener = object : ValueEventListener {
        private var lastUpdate = 0L
        override fun onDataChange(snapshot: DataSnapshot) {
            val now = System.currentTimeMillis()
            if (now - lastUpdate < 200) return
            lastUpdate = now
            println("Firebase update started for room ${_gameState.value.roomCode}, player: ${_gameState.value.playerName}")
            val newPlayers = snapshot.child("players").children.mapNotNull { it.key }
            val hostName = snapshot.child("host").getValue(String::class.java) ?: "Host"

            // Reassign host if current host is not in players list
            if (!newPlayers.contains(hostName) && newPlayers.isNotEmpty()) {
                viewModelScope.launch {
                    val newHost = newPlayers.first()
                    roomRef.child("host").setValue(newHost).await()
                    roomRef.child("lastUpdated").setValue(System.currentTimeMillis()).await()
                    println("Host reassigned to $newHost after $hostName disconnected")
                }
            }

            // Check if this player is becoming host
            val isNowHost = _gameState.value.playerName == hostName && !_gameState.value.isHost

            // Update state with dialog trigger first
            if (isNowHost) {
                _gameState.value = _gameState.value.copy(showNewHostDialog = true)
            }

            // Then update the rest of the state
            _gameState.value = _gameState.value.copy(
                players = newPlayers,
                isHost = _gameState.value.playerName == hostName,
                gameStarted = snapshot.child("state").value == "started",
                deckEmpty = snapshot.child("gameData").child("deck").childrenCount == 0L,
                deckSize = snapshot.child("gameData").child("deck").childrenCount.toInt(),
                otherPlayersHandSizes = newPlayers.filter { it != _gameState.value.playerName }.associateWith {
                    snapshot.child("gameData").child("playerHands").child(it).childrenCount.toInt()
                }
            )

            println("Players updated: ${_gameState.value.players}")
            println("Deck size synced: ${_gameState.value.deckSize}, empty: ${_gameState.value.deckEmpty}")
            if (_gameState.value.gameStarted) {
                val rawHand = snapshot.child("gameData").child("playerHands").child(_gameState.value.playerName)
                    .children.mapNotNull { CardGameLogic.cardFromSnapshot(it, cardResourceMap) }
                println("Raw hand for ${_gameState.value.playerName} from Firebase: ${rawHand.map { it.rank + " of " + it.suit }}")
                val newTable = snapshot.child("gameData").child("table").child("piles").children.mapNotNull { pileSnapshot ->
                    pileSnapshot.children.mapNotNull { CardGameLogic.cardFromSnapshot(it, cardResourceMap) }
                        .also { pile -> println("Table pile: ${pile.map { it.rank + " of " + it.suit }}") }
                }
                val newDiscardPile = snapshot.child("gameData").child("discardPile").children.mapNotNull { CardGameLogic.cardFromSnapshot(it, cardResourceMap) }
                println("Discard pile updated: ${newDiscardPile.map { it.rank + " of " + it.suit }}")
                val lastPlayed = snapshot.child("gameData").child("lastPlayed")
                val lastPlayedPlayer = lastPlayed.child("player").getValue(String::class.java)
                val lastPlayedHand = lastPlayed.child("hand").children.mapNotNull { CardGameLogic.cardFromSnapshot(it, cardResourceMap) }
                println("Last played by $lastPlayedPlayer: ${lastPlayedHand.map { it.rank + " of " + it.suit }}")
                _gameState.value = _gameState.value.copy(
                    myHand = rawHand,
                    table = newTable,
                    discardPile = newDiscardPile,
                    canRecall = lastPlayedPlayer == _gameState.value.playerName && lastPlayedHand.isNotEmpty() && newTable.isNotEmpty() && newTable.last().map { it.id } == lastPlayedHand.map { it.id }
                )
                println("Hand updated for ${_gameState.value.playerName}: ${_gameState.value.myHand.map { it.rank + " of " + it.suit }}")
                println("Table updated: ${_gameState.value.table.map { it.map { card -> card.rank + " of " + card.suit } }}")
                println("Other players hand sizes for ${_gameState.value.playerName}: ${_gameState.value.otherPlayersHandSizes}")
            }
            println("Firebase update completed for ${_gameState.value.playerName}")
        }

        override fun onCancelled(error: DatabaseError) {
            println("Firebase listener cancelled: ${error.message}")
            showError("Database sync error: ${error.message}", ErrorType.CRITICAL)
        }
    }

    fun navigateToCreate() {
        _gameState.value = _gameState.value.copy(screen = "create")
    }

    fun navigateToJoin() {
        _gameState.value = _gameState.value.copy(screen = "join")
    }

    fun navigateToHome() {
        _gameState.value = _gameState.value.copy(screen = "home")
    }

    fun onRoomCreated(code: String) {
        _gameState.value = _gameState.value.copy(
            roomCode = code,
            playerName = _gameState.value.hostName.trim(),
            screen = "room",
            isHost = true,
            showMenu = false,
            errorMessage = "",
            errorType = ErrorType.NONE
        )
        roomRef.removeEventListener(roomListener)
        roomRef.addValueEventListener(roomListener)
        setupOnDisconnect()
        refreshPlayers()
        println("Room created by ${_gameState.value.hostName.trim()}, playerName set to: ${_gameState.value.playerName}")
    }

    fun onRoomJoined(code: String, name: String) {
        _gameState.value = _gameState.value.copy(
            roomCode = code,
            playerName = name,
            screen = "room",
            showMenu = false,
            errorMessage = "",
            errorType = ErrorType.NONE
        )
        roomRef.removeEventListener(roomListener)
        roomRef.addValueEventListener(roomListener)
        setupOnDisconnect()
        refreshPlayers()
        println("Room joined by $name, playerName set to: ${_gameState.value.playerName}")
    }

    fun backToHome() {
        _gameState.value = _gameState.value.copy(screen = "home")
        roomRef.child("players").child(_gameState.value.playerName).removeValue()
        roomRef.removeEventListener(roomListener)
    }

    fun showError(message: String, type: ErrorType = ErrorType.TRANSIENT) {
        println("Error [${type.name}]: $message")
        _gameState.value = _gameState.value.copy(errorMessage = message, errorType = type)
    }

    fun clearError() {
        _gameState.value = _gameState.value.copy(errorMessage = "", errorType = ErrorType.NONE)
    }

    fun clearSuccess() {
        _gameState.value = _gameState.value.copy(successMessage = "")
    }

    fun clearNewHostDialog() {
        _gameState.value = _gameState.value.copy(showNewHostDialog = false)
    }

    fun createRoom() {
        viewModelScope.launch {
            _gameState.value = _gameState.value.copy(isLoadingGeneral = true)
            val now = System.currentTimeMillis()
            val fifteenMinutesAgo = now - (15 * 60 * 1000)
            val snapshot = database.get().await()
            snapshot.children.forEach { roomSnapshot ->
                val lastUpdated = roomSnapshot.child("lastUpdated").getValue(Long::class.java) ?: 0L
                if (lastUpdated < fifteenMinutesAgo) {
                    roomSnapshot.ref.removeValue().await()
                    println("Deleted stale room ${roomSnapshot.key} (last updated: $lastUpdated)")
                }
            }

            val dealCount = 0
            val settings = RoomSettings(_gameState.value.numDecks, _gameState.value.includeJokers, dealCount)
            FirebaseOperations.createRoom(database, settings, _gameState.value.hostName).fold(
                onSuccess = { code ->
                    val roomRef = database.child(code)
                    roomRef.child("players").child(_gameState.value.hostName.trim())
                        .setValue(mapOf("ready" to false)).await()
                    roomRef.child("lastUpdated").setValue(System.currentTimeMillis()).await()
                    onRoomCreated(code)
                    _gameState.value = _gameState.value.copy(successMessage = "Room $code created!")
                },
                onFailure = { error -> showError("Failed to create room: ${error.message}", ErrorType.TRANSIENT) }
            )
            _gameState.value = _gameState.value.copy(isLoadingGeneral = false)
        }
    }

    fun joinRoom(code: String, playerName: String) {
        viewModelScope.launch {
            _gameState.value = _gameState.value.copy(isLoadingGeneral = true)
            val trimmedCode = code.trim()
            val trimmedName = playerName.trim()
            if (trimmedName.isBlank()) {
                showError("Please enter a name!", ErrorType.TRANSIENT)
            } else if (trimmedCode.isBlank()) {
                showError("Please enter a room code!", ErrorType.TRANSIENT)
            } else if (!trimmedCode.matches(Regex("\\d{4}"))) {
                showError("Room code must be a 4-digit number!", ErrorType.TRANSIENT)
            } else {
                val roomRef = database.child(trimmedCode)
                val snapshot = roomRef.get().await()
                if (!snapshot.exists()) {
                    showError("Room $trimmedCode does not exist!", ErrorType.CRITICAL)
                } else if (snapshot.child("players").childrenCount >= 4) {
                    showError("Room $trimmedCode is full (max 4 players)!", ErrorType.CRITICAL)
                } else {
                    FirebaseOperations.joinRoom(roomRef, trimmedName).fold(
                        onSuccess = { success ->
                            if (success) {
                                roomRef.child("lastUpdated").setValue(System.currentTimeMillis()).await()
                                onRoomJoined(trimmedCode, trimmedName)
                                _gameState.value = _gameState.value.copy(successMessage = "Joined room $trimmedCode as $trimmedName!")
                            } else {
                                showError("Room $trimmedCode is full or name '$trimmedName' is taken!", ErrorType.CRITICAL)
                            }
                        },
                        onFailure = { error -> showError("Failed to join: ${error.message}", ErrorType.TRANSIENT) }
                    )
                }
            }
            _gameState.value = _gameState.value.copy(isLoadingGeneral = false)
        }
    }

    private fun rejoinRoom() {
        viewModelScope.launch {
            val roomCode = _gameState.value.roomCode
            val playerName = _gameState.value.playerName
            if (roomCode.isNotEmpty() && playerName.isNotEmpty()) {
                val roomRef = database.child(roomCode)
                val snapshot = roomRef.get().await()
                if (snapshot.exists()) {
                    if (!snapshot.child("players").child(playerName).exists()) {
                        roomRef.child("players").child(playerName).setValue(mapOf("ready" to false)).await()
                        roomRef.child("lastUpdated").setValue(System.currentTimeMillis()).await()
                        println("Rejoined room $roomCode as $playerName")
                    }
                    roomRef.removeEventListener(roomListener)
                    roomRef.addValueEventListener(roomListener)
                } else {
                    println("Room $roomCode no longer exists on reconnect")
                    _gameState.value = _gameState.value.copy(screen = "home", roomCode = "", players = emptyList())
                }
            }
        }
    }

    fun toggleCardSelection(card: Card) {
        println("Before toggle: ${_gameState.value.selectedCards.size} cards selected")
        _gameState.value = _gameState.value.copy(
            selectedCards = if (_gameState.value.selectedCards[card.id] == true) {
                _gameState.value.selectedCards - card.id
            } else {
                _gameState.value.selectedCards + (card.id to true)
            }
        )
        println("After toggle: ${_gameState.value.selectedCards.size} cards selected, keys: ${_gameState.value.selectedCards.keys}")
    }

    fun playCards() {
        val cardsToPlay = _gameState.value.myHand.filter { _gameState.value.selectedCards[it.id] ?: false }
        if (cardsToPlay.isEmpty()) {
            showError("No cards selected to play!", ErrorType.TRANSIENT)
            return
        }
        viewModelScope.launch {
            _gameState.value = _gameState.value.copy(isPlayingCards = true)
            FirebaseOperations.playCards(roomRef, cardResourceMap, _gameState.value.playerName, cardsToPlay).fold(
                onSuccess = {
                    roomRef.child("lastUpdated").setValue(System.currentTimeMillis()).await()
                    _gameState.value = _gameState.value.copy(
                        selectedCards = emptyMap(),
                        canRecall = true,
                        successMessage = "${cardsToPlay.size} card(s) played!"
                    )
                    println("Hand size after play: ${_gameState.value.myHand.size}, cards played: ${cardsToPlay.size}")
                },
                onFailure = { error -> showError("Failed to play cards: ${error.message}", ErrorType.TRANSIENT) }
            )
            _gameState.value = _gameState.value.copy(isPlayingCards = false)
        }
    }

    fun discardCards() {
        val cardsToDiscard = _gameState.value.myHand.filter { _gameState.value.selectedCards[it.id] ?: false }
        if (cardsToDiscard.isEmpty()) {
            showError("No cards selected to discard!", ErrorType.TRANSIENT)
            return
        }
        viewModelScope.launch {
            _gameState.value = _gameState.value.copy(isPlayingCards = true)
            FirebaseOperations.discardCards(roomRef, cardResourceMap, _gameState.value.playerName, cardsToDiscard).fold(
                onSuccess = {
                    roomRef.child("lastUpdated").setValue(System.currentTimeMillis()).await()
                    _gameState.value = _gameState.value.copy(
                        selectedCards = emptyMap(),
                        successMessage = "${cardsToDiscard.size} card(s) discarded!"
                    )
                    println("Hand size after discard: ${_gameState.value.myHand.size}, discarded: ${cardsToDiscard.size}, discard pile: ${_gameState.value.discardPile.size}")
                },
                onFailure = { error -> showError("Failed to discard cards: ${error.message}", ErrorType.TRANSIENT) }
            )
            _gameState.value = _gameState.value.copy(isPlayingCards = false)
        }
    }

    fun recallLastPile() {
        viewModelScope.launch {
            _gameState.value = _gameState.value.copy(isLoadingGeneral = true)
            FirebaseOperations.recallLastPile(roomRef, cardResourceMap, _gameState.value.playerName).fold(
                onSuccess = {
                    roomRef.child("lastUpdated").setValue(System.currentTimeMillis()).await()
                    _gameState.value = _gameState.value.copy(
                        canRecall = false,
                        successMessage = "Last pile recalled!"
                    )
                },
                onFailure = { error -> showError("Failed to recall: ${error.message}", ErrorType.TRANSIENT) }
            )
            _gameState.value = _gameState.value.copy(isLoadingGeneral = false)
        }
    }

    fun drawCard() {
        viewModelScope.launch {
            _gameState.value = _gameState.value.copy(isDrawingCard = true)
            FirebaseOperations.drawCard(roomRef, cardResourceMap, _gameState.value.playerName).fold(
                onSuccess = {
                    roomRef.child("lastUpdated").setValue(System.currentTimeMillis()).await()
                    println("Card drawn successfully for ${_gameState.value.playerName}")
                    _gameState.value = _gameState.value.copy(successMessage = "Card drawn!")
                    refreshDeck()
                },
                onFailure = { error -> showError("Failed to draw: ${error.message}", ErrorType.TRANSIENT) }
            )
            _gameState.value = _gameState.value.copy(isDrawingCard = false)
        }
    }

    fun drawFromDiscard() {
        viewModelScope.launch {
            _gameState.value = _gameState.value.copy(isDrawingCard = true)
            FirebaseOperations.drawFromDiscard(roomRef, cardResourceMap, _gameState.value.playerName).fold(
                onSuccess = {
                    roomRef.child("lastUpdated").setValue(System.currentTimeMillis()).await()
                    println("Top card drawn from discard pile for ${_gameState.value.playerName}")
                    _gameState.value = _gameState.value.copy(successMessage = "Card drawn from discard!")
                    refreshDiscardPile()
                },
                onFailure = { error -> showError("Failed to draw from discard: ${error.message}", ErrorType.TRANSIENT) }
            )
            _gameState.value = _gameState.value.copy(isDrawingCard = false)
        }
    }

    fun shuffleDeck() {
        viewModelScope.launch {
            _gameState.value = _gameState.value.copy(isLoadingGeneral = true)
            FirebaseOperations.shuffleDeck(roomRef, cardResourceMap).fold(
                onSuccess = {
                    roomRef.child("lastUpdated").setValue(System.currentTimeMillis()).await()
                    println("Deck shuffled successfully for ${_gameState.value.playerName}")
                    _gameState.value = _gameState.value.copy(successMessage = "Deck shuffled!")
                    refreshDeck()
                },
                onFailure = { error -> showError("Failed to shuffle: ${error.message}", ErrorType.TRANSIENT) }
            )
            _gameState.value = _gameState.value.copy(isLoadingGeneral = false)
        }
    }

    fun dealDeck(count: Int) {
        viewModelScope.launch {
            _gameState.value = _gameState.value.copy(isLoadingGeneral = true)
            val playerCount = _gameState.value.players.size
            val maxCardsPerPlayer = _gameState.value.deckSize / playerCount
            if (count > maxCardsPerPlayer) {
                showError("Cannot deal $count cards, max is $maxCardsPerPlayer per player!", ErrorType.TRANSIENT)
                _gameState.value = _gameState.value.copy(isLoadingGeneral = false)
                return@launch
            }
            FirebaseOperations.dealDeck(roomRef, cardResourceMap, _gameState.value.players, count).fold(
                onSuccess = {
                    roomRef.child("lastUpdated").setValue(System.currentTimeMillis()).await()
                    println("Dealt $count cards successfully for ${_gameState.value.playerName}")
                    _gameState.value = _gameState.value.copy(successMessage = "Dealt $count cards to each player!")
                    refreshDeck()
                },
                onFailure = { error -> showError("Failed to deal: ${error.message}", ErrorType.TRANSIENT) }
            )
            _gameState.value = _gameState.value.copy(isLoadingGeneral = false)
        }
    }

    fun moveCardsToPlayer(targetPlayer: String) {
        val cardsToMove = _gameState.value.myHand.filter { _gameState.value.selectedCards[it.id] ?: false }
        if (cardsToMove.isEmpty()) {
            showError("No cards selected to move!", ErrorType.TRANSIENT)
            return
        }
        viewModelScope.launch {
            _gameState.value = _gameState.value.copy(isLoadingGeneral = true)
            FirebaseOperations.moveCards(roomRef, cardResourceMap, _gameState.value.playerName, targetPlayer, cardsToMove).fold(
                onSuccess = {
                    roomRef.child("lastUpdated").setValue(System.currentTimeMillis()).await()
                    _gameState.value = _gameState.value.copy(
                        selectedCards = emptyMap(),
                        successMessage = "Moved ${cardsToMove.size} card(s) to $targetPlayer!"
                    )
                },
                onFailure = { error -> showError("Failed to move cards: ${error.message}", ErrorType.TRANSIENT) }
            )
            _gameState.value = _gameState.value.copy(isLoadingGeneral = false)
        }
    }

    fun startGame() {
        viewModelScope.launch {
            _gameState.value = _gameState.value.copy(isLoadingGeneral = true)
            refreshPlayers()
            val dealCount = 0
            val numDecks = _gameState.value.numDecks
            println("Starting game with $numDecks decks, dealCount: $dealCount, players: ${_gameState.value.players.size}")
            FirebaseOperations.startGame(roomRef, cardResourceMap, _gameState.value.players, dealCount, numDecks).fold(
                onSuccess = {
                    roomRef.child("lastUpdated").setValue(System.currentTimeMillis()).await()
                    _gameState.value = _gameState.value.copy(
                        showMenu = false,
                        successMessage = "Game started with $numDecks decks!"
                    )
                    println("Game started successfully by ${_gameState.value.playerName} with $dealCount cards and $numDecks decks")
                },
                onFailure = { error -> showError("Failed to start game: ${error.message}", ErrorType.TRANSIENT) }
            )
            _gameState.value = _gameState.value.copy(isLoadingGeneral = false)
        }
    }

    fun sortByRank() {
        val sortedHand = CardGameLogic.sortByRank(_gameState.value.myHand)
        _gameState.value = _gameState.value.copy(
            myHand = sortedHand,
            successMessage = "Hand sorted by rank!"
        )
        updateHandInFirebase(sortedHand)
        println("Sorted hand by rank for ${_gameState.value.playerName}: ${_gameState.value.myHand.map { it.rank + " of " + it.suit }}")
    }

    fun sortBySuit() {
        val sortedHand = CardGameLogic.sortBySuit(_gameState.value.myHand)
        _gameState.value = _gameState.value.copy(
            myHand = sortedHand,
            successMessage = "Hand sorted by suit!"
        )
        updateHandInFirebase(sortedHand)
        println("Sorted hand by suit for ${_gameState.value.playerName}: ${_gameState.value.myHand.map { it.rank + " of " + it.suit }}")
    }

    fun reorderHand(newOrder: List<Card>) {
        _gameState.value = _gameState.value.copy(myHand = newOrder)
        updateHandInFirebase(newOrder)
        println("Hand reordered in ViewModel: ${newOrder.map { it.rank + " of " + it.suit }}")
    }

    private fun updateHandInFirebase(hand: List<Card>) {
        viewModelScope.launch {
            roomRef.child("gameData").child("playerHands").child(_gameState.value.playerName)
                .setValue(hand.map { mapOf("suit" to it.suit, "rank" to it.rank, "id" to it.id) })
                .await()
            roomRef.child("lastUpdated").setValue(System.currentTimeMillis()).await()
            println("Hand synced to Firebase: ${hand.map { it.rank + " of " + it.suit }}")
        }
    }

    private fun refreshDiscardPile() {
        viewModelScope.launch {
            val snapshot = roomRef.get().await()
            _gameState.value = _gameState.value.copy(
                discardPile = snapshot.child("gameData").child("discardPile").children.mapNotNull { CardGameLogic.cardFromSnapshot(it, cardResourceMap) }
            )
            println("Discard pile refreshed: ${_gameState.value.discardPile.map { it.rank + " of " + it.suit }}")
        }
    }

    private fun refreshDeck() {
        viewModelScope.launch {
            val snapshot = roomRef.get().await()
            _gameState.value = _gameState.value.copy(
                deckSize = snapshot.child("gameData").child("deck").childrenCount.toInt(),
                deckEmpty = snapshot.child("gameData").child("deck").childrenCount.toInt() == 0
            )
            println("Deck refreshed: ${_gameState.value.deckSize} cards, empty: ${_gameState.value.deckEmpty}")
        }
    }

    fun toggleMenu() {
        _gameState.value = _gameState.value.copy(showMenu = !_gameState.value.showMenu)
    }

    fun leaveRoom() { backToHome() }
    fun restartGame() { startGame() }

    fun exitGame(activity: MainActivity) {
        viewModelScope.launch {
            if (_gameState.value.isHost && _gameState.value.roomCode.isNotEmpty()) {
                try {
                    roomRef.removeValue().await()
                    println("Room ${_gameState.value.roomCode} deleted by host ${_gameState.value.playerName} on normal exit")
                } catch (e: Exception) {
                    showError("Failed to delete room: ${e.message}", ErrorType.TRANSIENT)
                }
            }
            roomRef.removeEventListener(roomListener)
            activity.finishAffinity()
        }
    }

    fun setupOnDisconnect() {
        if (_gameState.value.roomCode.isNotEmpty()) {
            val playerPresenceRef = roomRef.child("players").child(_gameState.value.playerName)
            playerPresenceRef.setValue(mapOf("ready" to false))
            playerPresenceRef.onDisconnect().removeValue()
            println("onDisconnect set up for player ${_gameState.value.playerName} in room ${_gameState.value.roomCode}")
        }
    }

    private val roomRef: DatabaseReference get() = if (_gameState.value.roomCode.isNotEmpty()) database.child(_gameState.value.roomCode) else database

    fun refreshPlayers() {
        viewModelScope.launch {
            val snapshot = roomRef.get().await()
            _gameState.value = _gameState.value.copy(
                players = snapshot.child("players").children.mapNotNull { it.key }
            )
            println("Manual refresh for ${_gameState.value.playerName}: players = ${_gameState.value.players}")
        }
    }
}