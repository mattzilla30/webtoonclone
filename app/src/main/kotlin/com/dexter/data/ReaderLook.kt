package com.dexter.data

/** The settings as the reader for [seriesId] sees them: this series' own dimming and background when it has them. */
fun effectiveLook(settings: Settings, seriesId: String): Settings {
    val look = settings.seriesLooks[seriesId] ?: return settings
    return settings.copy(readerDim = look.dim, readerBackground = look.background)
}

/**
 * Applies a reader settings change for [seriesId]. When the series has its own look, changes to dimming
 * and background go to that look and leave the global ones alone. Every other change applies as usual.
 */
fun applyLookChange(settings: Settings, seriesId: String, change: (Settings) -> Settings): Settings {
    if (seriesId !in settings.seriesLooks) return change(settings)
    val after = change(effectiveLook(settings, seriesId))
    return after.copy(
        readerDim = settings.readerDim,
        readerBackground = settings.readerBackground,
        seriesLooks = after.seriesLooks + (seriesId to SeriesLook(after.readerDim, after.readerBackground)),
    )
}

/** Gives [seriesId] its own look, starting from the global one, or takes it back to the global look. */
fun withSeriesLook(settings: Settings, seriesId: String, enabled: Boolean): Settings =
    if (enabled) {
        settings.copy(seriesLooks = settings.seriesLooks + (seriesId to SeriesLook(settings.readerDim, settings.readerBackground)))
    } else {
        settings.copy(seriesLooks = settings.seriesLooks - seriesId)
    }
