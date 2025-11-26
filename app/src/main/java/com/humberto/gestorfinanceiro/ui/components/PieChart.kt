package com.humberto.gestorfinanceiro.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.humberto.gestorfinanceiro.ui.home.formatCurrency
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

data class PieChartData(
    val label: String,
    val value: Double,
    val color: Color
)

@Composable
fun PieChart(
    data: List<PieChartData>,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Sem dados para exibir",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        return
    }
    
    val total = data.sumOf { it.value }
    
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Gráfico de pizza
        Box(
            modifier = Modifier
                .size(240.dp)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasSize = size.minDimension
                val radius = canvasSize / 2
                val strokeWidth = radius * 0.4f
                val center = Offset(size.width / 2, size.height / 2)
                
                var startAngle = -90f
                
                data.forEach { item ->
                    val percentage = (item.value / total).toFloat()
                    val sweepAngle = 360f * percentage
                    
                    // Desenhar fatia
                    drawArc(
                        color = item.color,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = Offset(
                            center.x - radius + strokeWidth / 2,
                            center.y - radius + strokeWidth / 2
                        ),
                        size = Size(
                            (radius - strokeWidth / 2) * 2,
                            (radius - strokeWidth / 2) * 2
                        ),
                        style = Stroke(width = strokeWidth)
                    )
                    
                    startAngle += sweepAngle
                }
            }
            
            // Texto no centro
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Total",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    text = formatCurrency(total),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Legenda
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            data.forEach { item ->
                val percentage = (item.value / total * 100)
                PieChartLegendItem(
                    color = item.color,
                    label = item.label,
                    value = item.value,
                    percentage = percentage
                )
            }
        }
    }
}

@Composable
fun PieChartLegendItem(
    color: Color,
    label: String,
    value: Double,
    percentage: Double
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Indicador de cor
            Surface(
                modifier = Modifier.size(16.dp),
                shape = CircleShape,
                color = color
            ) {}
            
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        
        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = formatCurrency(value),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = String.format("%.1f%%", percentage),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

// Paleta de cores para o gráfico
val pieChartColors = listOf(
    Color(0xFF2196F3), // Azul
    Color(0xFF4CAF50), // Verde
    Color(0xFFF44336), // Vermelho
    Color(0xFFFF9800), // Laranja
    Color(0xFF9C27B0), // Roxo
    Color(0xFF00BCD4), // Ciano
    Color(0xFFFFEB3B), // Amarelo
    Color(0xFF795548), // Marrom
    Color(0xFF607D8B), // Azul-cinza
    Color(0xFFE91E63), // Pink
)

