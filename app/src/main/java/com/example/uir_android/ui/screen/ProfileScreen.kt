package com.example.uir_android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.uir_android.core.util.formatDisplayName

private val ProfileBackgroundTop = Color(0xFFF6E7D3)
private val ProfileBackgroundBottom = Color(0xFFF4F1EB)
private val ProfileCardColor = Color(0xFFFFFBF6)
private val ProfileAccent = Color(0xFFE58A14)
private val ProfileText = Color(0xFF1E2530)

@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    email: String,
    isSubmitting: Boolean
) {
    val displayName = formatDisplayName(email)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(ProfileBackgroundTop, ProfileBackgroundBottom)
                )
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = ProfileCardColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Личный кабинет",
                    style = MaterialTheme.typography.headlineMedium,
                    color = ProfileText,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = ProfileText,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = email.ifBlank { "Почта пока не указана" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = ProfileText.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ProfileAccent,
                        contentColor = Color.White,
                        disabledContainerColor = ProfileAccent.copy(alpha = 0.92f),
                        disabledContentColor = Color.White
                    )
                ) {
                    Text("Результаты тестирования", textAlign = TextAlign.Center)
                }
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ProfileAccent,
                        contentColor = Color.White,
                        disabledContainerColor = ProfileAccent.copy(alpha = 0.92f),
                        disabledContentColor = Color.White
                    )
                ) {
                    Text("Заказы книг", textAlign = TextAlign.Center)
                }
                OutlinedButton(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting
                ) {
                    Text(if (isSubmitting) "Выход..." else "Выйти из аккаунта")
                }
            }
        }
    }
}
