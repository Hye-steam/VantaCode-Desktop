/*
 * VantaCode Desktop — entry point.
 *
 * Port of AiCode (https://github.com/jieapi/AiCode), GPL-3.0, to the Windows desktop.
 * Copyright (C) 2026 VantaCode contributors. See LICENSE and NOTICE.
 */
package com.vantacode

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import java.awt.Dimension

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "VantaCode",
    ) {
        window.minimumSize = Dimension(960, 640)
        MaterialTheme {
            Surface {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("VantaCode Desktop — port in progress")
                }
            }
        }
    }
}
