#!/usr/bin/env python3
"""把本目录的源码推送到 GitHub 仓库（走 WorkBuddy 的 GitHub Connector）。

为什么需要它：GitHub 的文件写入接口是纯文本通道，二进制文件（Gradle wrapper jar、
签名库）无法直接承载，所以它们在仓库里以 .b64 形式存在 —— 本脚本只推送文本文件，
二进制由 scripts/bootstrap-binaries.sh 在构建前还原。

用法：
    python3 scripts/push_to_github.py <owner> <repo> [branch]

环境要求：CODEBUDDY_MCP_CONFIG 中包含 github 这个 MCP server（由 WorkBuddy 注入）。
"""
import json
import os
import sys
import urllib.error
import urllib.request

SERVER = "github"
BATCH_SIZE = 12

SKIP_DIRS = {
    ".git", ".gradle", ".idea", "build", "out", "captures",
    ".externalNativeBuild", ".cxx", "node_modules",
}
SKIP_FILES = {"local.properties", "gradle-wrapper.jar", "build-report.json"}
SKIP_SUFFIX = (".jks", ".apk", ".jar", ".keystore", ".png", ".webp", ".zip")


def mcp():
    cfg = json.loads(os.environ["CODEBUDDY_MCP_CONFIG"])
    entry = cfg["mcpServers"][SERVER]
    url = entry["url"]
    headers = dict(entry["headers"])
    headers["Content-Type"] = "application/json"
    headers["Accept"] = "application/json, text/event-stream"
    return url, headers


def parse_body(raw):
    raw = raw.strip()
    if not raw:
        return None
    if raw.startswith("{"):
        return json.loads(raw)
    out = None
    for line in raw.splitlines():
        if line.strip().startswith("data:"):
            payload = line.strip()[5:].strip()
            if payload:
                try:
                    out = json.loads(payload)
                except json.JSONDecodeError:
                    pass
    return out


class Client:
    def __init__(self):
        self.url, self.headers = mcp()
        self.session = None
        self._id = 0

    def call(self, method, params=None, notify=False):
        self._id += 1
        body = {"jsonrpc": "2.0", "method": method}
        if not notify:
            body["id"] = self._id
        if params is not None:
            body["params"] = params
        headers = dict(self.headers)
        if self.session:
            headers["Mcp-Session-Id"] = self.session
        req = urllib.request.Request(
            self.url, data=json.dumps(body).encode("utf-8"), headers=headers, method="POST"
        )
        try:
            with urllib.request.urlopen(req, timeout=180) as resp:
                self.session = resp.headers.get("Mcp-Session-Id") or self.session
                return parse_body(resp.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            return {"http_error": e.code, "body": e.read().decode("utf-8", "replace")[:800]}

    def init(self):
        self.call("initialize", {
            "protocolVersion": "2025-06-18",
            "capabilities": {},
            "clientInfo": {"name": "jianji-push", "version": "1.0"},
        })
        self.call("notifications/initialized", {}, notify=True)

    def push(self, owner, repo, branch, files, message):
        return self.call("tools/call", {
            "name": "push_files",
            "arguments": {
                "owner": owner, "repo": repo, "branch": branch,
                "files": files, "message": message,
            },
        })


def collect(root):
    out = []
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for name in sorted(filenames):
            if name in SKIP_FILES or name.endswith(SKIP_SUFFIX):
                continue
            full = os.path.join(dirpath, name)
            rel = os.path.relpath(full, root)
            try:
                with open(full, "r", encoding="utf-8") as fh:
                    out.append({"path": rel, "content": fh.read()})
            except UnicodeDecodeError:
                print(f"  跳过二进制文件：{rel}")
    return out


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        sys.exit(2)
    owner, repo = sys.argv[1], sys.argv[2]
    branch = sys.argv[3] if len(sys.argv) > 3 else "main"
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

    files = collect(root)
    print(f"待推送 {len(files)} 个文件 -> {owner}/{repo}@{branch}")

    client = Client()
    client.init()

    for i in range(0, len(files), BATCH_SIZE):
        batch = files[i:i + BATCH_SIZE]
        n = i // BATCH_SIZE + 1
        message = f"简记 1.0.0：源码与 CI（第 {n} 批）" if n > 1 else "简记 1.0.0：纯本地离线记账 App（Kotlin + Compose + Room）"
        res = client.push(owner, repo, branch, batch, message)
        text = json.dumps(res, ensure_ascii=False)
        if "isError" in text and '"isError": true' in text:
            print(f"  第 {n} 批失败：{text[:500]}")
            sys.exit(1)
        print(f"  第 {n} 批完成（{len(batch)} 个文件）")

    print("推送完成。")
    print(f"仓库地址：https://github.com/{owner}/{repo}")


if __name__ == "__main__":
    main()
