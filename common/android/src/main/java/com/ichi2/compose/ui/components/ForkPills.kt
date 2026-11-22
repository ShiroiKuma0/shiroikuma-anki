// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Fork: the black-yellow button and switch of the Compose screens. The colours are the theme's
 * (which the fork points at its colour roles), so nothing here names a colour.
 */
object ForkPills {
    val MainStroke: Dp = 3.dp
    val RegularStroke: Dp = 1.25.dp

    /** The frame of a pill in [ink]: 3 dp for the main action of a block, 1.25 dp otherwise */
    fun border(
        ink: Color,
        main: Boolean = false,
    ): BorderStroke = BorderStroke(if (main) MainStroke else RegularStroke, ink)
}

/**
 * An outlined pill: fully round, a frame, the ground showing through, never filled. The main
 * action of a block is 3 dp and bold; every other is 1.25 dp. [ink] colours frame and label.
 */
@Composable
fun ForkPillButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    main: Boolean = false,
    enabled: Boolean = true,
    ink: Color = MaterialTheme.colorScheme.primary,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = CircleShape,
        border = ForkPills.border(if (enabled) ink else ink.copy(alpha = 0.38f), main),
        colors =
            ButtonDefaults.outlinedButtonColors(
                containerColor = Color.Transparent,
                contentColor = ink,
                disabledContentColor = ink.copy(alpha = 0.38f),
            ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
    ) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(fontWeight = if (main) FontWeight.Bold else FontWeight.Normal)) {
            content()
        }
    }
}

/**
 * The outlined switch: a 2 dp round outline with the ground showing through and a solid dot
 * inside it, right when on and left when off; the states differ in colour only. A null
 * [onCheckedChange] draws it without making it a control, as Material's Switch does.
 */
@Composable
fun ForkSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val position by animateFloatAsState(if (checked) 1f else 0f, label = "forkSwitch")
    val on = MaterialTheme.colorScheme.primary
    val off = MaterialTheme.colorScheme.outlineVariant
    val color = (if (checked) on else off).let { if (enabled) it else it.copy(alpha = 0.38f) }
    val control =
        if (onCheckedChange != null) {
            Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
        } else {
            Modifier
        }
    Canvas(modifier.then(control).size(width = 52.dp, height = 28.dp)) {
        val stroke = 2.dp.toPx()
        val inset = stroke / 2f
        drawRoundRect(
            color = color,
            topLeft = Offset(inset, inset),
            size =
                androidx.compose.ui.geometry
                    .Size(size.width - stroke, size.height - stroke),
            cornerRadius = CornerRadius((size.height - stroke) / 2f),
            style = Stroke(stroke),
        )
        val from = size.height / 2f
        val to = size.width - size.height / 2f
        drawCircle(color, radius = size.height * 0.23f, center = Offset(from + (to - from) * position, size.height / 2f))
    }
}
