package com.humberto.gestorfinanceiro.data.log

import com.humberto.gestorfinanceiro.data.model.ActivityLog
import com.humberto.gestorfinanceiro.di.Dependencies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

object ActivityLogManager {
    private const val MAX_LOGS_IN_MEMORY = 100
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val _logs = MutableStateFlow<List<ActivityLogEntry>>(emptyList())
    val logs: StateFlow<List<ActivityLogEntry>> = _logs.asStateFlow()
    
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    
    data class ActivityLogEntry(
        val id: String = UUID.randomUUID().toString(),
        val tipoAtividade: String,
        val descricao: String?,
        val dados: String?,
        val sucesso: Boolean,
        val erro: String?,
        val timestamp: String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    )
    
    fun addLog(
        tipoAtividade: String,
        descricao: String? = null,
        dados: String? = null,
        sucesso: Boolean = true,
        erro: String? = null
    ) {
        val entry = ActivityLogEntry(
            tipoAtividade = tipoAtividade,
            descricao = descricao,
            dados = dados,
            sucesso = sucesso,
            erro = erro
        )
        
        // Adicionar à lista em memória
        val currentLogs = _logs.value.toMutableList()
        currentLogs.add(0, entry) // Adicionar no início
        
        // Limitar tamanho da lista
        if (currentLogs.size > MAX_LOGS_IN_MEMORY) {
            currentLogs.removeAt(currentLogs.size - 1)
        }
        
        _logs.value = currentLogs
        
        // Salvar no banco em background (não bloquear)
        scope.launch {
            try {
                Dependencies.supabaseRepository.saveActivityLog(
                    tipoAtividade = tipoAtividade,
                    descricao = descricao,
                    dados = dados,
                    sucesso = sucesso,
                    erro = erro
                )
            } catch (e: Exception) {
                // Log de erro ao salvar no banco não deve quebrar o fluxo
                android.util.Log.e("ActivityLogManager", "Erro ao salvar log no banco", e)
            }
        }
    }
    
    fun clearLogs() {
        _logs.value = emptyList()
    }
}

