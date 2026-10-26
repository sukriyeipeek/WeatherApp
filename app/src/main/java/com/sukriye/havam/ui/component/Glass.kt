package com.sukriye.havam.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukriye.havam.model.RiskLevel

private val GlassShape = RoundedCornerShape(28.dp)

// Buzlu cam görünümlü kart: yarı saydam beyaz degrade + ışığı yakalayan kenar çizgisi.
// Arkasındaki shader animasyonu kartın içinden hafifçe görünür.
@Composable
fun GlassCard(modifier: Modifier = Modifier, contentPadding: Dp = 20.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(GlassShape)
            .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.17f), Color.White.copy(alpha = 0.05f))))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.38f), Color.White.copy(alpha = 0.06f))),
                shape = GlassShape
            )
            .padding(contentPadding),
        content = content
    )
}

fun levelColor(level: RiskLevel): Color = when (level) {
    RiskLevel.Excellent -> Color(0xFF50E3C2)
    RiskLevel.Good -> Color(0xFF7ED957)
    RiskLevel.Moderate -> Color(0xFFF5D547)
    RiskLevel.Poor -> Color(0xFFFF9F43)
    RiskLevel.Hazardous -> Color(0xFFFF5E57)
}

// İndeks skorunu gösteren halka; skor değişince yay animasyonla dolar
@Composable
fun ScoreRing(score: Int, color: Color, modifier: Modifier = Modifier, diameter: Dp = 58.dp) {
    val progress by animateFloatAsState(targetValue = score / 100f, animationSpec = tween(900), label = "score")
    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val stroke = 6.dp.toPx()
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(stroke / 2, stroke / 2)
            drawArc(
                color = Color.White.copy(alpha = 0.15f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke)
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(color.copy(alpha = 0.6f), color)),
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Text(text = score.toString(), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}
