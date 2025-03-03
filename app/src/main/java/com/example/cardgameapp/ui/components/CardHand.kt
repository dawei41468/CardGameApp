package com.example.cardgameapp.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.cardgameapp.Card

@Composable
fun CardHand(
    cards: List<Card>,
    modifier: Modifier = Modifier,
    isSelectable: Boolean = false,
    selectedCards: Map<String, Boolean> = emptyMap(),
    onCardSelected: (Card) -> Unit = {},
    cardWidth: Dp = 70.dp,
    overlapOffset: Dp = 15.dp,
    onCardsReordered: (List<Card>) -> Unit = {}
) {
    val totalWidth = if (cards.isNotEmpty()) (overlapOffset * (cards.size - 1) + cardWidth) else 280.dp
    val cardHeight = if (isSelectable) 120.dp else cardWidth * 1.5f // 120dp for player hand, proportional for others
    var draggedCardIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var targetDropIndex by remember { mutableIntStateOf(-1) }
    val density = LocalDensity.current.density
    var cardListState by remember { mutableStateOf(cards) }

    LaunchedEffect(cards) {
        cardListState = cards
    }

    Box(modifier = modifier.width(totalWidth)) {
        cardListState.forEachIndexed { index, card ->
            val isSelected = selectedCards[card.id] ?: false
            val isDragging = draggedCardIndex == index
            val shiftOffset = if (!isDragging && draggedCardIndex != -1) {
                val dragCenter = (draggedCardIndex * overlapOffset.value) + dragOffsetX + (cardWidth.value / 2)
                val thisCenter = index * overlapOffset.value + (cardWidth.value / 2)
                if (dragCenter > thisCenter && index > draggedCardIndex) overlapOffset
                else if (dragCenter < thisCenter && index < draggedCardIndex) -overlapOffset
                else 0.dp
            } else 0.dp

            val isDropTarget = index == targetDropIndex && !isDragging && shiftOffset != 0.dp
            val isDraggingRight = draggedCardIndex != -1 && dragOffsetX > 0

            Box(
                modifier = Modifier
                    .width(cardWidth)
                    .height(cardHeight) // Dynamic height based on isSelectable
                    .offset(x = overlapOffset * index + (if (isDragging) dragOffsetX.dp else 0.dp) + shiftOffset)
                    .zIndex(
                        when {
                            isDragging -> 1f
                            isDropTarget && isDraggingRight -> 2f
                            isDropTarget -> 0.5f
                            else -> 0f
                        }
                    )
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggedCardIndex = index
                                dragOffsetX = 0f
                                targetDropIndex = index
                                println("Started dragging card at index $index: ${card.rank} of ${card.suit}")
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetX += dragAmount.x / density
                                val dragPosition = (draggedCardIndex * overlapOffset.value) + dragOffsetX
                                targetDropIndex = ((dragPosition + (overlapOffset.value / 2)) / overlapOffset.value).toInt()
                                    .coerceIn(0, cardListState.size - 1)
                                println("Dragging card $index, offsetX: $dragOffsetX, targetDropIndex: $targetDropIndex")
                            },
                            onDragEnd = {
                                if (draggedCardIndex != -1 && targetDropIndex != draggedCardIndex) {
                                    val newList = cardListState.toMutableList()
                                    println("Before reorder: size=${newList.size}, draggedIndex=$draggedCardIndex, dropIndex=$targetDropIndex")
                                    val draggedCard = newList.removeAt(draggedCardIndex)
                                    val adjustedDropIndex = if (targetDropIndex > draggedCardIndex) targetDropIndex - 1 else targetDropIndex
                                        .coerceIn(0, newList.size)
                                    newList.add(adjustedDropIndex, draggedCard)
                                    cardListState = newList
                                    onCardsReordered(newList)
                                    println("Reordered: ${newList.map { it.rank + " of " + it.suit }}")
                                }
                                draggedCardIndex = -1
                                dragOffsetX = 0f
                                targetDropIndex = -1
                                println("Dropped card, final drop index: $targetDropIndex")
                            },
                            onDragCancel = {
                                draggedCardIndex = -1
                                dragOffsetX = 0f
                                targetDropIndex = -1
                                println("Drag cancelled for card at index $index")
                            }
                        )
                    }
                    .then(
                        if (isSelectable && draggedCardIndex == -1) Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onCardSelected(card) } else Modifier
                    )
            ) {
                Image(
                    painter = painterResource(card.resourceId),
                    contentDescription = "${card.rank} of ${card.suit}",
                    modifier = Modifier
                        .width(cardWidth)
                        .scale(if (isDropTarget) 1.2f else 1.0f)
                        .align(if (isSelectable && (isSelected || isDragging)) Alignment.TopCenter else Alignment.BottomCenter)
                )
            }
        }
    }
}