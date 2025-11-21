package com.humberto.gestorfinanceiro.domain.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.humberto.gestorfinanceiro.di.Dependencies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            
            val pendingResult = goAsync()
            
            scope.launch(Dispatchers.IO) {
                try {
                    messages?.forEach { sms ->
                        val sender = sms.originatingAddress
                        val body = sms.messageBody
                        
                        Log.d("SmsReceiver", "SMS received from $sender: $body")
                        
                        // TODO: Add logic to filter by specific bank number if needed
                        // if (sender == "YOUR_BANK_NUMBER") { ... }

                        val transaction = Dependencies.llmService.parseSms(body)
                        if (transaction != null) {
                            Dependencies.supabaseRepository.saveTransaction(transaction)
                            Log.d("SmsReceiver", "Transaction saved: $transaction")
                        } else {
                            Log.d("SmsReceiver", "Failed to parse SMS")
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
