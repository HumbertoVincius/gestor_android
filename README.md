# Gestor Financeiro Android App

This is a personal finance app that parses bank SMS notifications using Gemini AI and stores them in Supabase.

## Setup Instructions

### 1. API Keys
Open `app/src/main/java/com/humberto/gestorfinanceiro/di/Dependencies.kt` and replace the placeholders with your actual keys:

```kotlin
private const val SUPABASE_URL = "YOUR_SUPABASE_URL"
private const val SUPABASE_KEY = "YOUR_SUPABASE_KEY"
private const val GEMINI_API_KEY = "YOUR_GEMINI_API_KEY"
```

### 2. Supabase Setup
Create a table named `transactions` in your Supabase project with the following columns:
- `id` (int8, primary key)
- `amount` (float8)
- `merchant` (text)
- `date` (int8)
- `category` (text)
- `original_sms` (text)

### 3. Permissions
The app requires `RECEIVE_SMS` and `READ_SMS`. On Android 6.0+, you will be prompted to grant these permissions at runtime.
**Note**: Google Play restricts these permissions. Since this is a personal app, you can install it via ADB or Android Studio.

### 4. Testing
To test SMS parsing in the emulator:
```bash
adb shell service call isms 7 i32 0 s16 "com.android.mms.service" s16 "+123456789" s16 "Compra aprovada R$ 50,00 em PADARIA DO JOAO"
```
