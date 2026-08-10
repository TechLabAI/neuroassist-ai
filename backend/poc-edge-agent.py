#!/usr/bin/env python3
"""
PoC Edge Agent — simula o agente local detectando um gesto e enviando para EVNT.

Pré-requisitos:
  pip install requests

Uso:
  python poc-edge-agent.py
  python poc-edge-agent.py --repeat          # reenvia o mesmo reportId (testa idempotência)
  python poc-edge-agent.py --confirm <id>    # confirma um evento existente
"""
import argparse
import json
import uuid
import sys
from datetime import datetime, timezone

try:
    import requests
except ImportError:
    print("Erro: biblioteca 'requests' não encontrada. Execute: pip install requests")
    sys.exit(1)

EVNT_BASE   = "http://localhost:8083/api/v1"
PACIENTE_ID  = "paciente-marcio-fixo"
COMMAND_REF  = "cmd-preciso-ajuda-fixo"
TIPO_GESTO_ID = "tg-piscar-duplo-001"
PROFILE_VERSION = 1
DEVICE_ID    = "device-quarto-marcio-fixo"

# reportId fixo para teste de idempotência
FIXED_REPORT_ID = "report-poc-001-fixo"


def send_report(report_id: str) -> dict:
    report = {
        "reportId":       report_id,
        "pacienteId":     PACIENTE_ID,
        "commandRef":     COMMAND_REF,
        "primitives":     [{"tipoGestoId": TIPO_GESTO_ID, "confidence": 0.91}],
        "confidence":     0.91,
        "profileVersion": PROFILE_VERSION,
        "timingWindow":   {"startMs": 1000, "endMs": 1850, "durationMs": 850},
        "recognizedAt":   datetime.now(timezone.utc).isoformat(),
        "deviceId":       DEVICE_ID,
    }
    headers = {
        "X-Correlation-Id": str(uuid.uuid4()),
        "Content-Type":     "application/json",
    }

    print(f"\n{'='*60}")
    print(f"Enviando report para EVNT...")
    print(f"  reportId: {report_id}")
    print(f"  endpoint: POST {EVNT_BASE}/reports")
    print(f"{'='*60}")

    r = requests.post(f"{EVNT_BASE}/reports", json=report, headers=headers, timeout=10)
    print(f"\nStatus HTTP: {r.status_code}")

    try:
        body = r.json()
        print(json.dumps(body, indent=2, ensure_ascii=False))
    except Exception:
        print(r.text)
        body = {}

    if r.status_code == 201:
        evento_id = body.get("eventoId")
        tier = body.get("notification", {}).get("tier", "?")
        print(f"\n✓ Evento criado com sucesso!")
        print(f"  eventoId : {evento_id}")
        print(f"  tier     : {tier}")
        print(f"\nPróximo passo — verificar alarme:")
        print(f"  curl -s {EVNT_BASE}/events/active | python -m json.tool")
        print(f"\nPróximo passo — confirmar evento:")
        print(f"  python poc-edge-agent.py --confirm {evento_id}")
    elif r.status_code == 200:
        print(f"\nℹ  Report duplicado (idempotência OK) — mesmo reportId já processado.")
    else:
        print(f"\n✗ Falha ao enviar report.")

    return body


def check_active():
    print(f"\n{'='*60}")
    print(f"Consultando eventos ativos...")
    print(f"  endpoint: GET {EVNT_BASE}/events/active")
    print(f"{'='*60}")

    r = requests.get(f"{EVNT_BASE}/events/active", timeout=10)
    print(f"\nStatus HTTP: {r.status_code}")

    try:
        body = r.json()
        print(json.dumps(body, indent=2, ensure_ascii=False))
    except Exception:
        print(r.text)


def confirm_event(evento_id: str, verdict: str = "real"):
    url = f"{EVNT_BASE}/events/{evento_id}/confirmation"
    print(f"\n{'='*60}")
    print(f"Confirmando evento...")
    print(f"  endpoint: POST {url}")
    print(f"  verdict : {verdict}")
    print(f"{'='*60}")

    headers = {"Content-Type": "application/json"}
    r = requests.post(url, json={"verdict": verdict}, headers=headers, timeout=10)
    print(f"\nStatus HTTP: {r.status_code}")

    try:
        body = r.json()
        print(json.dumps(body, indent=2, ensure_ascii=False))
    except Exception:
        print(r.text)

    if r.status_code == 200:
        print(f"\n✓ Evento confirmado com sucesso! verdict={verdict}")


def main():
    parser = argparse.ArgumentParser(description="PoC Edge Agent — NeuroAssist AI")
    parser.add_argument("--repeat",  action="store_true", help="Reenvia reportId fixo (teste de idempotência)")
    parser.add_argument("--confirm", metavar="EVENTO_ID",  help="Confirma um evento existente")
    parser.add_argument("--active",  action="store_true", help="Apenas consulta eventos ativos")
    parser.add_argument("--verdict", default="real",       help="Verdict para confirmação: real | falso_positivo")
    args = parser.parse_args()

    if args.active:
        check_active()
    elif args.confirm:
        confirm_event(args.confirm, args.verdict)
    elif args.repeat:
        send_report(FIXED_REPORT_ID)
    else:
        send_report(str(uuid.uuid4()))


if __name__ == "__main__":
    main()
