-- ============================================
-- Query para visualizar timeline de metas por categoria
-- ============================================

-- Ver todas as metas de uma categoria ao longo dos meses
SELECT 
  c.nome_categoria,
  m.data_inicio,
  m.valor_meta,
  m.last_notified_threshold,
  CASE 
    WHEN m.last_notified_threshold = 0 THEN '✅ Nenhuma notificação enviada'
    WHEN m.last_notified_threshold = 80 THEN '🟡 Notificado aos 80%'
    WHEN m.last_notified_threshold = 90 THEN '🟠 Notificado aos 90%'
    WHEN m.last_notified_threshold >= 100 THEN '🔴 Notificado aos ' || m.last_notified_threshold || '%'
    ELSE 'Estado desconhecido'
  END as status_notificacao,
  TO_CHAR(m.data_inicio, 'TMMonth/YYYY') as mes_ano
FROM metas m
JOIN categoria c ON c.id_categoria = m.id_categoria
ORDER BY c.nome_categoria, m.data_inicio DESC;

-- Ver comparativo de gastos vs metas nos últimos 3 meses
SELECT 
  c.nome_categoria,
  TO_CHAR(m.data_inicio, 'YYYY-MM') as mes,
  m.valor_meta,
  COALESCE(SUM(d.valor), 0) as total_gasto,
  ROUND((COALESCE(SUM(d.valor), 0) / NULLIF(m.valor_meta, 0) * 100)::numeric, 1) as percentual,
  m.last_notified_threshold as ultima_notificacao
FROM categoria c
JOIN metas m ON m.id_categoria = c.id_categoria
LEFT JOIN subcategoria s ON s.id_categoria = c.id_categoria
LEFT JOIN despesas d ON d.id_subcategoria = s.id_subcategoria
  AND TO_CHAR(d.data_despesa, 'YYYY-MM') = TO_CHAR(m.data_inicio, 'YYYY-MM')
WHERE m.data_inicio >= CURRENT_DATE - INTERVAL '3 months'
GROUP BY c.nome_categoria, m.data_inicio, m.valor_meta, m.last_notified_threshold
ORDER BY c.nome_categoria, m.data_inicio DESC;

-- Verificar se há metas que precisam ser copiadas para o próximo mês
-- (útil para rodar no fim do mês)
SELECT 
  c.nome_categoria,
  m.valor_meta,
  m.data_inicio as meta_atual,
  (m.data_inicio + INTERVAL '1 month')::date as proximo_mes,
  CASE 
    WHEN EXISTS (
      SELECT 1 FROM metas m2 
      WHERE m2.id_categoria = m.id_categoria 
        AND m2.data_inicio = (m.data_inicio + INTERVAL '1 month')::date
    ) THEN '✅ Já existe meta para próximo mês'
    ELSE '⚠️ Será criada automaticamente ao adicionar despesa'
  END as status
FROM metas m
JOIN categoria c ON c.id_categoria = m.id_categoria
WHERE m.data_inicio = DATE_TRUNC('month', CURRENT_DATE)::date
ORDER BY c.nome_categoria;

