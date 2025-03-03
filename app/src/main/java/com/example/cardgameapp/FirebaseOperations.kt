package com.example.cardgameapp

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import kotlinx.coroutines.tasks.await

object FirebaseOperations {
    suspend fun createRoom(database: DatabaseReference, settings: RoomSettings, hostName: String): Result<String> {
        return try {
            val code = (1000..9999).random().toString()
            val currentTime = System.currentTimeMillis()
            with(database.child(code)) {
                updateChildren(
                    mapOf(
                        "settings" to settings,
                        "state" to "waiting",
                        "players" to emptyMap<String, Any>(),
                        "host" to hostName.trim(),
                        "lastActive" to currentTime
                    )
                ).await()
            }
            cleanupOldRooms(database, code, currentTime)
            println("Room created: $code by $hostName with ${settings.numDecks} decks")
            Result.success(code)
        } catch (e: Exception) {
            println("Failed to create room: ${e.message}")
            Result.failure(Exception("Network error creating room: ${e.message}"))
        }
    }

    suspend fun joinRoom(roomRef: DatabaseReference, playerName: String): Result<Boolean> {
        return try {
            val snapshot = roomRef.get().await()
            if (!snapshot.exists()) return Result.failure(Exception("Room does not exist"))
            if (snapshot.child("players").childrenCount >= 4) return Result.success(false)
            if (snapshot.child("players").hasChild(playerName)) return Result.success(false)
            roomRef.updateChildren(
                mapOf(
                    "players/$playerName" to mapOf("ready" to false),
                    "lastActive" to System.currentTimeMillis()
                )
            ).await()
            println("Player $playerName joined room")
            Result.success(true)
        } catch (e: Exception) {
            println("Failed to join room: ${e.message}")
            Result.failure(Exception("Network error joining room: ${e.message}"))
        }
    }

    suspend fun startGame(
        roomRef: DatabaseReference,
        cardResourceMap: Map<String, Int>,
        players: List<String>,
        dealCount: Int,
        numDecks: Int
    ): Result<Unit> {
        return try {
            val snapshot = roomRef.get().await()
            val settingsFromFirebase = snapshot.child("settings").getValue(RoomSettings::class.java)
            println("Settings from Firebase: numDecks=${settingsFromFirebase?.numDecks}, includeJokers=${settingsFromFirebase?.includeJokers}")
            val effectiveNumDecks = numDecks.takeIf { it > 0 } ?: settingsFromFirebase?.numDecks ?: 1
            val effectiveSettings = RoomSettings(
                numDecks = effectiveNumDecks,
                includeJokers = settingsFromFirebase?.includeJokers ?: false,
                dealCount = dealCount
            )
            println("Effective settings: numDecks=${effectiveSettings.numDecks}, includeJokers=${effectiveSettings.includeJokers}")
            val deck = CardGameLogic.generateDeck(effectiveSettings, cardResourceMap) // Removed distinctBy
            println("Initial deck size: ${deck.size}, unique cards: ${deck.distinctBy { it.id }.size}, numDecks: $effectiveNumDecks")
            if (deck.size < dealCount * players.size) {
                println("Not enough cards! Deck size: ${deck.size}, required: ${dealCount * players.size}")
                return Result.failure(IllegalStateException("Not enough cards to deal"))
            }
            val playerHands = mutableMapOf<String, List<Card>>()
            var remainingDeck = deck
            for (player in players) {
                val hand = remainingDeck.take(dealCount)
                playerHands[player] = hand
                remainingDeck = remainingDeck.drop(dealCount)
                println("Dealt to $player: ${hand.map { it.rank + " of " + it.suit }}")
            }
            println("Remaining deck size: ${remainingDeck.size}")
            roomRef.updateChildren(
                mapOf(
                    "state" to "started",
                    "gameData" to mapOf(
                        "deck" to remainingDeck.map { mapOf("suit" to it.suit, "rank" to it.rank, "id" to it.id) },
                        "playerHands" to playerHands.mapValues { it.value.map { mapOf("suit" to it.suit, "rank" to it.rank, "id" to it.id) } },
                        "table" to mapOf("piles" to emptyList<List<Map<String, String>>>()),
                        "discardPile" to emptyList<Map<String, String>>(),
                        "lastPlayed" to emptyMap<String, Any>()
                    ),
                    "lastActive" to System.currentTimeMillis()
                )
            ).await()
            println("Game started: ${players.size} players, $dealCount cards each, $effectiveNumDecks decks")
            Result.success(Unit)
        } catch (e: Exception) {
            println("Failed to start game: ${e.message}")
            Result.failure(Exception("Network error starting game: ${e.message}"))
        }
    }

