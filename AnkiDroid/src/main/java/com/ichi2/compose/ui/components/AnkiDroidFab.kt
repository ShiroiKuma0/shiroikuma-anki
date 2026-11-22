// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.compose.ui.components

import androidx.compose.foundation.border
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ichi2.compose.theme.ankiColors

/**
 * Extended FAB tinted with the app's fabNormalColor accent, like the View FABs.
 * Prefer this over the bare Material3 one, which renders baseline purple.
 */
@Composable
fun AnkiDroidExtendedFab(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    text: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        // Fork: an outlined ring, the ground showing through
        modifier =
            modifier.border(
                ForkPills.border(MaterialTheme.ankiColors.onFab, main = true),
                FloatingActionButtonDefaults.extendedFabShape,
            ),
        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
        containerColor = MaterialTheme.ankiColors.fabContainer,
        contentColor = MaterialTheme.ankiColors.onFab,
        icon = icon,
        text = text,
    )
}
