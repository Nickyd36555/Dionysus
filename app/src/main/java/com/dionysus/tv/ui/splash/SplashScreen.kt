package com.dionysus.tv.ui.splash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.dionysus.tv.R
import kotlinx.coroutines.delay

/**
 * Branded launch screen: the DIONYSUS STREAMING wordmark fades in over black,
 * holds briefly, then hands off to the home screen.
 */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = 900),
        label = "splashAlpha",
    )

    LaunchedEffect(Unit) {
        shown = true
        delay(1900)
        onDone()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.logo_wordmark),
            contentDescription = "Dionysus Streaming",
            modifier = Modifier
                .fillMaxWidth(0.68f)
                .alpha(alpha),
        )
    }
}
