-- Migration 004: Add goal notification tracking
-- Description: Adiciona coluna para rastrear se já foi enviada notificação de 80% para metas

-- Adicionar coluna para rastrear se já notificou aos 80%
ALTER TABLE metas ADD COLUMN notified_80_percent BOOLEAN DEFAULT FALSE;

-- Índice para queries rápidas
CREATE INDEX idx_metas_notified_80 ON metas(notified_80_percent);

COMMENT ON COLUMN metas.notified_80_percent IS 'Indica se já foi enviada notificação de 80% para esta meta';