    suspend fun playCards(roomRef: DatabaseReference, cardResourceMap: Map<String, Int>, playerName: String, cardsToPlay: List<Card>): Result<Unit> {
        return try {
            if (cardsToPlay.isEmpty()) return Result.failure(IllegalArgumentException("No cards selected"))
            val snapshot = roomRef.get().await()
            val currentHand = snapshotToCardList(snapshot.child("gameData").child("playerHands").child(playerName), cardResourceMap)
            val newHand = currentHand.filterNot { cardsToPlay.any { played -> played.id == it.id } }
            val currentPiles = snapshot.child("gameData").child("table").child("piles").children.map { snapshotToCardList(it, cardResourceMap) }
            roomRef.updateChildren(
                mapOf(
                    "gameData/playerHands/$playerName" to newHand.map { mapOf("suit" to it.suit, "rank" to it.rank, "id" to it.id) },
                    "gameData/table/piles" to (currentPiles + listOf(cardsToPlay)).map { it.map { mapOf("suit" to it.suit, "rank" to it.rank, "id" to it.id) } },
                    "gameData/lastPlayed" to mapOf("player" to playerName, "hand" to cardsToPlay.map { mapOf("suit" to it.suit, "rank" to it.rank, "id" to it.id) })
                )
            ).await()
            println("$playerName played ${cardsToPlay.size} cards")
            Result.success(Unit)
        } catch (e: Exception) {
            println("Failed to play cards: ${e.message}")
            Result.failure(Exception("Network error playing cards: ${e.message}"))
        }
    }

    suspend fun discardCards(roomRef: DatabaseReference, cardResourceMap: Map<String, Int>, playerName: String, cardsToDiscard: List<Card>): Result<Unit> {
        return try {
            if (cardsToDiscard.isEmpty()) return Result.failure(IllegalArgumentException("No cards selected"))
            val snapshot = roomRef.get().await()
            val currentHand = snapshotToCardList(snapshot.child("gameData").child("playerHands").child(playerName), cardResourceMap)
            val newHand = currentHand.filterNot { cardsToDiscard.any { discarded -> discarded.id == it.id } }
            val currentDiscardPile = snapshotToCardList(snapshot.child("gameData").child("discardPile"), cardResourceMap)
            roomRef.updateChildren(
                mapOf(
                    "gameData/playerHands/$playerName" to newHand.map { mapOf("suit" to it.suit, "rank" to it.rank, "id" to it.id) },
                    "gameData/discardPile" to (currentDiscardPile + cardsToDiscard).map { mapOf("suit" to it.suit, "rank" to it.rank, "id" to it.id) }
                )
            ).await()
            println("$playerName discarded ${cardsToDiscard.size} cards")
            Result.success(Unit)
        } catch (e: Exception) {
            println("Failed to discard cards: ${e.message}")
            Result.failure(Exception("Network error discarding cards: ${e.message}"))
        }
    }

