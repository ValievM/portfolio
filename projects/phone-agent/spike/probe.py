#!/usr/bin/env python3
"""Stage 0 spike (throwaway): probe ChatGPT-subscription login and the Codex backend.

Constants come from the open-source openai/codex repo (see docs/backend-notes.md).
Tokens are stored in spike/auth.json (gitignored). Nothing secret is ever printed.

  python3 spike/probe.py login                  browser PKCE login on 127.0.0.1:1455
  python3 spike/probe.py status                 show token info (no network)
  python3 spike/probe.py refresh                refresh the tokens, report rotation
  python3 spike/probe.py models                 list model slugs
  python3 spike/probe.py chat [--model SLUG] [--originator VALUE] [--drop-reasoning]
"""
import argparse
import base64
import hashlib
import http.server
import json
import os
import re
import secrets
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
import webbrowser
from pathlib import Path

ISSUER = "https://auth.openai.com"
CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann"
PORT = 1455
REDIRECT_URI = f"http://localhost:{PORT}/auth/callback"
SCOPE = "openid profile email offline_access api.connectors.read api.connectors.invoke"
BASE_URL = "https://chatgpt.com/backend-api/codex"
DEFAULT_ORIGINATOR = "codex_cli_rs"
CLIENT_VERSION = "0.153.3"

HERE = Path(__file__).resolve().parent
AUTH_FILE = HERE / "auth.json"
SSE_FILE = HERE / "sample_sse.txt"

JWT_RE = re.compile(r"eyJ[\w-]+\.[\w-]+\.[\w-]+")


def say(*a):
    print(*a, flush=True)


def redact(text):
    text = JWT_RE.sub("<jwt>", text)
    return re.sub(r"Bearer\s+\S+", "Bearer <redacted>", text)


def b64url(raw):
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode()


def jwt_payload(token):
    try:
        part = token.split(".")[1]
        part += "=" * (-len(part) % 4)
        return json.loads(base64.urlsafe_b64decode(part))
    except Exception:
        return {}


def save_tokens(tokens):
    data = {
        "id_token": tokens.get("id_token"),
        "access_token": tokens.get("access_token"),
        "refresh_token": tokens.get("refresh_token"),
        "obtained_at": int(time.time()),
    }
    AUTH_FILE.write_text(json.dumps(data), encoding="utf-8")
    os.chmod(AUTH_FILE, 0o600)
    return data


def load_tokens():
    if not AUTH_FILE.exists():
        sys.exit("No spike/auth.json. Run: python3 spike/probe.py login")
    return json.loads(AUTH_FILE.read_text(encoding="utf-8"))


def auth_claims(tokens):
    for key in ("id_token", "access_token"):
        claims = jwt_payload(tokens.get(key) or "")
        auth = claims.get("https://api.openai.com/auth")
        if auth:
            return claims, auth
    return {}, {}


def account_id(tokens):
    return auth_claims(tokens)[1].get("chatgpt_account_id")


def describe(tokens):
    access = jwt_payload(tokens.get("access_token") or "")
    _, auth = auth_claims(tokens)
    exp = access.get("exp")
    acc = account_id(tokens) or ""
    say("token fields present:", [k for k in ("id_token", "access_token", "refresh_token") if tokens.get(k)])
    say("access_token expires in (s):", (exp - int(time.time())) if exp else "unknown")
    say("plan:", auth.get("chatgpt_plan_type"), "| account id:", (acc[:6] + "...") if acc else None)
    say("id_token claim paths:", sorted(k for k in jwt_payload(tokens.get("id_token") or "") if "openai" in k))


