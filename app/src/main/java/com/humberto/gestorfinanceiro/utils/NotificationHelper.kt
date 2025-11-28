package com.humberto.gestorfinanceiro.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.humberto.gestorfinanceiro.MainActivity
import com.humberto.gestorfinanceiro.R

object NotificationHelper {
    private const val CHANNEL_ID = "expense_notifications"
    private const val CHANNEL_NAME = "Novas Despesas"
    private const val NOTIFICATION_ID = 1001
    
    private const val CHANNEL_ID_GOAL = "goal_alerts"
    private const val CHANNEL_NAME_GOAL = "Alertas de Metas"
    private const val NOTIFICATION_ID_GOAL = 2001

    /**
     * Cria os canais de notificação (necessário para Android 8.0+)
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Canal para novas despesas
            val channelExpense = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificações de novas despesas registradas"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channelExpense)
            
            // Canal para alertas de metas (IMPORTÂNCIA ALTA)
            val channelGoal = NotificationChannel(
                CHANNEL_ID_GOAL,
                CHANNEL_NAME_GOAL,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alertas quando metas atingem 80%"
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(channelGoal)
            
            android.util.Log.d("NotificationHelper", "Canais de notificação criados: expense + goal")
        }
    }

    /**
     * Exibe notificação de nova despesa registrada
     */
    fun showExpenseNotification(
        context: Context,
        valor: Double,
        local: String,
        categoria: String?,
        subcategoria: String?
    ) {
        android.util.Log.d("NotificationHelper", "showExpenseNotification chamada: valor=$valor, local=$local")
        
        // Criar canal se necessário
        createNotificationChannel(context)

        // Formatar valor
        val valorFormatado = String.format("%.2f", valor)

        // Construir texto da notificação
        val categoriaTexto = if (categoria != null && subcategoria != null) {
            "$categoria > $subcategoria"
        } else if (subcategoria != null) {
            subcategoria
        } else if (categoria != null) {
            categoria
        } else {
            "Sem categoria"
        }

        val notificationText = "R$ $valorFormatado no $local - $categoriaTexto"
        
        android.util.Log.d("NotificationHelper", "Texto da notificação: $notificationText")

        // Intent para abrir tela Home (MetasScreen) com categoria expandida
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("app://home?category=${Uri.encode(categoria ?: "")}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            (categoria ?: "expense").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Criar notificação
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app_icon_foreground) // Ícone financeiro personalizado
            .setContentTitle("Nova Despesa Registrada")
            .setContentText(notificationText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        // Exibir notificação
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            android.util.Log.d("NotificationHelper", "Notificação exibida com sucesso")
        } catch (e: SecurityException) {
            // Permissão de notificação não concedida (Android 13+)
            android.util.Log.e("NotificationHelper", "Permissão de notificação não concedida", e)
        } catch (e: Exception) {
            android.util.Log.e("NotificationHelper", "Erro ao exibir notificação", e)
        }
    }
    
    /**
     * Exibe notificação de alerta quando meta atinge uma nova faixa (80%, 90%, 100%+)
     */
    fun showGoalAlertNotification(
        context: Context,
        categoryName: String,
        percentage: Double,
        spent: Double,
        goal: Double,
        title: String = "⚠️ Atenção! Meta quase atingida"
    ) {
        android.util.Log.d("NotificationHelper", "showGoalAlertNotification: $categoryName - $percentage%")
        
        // Criar canal se necessário
        createNotificationChannel(context)
        
        // Formatar valores
        val percentageText = String.format("%.0f%%", percentage)
        val spentText = String.format("R$ %.2f", spent)
        val goalText = String.format("R$ %.2f", goal)
        
        // Intent para abrir tela Home (MetasScreen) com categoria expandida
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("app://home?category=${Uri.encode(categoryName)}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            categoryName.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notificationText = "$categoryName: $percentageText da meta ($spentText de $goalText)"
        
        android.util.Log.d("NotificationHelper", "Notificação: $title - $notificationText")
        
        // Criar notificação com título customizado
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_GOAL)
            .setSmallIcon(R.drawable.ic_app_icon_foreground)
            .setContentTitle(title)
            .setContentText(notificationText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 500, 250, 500))
            .build()
        
        // Exibir notificação
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_GOAL, notification)
            android.util.Log.d("NotificationHelper", "Notificação de meta exibida com sucesso")
        } catch (e: SecurityException) {
            android.util.Log.e("NotificationHelper", "Permissão de notificação não concedida", e)
        } catch (e: Exception) {
            android.util.Log.e("NotificationHelper", "Erro ao exibir notificação de meta", e)
        }
    }
}

