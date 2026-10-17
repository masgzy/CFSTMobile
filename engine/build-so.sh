#!/usr/bin/env bash
# ============================================================
# CFSTMobile —— Rust 引擎 .so 一键构建脚本
#
# 产物: app/src/main/jniLibs/<abi>/libcfst.so
#       （arm64-v8a / armeabi-v7a / x86_64）
#
# 依赖:
#   1. Rust (rustup)         https://rustup.rs/
#   2. Android NDK           环境变量 ANDROID_NDK_HOME 指向 NDK 根目录
#   3. cargo-ndk             缺失时本脚本会自动 cargo install
#
# 关键点:
#   - 16KB 页对齐: Android 15+ 的 16KB 页设备要求 .so 按
#     max-page-size=16384 链接，否则加载即崩溃。
#     链接参数已写入 .cargo/config.toml 的各 android target rustflags。
#   - 构建的是 cdylib（默认 feature，不含 cli），panic 策略为 unwind，
#     FFI 边界 catch_unwind 才能生效。
# ============================================================
set -euo pipefail

cd "$(dirname "$0")"

# ---- 环境检查 ----
command -v cargo >/dev/null 2>&1 || { echo "错误: 未安装 Rust (cargo 不在 PATH)"; exit 1; }

if [ -z "${ANDROID_NDK_HOME:-}" ] && [ -n "${ANDROID_NDK_LATEST_HOME:-}" ]; then
  export ANDROID_NDK_HOME="$ANDROID_NDK_LATEST_HOME"
fi
if [ -z "${ANDROID_NDK_HOME:-}" ]; then
  # 尝试常见默认路径
  for d in "$HOME/Android/Sdk/ndk/"* "$HOME/Library/Android/sdk/ndk/"*; do
    [ -d "$d" ] && export ANDROID_NDK_HOME="$d" && break
  done
fi
[ -n "${ANDROID_NDK_HOME:-}" ] || { echo "错误: 未找到 Android NDK，请设置 ANDROID_NDK_HOME"; exit 1; }
echo "NDK: $ANDROID_NDK_HOME"

command -v cargo-ndk >/dev/null 2>&1 || {
  echo "安装 cargo-ndk..."
  cargo install cargo-ndk
}

# rustup 目标（幂等）
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android

# ---- 构建参数 ----
OUTPUT_DIR="../app/src/main/jniLibs"
ABIS=(arm64-v8a armeabi-v7a x86_64)

mkdir -p "$OUTPUT_DIR"

# ---- 编译（release + 16KB 对齐）----
# 注意：必须显式 -t 列全三 ABI。实测 cargo-ndk 不带 -t 时按环境自动取舍，
# 在 CI 上只构建了 arm64/armv7 而跳过 x86_64，导致产物校验失败。
cargo ndk -o "$OUTPUT_DIR" \
  -t arm64-v8a -t armeabi-v7a -t x86_64 \
  --platform 24 build --release

echo
echo "✅ 生成完成:"
for abi in "${ABIS[@]}"; do
  so="$OUTPUT_DIR/$abi/libcfst.so"
  [ -f "$so" ] || { echo "   缺失: $so"; exit 1; }
  SIZE=$(du -h "$so" | cut -f1)
  echo "   $so ($SIZE)"
done

echo
echo "校验 16KB 对齐（期望 LOAD 段对齐 0x4000）:"
for abi in "${ABIS[@]}"; do
  so="$OUTPUT_DIR/$abi/libcfst.so"
  READER="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf"
  [ -x "$READER" ] || READER=readelf
  echo "  [$abi]"
  "$READER" -l "$so" | grep -E 'LOAD' | awk '{print "    " $0}' | head -4
done
