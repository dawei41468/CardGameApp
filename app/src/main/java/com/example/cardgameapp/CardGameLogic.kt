package com.example.cardgameapp

import com.google.firebase.database.DataSnapshot
import java.util.UUID

data class Card(val suit: String, val rank: String, val resourceId: Int, val id: String = UUID.randomUUID().toString())

data class RoomSettings(val numDecks: Int = 1, val includeJokers: Boolean = false, val dealCount: Int = 5)

object CardGameLogic {
    fun generateDeck(settings: RoomSettings, cardResourceMap: Map<String, Int>): List<Card> {
        val suits = listOf("Spades", "Hearts", "Clubs", "Diamonds")
        val ranks = listOf("Ace", "2", "3", "4", "5", "6", "7", "8", "9", "10", "Jack", "Queen", "King")
        val standardDeck = suits.flatMap { suit -> ranks.map { rank -> Card(suit, rank, cardResourceMap["${suit}_$rank"] ?: R.drawable.card_back_red) } }
        val jokers = if (settings.includeJokers) listOf(
            Card("Joker", "Red", cardResourceMap["Joker_Red"] ?: R.drawable.red_joker),
            Card("Joker", "Black", cardResourceMap["Joker_Black"] ?: R.drawable.black_joker)
        ) else emptyList()
        return buildList { repeat(settings.numDecks) { addAll(standardDeck.map { Card(it.suit, it.rank, it.resourceId) } + jokers) } }.shuffled()
    }

    fun sortByRank(cards: List<Card>): List<Card> = cards.sortedWith(compareBy({ getRankValue(it.rank) }, { getSuitOrder(it.suit) }))

    fun sortBySuit(cards: List<Card>): List<Card> = cards.sortedWith(compareBy({ getSuitOrder(it.suit) }, { getRankValue(it.rank) }))

    private fun getRankValue(rank: String) = when (rank) {
        "Ace" -> 1; "2" -> 2; "3" -> 3; "4" -> 4; "5" -> 5; "6" -> 6; "7" -> 7; "8" -> 8; "9" -> 9; "10" -> 10
        "Jack" -> 11; "Queen" -> 12; "King" -> 13; else -> 0
    }

    private fun getSuitOrder(suit: String) = when (suit) {
        "Spades" -> 1; "Hearts" -> 2; "Clubs" -> 3; "Diamonds" -> 4; else -> 0
    }

    fun cardFromSnapshot(snapshot: DataSnapshot, cardResourceMap: Map<String, Int>): Card? {
        val suit = snapshot.child("suit").getValue(String::class.java) ?: ""
        val rank = snapshot.child("rank").getValue(String::class.java) ?: ""
        val id = snapshot.child("id").getValue(String::class.java) ?: ""
        val resourceId = cardResourceMap["${suit}_$rank"] ?: 0
        return Card(suit, rank, resourceId, id).takeIf { it.resourceId != 0 }
    }
}