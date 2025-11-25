-- Migration: Create activity_log table
-- Description: Tabela para armazenar logs de atividades do sistema (captura de SMS, atividade LLM, inserções no banco)

CREATE TABLE IF NOT EXISTS activity_log (
    id_log UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo_atividade VARCHAR(50) NOT NULL,
    descricao TEXT,
    dados JSONB,
    sucesso BOOLEAN NOT NULL DEFAULT true,
    erro TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Índices para melhor performance
CREATE INDEX IF NOT EXISTS idx_activity_log_tipo_atividade ON activity_log(tipo_atividade);
CREATE INDEX IF NOT EXISTS idx_activity_log_created_at ON activity_log(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_activity_log_sucesso ON activity_log(sucesso);

-- Comentários nas colunas
COMMENT ON TABLE activity_log IS 'Tabela de log de atividades do sistema';
COMMENT ON COLUMN activity_log.tipo_atividade IS 'Tipo de atividade: sms_capture, llm_request, llm_response, db_insert';
COMMENT ON COLUMN activity_log.descricao IS 'Descrição textual da atividade';
COMMENT ON COLUMN activity_log.dados IS 'Dados adicionais em formato JSON';
COMMENT ON COLUMN activity_log.sucesso IS 'Indica se a atividade foi bem-sucedida';
COMMENT ON COLUMN activity_log.erro IS 'Mensagem de erro, se houver';

-- Habilitar RLS (Row Level Security)
ALTER TABLE activity_log ENABLE ROW LEVEL SECURITY;

-- Política RLS: Permitir leitura e escrita para usuários autenticados
-- Ajuste conforme sua política de segurança
CREATE POLICY "Permitir leitura e escrita de activity_log para usuários autenticados"
    ON activity_log
    FOR ALL
    USING (auth.role() = 'authenticated')
    WITH CHECK (auth.role() = 'authenticated');

-- Política alternativa: Permitir acesso público (apenas para desenvolvimento)
-- Descomente se precisar de acesso público durante desenvolvimento
-- CREATE POLICY "Permitir acesso público a activity_log"
--     ON activity_log
--     FOR ALL
--     USING (true)
--     WITH CHECK (true);

