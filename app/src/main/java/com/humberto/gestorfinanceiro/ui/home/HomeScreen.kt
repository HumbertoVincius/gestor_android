package com.humberto.gestorfinanceiro.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.humberto.gestorfinanceiro.data.supabase.TransactionDto
import com.humberto.gestorfinanceiro.di.Dependencies
import kotlinx.coroutines.launch

@Composable
fun HomeScreen() {
    val scope = rememberCoroutineScope()
    var transactions by remember { mutableStateOf<List<TransactionDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        transactions = Dependencies.supabaseRepository.getTransactions()
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            text = "Gastos Mensais",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Total Gasto",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                val total = transactions.sumOf { it.amount }
                Text(
                    text = "R$ %.2f".format(total),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(transactions) { transaction ->
                    TransactionItem(transaction)
                }
            }
        }
    }
}

@Composable
fun TransactionItem(transaction: TransactionDto) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = transaction.merchant,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = transaction.category,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            Text(
                text = "R$ %.2f".format(transaction.amount),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
