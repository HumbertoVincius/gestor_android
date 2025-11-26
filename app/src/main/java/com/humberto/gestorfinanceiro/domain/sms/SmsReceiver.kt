package com.humberto.gestorfinanceiro.domain.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.humberto.gestorfinanceiro.data.log.ActivityLogManager
import com.humberto.gestorfinanceiro.data.settings.SettingsManager
import com.humberto.gestorfinanceiro.di.Dependencies
import com.humberto.gestorfinanceiro.utils.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

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
                        
                        // Log de captura de SMS
                        val smsData = JSONObject().apply {
                            put("sender", sender ?: "unknown")
                            put("body", body)
                            put("configured_sender", configuredSenderNumber ?: "")
                        }.toString()
                        ActivityLogManager.addLog(
                            tipoAtividade = "sms_capture",
                            descricao = "SMS capturado do remetente: $sender",
                            dados = smsData,
                            sucesso = true
                        )
                        
                        // Filtrar por número do remetente se configurado
                        if (configuredSenderNumber != null && configuredSenderNumber.isNotBlank()) {
                            // Normalizar números para comparação (remover espaços, caracteres especiais)
                            val normalizedSender = sender?.replace(Regex("[^0-9]"), "")
                            val normalizedConfigured = configuredSenderNumber.replace(Regex("[^0-9]"), "")
                            
                            if (normalizedSender != normalizedConfigured) {
                                Log.d("SmsReceiver", "SMS ignorado: remetente $sender não corresponde ao número configurado ($configuredSenderNumber)")
                                
                                // Log de SMS ignorado
                                ActivityLogManager.addLog(
                                    tipoAtividade = "sms_capture",
                                    descricao = "SMS ignorado: remetente não corresponde ao número configurado",
                                    dados = smsData,
                                    sucesso = false,
                                    erro = "Remetente $sender não corresponde ao número configurado ($configuredSenderNumber)"
                                )
                                
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
                            
                            // Log de erro: sem subcategorias
                            ActivityLogManager.addLog(
                                tipoAtividade = "sms_capture",
                                descricao = "SMS não processado: nenhuma subcategoria encontrada no banco",
                                dados = smsData,
                                sucesso = false,
                                erro = "Nenhuma subcategoria encontrada no banco de dados"
                            )
                            
                            return@forEach
                        }
                        
                        Log.d("SmsReceiver", "Encontradas ${subcategories.size} subcategorias e ${categories.size} categorias")
                        
                        // Processar SMS com LLM usando subcategorias reais
                        val expense = Dependencies.llmService.parseSms(body, subcategories, categories)
                        if (expense != null) {
                            Dependencies.supabaseRepository.saveExpense(expense, context)
                            Log.d("SmsReceiver", "Despesa salva com sucesso: ${expense.local} - R$ ${expense.valor} - Subcategoria: ${expense.subcategoria}")
                            
                            // Exibir notificação
                            NotificationHelper.showExpenseNotification(
                                context = context,
                                valor = expense.valor ?: 0.0,
                                local = expense.local ?: "Desconhecido",
                                categoria = expense.categoria,
                                subcategoria = expense.subcategoria
                            )
                        } else {
                            Log.w("SmsReceiver", "Falha ao processar SMS com LLM")
                            
                            // Log de erro: falha no processamento LLM
                            ActivityLogManager.addLog(
                                tipoAtividade = "sms_capture",
                                descricao = "Falha ao processar SMS com LLM",
                                dados = smsData,
                                sucesso = false,
                                erro = "LLM não retornou despesa válida"
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SmsReceiver", "Error processing SMS", e)
                    
                    // Log de erro geral no processamento de SMS
                    ActivityLogManager.addLog(
                        tipoAtividade = "sms_capture",
                        descricao = "Erro ao processar SMS",
                        dados = null,
                        sucesso = false,
                        erro = e.message ?: "Erro desconhecido"
                    )
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
