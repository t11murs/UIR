package com.example.uir_android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.uir_android.core.util.formatDisplayName

private data class MenuTile(
    val title: String,
    val icon: @Composable () -> Unit,
    val onClickKey: String
)

private data class NewsCardData(
    val title: String,
    val description: String
)

private val homeNews = listOf(
    NewsCardData(
        title = "Обновление практикума",
        description = "Исправлены найденные ошибки и неточности. PDF-версия будет добавлена в приложение отдельным разделом."
    ),
    NewsCardData(
        title = "Вопросы к экзамену",
        description = "Раздел с материалами для подготовки появится здесь отдельными карточками и быстрыми ссылками."
    ),
    NewsCardData(
        title = "Видеозаписи лекций",
        description = "Здесь будет собрана подборка записей и дополнительных материалов по курсу."
    )
)

private val menuTiles = listOf(
    MenuTile(
        title = "Эмулятор машины Тьюринга",
        icon = { Icon(Icons.Outlined.Calculate, contentDescription = null) },
        onClickKey = "emulator"
    ),
    MenuTile(
        title = "Новости",
        icon = { Icon(Icons.AutoMirrored.Outlined.Article, contentDescription = null) },
        onClickKey = "news"
    )
)

private val HomeBackgroundTop = Color(0xFFF6E7D3)
private val HomeBackgroundBottom = Color(0xFFF4F1EB)
private val HeroAccent = Color(0xFFE58A14)
private val HeroDeep = Color(0xFF0F7A7A)
private val HeroText = Color(0xFF1E2530)
private val CardSurface = Color(0xFFFFFBF6)

@Composable
fun HomeScreen(
    email: String
) {
    val displayName = formatDisplayName(email)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(HomeBackgroundTop, HomeBackgroundBottom)
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                HomeHeroCard(displayName = displayName)
            }

            item {
                HomeInfoCard()
            }

            item {
                Text(
                    text = "Новости",
                    style = MaterialTheme.typography.titleLarge,
                    color = HeroText,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            items(homeNews) { item ->
                NewsCard(item = item)
            }
        }
    }
}

@Composable
fun MenuScreen(
    onOpenEmulator: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(HomeBackgroundTop, HomeBackgroundBottom)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Меню",
                    style = MaterialTheme.typography.headlineMedium,
                    color = HeroText
                )
                Text(
                    text = "Выберите нужный раздел.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = HeroText.copy(alpha = 0.74f)
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                gridItems(menuTiles) { tile ->
                    MenuTileCard(
                        tile = tile,
                        onClick = {
                            if (tile.onClickKey == "emulator") {
                                onOpenEmulator()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHeroCard(displayName: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(78.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(HeroDeep, Color(0xFF28A39A))
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }

            Text(
                text = "Algorithms Theory",
                style = MaterialTheme.typography.headlineMedium,
                color = HeroAccent
            )

            Text(
                text = "Добро пожаловать, $displayName!",
                style = MaterialTheme.typography.titleLarge,
                color = HeroText,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Система поддержки обучения по курсу \"Теория алгоритмов\" объединяет материалы, эмуляторы и рабочие разделы курса в одном приложении.",
                style = MaterialTheme.typography.bodyLarge,
                color = HeroText.copy(alpha = 0.86f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun HomeInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Возможности",
                style = MaterialTheme.typography.titleLarge,
                color = HeroText
            )
            FeatureLine("Проходить тренировочные и контрольные тесты")
            FeatureLine("Читать лекции и готовиться к экзамену")
            FeatureLine("Работать с эмуляторами по темам курса")
            FeatureLine("Отслеживать результаты в личном кабинете")
            Text(
                text = "Описание возникающих при работе проблем, а также замечания и предложения, просим направлять по нашему электронному адресу algorithms.theory@yandex.ru",
                style = MaterialTheme.typography.bodyLarge,
                color = HeroText.copy(alpha = 0.82f)
            )
        }
    }
}

@Composable
private fun FeatureLine(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .size(7.dp)
                .background(HeroAccent, CircleShape)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = HeroText
        )
    }
}

@Composable
private fun NewsCard(item: NewsCardData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HeroAccent)
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Text(
                    text = item.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge
                )
            }

            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = HeroText.copy(alpha = 0.82f)
                )
            }
        }
    }
}

@Composable
private fun MenuTileCard(
    tile: MenuTile,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(
                        color = HeroDeep,
                        shape = RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                tile.icon()
            }

            Text(
                text = tile.title,
                style = MaterialTheme.typography.titleLarge,
                color = HeroText
            )
        }
    }
}
