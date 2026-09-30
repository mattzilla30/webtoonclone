package com.dexter.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo

private const val TILE_WIDTH_DP = 170
private const val MIN_COLUMNS = 2
private const val MAX_COLUMNS = 6

/** Window width (dp) from which navigation moves from the bottom bar to a side rail. */
const val RAIL_MIN_WIDTH_DP = 600

/** How many cover tiles fit across [widthDp], between two and six. */
fun adaptiveColumns(widthDp: Float): Int = (widthDp / TILE_WIDTH_DP).toInt().coerceIn(MIN_COLUMNS, MAX_COLUMNS)

/** The window width in dp, read from the window so it follows resizing and split screen. */
@Composable
fun windowWidthDp(): Float = LocalWindowInfo.current.containerSize.width / LocalDensity.current.density
