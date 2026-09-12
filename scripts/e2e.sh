#!/usr/bin/env bash
#
# Teste ponta a ponta do fluxo completo, contra o ambiente do docker-compose
# da raiz:  autenticacao -> agendamento -> evento assincrono -> notificacao
# -> historico (GraphQL).
#
# Pre-requisitos: docker compose up --build ja rodando; curl e jq instalados.
# Uso:  ./scripts/e2e.sh
#
set -euo pipefail

AGENDAMENTO="${AGENDAMENTO_URL:-http://localhost:8080}"
NOTIFICACOES="${NOTIFICACOES_URL:-http://localhost:8082}"
HISTORICO="${HISTORICO_URL:-http://localhost:8083}"

MEDICO="${SEED_MEDICO_EMAIL:-medico@hospital.com}:${SEED_MEDICO_SENHA:-medico123}"
ENFERMEIRO="${SEED_ENFERMEIRO_EMAIL:-enfermeiro@hospital.com}:${SEED_ENFERMEIRO_SENHA:-enfermeiro123}"
PACIENTE1="${SEED_PACIENTE1_EMAIL:-paciente1@hospital.com}:${SEED_PACIENTE1_SENHA:-paciente123}"
PACIENTE2="${SEED_PACIENTE2_EMAIL:-paciente2@hospital.com}:${SEED_PACIENTE2_SENHA:-paciente123}"

PACIENTE_ID=3          # paciente1 (ordem de criacao do DemoUsersSeeder)
PROFISSIONAL_ID=1      # medico
DATA_HORA="2999-01-15T14:30:00"

pass() { printf '  \033[32mOK\033[0m   %s\n' "$1"; }
fail() { printf '  \033[31mFALHOU\033[0m %s\n' "$1"; exit 1; }

echo "==> Aguardando os servicos ficarem de pe"
for url in "$AGENDAMENTO/actuator/health" "$NOTIFICACOES/actuator/health" "$HISTORICO/actuator/health"; do
  for i in $(seq 1 90); do
    CODE=$(curl -s -o /dev/null -w '%{http_code}' "$url" || true)
    # 200 = UP; qualquer outra resposta HTTP (000 = sem conexao ainda) tambem serve como "de pe"
    [ "$CODE" != "000" ] && break
    [ "$i" = 90 ] && fail "timeout aguardando $url"
    sleep 2
  done
done
pass "agendamento, notificacoes e historico responderam em /actuator/health"

echo "==> 1. Enfermeiro registra uma consulta (POST /consultas)"
CONSULTA=$(curl -sf -u "$ENFERMEIRO" -H 'Content-Type: application/json' \
  -d "{\"pacienteId\":$PACIENTE_ID,\"profissionalId\":$PROFISSIONAL_ID,\"dataHora\":\"$DATA_HORA\",\"observacoes\":\"E2E\"}" \
  "$AGENDAMENTO/consultas")
CONSULTA_ID=$(echo "$CONSULTA" | jq -r '.id')
[ -n "$CONSULTA_ID" ] && [ "$CONSULTA_ID" != "null" ] || fail "nao criou a consulta: $CONSULTA"
pass "consulta $CONSULTA_ID criada"

echo "==> 2. Medico NAO pode registrar (POST) - espera 403"
CODE=$(curl -s -o /dev/null -w '%{http_code}' -u "$MEDICO" -H 'Content-Type: application/json' \
  -d "{\"pacienteId\":$PACIENTE_ID,\"profissionalId\":$PROFISSIONAL_ID,\"dataHora\":\"$DATA_HORA\",\"observacoes\":\"x\"}" \
  "$AGENDAMENTO/consultas")
[ "$CODE" = 403 ] || fail "esperava 403 para medico no POST, veio $CODE"
pass "regra de autorizacao por perfil aplicada (403)"

echo "==> 3. Evento assincrono chegou no Servico de Notificacoes"
for i in $(seq 1 30); do
  N=$(curl -sf "$NOTIFICACOES/notificacoes/paciente/$PACIENTE_ID" | jq --argjson c "$CONSULTA_ID" '[.[] | select(.consultaId == $c)] | length')
  [ "${N:-0}" -ge 1 ] && break
  [ "$i" = 30 ] && fail "notificacao da consulta $CONSULTA_ID nao apareceu"
  sleep 2
done
pass "lembrete registrado em notificacoes"

echo "==> 4. Historico (GraphQL) projetou a consulta - consultasFuturas"
Q1='{"query":"{ consultasFuturas(pacienteId: '"$PACIENTE_ID"') { consultaId status } }"}'
for i in $(seq 1 30); do
  R=$(curl -sf -u "$MEDICO" -H 'Content-Type: application/json' -d "$Q1" "$HISTORICO/graphql" \
      | jq --arg c "$CONSULTA_ID" '[.data.consultasFuturas[] | select(.consultaId == $c)] | length')
  [ "${R:-0}" -ge 1 ] && break
  [ "$i" = 30 ] && fail "consulta $CONSULTA_ID nao apareceu no historico"
  sleep 2
done
pass "GraphQL consultasFuturas retornou a consulta"

echo "==> 5. Medico edita a consulta para REALIZADA (PUT /consultas/{id})"
curl -sf -u "$MEDICO" -H 'Content-Type: application/json' \
  -d "{\"dataHora\":\"$DATA_HORA\",\"status\":\"REALIZADA\",\"observacoes\":\"Paciente atendido\"}" \
  "$AGENDAMENTO/consultas/$CONSULTA_ID" >/dev/null
pass "consulta editada"

echo "==> 6. Historico reflete o novo status via evento consulta.editada"
Q2='{"query":"{ historicoPaciente(pacienteId: '"$PACIENTE_ID"') { consultaId status } }"}'
for i in $(seq 1 30); do
  S=$(curl -sf -u "$MEDICO" -H 'Content-Type: application/json' -d "$Q2" "$HISTORICO/graphql" \
      | jq -r --arg c "$CONSULTA_ID" '.data.historicoPaciente[] | select(.consultaId == $c) | .status')
  [ "$S" = "REALIZADA" ] && break
  [ "$i" = 30 ] && fail "status no historico nao virou REALIZADA (veio: ${S:-vazio})"
  sleep 2
done
pass "GraphQL historicoPaciente mostra status REALIZADA"

echo "==> 7. Paciente so ve o proprio historico"
Q3='{"query":"{ historicoPaciente(pacienteId: 4) { consultaId } }"}'
CLASS=$(curl -sf -u "$PACIENTE1" -H 'Content-Type: application/json' -d "$Q3" "$HISTORICO/graphql" \
  | jq -r '.errors[0].extensions.classification // empty')
[ "$CLASS" = "FORBIDDEN" ] || fail "paciente conseguiu consultar historico de outro (classification=$CLASS)"
pass "paciente bloqueado ao pedir historico de outro paciente (FORBIDDEN)"

echo
echo "==> E2E OK - fluxo completo validado."
