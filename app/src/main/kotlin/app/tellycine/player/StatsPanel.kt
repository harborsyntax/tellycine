package app.tellycine.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** "Stats for nerds" over the picture, refreshed every second from [rows]. */
@Composable
fun StatsPanel(rows: () -> List<Pair<String, String>>, modifier: Modifier = Modifier) {
    val current by produceState(rows()) {
        while (true) {
            delay(1_000)
            value = rows()
        }
    }
    val style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp, color = Color.White)
    Column(
        modifier
            .widthIn(max = 520.dp)
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        current.forEach { (label, value) ->
            Row {
                Text(label, style = style.copy(fontWeight = FontWeight.Bold, color = Color(0xFFFFC56E)), modifier = Modifier.width(112.dp))
                Text(value, style = style)
            }
        }
    }
}
