package com.dexter.ui.search

private const val MIN_SUGGEST_LENGTH = 2

/** Suggestions start at two characters, since one letter matches almost everything. */
fun shouldSuggest(text: String): Boolean = text.trim().length >= MIN_SUGGEST_LENGTH
