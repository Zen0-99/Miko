package eu.kanade.presentation.library

/**
 * Threshold above which the library grid switches to performance mode.
 *
 * In performance mode, expensive visual effects (shadows, glow contours,
 * stacked cover shadows) are reduced or disabled to keep large grids smooth.
 *
 * Ported from Tadami.
 */
internal const val AURORA_LARGE_GRID_PERFORMANCE_THRESHOLD = 500
