package com.humberto.gestorfinanceiro.data.settings

import android.content.Context
import android.content.SharedPreferences

object SettingsManager {
    private const val PREFS_NAME = "gestor_financeiro_prefs"
    private const val KEY_SMS_SENDER_NUMBER = "sms_sender_number"
    
    private var prefs: SharedPreferences? = null
    
    fun initialize(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }
    
    fun getSmsSenderNumber(): String? {
        return prefs?.getString(KEY_SMS_SENDER_NUMBER, null)
    }
    
    fun setSmsSenderNumber(number: String?) {
        prefs?.edit()?.apply {
            if (number.isNullOrBlank()) {
                remove(KEY_SMS_SENDER_NUMBER)
            } else {
                putString(KEY_SMS_SENDER_NUMBER, number)
            }
            apply()
        }
    }
    
    fun isSmsSenderNumberConfigured(): Boolean {
        return !getSmsSenderNumber().isNullOrBlank()
    }
}

