#!/bin/sh
# 一键冒烟测试：起三个 mock 上游 + 网关，验证核心链路。
# 依赖：JDK 17、python3、curl。在项目根目录执行：sh test/smoke.sh
set -e
cd "$(dirname "$0")/.."

DATA=$(mktemp -d)
PORT=18790

cleanup() {
  kill "$M1" "$M2" "$M3" "$GW" 2>/dev/null || true
  rm -rf "$DATA"
}
trap cleanup EXIT INT TERM

python3 test/mock_openai.py >/dev/null 2>&1 & M1=$!
python3 test/mock_bad.py >/dev/null 2>&1 & M2=$!
python3 test/mock_401.py >/dev/null 2>&1 & M3=$!
sleep 1

java -jar aigw.jar add --name bad  --base-url http://127.0.0.1:18081/v1 --key k --uid bad1  --data "$DATA" >/dev/null
java -jar aigw.jar add --name mock --base-url http://127.0.0.1:18080/v1 --key k --uid mock1 --data "$DATA" >/dev/null
java -jar aigw.jar add --name auth --base-url http://127.0.0.1:18082/v1 --key k --uid auth1 --data "$DATA" >/dev/null

java -jar aigw.jar serve --data "$DATA" --port "$PORT" >/dev/null 2>&1 & GW=$!
sleep 1

pass=0
fail=0
check() {
  if [ "$2" = "$3" ]; then
    echo "  PASS  $1"
    pass=$((pass + 1))
  else
    echo "  FAIL  $1 (期望 $3, 实际 $2)"
    fail=$((fail + 1))
  fi
}

echo "[1] 健康检查"
check "healthz 返回 ok" "$(curl -s "http://127.0.0.1:$PORT/healthz")" "ok"

echo "[2] 模型目录"
MODELS=$(curl -s "http://127.0.0.1:$PORT/v1/models" | grep -c '"id"')
check "拉到 2 个模型" "$MODELS" "2"

echo "[3] 非流式 + Failover（坏账号在前）"
BODY='{"model":"custom/mock-gpt-4o","messages":[{"role":"user","content":"hi"}]}'
CONTENT=$(curl -s -X POST "http://127.0.0.1:$PORT/v1/chat/completions" -H "Content-Type: application/json" -d "$BODY" | grep -c 'mock\[mock-gpt-4o\]')
check "换号后成功" "$CONTENT" "1"

echo "[4] 流式 SSE"
FRAMES=$(curl -sN -X POST "http://127.0.0.1:$PORT/v1/chat/completions" -H "Content-Type: application/json" -d '{"model":"custom:mock1/mock-gpt-4o","stream":true,"messages":[{"role":"user","content":"hi"}]}' | grep -c '^data:')
check "收到 4 帧（3 chunk + DONE）" "$FRAMES" "4"

echo "[5] 凭证失效硬禁用"
CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "http://127.0.0.1:$PORT/v1/chat/completions" -H "Content-Type: application/json" -d '{"model":"custom:auth1/mock-gpt-4o","messages":[{"role":"user","content":"hi"}]}')
check "401 上游返回 502" "$CODE" "502"
DISABLED=$(java -jar aigw.jar accounts --data "$DATA" | grep auth1 | grep -c "启用=false")
check "账号被禁用" "$DISABLED" "1"

echo ""
echo "结果：$pass 通过，$fail 失败"
[ "$fail" -eq 0 ]
