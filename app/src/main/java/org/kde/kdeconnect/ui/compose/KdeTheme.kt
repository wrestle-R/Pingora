/*
 * SPDX-FileCopyrightText: 2024 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
fun KdeTheme(context : Context, content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) {
        darkColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF80E1C1),
            onPrimary = androidx.compose.ui.graphics.Color(0xFF092D26),
            background = androidx.compose.ui.graphics.Color(0xFF080B0E),
            surface = androidx.compose.ui.graphics.Color(0xFF11171C),
            surfaceVariant = androidx.compose.ui.graphics.Color(0xFF19232B),
        )
    } else {
        lightColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF087C72),
            background = androidx.compose.ui.graphics.Color(0xFFF5F8F7),
            surface = androidx.compose.ui.graphics.Color(0xFFF5F8F7),
        )
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
