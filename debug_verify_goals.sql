-- ============================================
-- SCRIPT DE VERIFICAÇÃO DE METAS E NOTIFICAÇÕES
-- Execute este script no Supabase SQL Editor
-- ============================================

-- 1. Verificar estrutura da tabela metas
SELECT column_name, data_type, is_nullable 
FROM information_schema.columns 
WHERE table_name = 'metas' 
  AND column_name IN ('id_meta', 'id_categoria', 'valor_meta', 'data_inicio', 'periodo', 'notified_80_percent')
ORDER BY ordinal_position;

-- 2. Listar todas as metas com suas categorias
SELECT 
  m.id_meta,
  m.id_categoria,
  c.nome_categoria,
  m.valor_meta,
  m.data_inicio,
  m.periodo,
  COALESCE(m.notified_80_percent, FALSE) as notified_80_percent
FROM metas m
JOIN categoria c ON c.id_categoria = m.id_categoria
ORDER BY m.data_inicio DESC, c.nome_categoria;

-- 3. Calcular totais gastos por categoria no mês atual (novembro/2025)
SELECT 
  c.id_categoria,
  c.nome_categoria,
  COALESCE(m.valor_meta, 0) as meta_mensal,
  COALESCE(SUM(d.valor), 0) as total_gasto,
  ROUND(
    CASE 
      WHEN COALESCE(m.valor_meta, 0) > 0 
      THEN (COALESCE(SUM(d.valor), 0) / m.valor_meta * 100)::numeric 
      ELSE 0 
    END, 
    2
  ) as percentual,
  CASE 
    WHEN COALESCE(m.valor_meta, 0) > 0 AND (COALESCE(SUM(d.valor), 0) / m.valor_meta * 100) >= 80 
    THEN '🔴 DEVE NOTIFICAR!' 
    ELSE '✅ OK' 
  END as status_notificacao,
  COALESCE(m.notified_80_percent, FALSE) as ja_notificado,
  m.data_inicio as meta_data_inicio,
  m.periodo as meta_periodo
FROM categoria c
LEFT JOIN metas m ON m.id_categoria = c.id_categoria 
  AND m.data_inicio = '2025-11-01'
  AND m.periodo = 'mensal'
LEFT JOIN subcategoria s ON s.id_categoria = c.id_categoria
LEFT JOIN despesas d ON d.id_subcategoria = s.id_subcategoria 
  AND d.data_despesa >= '2025-11-01' 
  AND d.data_despesa < '2025-12-01'
GROUP BY c.id_categoria, c.nome_categoria, m.valor_meta, m.notified_80_percent, m.data_inicio, m.periodo
HAVING COALESCE(m.valor_meta, 0) > 0
ORDER BY percentual DESC;

-- 4. Listar despesas de novembro/2025 por categoria
SELECT 
  c.nome_categoria,
  s.nome_subcategoria,
  d.local,
  d.valor,
  d.data_despesa,
  d.id_subcategoria
FROM despesas d
JOIN subcategoria s ON s.id_subcategoria = d.id_subcategoria
JOIN categoria c ON c.id_categoria = s.id_categoria
WHERE d.data_despesa >= '2025-11-01' 
  AND d.data_despesa < '2025-12-01'
ORDER BY c.nome_categoria, d.data_despesa DESC;

-- 5. RESET das notificações (execute se quiser testar novamente)
-- ATENÇÃO: Descomente a linha abaixo APENAS se quiser resetar os flags
-- UPDATE metas SET notified_80_percent = FALSE WHERE data_inicio = '2025-11-01';

-- 6. Verificar se existe alguma meta que deveria notificar mas não notificou
SELECT 
  c.nome_categoria,
  m.valor_meta,
  SUM(d.valor) as total_gasto,
  ROUND((SUM(d.valor) / m.valor_meta * 100)::numeric, 2) as percentual,
  m.notified_80_percent,
  CASE 
    WHEN (SUM(d.valor) / m.valor_meta * 100) >= 80 AND COALESCE(m.notified_80_percent, FALSE) = FALSE
    THEN '❌ BUG: Deveria ter notificado mas não notificou!'
    WHEN (SUM(d.valor) / m.valor_meta * 100) >= 80 AND m.notified_80_percent = TRUE
    THEN '✅ OK: Notificado corretamente'
    ELSE '✅ OK: Ainda não atingiu 80%'
  END as diagnostico
FROM metas m
JOIN categoria c ON c.id_categoria = m.id_categoria
JOIN subcategoria s ON s.id_categoria = c.id_categoria
JOIN despesas d ON d.id_subcategoria = s.id_subcategoria 
  AND d.data_despesa >= '2025-11-01' 
  AND d.data_despesa < '2025-12-01'
WHERE m.data_inicio = '2025-11-01'
  AND m.periodo = 'mensal'
GROUP BY c.nome_categoria, m.valor_meta, m.notified_80_percent;

