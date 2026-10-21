package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AlertBoxBorder
import com.example.ui.theme.BtnBlue
import com.example.ui.theme.BtnDanger
import com.example.ui.theme.BtnPrimary
import com.example.ui.theme.BtnWarn
import com.example.ui.theme.CardBg
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DangerBright
import com.example.ui.theme.HintBg
import com.example.ui.theme.HintText
import com.example.ui.theme.HpDevilEnd
import com.example.ui.theme.HpDevilStart
import com.example.ui.theme.HpPlayerEnd
import com.example.ui.theme.HpPlayerStart
import com.example.ui.theme.LogBg
import com.example.ui.theme.LogText
import com.example.ui.theme.TextAccent
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TipBg
import com.example.ui.theme.TipText
import com.example.ui.theme.WarnBright

enum class ButtonType {
    DEFAULT,
    PRIMARY,
    DANGER,
    WARN
}

@Composable
fun GameButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    type: ButtonType = ButtonType.DEFAULT,
    testTag: String = "game_button"
) {
    val bgColor = when (type) {
        ButtonType.PRIMARY -> BtnPrimary
        ButtonType.DANGER -> BtnDanger
        ButtonType.WARN -> BtnWarn
        ButtonType.DEFAULT -> BtnBlue
    }

    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .testTag(testTag)
            .semantics(mergeDescendants = true) {
                // Accessibility TalkBack
            },
        colors = ButtonDefaults.buttonColors(containerColor = bgColor),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            text = text,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun GameCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = CardBg,
    borderColor: Color = CardBorder,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(2.dp, borderColor, RoundedCornerShape(10.dp)),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(15.dp)
        ) {
            content()
        }
    }
}

@Composable
fun LogBox(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(LogBg)
            .border(2.dp, LogText, RoundedCornerShape(10.dp))
            .padding(14.dp)
            .semantics(mergeDescendants = true) {},
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = LogText,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun TipBox(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(TipBg)
            .border(2.dp, TipText, RoundedCornerShape(10.dp))
            .padding(12.dp)
            .semantics(mergeDescendants = true) {},
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = TipText,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun AlertBox(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BtnDanger)
            .border(2.dp, DangerBright, RoundedCornerShape(10.dp))
            .padding(12.dp)
            .semantics(mergeDescendants = true) {},
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun HintBox(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(HintBg)
            .border(2.dp, BtnPrimary, RoundedCornerShape(10.dp))
            .padding(12.dp)
            .semantics(mergeDescendants = true) {}
    ) {
        Text(
            text = text,
            color = HintText,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
    }
}

@Composable
fun StatBadge(label: String, value: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(BtnBlue)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = "$label: $value",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun HpBar(current: Int, max: Int, isDevil: Boolean, modifier: Modifier = Modifier) {
    val fraction = if (max > 0) (current.toFloat() / max.toFloat()).coerceIn(0f, 1f) else 0f
    val animatedFraction by animateFloatAsState(targetValue = fraction, label = "hp_anim")

    val gradient = if (isDevil) {
        Brush.horizontalGradient(listOf(HpDevilStart, HpDevilEnd))
    } else {
        Brush.horizontalGradient(listOf(HpPlayerStart, HpPlayerEnd))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF101820))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedFraction)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(gradient)
        )
    }
}

@Composable
fun RadarBar(distance: Float, modifier: Modifier = Modifier) {
    val prox = (1.0f - distance / 100.0f).coerceIn(0f, 1f)
    val animatedProx by animateFloatAsState(targetValue = prox, label = "radar_anim")

    val gradient = Brush.horizontalGradient(
        listOf(HpPlayerStart, WarnBright, DangerBright)
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF101820))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedProx)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(gradient)
        )
    }
}
