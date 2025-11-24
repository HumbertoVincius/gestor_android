package com.humberto.gestorfinanceiro.domain.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.humberto.gestorfinanceiro.data.settings.SettingsManager
import com.humberto.gestorfinanceiro.di.Dependencies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            // Inicializar SettingsManager se ainda não foi inicializado
            SettingsManager.initialize(context)
            
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            
            val pendingResult = goAsync()
            
            scope.launch(Dispatchers.IO) {
                try {
                    // Obter número configurado do remetente
                    val configuredSenderNumber = SettingsManager.getSmsSenderNumber()
                    
                    messages?.forEach { sms ->
                        val sender = sms.originatingAddress
                        val body = sms.messageBody
                        
                        Log.d("SmsReceiver", "SMS received from $sender: $body")
                        
                        // Filtrar por número do remetente se configurado
                        if (configuredSenderNumber != null && configuredSenderNumber.isNotBlank()) {
                            // Normalizar números para comparação (remover espaços, caracteres especiais)
                            val normalizedSender = sender?.replace(Regex("[^0-9]"), "")
                            val normalizedConfigured = configuredSenderNumber.replace(Regex("[^0-9]"), "")
                            
                            if (normalizedSender != normalizedConfigured) {
                                Log.d("SmsReceiver", "SMS ignorado: remetente $sender não corresponde ao número configurado ($configuredSenderNumber)")
                                return@forEach
                            }
                            
                            Log.d("SmsReceiver", "SMS aceito: remetente corresponde ao número configurado")
                        }

                        // Buscar subcategorias e categorias do banco antes de processar o SMS
                        Log.d("SmsReceiver", "Buscando subcategorias e categorias do banco...")
                        val subcategories = Dependencies.supabaseRepository.getSubcategoriesList()
                        val categories = Dependencies.supabaseRepository.getCategoriesList()
                        
                        if (subcategories.isEmpty()) {
                            Log.w("SmsReceiver", "Nenhuma subcategoria encontrada no banco. SMS não pode ser processado.")
                            return@forEach
                        }
                        
                        Log.d("SmsReceiver", "Encontradas ${subcategories.size} subcategorias e ${categories.size} categorias")
                        
                        // Processar SMS com LLM usando subcategorias reais
                        val expense = Dependencies.llmService.parseSms(body, subcategories, categories)
                        if (expense != null) {
                            Dependencies.supabaseRepository.saveExpense(expense)
                            Log.d("SmsReceiver", "Despesa salva com sucesso: ${expense.estabelecimento} - ${expense.valor} - Subcategoria: ${expense.subcategoria}")
                        } else {
                            Log.w("SmsReceiver", "Falha ao processar SMS com LLM")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SmsReceiver", "Error processing SMS", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
