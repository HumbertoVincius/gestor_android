# 🔍 Checklist de Debug - Notificação de Metas 80%

## ✅ Verificações Necessárias

### 1. Banco de Dados
- [ ] A coluna `notified_80_percent` existe na tabela `metas`?
- [ ] Há pelo menos uma meta cadastrada para o mês atual?
- [ ] A meta tem `data_inicio` = "2025-11-01" (formato YYYY-MM-01)?
- [ ] A meta tem `periodo` = "mensal"?

**SQL para verificar:**
```sql
SELECT 
  m.id_meta,
  c.nome_categoria,
  m.valor_meta,
  m.data_inicio,
  m.periodo,
  m.notified_80_percent
FROM metas m
JOIN categoria c ON c.id_categoria = m.id_categoria
WHERE m.data_inicio = '2025-11-01';
```

### 2. Despesas
**SQL para verificar total gasto por categoria:**
```sql
SELECT 
  c.nome_categoria,
  SUM(d.valor) as total_gasto,
  m.valor_meta,
  ROUND((SUM(d.valor) / m.valor_meta * 100)::numeric, 2) as percentual
FROM despesas d
JOIN subcategoria s ON s.id_subcategoria = d.id_subcategoria
JOIN categoria c ON c.id_categoria = s.id_categoria
LEFT JOIN metas m ON m.id_categoria = c.id_categoria 
  AND m.data_inicio = '2025-11-01'
  AND m.periodo = 'mensal'
WHERE d.data_despesa LIKE '2025-11%'
GROUP BY c.nome_categoria, m.valor_meta
ORDER BY percentual DESC;
```

### 3. Logcat (Android Studio)
Filtre por **"SupabaseRepository"** e procure por:

```
========== VERIFICANDO METAS (80%) ==========
```

**Verifique se aparece:**
- ✅ "Context fornecido: true" 
- ✅ "Chamando checkAndNotifyGoals..."
- ✅ "Despesa: [nome] - R$ [valor]"
- ✅ "Mês/Ano: 11/2025"
- ✅ "Categoria ID: [uuid]"
- ✅ "Meta encontrada: R$ [valor]"
- ✅ "Total gasto na categoria: R$ [valor]"
- ✅ "Porcentagem da meta: [XX.XX]%"

**Se aparecer:**
- ❌ "Context não fornecido" → Problema no código
- ❌ "Nenhuma meta encontrada" → Problema no banco (data_inicio ou período)
- ❌ "Meta já foi notificada" → Resete o campo no banco
- ❌ "Meta ainda não atingiu 80%" → Adicione mais despesas

### 4. Permissões do App
No Android:
1. Configurações → Apps → Gestor Financeiro → Notificações
2. Verifique se "Alertas de Metas" está habilitado
3. Se não aparecer, desinstale e reinstale o app

### 5. Teste Manual

**Passo a passo:**
1. Abra o Logcat no Android Studio
2. Filtre por tag: `SupabaseRepository`
3. No app, vá na tela **Debug**
4. No campo "Teste de LLM", digite: `R$50,00 no bar do zé em 24/11/2025`
5. Clique em "Processar com LLM"
6. **IMEDIATAMENTE** veja os logs no Logcat

**Copie e cole TODOS os logs que aparecerem com:**
- `========== VERIFICANDO METAS`
- `showGoalAlertNotification`
- `NotificationHelper`

## 🐛 Possíveis Problemas

### Problema 1: Context não está sendo passado
**Sintoma:** Log diz "Context não fornecido"
**Solução:** Bug no código, preciso corrigir

### Problema 2: Meta não encontrada
**Sintoma:** Log diz "Nenhuma meta encontrada"
**Causa comum:** 
- `data_inicio` está errado (precisa ser "2025-11-01", não "2025-11-24")
- `periodo` não é "mensal"

**Correção no banco:**
```sql
UPDATE metas 
SET data_inicio = '2025-11-01',
    periodo = 'mensal'
WHERE id_categoria = 'seu-uuid-aqui';
```

### Problema 3: Não atingiu 80%
**Sintoma:** Log diz "Meta ainda não atingiu 80%"
**Solução:** Adicione mais despesas ou reduza o valor da meta

### Problema 4: Já foi notificado
**Sintoma:** Log diz "Meta já foi notificada"
**Solução:** Reset no banco
```sql
UPDATE metas 
SET notified_80_percent = FALSE 
WHERE id_categoria = 'seu-uuid-aqui';
```

## 📋 O que enviar para debug

Se ainda não funcionar, me envie:

1. **Screenshot ou texto** da query SQL das metas (seção 1)
2. **Screenshot ou texto** da query SQL dos totais por categoria (seção 2)
3. **TODOS os logs** do Logcat filtrados por `SupabaseRepository` durante o teste
4. **Screenshot** das permissões de notificação do app

