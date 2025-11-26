# 📊 Sistema de Metas e Notificações

## 🎯 Como Funciona

### **Criação de Metas**

Cada meta tem uma `data_inicio` específica no formato `YYYY-MM-01` (sempre dia 1 do mês).

**Exemplo:**
- Meta de Novembro/2025: `data_inicio = 2025-11-01`
- Meta de Dezembro/2025: `data_inicio = 2025-12-01`

### **Cópia Automática na Virada do Mês**

Quando você adiciona uma despesa em um **novo mês** e não existe meta cadastrada:

1. ✅ Sistema busca a meta do **mês anterior**
2. ✅ Se encontrar, **copia automaticamente** para o mês atual
3. ✅ O campo `last_notified_threshold` é **resetado para 0**
4. ✅ Você pode receber notificações novamente (80%, 90%, 100%...)

**Exemplo:**
```
Novembro: Meta R$ 500 (last_notified_threshold = 90)
         ↓
Dezembro: Meta R$ 500 (last_notified_threshold = 0) ← COPIADA AUTOMATICAMENTE
```

### **Notificações por Faixas**

O sistema notifica **UMA VEZ** por faixa atingida:

| Faixa | Título | Quando Notifica |
|-------|--------|-----------------|
| **80-89%** | 🟡 "⚠️ Atenção! Meta atingindo limite" | Primeira despesa que ultrapassar 80% |
| **90-99%** | 🟠 "🟠 Cuidado! Meta próxima do limite" | Primeira despesa que ultrapassar 90% |
| **100-109%** | 🔴 "🔴 Alerta! Meta estourou" | Primeira despesa que ultrapassar 100% |
| **110-119%** | 🔴 "🔴 Alerta! Meta estourou" | Primeira despesa que ultrapassar 110% |
| **120%+** | 🔴 "🔴 Alerta! Meta estourou" | A cada 10% adicional |

### **Exemplo de Fluxo**

**Cenário:** Meta de "Lazer" = R$ 100,00 em Novembro

1. **Despesa 1:** R$ 85,00 → Total: R$ 85,00 (85%)
   - 🔔 Notificação: "🟡 Atenção! Meta atingindo limite"
   - `last_notified_threshold = 80`

2. **Despesa 2:** R$ 10,00 → Total: R$ 95,00 (95%)
   - 🔔 Notificação: "🟠 Cuidado! Meta próxima do limite"
   - `last_notified_threshold = 90`

3. **Despesa 3:** R$ 10,00 → Total: R$ 105,00 (105%)
   - 🔔 Notificação: "🔴 Alerta! Meta estourou"
   - `last_notified_threshold = 100`

4. **Despesa 4:** R$ 5,00 → Total: R$ 110,00 (110%)
   - ❌ **Sem notificação** (ainda na faixa 100-109%)

5. **Despesa 5:** R$ 5,00 → Total: R$ 115,00 (115%)
   - 🔔 Notificação: "🔴 Alerta! Meta estourou"
   - `last_notified_threshold = 110`

---

## 🔧 Gerenciamento Manual

### **Ver metas de todos os meses:**
```sql
SELECT 
  c.nome_categoria,
  m.data_inicio,
  m.valor_meta,
  m.last_notified_threshold
FROM metas m
JOIN categoria c ON c.id_categoria = m.id_categoria
ORDER BY c.nome_categoria, m.data_inicio DESC;
```

### **Resetar notificações do mês atual:**
```sql
UPDATE metas 
SET last_notified_threshold = 0 
WHERE data_inicio = DATE_TRUNC('month', CURRENT_DATE);
```

### **Copiar meta manualmente para próximo mês:**
```sql
-- Exemplo: Copiar meta de Novembro para Dezembro
INSERT INTO metas (id_categoria, valor_meta, periodo, data_inicio, last_notified_threshold)
SELECT 
  id_categoria,
  valor_meta,
  'mensal',
  '2025-12-01', -- Mês de destino
  0 -- Reset do threshold
FROM metas
WHERE data_inicio = '2025-11-01';
```

### **Deletar meta de um mês específico:**
```sql
DELETE FROM metas 
WHERE data_inicio = '2025-11-01' 
  AND id_categoria = (SELECT id_categoria FROM categoria WHERE nome_categoria = 'lazer');
```

---

## 📊 Queries Úteis

Veja o arquivo `migrations/006_view_metas_timeline.sql` para queries completas de:
- Timeline de metas
- Comparativo de gastos vs metas
- Verificação de metas futuras

---

## ❓ FAQ

**P: O que acontece se eu mudar o valor da meta no meio do mês?**  
R: A meta é atualizada, mas o `last_notified_threshold` permanece. Você só receberá novas notificações se ultrapassar uma faixa ainda maior.

**P: Posso receber a mesma notificação duas vezes?**  
R: Não. Cada faixa (80%, 90%, 100%, etc.) só notifica UMA VEZ por mês.

**P: Como funciona se eu pular um mês?**  
R: O sistema copia da meta **mais recente** que encontrar. Se tinha meta em Janeiro e não teve em Fevereiro, quando criar despesa em Março, ele copia de Janeiro.

**P: As metas antigas são deletadas automaticamente?**  
R: Não. Elas ficam no banco para histórico. Você pode deletar manualmente se quiser.

---

## 🎨 Cores das Notificações

- 🟡 **80-89%**: Amarelo - Atenção leve
- 🟠 **90-99%**: Laranja - Cuidado maior
- 🔴 **100%+**: Vermelho - Alerta crítico

