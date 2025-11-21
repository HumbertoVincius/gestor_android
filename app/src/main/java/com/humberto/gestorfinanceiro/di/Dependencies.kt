package com.humberto.gestorfinanceiro.di

import com.humberto.gestorfinanceiro.data.llm.GeminiLlmService
import com.humberto.gestorfinanceiro.data.llm.LlmService
import com.humberto.gestorfinanceiro.data.supabase.SupabaseRepository

object Dependencies {
    // TODO: Replace with real keys or load from BuildConfig
    private const val SUPABASE_URL = "YOUR_SUPABASE_URL"
    private const val SUPABASE_KEY = "YOUR_SUPABASE_KEY"
    private const val GEMINI_API_KEY = "YOUR_GEMINI_API_KEY"

    val llmService: LlmService by lazy {
        GeminiLlmService(GEMINI_API_KEY)
    }

    val supabaseRepository: SupabaseRepository by lazy {
        SupabaseRepository(SUPABASE_URL, SUPABASE_KEY)
    }
}
