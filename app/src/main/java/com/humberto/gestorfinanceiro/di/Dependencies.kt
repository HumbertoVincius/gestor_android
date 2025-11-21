package com.humberto.gestorfinanceiro.di

import com.humberto.gestorfinanceiro.BuildConfig
import com.humberto.gestorfinanceiro.data.llm.GeminiLlmService
import com.humberto.gestorfinanceiro.data.llm.LlmService
import com.humberto.gestorfinanceiro.data.supabase.SupabaseRepository

object Dependencies {
    private val SUPABASE_URL = BuildConfig.SUPABASE_URL
    private val SUPABASE_KEY = BuildConfig.SUPABASE_KEY
    private val GEMINI_API_KEY = BuildConfig.GEMINI_API_KEY

    val llmService: LlmService by lazy {
        GeminiLlmService(GEMINI_API_KEY)
    }

    val supabaseRepository: SupabaseRepository by lazy {
        SupabaseRepository(SUPABASE_URL, SUPABASE_KEY)
    }
}
