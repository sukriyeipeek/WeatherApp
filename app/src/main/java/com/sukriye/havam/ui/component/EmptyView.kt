package com.sukriye.havam.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukriye.havam.ui.theme.HavamTheme
import com.sukriye.havam.ui.theme.SoftWhite

/**
 * "Gösterilecek bir şey yok" durumunun görünümü. Boş ekran kullanıcıyı yalnız bırakır
 * (uygulama mı bozuldu, veri mi yok?); bu yüzden her zaman bir başlık ve mümkünse yönlendirme gösterilir.
 */
@Composable
fun EmptyView(title: String, modifier: Modifier = Modifier, message: String? = null) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "🌤️", fontSize = 44.sp)
        Text(text = title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        // Yardımcı mesaj isteğe bağlıdır; `?.let` yalnızca doluysa çizer
        message?.let {
            Text(text = it, color = SoftWhite, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B1426)
@Composable
private fun EmptyViewPreview() {
    HavamTheme {
        EmptyView(title = "İlgi alanı seçilmedi", message = "Yukarıdan en az bir ilgi alanı seç.")
    }
}
