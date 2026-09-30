package com.dexter.ui.home

/** The confirmation shown after a long press subscribes or unsubscribes. */
fun subscriptionMessage(title: String, nowSubscribed: Boolean): String =
    if (nowSubscribed) "Subscribed to $title" else "Unsubscribed from $title"
