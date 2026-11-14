package ai.harness.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// PASTE ZONE: everything between the markers gets replaced later.
// Nothing outside this file needs to know what the app does.
@Composable
fun Screen() {
    // --- BEGIN PASTE ---
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Harness is live")
            Spacer(Modifier.height(8.dp))
            Text("Pipeline verified. Paste the real screen here.")
        }
    }
    // --- END PASTE ---
}
