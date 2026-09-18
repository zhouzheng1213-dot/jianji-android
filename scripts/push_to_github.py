#!/usr/bin/env python3
"""把本目录的源码推送到 GitHub 仓库（走 WorkBuddy 的 GitHub Connector）。

为什么需要它：GitHub 的文件写入接口是纯文本通道，二进制文件（Gradle wrapper jar、
签名库）无法直接承载，所以它们在仓库里以 .b64 形式存在 —— 本脚本只推送文本文件，
二进制由 scripts/bootstrap-binaries.sh 在构建前还原。

用法：
    python3 scripts/push_to_github.py <owner> <repo> [branch]

仓库必须已经存在（本脚本只能写内容，不能建仓库）。空仓库也能用：会先落一个
初始提交，之后的批量推送才有 base 可以挂。

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

    def tool(self, name, arguments):
        return self.call("tools/call", {"name": name, "arguments": arguments})

    def ensure_initial_commit(self, owner, repo, branch):
        """空仓库（一次提交都没有）时，push_files 拿不到 base commit，挂不上去。

        这里先探一次分支：没有分支就用 Contents 接口落一个初始提交，
        让后面的批量推送有个可以挂的 base。
        """
        res = self.tool("list_branches", {"owner": owner, "repo": repo})
        if tool_failed(res):
            print("  [预检] 列分支失败，跳过空仓库处理：" + tool_text(res)[:200])
            return
        try:
            names = [b.get("name") for b in json.loads(tool_text(res)) if b.get("name")]
        except (json.JSONDecodeError, TypeError, AttributeError):
            names = None
        if names:
            print(f"  [预检] 仓库已有分支：{', '.join(names)}")
            return

        print(f"  [预检] 仓库还没有任何提交，先在 {branch} 上落一个初始提交")
        boot = self.tool("create_or_update_file", {
            "owner": owner, "repo": repo, "branch": branch,
            "path": "README.md",
            "content": "# 简记\n\n初始提交。随后的推送会覆盖本文件。\n",
            "message": "初始化仓库",
        })
        if tool_failed(boot):
            print("  [预检] 初始提交失败：" + tool_text(boot)[:300])
            print("  提示：在 GitHub 上建仓库时勾选 “Add a README file”，或先手工建一个初始提交。")
        else:
            print("  [预检] 初始提交完成")

    def push(self, owner, repo, branch, files, message):
        return self.tool("push_files", {
            "owner": owner, "repo": repo, "branch": branch,
            "files": files, "message": message,
        })


def tool_text(res):
    """把 tools/call 结果里那段 text 抠出来。"""
    try:
        for item in res.get("result", {}).get("content", []):
            if item.get("type") == "text":
                return item.get("text", "")
    except AttributeError:
        pass
    return json.dumps(res, ensure_ascii=False)


def tool_failed(res):
    text = json.dumps(res, ensure_ascii=False)
    return '"isError": true' in text or '"http_error"' in text


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
    client.ensure_initial_commit(owner, repo, branch)

    for i in range(0, len(files), BATCH_SIZE):
        batch = files[i:i + BATCH_SIZE]
        n = i // BATCH_SIZE + 1
        message = f"简记 1.0.0：源码与 CI（第 {n} 批）" if n > 1 else "简记 1.0.0：纯本地离线记账 App（Kotlin + Compose + Room）"
        res = client.push(owner, repo, branch, batch, message)
        if tool_failed(res):
            print(f"  第 {n} 批失败：{tool_text(res)[:500]}")
            sys.exit(1)
        print(f"  第 {n} 批完成（{len(batch)} 个文件）")

    print("推送完成。")
    print(f"仓库地址：https://github.com/{owner}/{repo}")


if __name__ == "__main__":
    main()
