package com.webtoonclone.ui

import androidx.compose.foundation.clickable
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

/** A tap target for an icon: announced as a button, and at least 48dp square so it is easy to hit. */
fun Modifier.iconTap(onClick: () -> Unit): Modifier =
    this.minimumInteractiveComponentSize().clickable(role = Role.Button, onClick = onClick)
