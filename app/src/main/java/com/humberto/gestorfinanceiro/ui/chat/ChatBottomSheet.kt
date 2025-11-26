package com.humberto.gestorfinanceiro.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.humberto.gestorfinanceiro.data.model.Expense

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatBottomSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onExpenseRegistered: (Expense) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    
    // Expandir automaticamente quando abrir
    LaunchedEffect(isVisible) {
        if (isVisible) {
            kotlinx.coroutines.delay(50) // Pequeno delay para garantir que o sheet foi criado
            sheetState.expand()
        }
    }
    
    if (isVisible) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            modifier = Modifier.fillMaxSize(),
            windowInsets = WindowInsets.ime,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .padding(vertical = 12.dp)
                ) {
                    Divider(
                        modifier = Modifier.fillMaxWidth(),
                        thickness = 4.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }
        ) {
            ChatAgentScreen(
                onExpenseRegistered = onExpenseRegistered,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

