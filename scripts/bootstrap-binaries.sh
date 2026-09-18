#!/usr/bin/env bash
# 把随源码一起托管的 base64 文件还原成二进制。
#
# 为什么需要这一步：本仓库的源码是通过 GitHub API（纯文本通道）写入的，
# gradle-wrapper.jar 与签名库 jks 都是二进制，无法直接承载。
# 因此它们在仓库里以 .b64 形式存在，构建前需要先解码。
#
# 全新 clone 后执行一次即可：
#   bash scripts/bootstrap-binaries.sh
set -euo pipefail

cd "$(dirname "$0")/.."

decode() {
  local b64="$1" out="$2"
  if [ -f "$out" ]; then
    echo "跳过（已存在）：$out"
    return 0
  fi
  if [ ! -f "$b64" ]; then
    echo "缺少 $b64" >&2
    return 1
  fi
  # macOS 与 Linux 的 base64 参数名不同，这里都兼容。
  if base64 --decode </dev/null >/dev/null 2>&1; then
    base64 --decode "$b64" > "$out"
  else
    base64 -D -i "$b64" -o "$out"
  fi
  echo "已还原：$out"
}

decode "gradle/wrapper/gradle-wrapper.jar.b64" "gradle/wrapper/gradle-wrapper.jar"
decode "keystore/jianji.jks.b64" "keystore/jianji.jks"

chmod +x gradlew 2>/dev/null || true
echo "完成。"
