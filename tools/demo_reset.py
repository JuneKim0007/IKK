#!/usr/bin/env python3
"""Reset a project to a fixture contract.

`PUT /contract` rejects a submission whose checkpoint is older than the one
the project already holds (docs/api.md), and a fixture on disk is always
older than a project that has been generated a few times. So a reset cannot
just replay the fixture: it has to re-stamp it with a checkpoint newer than
whatever the project is on.

Usage: demo_reset.py <base-url> <project> <fixture.json>
"""
import json
import re
import sys
import urllib.error
import urllib.request


def request(method, url, payload=None):
    body = json.dumps(payload).encode() if payload is not None else None
    headers = {"content-type": "application/json"} if body else {}
    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=10) as res:
            return res.status, json.loads(res.read() or b"{}")
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read() or b"{}")
    except urllib.error.URLError as e:
        print(f"  cannot reach {url} ({e.reason}) - run 'make backend'", file=sys.stderr)
        sys.exit(1)


def next_checkpoint(current):
    """cp_009 -> cp_010. Unrecognised shapes fall back to cp_001."""
    m = re.fullmatch(r"(.*?)(\d+)", current or "")
    if not m:
        return "cp_001"
    prefix, digits = m.groups()
    return f"{prefix}{int(digits) + 1:0{len(digits)}d}"


def main():
    base, project, fixture = sys.argv[1], sys.argv[2], sys.argv[3]
    contract = json.load(open(fixture))

    status, current = request("GET", f"{base}/v1/projects/{project}/contract")
    # 404 simply means the project does not exist yet; the fixture's own
    # checkpoint is then already new enough.
    if status == 200:
        contract["checkpoint"] = next_checkpoint(current.get("checkpoint"))

    status, body = request("PUT", f"{base}/v1/projects/{project}/contract", contract)
    if status != 200:
        print(f"  reset failed (HTTP {status}): {body.get('message', body)}", file=sys.stderr)
        sys.exit(1)

    print(f"  reset {body['projectId']} -> {body['checkpoint']} ({body['components']} components)")


if __name__ == "__main__":
    main()
