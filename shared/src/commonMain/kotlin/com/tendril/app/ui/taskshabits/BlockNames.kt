package com.tendril.app.ui.taskshabits

import androidx.compose.runtime.Composable
import com.tendril.app.data.habit.HabitBlock
import com.tendril.app.generated.resources.Res
import com.tendril.app.generated.resources.plan_block_afternoon
import com.tendril.app.generated.resources.plan_block_evening
import com.tendril.app.generated.resources.plan_block_midday
import com.tendril.app.generated.resources.plan_block_morning
import com.tendril.app.generated.resources.plan_block_night
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * §6.3 (H1) — a default block the person has not renamed stores no name and is shown in the app's
 * language: its fixed uid picks the string. Null for any other block, which always has its own name.
 */
fun defaultBlockName(uid: String): StringResource? = when (uid) {
    "block-morning" -> Res.string.plan_block_morning
    "block-midday" -> Res.string.plan_block_midday
    "block-afternoon" -> Res.string.plan_block_afternoon
    "block-evening" -> Res.string.plan_block_evening
    "block-night" -> Res.string.plan_block_night
    else -> null
}

/** [defaultBlockName]'s resource key, as plain text for a check that has no resource classes to hand. */
fun defaultBlockNameKey(uid: String): String? = defaultBlockName(uid)?.key

/** What a block is called on screen: the person's name for it, else its default in the app's language. */
@Composable
fun blockName(block: HabitBlock): String = block.name ?: defaultBlockName(block.uid)?.let { stringResource(it) } ?: ""
