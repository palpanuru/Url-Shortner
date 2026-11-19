#!/usr/bin/env python3
"""Minimal redirect latency check (stdlib only). Not a substitute for Gatling/k6 - a sanity gate.

Usage: APP_RATE_LIMIT_ENABLED=false <start app>;  python3 scripts/loadtest.py [requests] [threads]
"""
import http.client
import json
import os
import statistics
import sys
import threading
import time

HOST, PORT = os.environ.get("HOST", "localhost"), int(os.environ.get("PORT", "8080"))
KEY = os.environ.get("API_KEY", "demo-key-change-me")
TOTAL = int(sys.argv[1]) if len(sys.argv) > 1 else 5000
THREADS = int(sys.argv[2]) if len(sys.argv) > 2 else 16


def create_link():
    c = http.client.HTTPConnection(HOST, PORT)
    c.request("POST", "/api/v1/links", json.dumps({"url": "https://example.com/load"}),
              {"Content-Type": "application/json", "X-API-Key": KEY})
    r = c.getresponse()
    assert r.status == 201, r.status
    return json.loads(r.read())["code"]


def worker(code, n, latencies, statuses):
    c = http.client.HTTPConnection(HOST, PORT)
    for _ in range(n):
        t0 = time.perf_counter()
        c.request("GET", "/" + code)
        r = c.getresponse()
        r.read()
        latencies.append((time.perf_counter() - t0) * 1000)
        statuses[r.status] = statuses.get(r.status, 0) + 1


def main():
    code = create_link()
    latencies, statuses = [], {}
    per = TOTAL // THREADS
    threads = [threading.Thread(target=worker, args=(code, per, latencies, statuses))
               for _ in range(THREADS)]
    start = time.perf_counter()
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    elapsed = time.perf_counter() - start
    q = statistics.quantiles(latencies, n=100)
    print(f"redirects={len(latencies)} threads={THREADS} statuses={statuses}")
    print(f"throughput={len(latencies) / elapsed:.0f} req/s  p50={q[49]:.2f}ms  "
          f"p95={q[94]:.2f}ms  p99={q[98]:.2f}ms  max={max(latencies):.2f}ms")
    time.sleep(2)
    c = http.client.HTTPConnection(HOST, PORT)
    c.request("GET", f"/api/v1/links/{code}", headers={"X-API-Key": KEY})
    print("click_count recorded:", json.loads(c.getresponse().read())["clickCount"])


if __name__ == "__main__":
    main()