    suspend fun recallLastPile(roomRef: DatabaseReference, cardResourceMap: Map<String, Int>, playerName: String): Result<Unit> {
        return try {
            val snapshot = roomRef.get().await()
            val lastPlayed = snapshot.child("gameData").child("lastPlayed")
            if (lastPlayed.child("player").getValue(String::class.java) != playerName) {
                return Result.failure(IllegalStateException("Not your last play"))
            }
            val lastHand = snapshotToCardList(lastPlayed.child("hand"), cardResourceMap)
            val currentPiles = snapshot.child("gameData").child("table").child("piles").children.map { snapshotToCardList(it, cardResourceMap) }
            if (currentPiles.isNotEmpty() && currentPiles.last().map { it.id } == lastHand.map { it.id }) {
                val newPiles = currentPiles.dropLast(1)
                val currentHand = snapshotToCardList(snapshot.child("gameData").child("playerHands").child(playerName), cardResourceMap)
                val newHand = currentHand + lastHand
                roomRef.updateChildren(
                    mapOf(
                        "gameData/playerHands/$playerName" to newHand.map { mapOf("suit" to it.suit, "rank" to it.rank, "id" to it.id) },
                        "gameData/table/piles" to newPiles.map { it.map { mapOf("suit" to it.suit, "rank" to it.rank, "id" to it.id) } },
                        "gameData/lastPlayed" to emptyMap<String, Any>()
                    )
                ).await()
                println("$playerName recalled last played hand")
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("No valid pile to recall"))
            }
        } catch (e: Exception) {
            println("Failed to recall pile: ${e.message}")
            Result.failure(Exception("Network error recalling pile: ${e.message}"))
        }
    }

    suspend fun drawCard(roomRef: DatabaseReference, cardResourceMap: Map<String, Int>, playerName: String): Result<Card> {
        return try {
            val snapshot = roomRef.get().await()
            val deck = snapshotToCardList(snapshot.child("gameData").child("deck"), cardResourceMap)
            if (deck.isEmpty()) return Result.failure(Exception("Deck is empty—shuffle or end the game!"))
            val drawnCard = deck.first()
            val newDeck = deck.drop(1)
            val currentHand = snapshotToCardList(snapshot.child("gameData").child("playerHands").child(playerName), cardResourceMap)
            val newHand = currentHand + drawnCard
            roomRef.updateChildren(
                mapOf(
                    "gameData/deck" to newDeck.map { mapOf("suit" to it.suit, "rank" to it.rank, "id" to it.id) },
                    "gameData/playerHands/$playerName" to newHand.map { mapOf("suit" to it.suit, "rank" to it.rank, "id" to it.id) }
                )
            ).await()
            println("$playerName drew a card: ${drawnCard.rank} of ${drawnCard.suit}")
            Result.success(drawnCard)
        } catch (e: Exception) {
            println("Failed to draw card: ${e.message}")
            Result.failure(Exception("Network error drawing card: ${e.message}"))
        }
    }

    suspend fun drawFromDiscard(roomRef: DatabaseReference, cardResourceMap: Map<String, Int>, playerName: String): Result<Unit> {
        return try {
            val snapshot = roomRef.get().await()
            val discardPile = snapshotToCardList(snapshot.child("gameData").child("discardPile"), cardResourceMap)
            if (discardPile.isEmpty()) return Result.failure(Exception("Discard pile is empty!"))
            val drawnCard = discardPile.last()
            val newDiscardPile = discardPile.dropLast(1)
            val currentHand = snapshotToCardList(snapshot.child("gameData").child("playerHands").child(playerName), cardResourceMap)
            val newHand = currentHand + drawnCard
            roomRef.updateChildren(
                mapOf(
                    "gameData/discardPile" to newDiscardPile.map { mapOf("suit" to it.suit, "rank" to it.rank, "id" to it.id) },
                    "gameData/playerHands/$playerName" to newHand.map { mapOf("suit" to it.suit, "rank" to it.rank, "id" to it.id) }
                )
            ).await()
            println("$playerName drew top card from discard pile")
            Result.success(Unit)
        } catch (e: Exception) {
            println("Failed to draw from discard: ${e.message}")
            Result.failure(Exception("Network error drawing from discard: ${e.message}"))
        }
    }

    suspend fun shuffleDeck(roomRef: DatabaseReference, cardResourceMap: Map<String, Int>): Result<Unit> {
        return try {
            val snapshot = roomRef.get().await()
            val currentDeck = snapshotToCardList(snapshot.child("gameData").child("deck"), cardResourceMap)
            val tablePiles = snapshot.child("gameData").child("table").child("piles")
                .children.map { snapshotToCardList(it, cardResourceMap) }
            if (tablePiles.isEmpty()) return Result.failure(Exception("No cards on table to shuffle!"))

            val allTableCards = tablePiles.flatten().shuffled()
            val newDeck = (currentDeck + allTableCards).shuffled()

            roomRef.updateChildren(
                mapOf(
                    "gameData/deck" to newDeck.map { mapOf("suit" to it.suit, "rank" to it.rank, "id" to it.id) },
                    "gameData/table/piles" to emptyList<List<Map<String, String>>>()
                )
            ).await()
            println("Deck shuffled with ${allTableCards.size} cards from table appended to ${currentDeck.size} existing cards")
            Result.success(Unit)
        } catch (e: Exception) {
            println("Failed to shuffle deck: ${e.message}")
            Result.failure(Exception("Network error shuffling deck: ${e.message}"))
        }
    }

    suspend fun dealDeck(roomRef: DatabaseReference, cardResourceMap: Map<String, Int>, players: List<String>, count: Int): Result<Map<String, List<Card>>> {
        return try {
            val snapshot = roomRef.get().await()
            val currentDeck = snapshotToCardList(snapshot.child("gameData").child("deck"), cardResourceMap)
            if (currentDeck.size < count * players.size) return Result.failure(Exception("Not enough cards to deal!"))

            val newDeck = currentDeck.drop(count * players.size)
            val dealtCards = currentDeck.take(count * players.size)
            val playerHands = players.associateWith { player ->
                val currentHand = snapshotToCardList(snapshot.child("gameData").child("playerHands").child(player), cardResourceMap)
                val startIndex = players.indexOf(player) * count
                val playerCards = dealtCards.subList(startIndex, startIndex + count)
                currentHand + playerCards
            }

            roomRef.updateChildren(
                mapOf(
                    "gameData/deck" to newDeck.map { mapOf("suit" to it.suit, "rank" to it.rank, "id" to it.id) },
                    "gameData/playerHands" to playerHands.mapValues { it.value.map { mapOf("suit" to it.suit, "rank" to it.rank, "id" to it.id) } }
                )
            ).await()
            println("Dealt $count cards to each of ${players.size} players")
            Result.success(playerHands)
        } catch (e: Exception) {
            println("Failed to deal deck: ${e.message}")
            Result.failure(Exception("Network error dealing deck: ${e.message}"))
        }
    }

    suspend fun moveCards(
        roomRef: DatabaseReference,
        cardResourceMap: Map<String, Int>,
        fromPlayer: String,
        toPlayer: String,
        cardsToMove: List<Card>
    ): Result<Unit> {
        return try {
            val snapshot = roomRef.get().await()
            val fromHand = snapshotToCardList(snapshot.child("gameData").child("playerHands").child(fromPlayer), cardResourceMap)
            val toHand = snapshotToCardList(snapshot.child("gameData").child("playerHands").child(toPlayer), cardResourceMap)

            val newFromHand = fromHand.filterNot { card -> cardsToMove.any { it.id == card.id } }
            val newToHand = toHand + cardsToMove

            roomRef.updateChildren(
                mapOf(
                    "gameData/playerHands/$fromPlayer" to newFromHand.map { mapOf("suit" to it.suit, "rank" to it.rank, "id" to it.id) },
                    "gameData/playerHands/$toPlayer" to newToHand.map { mapOf("suit" to it.suit, "rank" to it.rank, "id" to it.id) }
                )
            ).await()

            println("$fromPlayer moved ${cardsToMove.size} cards to $toPlayer")
            Result.success(Unit)
        } catch (e: Exception) {
            println("Failed to move cards: ${e.message}")
            Result.failure(Exception("Network error moving cards: ${e.message}"))
        }
    }

    suspend fun cleanupOldRooms(database: DatabaseReference, newRoomCode: String, currentTime: Long) {
        try {
            val snapshot = database.get().await()
            val staleThreshold = currentTime - (5 * 60 * 1000)
            snapshot.children.forEach { roomSnapshot ->
                val roomCode = roomSnapshot.key ?: return@forEach
                if (roomCode != newRoomCode) {
                    val lastActive = roomSnapshot.child("lastActive").getValue(Long::class.java) ?: 0L
                    if (lastActive < staleThreshold) {
                        database.child(roomCode).removeValue().await()
                        println("Deleted stale room: $roomCode (last active: $lastActive)")
                    }
                }
            }
        } catch (e: Exception) {
            println("Failed to clean up old rooms: ${e.message}")
        }
    }

    private fun snapshotToCardList(snapshot: DataSnapshot, cardResourceMap: Map<String, Int>): List<Card> =
        snapshot.children.mapNotNull { CardGameLogic.cardFromSnapshot(it, cardResourceMap) }
}