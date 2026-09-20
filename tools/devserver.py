#!/usr/bin/env python3
"""Static dev server that does not cache.

`python -m http.server` sends no cache headers, so Chrome caches ES modules
aggressively and an edit does not show on reload. That wastes more time than
it saves, because the failure looks like a bug in the code you just wrote.
"""
import sys
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer


class NoCacheHandler(SimpleHTTPRequestHandler):
    extensions_map = {
        **SimpleHTTPRequestHandler.extensions_map,
        ".js": "text/javascript",
        ".mjs": "text/javascript",
        ".json": "application/json",
        ".css": "text/css",
    }

    def end_headers(self):
        self.send_header("Cache-Control", "no-store, must-revalidate")
        self.send_header("Pragma", "no-cache")
        super().end_headers()

    def log_message(self, fmt, *args):
        if "GET" in (args[0] if args else ""):
            return
        super().log_message(fmt, *args)


def main() -> int:
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 5173
    host = sys.argv[2] if len(sys.argv) > 2 else "127.0.0.1"
    server = ThreadingHTTPServer((host, port), partial(NoCacheHandler, directory="."))
    print(f"serving . on http://{host}:{port} (no-store)")
    server.serve_forever()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
