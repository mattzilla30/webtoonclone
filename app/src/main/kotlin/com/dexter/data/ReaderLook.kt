package com.dexter.data

/** The settings as the reader for [seriesId] sees them: this series' own look when it has one. */
fun effectiveLook(settings: Settings, seriesId: String): Settings {
    val look = settings.seriesLooks[seriesId] ?: return settings
    return settings.copy(
        readerDim = look.dim,
        readerBackground = look.background,
        // -1 means the series follows the global brightness.
        readerBrightness = if (look.brightness in 1..100) look.brightness else settings.readerBrightness,
        cropBorders = look.cropBorders,
    )
}

/**
 * Applies a reader settings change for [seriesId]. When the series has its own look, changes to
 * dimming, background, brightness, and border cropping go to that look and leave the global ones
 * alone. Every other change applies as usual.
 */
fun applyLookChange(settings: Settings, seriesId: String, change: (Settings) -> Settings): Settings {
    if (seriesId !in settings.seriesLooks) return change(settings)
    val after = change(effectiveLook(settings, seriesId))
    return after.copy(
        readerDim = settings.readerDim,
        readerBackground = settings.readerBackground,
        readerBrightness = settings.readerBrightness,
        cropBorders = settings.cropBorders,
        seriesLooks = after.seriesLooks + (
            seriesId to SeriesLook(after.readerDim, after.readerBackground, after.readerBrightness, after.cropBorders)
            ),
    )
}

/** Gives [seriesId] its own look, starting from the global one, or takes it back to the global look. */
fun withSeriesLook(settings: Settings, seriesId: String, enabled: Boolean): Settings =
    if (enabled) {
        settings.copy(
            seriesLooks = settings.seriesLooks + (
                seriesId to SeriesLook(
                    dim = settings.readerDim,
                    background = settings.readerBackground,
                    // Brightness keeps following the global setting until changed; cropping starts as it is now.
                    brightness = -1,
                    cropBorders = settings.cropBorders,
                )
                ),
        )
    } else {
        settings.copy(seriesLooks = settings.seriesLooks - seriesId)
    }

/**
 * Puts the reader options back to their defaults. Per-series looks, reading modes and preferred groups,
 * and everything outside the reader, stay as they are.
 */
fun resetReaderSettings(settings: Settings): Settings {
    val defaults = Settings()
    return settings.copy(
        readerBackground = defaults.readerBackground,
        readerDim = defaults.readerDim,
        autoScrollLevel = defaults.autoScrollLevel,
        volumeKeys = defaults.volumeKeys,
        readerOrientation = defaults.readerOrientation,
        keepScreenOn = defaults.keepScreenOn,
        pageGap = defaults.pageGap,
        prefetchPages = defaults.prefetchPages,
        continuousScroll = defaults.continuousScroll,
        autoHideBars = defaults.autoHideBars,
        tapToScroll = defaults.tapToScroll,
        pageFit = defaults.pageFit,
        spreads = defaults.spreads,
        cropBorders = defaults.cropBorders,
        readerBrightness = defaults.readerBrightness,
        readerFilter = defaults.readerFilter,
        showClock = defaults.showClock,
        defaultReadingMode = defaults.defaultReadingMode,
        pageTransition = defaults.pageTransition,
        tapZoneLayout = defaults.tapZoneLayout,
        invertTapZones = defaults.invertTapZones,
        tapZonesInWebtoon = defaults.tapZonesInWebtoon,
        hapticPageTurn = defaults.hapticPageTurn,
        reduceMotion = defaults.reduceMotion,
        eInkMode = defaults.eInkMode,
        guidedPanels = defaults.guidedPanels,
        smartBackground = defaults.smartBackground,
        oneHandedMode = defaults.oneHandedMode,
    )
}
