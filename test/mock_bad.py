#!/usr/bin/env python3
"""坏上游：恒 500，用于验证 Failover 换号。"""
import json
from http.server import BaseHTTPRequestHandler, HTTPServer


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def do_GET(self):
        self._send(500)

    def do_POST(self):
        self._send(500)

    def _send(self, code):
        body = json.dumps({"error": {"message": "mock upstream 500"}})
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body.encode())


if __name__ == "__main__":
    HTTPServer(("127.0.0.1", 18081), Handler).serve_forever()
