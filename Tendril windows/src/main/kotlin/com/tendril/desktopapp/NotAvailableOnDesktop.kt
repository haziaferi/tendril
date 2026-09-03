package com.tendril.desktopapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Milestone 3 ports theming/nav/block editor, not these screens yet (tendril-windows-spec.md
 * §6 step 3's own scope note) — each is Android-integration-heavy (AlarmManager, Calendar
 * Provider, Google Calendar, Notion import, BiometricPrompt) and out of scope for this pass. */
@Composable
fun NotAvailableOnDesktop(feature: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("$feature isn't available in this desktop build yet", style = MaterialTheme.typography.bodyLarge)
    }
}
