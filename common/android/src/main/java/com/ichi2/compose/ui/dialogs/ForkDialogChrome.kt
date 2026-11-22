// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.dialogs

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Fork: the colours of a Compose dialog, as ARGB ints. They are the app's dialog colour
 * roles; this module cannot see the app's settings, so the app supplies them through
 * [ForkDialogChrome.palette].
 */
data class ForkDialogPalette(
    val ground: Int,
    val frame: Int,
    val title: Int,
    val text: Int,
    val buttonText: Int,
    val buttonFrame: Int,
    val delete: Int,
)

/** Fork: the black-yellow look of the Compose dialogs; inert until the app sets [palette] */
object ForkDialogChrome {
    @Volatile
    var palette: ((Context) -> ForkDialogPalette)? = null

    @Composable
    fun current(): ForkDialogPalette? {
        val context = LocalContext.current
        val provider = palette ?: return null
        return remember(context) { provider(context) }
    }

    /** [base] with every entry a dialog reads replaced by its role */
    fun scheme(
        base: ColorScheme,
        p: ForkDialogPalette,
    ): ColorScheme =
        base.copy(
            surface = Color(p.ground),
            surfaceContainer = Color(p.ground),
            surfaceContainerHigh = Color(p.ground),
            surfaceContainerHighest = Color(p.ground),
            surfaceTint = Color(p.ground),
            onSurface = Color(p.title),
            onSurfaceVariant = Color(p.text),
            primary = Color(p.buttonText),
            outline = Color(p.frame),
            error = Color(p.delete),
        )

    /** The thin frame round the dialog's card */
    @Composable
    fun Framed(
        p: ForkDialogPalette?,
        content: @Composable () -> Unit,
    ) {
        if (p == null) {
            content()
        } else {
            Box(Modifier.border(1.25.dp, Color(p.frame), AlertDialogDefaults.shape), propagateMinConstraints = true) { content() }
        }
    }

    /** An outlined pill: 3 dp and bold for the main action, 1.25 dp for any other */
    @Composable
    fun Pill(
        text: String,
        main: Boolean,
        enabled: Boolean,
        p: ForkDialogPalette,
        onClick: () -> Unit,
    ) {
        val alpha = if (enabled) 1f else 0.38f
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            border = BorderStroke(if (main) 3.dp else 1.25.dp, Color(p.buttonFrame).copy(alpha = alpha)),
            colors =
                ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color(p.buttonText),
                    disabledContentColor = Color(p.buttonText).copy(alpha = 0.38f),
                ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) { Text(text, fontWeight = if (main) FontWeight.Bold else FontWeight.Normal) }
    }
}

/** Fork: wraps a dialog's content in the fork's colours and frame, when the app set them */
@Composable
fun ForkDialogTheme(content: @Composable () -> Unit) {
    val p = ForkDialogChrome.current()
    if (p == null) {
        content()
    } else {
        MaterialTheme(colorScheme = ForkDialogChrome.scheme(MaterialTheme.colorScheme, p)) {
            ForkDialogChrome.Framed(p, content)
        }
    }
}

/** Fork: the dialog colour roles without the frame, for a stock Material dialog called inside */
@Composable
fun ForkDialogScheme(content: @Composable () -> Unit) {
    val p = ForkDialogChrome.current()
    if (p == null) {
        content()
    } else {
        MaterialTheme(colorScheme = ForkDialogChrome.scheme(MaterialTheme.colorScheme, p), content = content)
    }
}

/** Fork: a dialog button — the fork's pill, or upstream's text button while no colours are set */
@Composable
fun ForkDialogButton(
    text: String,
    main: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val p = ForkDialogChrome.current()
    if (p == null) {
        TextButton(onClick = onClick, enabled = enabled) { Text(text) }
    } else {
        ForkDialogChrome.Pill(text, main, enabled, p, onClick)
    }
}
