// GENERATED FROM contract cp_005 — DO NOT EDIT.
package com.ikk.ui.generated

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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
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
        modifier = modifier.aspectRatio(375f / 667f),
    ) {
        fun Modifier.rel(x: Float, y: Float, w: Float, h: Float) = this
            .offset(x = maxWidth * x, y = maxHeight * y)
            .size(width = maxWidth * w, height = maxHeight * h)

        Box(
            modifier =
                Modifier
                  .rel(0f, 0f, 1f, 0.27f)
                  .zIndex(0f)
                  .alpha(1f)
                  .background(Color(0xFF65558F), RoundedCornerShape(0.dp)),
        )

        Box(
            modifier =
                Modifier
                  .rel(0.075f, 0.111f, 0.693f, 0.051f)
                  .zIndex(1f)
                  .alpha(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = "Good morning",
                color = Color(0xFFFFFFFF),
                fontFamily = FontFamily.SansSerif,
                fontSize = 26.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Start,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }

        Box(
            modifier =
                Modifier
                  .rel(0.075f, 0.168f, 0.747f, 0.033f)
                  .zIndex(2f)
                  .alpha(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = "Three things need you today.",
                color = Color(0xFFE5DEF5),
                fontFamily = FontFamily.SansSerif,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Start,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }

        Box(
            modifier =
                Modifier
                  .rel(0.064f, 0.318f, 0.872f, 0.165f)
                  .zIndex(3f)
                  .alpha(1f)
                  .background(Color(0xFFFFFFFF), RoundedCornerShape(14.dp))
                  .border(1.dp, Color(0xFFDAD5E2), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = "Draft review\nDue 4:00 PM",
                color = Color(0xFF1B1D1C),
                fontFamily = FontFamily.SansSerif,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Start,
                maxLines = 2,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }

        imageContent(
            id = "n5",
            contentDescription = "Cover",
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                  .rel(0.064f, 0.513f, 0.872f, 0.225f)
                  .zIndex(4f)
                  .alpha(1f)
                  .clip(RoundedCornerShape(14.dp))
                  .background(Color(0xFFDFDAE6), RoundedCornerShape(14.dp)),
        )

        Box(
            modifier =
                Modifier
                  .rel(0.784f, 0.267f, 0.149f, 0.084f)
                  .zIndex(5f)
                  .alpha(1f)
                  .background(Color(0xFF2E8B74), IkkOvalShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "3",
                color = Color(0xFFFFFFFF),
                fontFamily = FontFamily.SansSerif,
                fontSize = 22.sp,
                lineHeight = 29.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }

        Box(
            modifier =
                Modifier
                  .rel(0.064f, 0.78f, 0.872f, 0.075f)
                  .zIndex(6f)
                  .alpha(1f)
                  .background(Color(0xFF4C6FBF), RoundedCornerShape(25.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Open inbox",
                color = Color(0xFFFFFFFF),
                fontFamily = FontFamily.SansSerif,
                fontSize = 16.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }

    }
}
