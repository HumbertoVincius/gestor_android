package com.humberto.gestorfinanceiro.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.humberto.gestorfinanceiro.R

object NotificationHelper {
    private const val CHANNEL_ID = "expense_notifications"
    private const val CHANNEL_NAME = "Novas Despesas"
    private const val NOTIFICATION_ID = 1001

    /**
     * Cria o canal de notificação (necessário para Android 8.0+)
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificações de novas despesas registradas"
                enableVibration(true)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
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
        // Criar canal se necessário
        createNotificationChannel(context)

        // Formatar valor
        val valorFormatado = String.format("%.2f", valor)

        // Construir texto da notificação
        val categoriaTexto = if (categoria != null && subcategoria != null) {
            "$categoria/$subcategoria"
        } else if (subcategoria != null) {
            subcategoria
        } else if (categoria != null) {
            categoria
        } else {
            "Sem categoria"
        }

        val notificationText = "R$ $valorFormatado no $local - $categoriaTexto"

        // Criar notificação
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Usando ícone do launcher
            .setContentTitle("Nova Despesa Registrada")
            .setContentText(notificationText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        // Exibir notificação
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Permissão de notificação não concedida (Android 13+)
            android.util.Log.e("NotificationHelper", "Permissão de notificação não concedida", e)
        }
    }
}

