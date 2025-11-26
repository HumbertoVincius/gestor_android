-- ============================================
-- Migration 005: Adicionar rastreamento de faixas de notificação
-- ============================================

-- Adicionar coluna para armazenar a última faixa notificada
ALTER TABLE metas 
ADD COLUMN IF NOT EXISTS last_notified_threshold INTEGER DEFAULT 0;

-- Comentário da coluna
COMMENT ON COLUMN metas.last_notified_threshold IS 
'Última faixa de porcentagem notificada (80, 90, 100, 110, etc). Zero significa nunca notificou.';

-- Criar índice para performance
CREATE INDEX IF NOT EXISTS idx_metas_last_notified_threshold 
ON metas(last_notified_threshold);

-- Remover a coluna antiga notified_80_percent (não mais necessária)
ALTER TABLE metas 
DROP COLUMN IF EXISTS notified_80_percent;

-- Remover o índice antigo
DROP INDEX IF EXISTS idx_metas_notified_80;

