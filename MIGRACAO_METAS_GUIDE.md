# 🔄 Guia de Migração - Sistema de Metas

## ⚠️ Situação Atual

O app está compatível com **AMBAS** as versões do banco:
- ✅ **Versão Antiga:** Coluna `notified_80_percent` (boolean)
- ✅ **Versão Nova:** Coluna `last_notified_threshold` (integer)

**O código funciona com qualquer uma!**

---

## 🚀 Como Executar a Migration

### **Passo 1: Execute a Migration 005 no Supabase**

Abra o **SQL Editor** no Supabase e execute:

```sql
-- Migration 005: Sistema de notificações por faixas
-- =================================================

-- 1. Adicionar nova coluna
ALTER TABLE metas 
ADD COLUMN IF NOT EXISTS last_notified_threshold INTEGER DEFAULT 0;

-- 2. Migrar dados antigos
-- Metas que já foram notificadas (notified_80_percent = true) recebem threshold 80
UPDATE metas 
SET last_notified_threshold = 80 
WHERE notified_80_percent = true 
  AND last_notified_threshold = 0;

-- 3. Adicionar comentário
COMMENT ON COLUMN metas.last_notified_threshold IS 
'Última faixa de porcentagem notificada (80, 90, 100, 110, etc). Zero significa nunca notificou.';

-- 4. Criar índice
CREATE INDEX IF NOT EXISTS idx_metas_last_notified_threshold 
ON metas(last_notified_threshold);

-- 5. Remover coluna antiga (OPCIONAL - só execute depois de confirmar que tudo funciona)
-- ALTER TABLE metas DROP COLUMN IF EXISTS notified_80_percent;
-- DROP INDEX IF EXISTS idx_metas_notified_80;
```

### **Passo 2: Reinstale o App**

No Android Studio:
- Clique em **Run** (ou Shift+F10)
- Ou execute: `./gradlew installDebug`

### **Passo 3: Teste**

1. Abra a tela de **Categorias**
2. Verifique se as metas aparecem
3. Adicione uma despesa
4. Verifique se as notificações funcionam

---

## 🔍 Como Verificar se a Migration Funcionou

### **No Supabase:**

```sql
-- Ver estrutura da tabela metas
SELECT column_name, data_type, column_default
FROM information_schema.columns
WHERE table_name = 'metas'
ORDER BY ordinal_position;
```

**Resultado esperado:**
```
id_meta               | uuid
id_categoria          | uuid
valor_meta            | numeric
periodo               | character varying
data_inicio           | date
last_notified_threshold | integer  ← DEVE APARECER
notified_80_percent   | boolean  ← PODE EXISTIR (compatibilidade)
```

### **Ver dados das metas:**

```sql
SELECT 
  c.nome_categoria,
  m.valor_meta,
  m.last_notified_threshold,
  m.notified_80_percent
FROM metas m
JOIN categoria c ON c.id_categoria = m.id_categoria
ORDER BY c.nome_categoria;
```

---

## 🐛 Solução de Problemas

### **Problema: "Metas não aparecem no app"**

**Causa:** Migration não foi executada, o app não consegue deserializar.

**Solução:**
1. Execute a migration 005 no Supabase
2. Reinstale o app
3. Se ainda não funcionar, limpe o cache:
   ```sql
   -- No app, na tela Debug, adicione um botão de "Limpar Cache" ou
   -- Force refresh puxando para baixo nas telas
   ```

### **Problema: "Erro ao inserir nova meta"**

**Causa:** Banco ainda tem `notified_80_percent` como NOT NULL.

**Solução:**
```sql
-- Tornar a coluna antiga nullable
ALTER TABLE metas 
ALTER COLUMN notified_80_percent DROP NOT NULL;
```

### **Problema: "Notificações não estão funcionando"**

**Causa:** Valores não migraram corretamente.

**Solução:**
```sql
-- Reset todos os thresholds
UPDATE metas 
SET last_notified_threshold = 0;

-- Se quiser manter histórico:
UPDATE metas 
SET last_notified_threshold = 80 
WHERE notified_80_percent = true;
```

---

## 📊 Compatibilidade

### **Durante a Transição:**

O código funciona assim:

```kotlin
fun getEffectiveThreshold(): Int {
    // Prioriza o campo novo
    if (lastNotifiedThreshold != null && lastNotifiedThreshold > 0) {
        return lastNotifiedThreshold ✅
    }
    // Fallback para o campo antigo
    if (notified80Percent == true) {
        return 80 ⚠️
    }
    // Nunca notificou
    return 0
}
```

**Isso significa:**
- ✅ Se o banco tem `last_notified_threshold`, usa ele
- ✅ Se o banco tem apenas `notified_80_percent = true`, assume 80%
- ✅ Se ambos são vazios, assume 0 (nunca notificou)

---

## 🧹 Limpeza Pós-Migration (OPCIONAL)

**⚠️ Só execute depois de confirmar que tudo está funcionando!**

```sql
-- Remover campo antigo
ALTER TABLE metas DROP COLUMN IF EXISTS notified_80_percent;

-- Remover índice antigo
DROP INDEX IF EXISTS idx_metas_notified_80;

-- Verificar
SELECT column_name 
FROM information_schema.columns 
WHERE table_name = 'metas';
```

Depois disso, você pode remover o campo `notified80Percent` do modelo Kotlin também.

---

## ✅ Checklist de Migration

- [ ] Executar migration 005 no Supabase
- [ ] Verificar que coluna `last_notified_threshold` existe
- [ ] Verificar que dados antigos foram migrados (UPDATE)
- [ ] Reinstalar app
- [ ] Testar tela de Categorias (metas aparecem?)
- [ ] Testar notificação (adicionar despesa >= 80%)
- [ ] (Opcional) Remover coluna antiga `notified_80_percent`

---

## 📞 Suporte

Se algo não funcionar:
1. Verifique os logs do Logcat (filtrar por `SupabaseRepository`)
2. Execute as queries de diagnóstico acima
3. Verifique se a migration foi realmente aplicada

