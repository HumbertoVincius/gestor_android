-- ============================================
-- QUERIES SIMPLIFICADAS PARA DEBUG RÁPIDO
-- ============================================

-- QUERY 1: Ver todas as metas cadastradas
SELECT 
  c.nome_categoria,
  m.valor_meta,
  m.data_inicio,
  m.last_notified_threshold as ultima_faixa_notificada
FROM metas m
JOIN categoria c ON c.id_categoria = m.id_categoria
WHERE m.data_inicio = '2025-11-01'
ORDER BY c.nome_categoria;

-- QUERY 2: Ver despesas de novembro/2025
SELECT 
  c.nome_categoria,
  d.local,
  d.valor,
  d.data_despesa
FROM despesas d
JOIN subcategoria s ON s.id_subcategoria = d.id_subcategoria
JOIN categoria c ON c.id_categoria = s.id_categoria
WHERE d.data_despesa >= '2025-11-01' 
  AND d.data_despesa < '2025-12-01'
ORDER BY c.nome_categoria, d.data_despesa;

-- QUERY 3: PRINCIPAL - Ver faixas de notificação por categoria
SELECT 
  c.nome_categoria,
  COALESCE(SUM(d.valor), 0) as total_gasto,
  m.valor_meta,
  ROUND((COALESCE(SUM(d.valor), 0) / NULLIF(m.valor_meta, 0) * 100)::numeric, 2) as percentual,
  COALESCE(m.last_notified_threshold, 0) as ultima_faixa_notificada,
  CASE 
    WHEN COALESCE(SUM(d.valor), 0) / NULLIF(m.valor_meta, 0) * 100 < 80 THEN '✅ Abaixo de 80%'
    WHEN COALESCE(SUM(d.valor), 0) / NULLIF(m.valor_meta, 0) * 100 < 90 THEN '🟡 80-89% (Atenção)'
    WHEN COALESCE(SUM(d.valor), 0) / NULLIF(m.valor_meta, 0) * 100 < 100 THEN '🟠 90-99% (Cuidado)'
    ELSE '🔴 100%+ (Estourou!)'
  END as status_faixa,
  CASE 
    WHEN (COALESCE(SUM(d.valor), 0) / NULLIF(m.valor_meta, 0) * 100)::int >= 100 
    THEN ((COALESCE(SUM(d.valor), 0) / NULLIF(m.valor_meta, 0) * 100)::int / 10 * 10)
    WHEN COALESCE(SUM(d.valor), 0) / NULLIF(m.valor_meta, 0) * 100 >= 90 THEN 90
    WHEN COALESCE(SUM(d.valor), 0) / NULLIF(m.valor_meta, 0) * 100 >= 80 THEN 80
    ELSE 0
  END as faixa_atual
FROM categoria c
LEFT JOIN metas m ON m.id_categoria = c.id_categoria 
  AND m.data_inicio = '2025-11-01'
LEFT JOIN subcategoria s ON s.id_categoria = c.id_categoria
LEFT JOIN despesas d ON d.id_subcategoria = s.id_subcategoria 
  AND d.data_despesa >= '2025-11-01' 
  AND d.data_despesa < '2025-12-01'
WHERE m.valor_meta IS NOT NULL
GROUP BY c.nome_categoria, m.valor_meta, m.last_notified_threshold
ORDER BY percentual DESC;

-- QUERY 4: Ver timeline de metas (últimos 3 meses)
SELECT 
  c.nome_categoria,
  TO_CHAR(m.data_inicio, 'YYYY-MM') as mes,
  m.valor_meta,
  m.last_notified_threshold,
  CASE 
    WHEN m.last_notified_threshold = 0 THEN '✅ Nunca notificou'
    WHEN m.last_notified_threshold = 80 THEN '🟡 80%'
    WHEN m.last_notified_threshold = 90 THEN '🟠 90%'
    WHEN m.last_notified_threshold >= 100 THEN '🔴 ' || m.last_notified_threshold || '%'
  END as status
FROM metas m
JOIN categoria c ON c.id_categoria = m.id_categoria
WHERE m.data_inicio >= CURRENT_DATE - INTERVAL '3 months'
ORDER BY c.nome_categoria, m.data_inicio DESC;

-- QUERY 5: Reset notificações do mês atual (descomente para executar)
-- UPDATE metas 
-- SET last_notified_threshold = 0 
-- WHERE data_inicio = DATE_TRUNC('month', CURRENT_DATE);

