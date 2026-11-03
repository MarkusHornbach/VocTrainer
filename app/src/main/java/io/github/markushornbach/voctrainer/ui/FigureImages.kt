package io.github.markushornbach.voctrainer.ui

import androidx.annotation.DrawableRes
import io.github.markushornbach.voctrainer.R
import io.github.markushornbach.voctrainer.learning.FigureKind

/**
 * Images of the figures by stage (0-10). Should a stage ever lack its own drawable, the highest
 * existing one is shown instead.
 */
object FigureImages {
    private val images: Map<FigureKind, List<Int>> = mapOf(
        FigureKind.DRAGON to listOf(
            R.drawable.figure_dragon_0, R.drawable.figure_dragon_1, R.drawable.figure_dragon_2,
            R.drawable.figure_dragon_3, R.drawable.figure_dragon_4, R.drawable.figure_dragon_5,
            R.drawable.figure_dragon_6, R.drawable.figure_dragon_7, R.drawable.figure_dragon_8,
            R.drawable.figure_dragon_9, R.drawable.figure_dragon_10
        ),
        FigureKind.MONSTER to listOf(
            R.drawable.figure_monster_0, R.drawable.figure_monster_1, R.drawable.figure_monster_2,
            R.drawable.figure_monster_3, R.drawable.figure_monster_4, R.drawable.figure_monster_5,
            R.drawable.figure_monster_6, R.drawable.figure_monster_7, R.drawable.figure_monster_8,
            R.drawable.figure_monster_9, R.drawable.figure_monster_10
        ),
        FigureKind.FIGHTER to listOf(
            R.drawable.figure_fighter_0, R.drawable.figure_fighter_1, R.drawable.figure_fighter_2,
            R.drawable.figure_fighter_3, R.drawable.figure_fighter_4, R.drawable.figure_fighter_5,
            R.drawable.figure_fighter_6, R.drawable.figure_fighter_7, R.drawable.figure_fighter_8,
            R.drawable.figure_fighter_9, R.drawable.figure_fighter_10
        )
    )

    /** Number of stages with their own image for [kind]. */
    fun availableStages(kind: FigureKind): Int = images.getValue(kind).size

    @DrawableRes
    fun forStage(kind: FigureKind, stage: Int): Int {
        val list = images.getValue(kind)
        return list[stage.coerceIn(0, list.lastIndex)]
    }

    /** Shown on the choice cards: more attractive than an egg. */
    const val CHOICE_STAGE = 2
}
