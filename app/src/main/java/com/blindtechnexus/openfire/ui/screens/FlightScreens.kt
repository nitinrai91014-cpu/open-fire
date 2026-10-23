package com.blindtechnexus.openfire.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blindtechnexus.openfire.ui.components.GameCard
import com.blindtechnexus.openfire.ui.components.HintBox
import com.blindtechnexus.openfire.ui.components.LogBox
import com.blindtechnexus.openfire.viewmodel.GameViewModel

/**
 * Gesture-only flight sequence. Once Play Game is pressed there are no button
 * controls: swipe up advances and swipe down returns to the main menu.
 */
@Composable
fun ReaderScreen(viewModel: GameViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        GameCard {
            Text(
                text = viewModel.t("readerTitle"),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = viewModel.t("readerMsg"),
                fontSize = 16.sp,
                color = Color.White,
                lineHeight = 22.sp
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        HintBox(text = "${viewModel.t("readerUp")}\n${viewModel.t("readerDown")}")
    }
}

@Composable
fun JumpScreen(viewModel: GameViewModel) {
    val gameState by viewModel.gameState.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        GameCard {
            Text(text = viewModel.t("jumpMsg"), fontSize = 16.sp, color = Color.White, lineHeight = 22.sp)
        }
        Spacer(modifier = Modifier.height(12.dp))
        LogBox(text = gameState.log)
        Spacer(modifier = Modifier.height(12.dp))
        HintBox(text = "${viewModel.t("readerUp")} / ${viewModel.t("readerDown")}")
    }
}

@Composable
fun ParaScreen(viewModel: GameViewModel) {
    val gameState by viewModel.gameState.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        GameCard {
            Text(text = viewModel.t("paraMsg"), fontSize = 16.sp, color = Color.White, lineHeight = 22.sp)
        }
        Spacer(modifier = Modifier.height(12.dp))
        LogBox(text = gameState.log)
        Spacer(modifier = Modifier.height(12.dp))
        HintBox(text = "${viewModel.t("readerUp")} / ${viewModel.t("readerDown")}")
    }
}

@Composable
fun LandScreen(viewModel: GameViewModel) {
    val gameState by viewModel.gameState.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        GameCard {
            Text(text = viewModel.t("landMsg"), fontSize = 16.sp, color = Color.White, lineHeight = 22.sp)
        }
        Spacer(modifier = Modifier.height(12.dp))
        LogBox(text = gameState.log)
        Spacer(modifier = Modifier.height(12.dp))
        HintBox(text = "${viewModel.t("readerUp")} / ${viewModel.t("readerDown")}")
    }
}
