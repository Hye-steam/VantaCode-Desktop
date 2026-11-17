#!/usr/bin/env python3
"""本地 mock OpenAI 兼容上游：GET /v1/models、POST /v1/chat/completions（流式/非流式）。"""
import json
import time
from http.server import BaseHTTPRequestHandler, HTTPServer


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def _send(self, code, body, ctype="application/json"):
        data = body.encode() if isinstance(body, str) else body
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path == "/v1/models":
            self._send(200, json.dumps({
                "object": "list",
                "data": [
                    {"id": "mock-gpt-4o", "object": "model", "owned_by": "mock"},
                    {"id": "mock-gpt-4o-mini", "object": "model", "owned_by": "mock"},
                ],
            }))
        else:
            self._send(404, json.dumps({"error": {"message": "not found"}}))

    def do_POST(self):
        if self.path != "/v1/chat/completions":
            self._send(404, json.dumps({"error": {"message": "not found"}}))
            return
        n = int(self.headers.get("Content-Length", 0))
        req = json.loads(self.rfile.read(n))
        stream = req.get("stream", False)
        model = req.get("model", "mock")
        prompt = (req.get("messages") or [{}])[-1].get("content", "")
        reply = f"mock[{model}] 收到：{str(prompt)[:40]}"

        if stream:
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream")
            self.end_headers()
            for i, txt in enumerate(["你好，", "这是 mock 上游的流式回复。", f"（model={model}）"]):
                chunk = {
                    "id": "chatcmpl-mock", "object": "chat.completion.chunk",
                    "created": int(time.time()), "model": model,
                    "choices": [{"index": 0, "delta": {"content": txt}, "finish_reason": None}],
                }
                self.wfile.write(("data: " + json.dumps(chunk) + "\n\n").encode())
                self.wfile.flush()
                time.sleep(0.15)
            self.wfile.write(b"data: [DONE]\n\n")
            self.wfile.flush()
        else:
            self._send(200, json.dumps({
                "id": "chatcmpl-mock", "object": "chat.completion",
                "created": int(time.time()), "model": model,
                "choices": [{
                    "index": 0,
                    "message": {"role": "assistant", "content": reply},
                    "finish_reason": "stop",
                }],
                "usage": {"prompt_tokens": 10, "completion_tokens": 20, "total_tokens": 30},
            }))


if __name__ == "__main__":
    HTTPServer(("127.0.0.1", 18080), Handler).serve_forever()
