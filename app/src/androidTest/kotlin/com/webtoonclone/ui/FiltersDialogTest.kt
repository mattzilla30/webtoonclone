package com.webtoonclone.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.webtoonclone.data.SearchFilters
import com.webtoonclone.ui.search.FiltersDialog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FiltersDialogTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun chipShowsItsSelection() {
        var selected by mutableStateOf(false)
        rule.setContent { MaterialTheme { ChoiceChip("Ongoing", selected) { selected = !selected } } }
        rule.onNodeWithText("Ongoing").performClick()
        rule.onNodeWithText("Ongoing").assertIsSelected()
    }

    @Test
    fun applyReturnsWhatYouPicked() {
        var applied: SearchFilters? = null
        rule.setContent { MaterialTheme { FiltersDialog(SearchFilters(), onApply = { applied = it }, onDismiss = {}) } }
        rule.onNodeWithText("Ongoing").performClick()
        rule.onNodeWithText("Action").performClick()
        rule.onNodeWithText("Apply").performClick()
        assertEquals(listOf("ongoing"), applied?.status)
        assertEquals(listOf("Action"), applied?.included)
    }
}
