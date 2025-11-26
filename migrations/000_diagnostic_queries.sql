-- ============================================
-- QUERIES DE DIAGNÓSTICO - Verificar estado do sistema
-- ============================================

-- 1. Verificar estrutura da tabela metas
SELECT 
  column_name,
  data_type,
  is_nullable,
  column_default
FROM information_schema.columns
WHERE table_name = 'metas'
ORDER BY ordinal_position;

-- 2. Verificar se metas estão sendo carregadas
SELECT COUNT(*) as total_metas FROM metas;

-- 3. Ver todas as metas com todos os campos
SELECT 
  m.id_meta,
  c.nome_categoria,
  m.valor_meta,
  m.periodo,
  m.data_inicio,
  m.last_notified_threshold,
  m.notified_80_percent
FROM metas m
LEFT JOIN categoria c ON c.id_categoria = m.id_categoria
ORDER BY c.nome_categoria, m.data_inicio DESC;

-- 4. Verificar se há metas com problemas de serialização
SELECT 
  'Metas com last_notified_threshold NULL' as tipo,
  COUNT(*) as quantidade
FROM metas 
WHERE last_notified_threshold IS NULL

UNION ALL

SELECT 
  'Metas com ambos os campos' as tipo,
  COUNT(*) as quantidade
FROM metas 
WHERE last_notified_threshold IS NOT NULL 
  AND notified_80_percent IS NOT NULL

UNION ALL

SELECT 
  'Metas só com campo antigo' as tipo,
  COUNT(*) as quantidade
FROM metas 
WHERE notified_80_percent IS NOT NULL 
  AND (last_notified_threshold IS NULL OR last_notified_threshold = 0);

-- 5. Verificar metas do mês atual
SELECT 
  c.nome_categoria,
  m.valor_meta,
  m.last_notified_threshold,
  m.notified_80_percent,
  CASE 
    WHEN m.last_notified_threshold > 0 THEN '✅ Campo novo OK'
    WHEN m.notified_80_percent = true THEN '⚠️ Apenas campo antigo'
    ELSE '✅ Nunca notificou'
  END as status
FROM metas m
JOIN categoria c ON c.id_categoria = m.id_categoria
WHERE m.data_inicio = DATE_TRUNC('month', CURRENT_DATE)
ORDER BY c.nome_categoria;

-- 6. Verificar índices
SELECT 
  indexname,
  indexdef
FROM pg_indexes
WHERE tablename = 'metas'
ORDER BY indexname;

-- 7. Test query completa (igual ao app usa)
SELECT 
  m.*,
  c.nome_categoria
FROM metas m
LEFT JOIN categoria c ON c.id_categoria = m.id_categoria
WHERE m.data_inicio = '2025-11-01'
  AND m.periodo = 'mensal'
ORDER BY c.nome_categoria;

