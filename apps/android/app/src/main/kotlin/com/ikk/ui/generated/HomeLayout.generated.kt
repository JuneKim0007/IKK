// GENERATED FROM contract cp_016 — DO NOT EDIT.
package com.ikk.ui.generated

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

private val IkkOvalShape = GenericShape { size, _ ->
    addOval(Rect(0f, 0f, size.width, size.height))
}

// Apex at top centre, base on the bottom edge — the same three points
// the web emits as clip-path: polygon(50% 0%, 100% 100%, 0% 100%).
private val IkkTriangleShape = GenericShape { size, _ ->
    moveTo(size.width / 2f, 0f)
    lineTo(size.width, size.height)
    lineTo(0f, size.height)
    close()
}

@Composable
fun HomeLayoutGenerated(
    modifier: Modifier = Modifier,
    imageContent: @Composable (
        id: String,
        contentDescription: String?,
        contentScale: ContentScale,
        modifier: Modifier,
    ) -> Unit = { _, _, _, imageModifier -> Box(imageModifier) },
) {
    BoxWithConstraints(
        modifier = modifier.aspectRatio(375f / 667f)
            .background(
                Brush.linearGradient(
                    0f to Color(0xFFF49AA8), 1f to Color(0xFFC86DD7),
                    start = Offset(0f, 0f),
                    end = Offset(0.7071f * 1000f, 0.7071f * 1000f),
                ),
            ),
    ) {
        fun Modifier.rel(x: Float, y: Float, w: Float, h: Float) = this
            .offset(x = maxWidth * x, y = maxHeight * y)
            .size(width = maxWidth * w, height = maxHeight * h)

        Box(
            modifier =
                Modifier
                  .rel(0f, 0f, 1f, 1f)
                  .zIndex(0f)
                  .alpha(1f)
                  .background(Color(0xFFF4F0E8), RoundedCornerShape(0.dp)),
        )

        Box(
            modifier =
                Modifier
                  .rel(0.08f, 0.13f, 0.84f, 0.27f)
                  .zIndex(1f)
                  .alpha(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = "One source.\nEvery surface.",
                color = Color(0xFF181B1A),
                fontFamily = FontFamily.SansSerif,
                fontSize = 42.sp,
                lineHeight = 46.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Start,
                maxLines = 2,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }

        Box(
            modifier =
                Modifier
                  .rel(0.08f, 0.45f, 0.84f, 0.008f)
                  .zIndex(2f)
                  .alpha(1f)
                  .border(1.5f.dp, Color(0xFF181B1A), RoundedCornerShape(0.dp)),
        )

        Box(
            modifier =
                Modifier
                  .rel(0.08f, 0.5f, 0.12f, 0.068f)
                  .zIndex(3f)
                  .alpha(1f)
                  .background(Color(0xFFB7E36D), IkkOvalShape),
        )

        Box(
            modifier =
                Modifier
                  .rel(0.84f, 0.07f, 0.08f, 0.048f)
                  .zIndex(4f)
                  .alpha(1f)
                  .background(Color(0xFF3F5CE1), IkkTriangleShape),
        )

        imageContent(
            "n6",
            "CONTRACT → CSS / HTML / KOTLIN",
            ContentScale.Crop,
            Modifier
              .rel(0.08f, 0.61f, 0.84f, 0.29f)
              .zIndex(5f)
              .alpha(1f)
              .clip(RoundedCornerShape(20.dp))
              .background(Color(0xFF181B1A), RoundedCornerShape(20.dp)),
        )

    }
}
