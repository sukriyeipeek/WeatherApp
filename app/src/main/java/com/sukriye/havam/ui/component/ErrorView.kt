package com.sukriye.havam.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukriye.havam.ui.theme.HavamTheme

/**
 * Hata durumunun görünümü. İki kural:
 * 1. Kullanıcıya teknik mesaj gösterilmez ("UnknownHostException" değil, "İnternet bağlantısı yok").
 * 2. Hata ekranı mümkünse bir çıkış yolu sunar: "Tekrar dene".
 *
 * @param onRetry null verilirse buton çizilmez (tekrar denemenin anlamsız olduğu durumlar).
 */
@Composable
fun ErrorView(message: String, modifier: Modifier = Modifier, icon: String = "⚠️", onRetry: (() -> Unit)? = null) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = icon, fontSize = 56.sp)
        Text(text = message, color = Color.White, fontSize = 17.sp, textAlign = TextAlign.Center)
        onRetry?.let {
            Button(onClick = it) { Text("Tekrar dene") }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B1426)
@Composable
private fun ErrorViewPreview() {
    HavamTheme {
        ErrorView(message = "İnternet bağlantısı yok", icon = "📡", onRetry = {})
    }
}
