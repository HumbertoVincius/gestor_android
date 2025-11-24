package com.humberto.gestorfinanceiro.di

import com.humberto.gestorfinanceiro.BuildConfig
import com.humberto.gestorfinanceiro.data.llm.LlmService
import com.humberto.gestorfinanceiro.data.llm.OpenAILlmService
import com.humberto.gestorfinanceiro.data.supabase.SupabaseRepository

object Dependencies {
    private val SUPABASE_URL = BuildConfig.SUPABASE_URL
    private val SUPABASE_KEY = BuildConfig.SUPABASE_KEY
    private val OPENAI_API_KEY = BuildConfig.OPENAI_API_KEY

    val llmService: LlmService by lazy {
        OpenAILlmService(OPENAI_API_KEY)
    }

    val supabaseRepository: SupabaseRepository by lazy {
        SupabaseRepository(SUPABASE_URL, SUPABASE_KEY)
    }
}