def post_form(url, params):
    req = urllib.request.Request(
        url,
        data=urllib.parse.urlencode(params).encode(),
        headers={"Content-Type": "application/x-www-form-urlencoded"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.status, json.loads(resp.read())
    except urllib.error.HTTPError as e:
        return e.code, {"error_body": redact(e.read().decode(errors="replace")[:400])}


def login():
    verifier = b64url(secrets.token_bytes(64))
    challenge = b64url(hashlib.sha256(verifier.encode()).digest())
    state = b64url(secrets.token_bytes(32))
    params = {
        "response_type": "code",
        "client_id": CLIENT_ID,
        "redirect_uri": REDIRECT_URI,
        "scope": SCOPE,
        "code_challenge": challenge,
        "code_challenge_method": "S256",
        "id_token_add_organizations": "true",
        "codex_cli_simplified_flow": "true",
        "state": state,
        "originator": DEFAULT_ORIGINATOR,
    }
    url = f"{ISSUER}/oauth/authorize?" + urllib.parse.urlencode(params)
    result = {}

    class Handler(http.server.BaseHTTPRequestHandler):
        def do_GET(self):
            parsed = urllib.parse.urlparse(self.path)
            if parsed.path != "/auth/callback":
                self.send_response(404)
                self.end_headers()
                return
            result.update({k: v[0] for k, v in urllib.parse.parse_qs(parsed.query).items()})
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.end_headers()
            self.wfile.write("<h3>Готово. Вкладку можно закрыть.</h3>".encode())

        def log_message(self, *args):
            pass

    server = http.server.HTTPServer(("127.0.0.1", PORT), Handler)
    server.timeout = 1
    say("Opening browser for login (180 s). If it does not open, visit:")
    say(url)
    webbrowser.open(url)
    deadline = time.time() + 180
    while not result and time.time() < deadline:
        server.handle_request()
    server.server_close()
    if not result:
        sys.exit("Timed out waiting for the callback")
    if result.get("state") != state:
        sys.exit("State mismatch, aborting")
    if "code" not in result:
        sys.exit(f"Callback without code: {sorted(result)}")
    status, tokens = post_form(f"{ISSUER}/oauth/token", {
        "grant_type": "authorization_code",
        "client_id": CLIENT_ID,
        "code": result["code"],
        "redirect_uri": REDIRECT_URI,
        "code_verifier": verifier,
    })
    say("token exchange status:", status)
    if status != 200:
        sys.exit(f"Exchange failed: {tokens}")
    describe(save_tokens(tokens))


def refresh():
    old = load_tokens()
    status, tokens = post_form(f"{ISSUER}/oauth/token", {
        "grant_type": "refresh_token",
        "client_id": CLIENT_ID,
        "refresh_token": old["refresh_token"],
    })
    say("refresh status:", status)
    if status != 200:
        sys.exit(f"Refresh failed: {tokens}")
    tokens.setdefault("id_token", old.get("id_token"))
    tokens.setdefault("refresh_token", old["refresh_token"])
    new = save_tokens(tokens)
    say("refresh_token rotated:", new["refresh_token"] != old["refresh_token"])
    say("response fields:", sorted(k for k in tokens if k not in ("access_token", "refresh_token", "id_token")))
    describe(new)


def fresh_tokens():
    tokens = load_tokens()
    exp = jwt_payload(tokens.get("access_token") or "").get("exp", 0)
    if exp - time.time() < 60:
        say("access token is stale, refreshing")
        refresh()
        tokens = load_tokens()
    return tokens


def headers(tokens, originator, session_id, accept):
    h = {
        "Authorization": f"Bearer {tokens['access_token']}",
        "originator": originator,
        "User-Agent": f"{originator}/{CLIENT_VERSION} (Mac OS; arm64) spike",
        "version": CLIENT_VERSION,
        "Accept": accept,
        "Content-Type": "application/json",
        "session-id": session_id,
        "x-client-request-id": str(uuid.uuid4()),
    }
    acc = account_id(tokens)
    if acc:
        h["ChatGPT-Account-ID"] = acc
    return h


def http_json(url, hdrs):
    req = urllib.request.Request(url, headers=hdrs)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.status, json.loads(resp.read())
    except urllib.error.HTTPError as e:
        return e.code, {"error_body": redact(e.read().decode(errors="replace")[:400])}


def list_models(tokens, originator=DEFAULT_ORIGINATOR):
    url = f"{BASE_URL}/models?client_version={CLIENT_VERSION}"
    status, data = http_json(url, headers(tokens, originator, str(uuid.uuid4()), "application/json"))
    slugs = []
    if isinstance(data, dict):
        items = data.get("models") or data.get("data") or []
        slugs = [m.get("slug") or m.get("id") for m in items if isinstance(m, dict)]
    return status, data, [s for s in slugs if s]


def models():
    status, data, slugs = list_models(fresh_tokens())
    say("models status:", status)
    if status != 200:
        say(data)
    say("model slugs:", slugs)
    if status == 200 and not slugs:
        say("response top-level keys:", sorted(data) if isinstance(data, dict) else type(data).__name__)


def shorten(line):
    return re.sub(r'("encrypted_content"\s*:\s*")([^"]{40})[^"]*(")', r"\1\2...(truncated)\3", line)


def stream_request(tokens, body, originator, sse_out, label):
    sid = body["prompt_cache_key"]
    req = urllib.request.Request(
        f"{BASE_URL}/responses",
        data=json.dumps(body).encode(),
        headers=headers(tokens, originator, sid, "text/event-stream"),
        method="POST",
    )
    items, text, events, error = [], [], {}, None
    try:
        resp = urllib.request.urlopen(req, timeout=120)
    except urllib.error.HTTPError as e:
        return {"http_status": e.code, "error": redact(e.read().decode(errors="replace")[:600])}
    say(f"[{label}] http status:", resp.status, "| content-type:", resp.headers.get("Content-Type"))
    sse_out.write(f"### {label}\n")
    event = None
    for raw in resp:
        line = raw.decode(errors="replace").rstrip("\n")
        sse_out.write(redact(shorten(line)) + "\n")
        if line.startswith("event:"):
            event = line[6:].strip()
        elif line.startswith("data:"):
            payload = line[5:].strip()
            try:
                data = json.loads(payload)
            except ValueError:
                continue
            etype = data.get("type") or event
            events[etype] = events.get(etype, 0) + 1
            if etype == "response.output_item.done":
                items.append(data.get("item"))
            elif etype == "response.output_text.delta":
                text.append(data.get("delta", ""))
            elif etype in ("response.failed", "error"):
                error = redact(json.dumps(data)[:500])
    return {"items": items, "text": "".join(text), "events": events, "error": error}


def strip_ids(items):
    return [{k: v for k, v in it.items() if k != "id"} for it in items]


def chat(args):
    tokens = fresh_tokens()
    model = args.model
    if not model:
        status, _, slugs = list_models(tokens, args.originator)
        say("models status:", status, "| slugs:", slugs)
        if not slugs:
            sys.exit("No model slug found; pass --model")
        model = slugs[0]
    say("using model:", model, "| originator:", args.originator)
    session_id = str(uuid.uuid4())
    tools = [{
        "type": "function",
        "name": "get_local_time",
        "description": "Returns the current local time for a city.",
        "parameters": {
            "type": "object",
            "properties": {"city": {"type": "string"}},
            "required": ["city"],
            "additionalProperties": False,
        },
        "strict": False,
    }]
    user_msg = {"type": "message", "role": "user",
                "content": [{"type": "input_text", "text": "Который час в Москве? Обязательно используй инструмент."}]}

    def body(input_items):
        return {
            "model": model,
            "instructions": "You are a test assistant. Use the tool when asked about time. Answer briefly in Russian.",
            "input": input_items,
            "tools": tools,
            "tool_choice": "auto",
            "parallel_tool_calls": False,
            "reasoning": {"effort": args.effort},
            "store": False,
            "stream": True,
            "include": ["reasoning.encrypted_content"],
            "prompt_cache_key": session_id,
        }

    with SSE_FILE.open("w", encoding="utf-8") as out:
        first = stream_request(tokens, body([user_msg]), args.originator, out, "turn 1")
        if "http_status" in first:
            say("turn 1 failed:", first)
            return
        say("turn 1 events:", first["events"])
        say("turn 1 item types:", [i.get("type") for i in first["items"]])
        if first["error"]:
            say("turn 1 error event:", first["error"])
        calls = [i for i in first["items"] if i.get("type") == "function_call"]
        if not calls:
            say("no function_call returned; text:", first["text"][:200])
            return
        call = calls[0]
        say("function_call:", {k: call.get(k) for k in ("name", "arguments", "call_id")})
        echoed = [i for i in first["items"] if not (args.drop_reasoning and i.get("type") == "reasoning")]
        output = {"type": "function_call_output", "call_id": call["call_id"], "output": "14:32"}
        variants = [("as-is", [user_msg] + echoed + [output]), ("ids-stripped", [user_msg] + strip_ids(echoed) + [output])]
        for name, items in variants:
            second = stream_request(tokens, body(items), args.originator, out, f"turn 2 ({name})")
            if "http_status" in second:
                say(f"turn 2 ({name}) failed:", second["http_status"], second["error"])
                continue
            say(f"turn 2 ({name}) events:", second["events"])
            say(f"turn 2 ({name}) final text:", second["text"][:200])
            if second["error"]:
                say(f"turn 2 ({name}) error event:", second["error"])
            else:
                break
    say("raw SSE sample saved to spike/sample_sse.txt (tokens redacted, encrypted blobs truncated)")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("mode", choices=["login", "status", "refresh", "models", "chat"])
    ap.add_argument("--model")
    ap.add_argument("--originator", default=DEFAULT_ORIGINATOR)
    ap.add_argument("--effort", default="low")
    ap.add_argument("--drop-reasoning", action="store_true", help="do not echo reasoning items back in turn 2")
    args = ap.parse_args()
    if args.mode == "login":
        login()
    elif args.mode == "status":
        describe(load_tokens())
    elif args.mode == "refresh":
        refresh()
    elif args.mode == "models":
        models()
    else:
        chat(args)


if __name__ == "__main__":
    main()
