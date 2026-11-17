#!/usr/bin/env python3
"""凭证失效上游：恒 401，验证网关「凭证失效硬禁用」账号。"""
import json
from http.server import BaseHTTPRequestHandler, HTTPServer


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def _send(self, code, msg):
        body = json.dumps({"error": {"message": msg, "type": "authentication_error"}})
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body.encode())

    def do_GET(self):
        self._send(401, "invalid api key (mock)")

    def do_POST(self):
        self._send(401, "invalid api key (mock)")


if __name__ == "__main__":
    HTTPServer(("127.0.0.1", 18082), Handler).serve_forever()
