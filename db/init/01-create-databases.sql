-- Executado uma unica vez, no primeiro start do container Postgres.
-- O banco hospital_agendamento ja e criado via POSTGRES_DB; aqui criamos
-- os bancos dos outros dois servicos (um Postgres, tres schemas isolados).
CREATE DATABASE hospital_notificacoes;
CREATE DATABASE hospital_historico;
