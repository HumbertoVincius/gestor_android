-- ============================================
-- Migration 007: Adicionar status de visualização na despesa
-- ============================================

-- Adicionar coluna visto
ALTER TABLE despesas 
ADD COLUMN IF NOT EXISTS visto BOOLEAN DEFAULT FALSE;

-- Atualizar despesas antigas para "vistas" (para não poluir o histórico)
UPDATE despesas 
SET visto = TRUE 
WHERE visto IS FALSE;

-- Comentário
COMMENT ON COLUMN despesas.visto IS 
'Indica se o usuário já visualizou/conferiu a despesa. False = Novo/Não visto.';

-- Índice para queries de "não vistos"
CREATE INDEX IF NOT EXISTS idx_despesas_visto 
ON despesas(visto);

