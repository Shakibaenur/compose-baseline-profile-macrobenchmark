package com.shakibaenur.macrobenchmarkstartupoptimizer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallTopAppBar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shakibaenur.macrobenchmarkstartupoptimizer.ui.theme.MacrobenchmarkStartupOptimizerTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MacrobenchmarkStartupOptimizerTheme {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier
                    .fillMaxSize()
                    .semantics { testTagsAsResourceId = true }) {
                    val navController = rememberNavController()

                    // Make testTag work as resource-id for UIAutomator/Macrobenchmark
                    NavHost(
                        navController = navController,
                        startDestination = "start",
                        modifier = Modifier.semantics { testTagsAsResourceId = true }
                    ) {
                        composable("start") {
                            StartScreen(
                                onItemClick = { text -> navController.navigate("detail/$text") }
                            )
                        }
                        composable(
                            route = "detail/{text}",
                            arguments = listOf(
                                navArgument("text") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val text = backStackEntry.arguments?.getString("text") ?: "Default"
                            DetailScreen(text = text)
                        }
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun StartScreen(
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var counter by rememberSaveable { mutableIntStateOf(0) }

    val bg = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
        )
    )

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .semantics { testTagsAsResourceId = true },
        topBar = {
            SmallTopAppBar(
                title = { Text("Macrobenchmark Startup Optimizer", fontWeight = FontWeight.SemiBold) }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                modifier = Modifier.testTag("fab_add_item").semantics { testTagsAsResourceId = true },
                onClick = { counter++ },
                icon = { Icon(Icons.Rounded.Add, contentDescription = "Add") },
                text = { Text("Add Item") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp)
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(bg)
                .padding(inner)
        ) {
            // Helpful hint row
            AssistChipRow(counter = counter)

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("item_list"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Animate items as they appear; keep a stable key
                itemsIndexed((0 until counter).toList(), key = { _, i -> "item_$i" }) { index, i ->
                    val text = "Element $i"
                    AnimatedListCard(
                        index = index,
                        title = text,
                        subtitle = "Tap to open details",
                        onClick = { onItemClick(text) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistChipRow(counter: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Click the button to add animated cards",
            style = MaterialTheme.typography.bodyMedium
        )
        AssistChip(
            onClick = {},
            label = { Text("$counter added") },
            enabled = false
        )
    }
}

/**
 * A pretty card with a staggered enter animation + subtle press feel.
 */
@Composable
private fun AnimatedListCard(
    index: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    // Staggered fade/slide-in based on index
    val delay = (50 * index).coerceAtMost(600)
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(300, delay, FastOutSlowInEasing)) +
                slideInVertically(
                    animationSpec = tween(350, delay, FastOutSlowInEasing),
                    initialOffsetY = { it / 2 } // slide up from 50% height
                )
    ) {
        // Press feedback: on press, tweak alpha just a touch
        var pressed by remember { mutableStateOf(false) }
        val alpha by animateFloatAsState(
            targetValue = if (pressed) 0.85f else 1f,
            animationSpec = tween(120),
            label = "pressAlpha"
        )

        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(alpha)
                .clickable(
                    onClick = onClick,
                    onClickLabel = "Open $title",
                    // onClickCapture = { pressed = true }
                )
                .padding(0.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Reset pressed after click animation frame
        LaunchedEffect(Unit) {
            if (pressed) pressed = false
        }
    }
}


@Composable
private fun DetailScreen(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("detail_screen"),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "Detail: $text")
    }
}

@Preview(showBackground = true)
@Composable
private fun StartScreenPreview() {
    MacrobenchmarkStartupOptimizerTheme {
        StartScreen(onItemClick = {})
    }
}
