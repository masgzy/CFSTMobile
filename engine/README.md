# CFSTMobile 引擎（Rust）

Android 原生 Cloudflare 优选测速核心引擎。编译为 `libcfst.so`（cdylib），通过
C ABI 供 Kotlin/JNI 调用；亦可 `--features cli` 构建桌面命令行（交互形态与上游一致）。

- **来源**：改造自 [GuangYu-yu/CloudflareST-Rust](https://github.com/GuangYu-yu/CloudflareST-Rust)
  （其本身是 [XIU2/CloudflareSpeedTest](https://github.com/XIU2/CloudflareSpeedTest) 的 Rust 重写）
- **许可**：GPL-3.0（整库沿用；衍生关系声明见根目录 LICENSE）
- **测速算法**：与上游一致——延迟为多次探测算术平均，下载速度为
  500ms 区间采样 + 中位数-MAD 高斯加权，三维 z-score（速度/延迟/丢包）综合排序

---

## 一、构建

### 本地构建 .so（产出三 ABI，自动 16KB 页对齐）

```bash
# 依赖：rustup、Android NDK（ANDROID_NDK_HOME）、cargo-ndk（缺失自动安装）
./build-so.sh
# 产物：../app/src/main/jniLibs/<abi>/libcfst.so
#       arm64-v8a / armeabi-v7a / x86_64
```

### 桌面 CLI（调试用）

```bash
cargo build --release --features cli
./target/release/cfst -ip 1.1.1.1 -dd
```

### 16KB 页对齐

Android 15+ 的 16KB 页设备要求 `.so` 按 `max-page-size=16384` 链接，
参数已写入 `.cargo/config.toml` 的各 android target，无需额外操作。

### Cargo feature 一览

| feature | 作用 | Android 默认 |
|---------|------|--------------|
| （无）   | 仅构建 cdylib + rlib | ✅ |
| `cli`   | 桌面命令行 + 终端进度条 + mimalloc | ❌ |
| `icmp`  | ICMP 延迟测速（需原始套接字/root 权限） | ❌ |

---

## 二、C ABI 契约（v1）

```rust
pub const CFST_ABI_VERSION: u32 = 1;

cfst_abi_version() -> u32
cfst_engine_create(config_json: *const c_char, err_out: *mut c_char, err_cap: i32) -> *mut Engine
cfst_engine_start(engine: *mut Engine) -> bool
cfst_engine_phase(engine: *mut Engine) -> u32          // 0 idle / 1 ping / 2 download / 3 done / 4 error
cfst_engine_stop(engine: *mut Engine)                  // 协作式停止，立即返回
cfst_engine_poll(engine: *mut Engine) -> *mut c_char   // 进度 JSON，cfst_string_free 释放
cfst_engine_take_result(engine: *mut Engine) -> *mut c_char // 结果 JSON，一次性
cfst_engine_destroy(engine: *mut Engine)               // 永不阻塞
cfst_string_free(s: *mut c_char)
```

### 使用模型

```
create ──► start ──► poll(250ms 轮询) ──► phase==done ──► take_result ──► destroy
  │            │                                        │
  │            └─ 可随时 stop（部分结果仍可取）           └─ error 时 message 在 poll JSON 里
  └─ 失败返回 NULL，错误写入 err_out（建议 512 字节缓冲）
```

约定与保证：

- **一个引擎实例 = 一次测速会话**；`start` 仅 idle 状态可调，重复调用返回 false。
- **所有导出函数不 panic**（`catch_unwind` 拦截），不会因内部错误杀死宿主进程。
- **`destroy` 永不阻塞**：若仍在测速，先请求停止再立即返回；运行线程持有共享
  状态自然续命到收尾完成，之后自行释放。
- **`take_result` 一次性**：仅 `done` 阶段首次调用返回 JSON，之后返回 NULL。
- `poll` / `phase` 在任意阶段均可安全调用（含 done 之后）。
- 引擎运行在专属线程 + 专属 tokio runtime（2 worker），不依赖调用方线程环境。
- 传给 `create` 的 JSON 必须以 `\0` 结尾（C 字符串）。

### Kotlin 侧声明示例（已实现，见 app/ 的 CfstFfi）

```kotlin
object CfstFfi {
    init { System.loadLibrary("cfst") }

    external fun abiVersion(): Int
    // 配置/错误均走 UTF-8 ByteArray（绕开 JNI Modified-UTF-8 差异）；
    // 失败返回 0，错误消息写回 errOut（截去尾部 NUL 即消息本身）
    external fun engineCreate(configJson: ByteArray, errOut: ByteArray): Long
    external fun engineStart(ptr: Long): Boolean
    external fun enginePhase(ptr: Long): Int
    external fun engineStop(ptr: Long)
    // 进度/结果以 UTF-8 ByteArray 返回（JNI 层拷贝后立即释放 C 字符串，
    // 无悬垂风险）；NULL 引擎句柄或暂无数据时返回 null
    external fun enginePoll(ptr: Long): ByteArray?
    external fun engineTakeResult(ptr: Long): ByteArray?
    external fun engineDestroy(ptr: Long)
}
```

> 字符串释放策略（已采用 1，由 `src/jni_bridge.rs` 实现）：
> 1. Kotlin 用 `ByteArray` 接收 poll 结果：JNI 层读指针 → 拷贝进 ByteArray →
>    立即 `cfst_string_free` → 返回 ByteArray（无悬垂风险，Kotlin 无需手动释放）；
> 2. Kotlin 声明返回 `String?` 并配 `external fun stringFree(ptr: Long)`，
>    此时需要额外的 `cfst_string_handle` 风格接口（当前 ABI 暂未提供）。

---

## 三、配置 JSON（`cfst_engine_create` 输入）

snake_case；全部字段可缺省，缺省值与上游 CLI 默认一致。

| 字段 | 类型 | 默认 | 说明 |
|------|------|------|------|
| `ip_text` | String | `""` | IP / CIDR 列表，逗号分隔（`1.1.1.1/24,8.8.8.8`，支持 `ip=数量` 定量采样） |
| `ip_file` | String | `""` | IP 列表文件路径（与 `ip_text` 至少其一） |
| `tcp_port` | Int | `443` | 测速端口（0 拒绝） |
| `ping_mode` | String? | `null`(=tcp) | `tcp` / `http` / `icmp`（`icmp` 需编译 `icmp` feature 且环境具备权限） |
| `httping_url` | String? | `null` | ping_mode=http 的探测地址（缺省 `http://cp.cloudflare.com/cdn-cgi/trace`） |
| `httping_code` | String | `""` | 要求的状态码，如 `"200,301"` |
| `httping_colo` | String | `""` | 数据中心过滤，如 `"HKG,SJC"` |
| `url` | String | `""` | 下载测速 URL（`disable_download=false` 时必填） |
| `disable_download` | Bool | `false` | 只测延迟 |
| `download_time` | Int? | `10` | 单 IP 下载测速秒数（1~120） |
| `min_speed_mb_s` | Float? | `0.0` | 达标速度下限（MB/s） |
| `test_count` | Int? | `10` | 需要达标的结果数量 |
| `target_num` | Int? | `null` | 达到可用数量后提前结束 ping |
| `print_num` | Int? | `10` | 结果保留数量上限 |
| `ping_times` | Int? | `4` | 每 IP 延迟测速次数 |
| `max_threads` | Int? | `256` | 并发上限（1~1024；Android 建议 64~256） |
| `min_delay_ms` | Int? | `0` | 延迟下限（0~2000，不得大于 max_delay_ms） |
| `max_delay_ms` | Int? | `2000` | 延迟上限（0~2000） |
| `max_loss_rate` | Float? | `1.0` | 丢包率上限（0.0~1.0） |
| `global_timeout_secs` | Int? | `null` | 全局超时，到时结算已有结果并结束 |
| `test_all_ipv4` | Bool | `false` | 测试 CIDR 全部 IPv4（否则按前缀采样） |
| `show_port` | Bool | `false` | 仅影响 CSV/终端输出（JSON 恒定分离返回 ip/port） |
| `bind_ip` | String? | `null` | 绑定源 IP（普通 App 可用；与 `bind_interface` 二选一） |
| `bind_interface` | String? | `null` | 绑定接口名（Android 需 root；SO_BINDTODEVICE） |
| `output_csv` | String? | `null` | CSV 输出路径（可选） |

---

## 四、进度 JSON（`poll` 返回）

```json
{
  "phase": "idle | ping | download | done | error",
  "stop_requested": false,
  "ping":     { "tested": 123, "success": 45, "total": 262144 },
  "download": { "tested": 12, "qualified": 8, "total": 30, "speed_mbps": 12.34 },
  "message": null
}
```

- `ping` / `download` 键仅在对应阶段（或之后）出现；
- `message` 仅在 `phase=error` 时携带错误原因（如配置非法、runtime 创建失败）；
- `download.speed_mbps` 为最近一个 500ms 采样窗口的实时速度。

## 五、结果 JSON（`take_result` 返回，一次性）

```json
{
  "count": 2,
  "items": [
    {
      "ip": "1.0.0.59", "port": 443,
      "sent": 4, "received": 4, "loss_rate": 0.0,
      "delay_ms": 4.36,
      "download_speed_mbps": 2.92,
      "colo": "HKG"
    }
  ]
}
```

- `items` 已按综合评分排序（靠前者优先）；`download_speed_mbps` / `colo` 可为 `null`；
- stop / 全局超时触发的提前结束同样返回**已完成的部分结果**。

---

## 六、与上游的差异（库化改造记录）

| 变更 | 原因 |
|------|------|
| `main.rs` 编排逻辑迁入 `engine.rs` 状态机；CLI 降级为 `feature = "cli"` 可选 bin | 单 crate 双目标，Android 只编 cdylib |
| `error_and_exit`（`process::exit`）从共享代码路径移除，校验改返回 `Result` | 库内禁止 exit——会杀死宿主 App |
| `pool.rs` 全局 `OnceLock` 限流器 → 会话级 `Limiter`（挂在 `BasePing`） | 修复二次 init 被静默忽略；支持多引擎实例并存 |
| `progress.rs` 终端渲染（独立线程 16ms 刷 stdout）→ `headless::Bar`（纯内存） | Android 无终端；终端版保留在 `feature = "cli"` |
| 新增 FFI 边界：三道 `catch_unwind` 防线 + 非 `panic = "abort"` 的 release 配置 | panic 跨 FFI 是 UB；上游 `.cargo` 的 abort 配置已移除 |
| 下载 body 每帧读取加 5s 停滞超时 | 修复服务端停流时循环永久挂起（stop 后无法有界收尾） |
| 新增 serde/serde_json（配置/进度/结果 JSON 协议） | FFI 载荷协议 |
| 全引擎 stop 语义：置位共享 `timeout_flag`，各阶段在既有检查点收尾并输出部分结果 | App 的取消按钮 |
| `mimalloc` 仅 CLI 启用 | cdylib 走系统分配器，减小体积与兼容面 |
| rustls 显式仅 ring provider；Android 目标统一 16KB 页对齐链接 | Android arm64 兼容 + 15+ 页面大小要求 |

## 七、目录结构

```
engine/
├── Cargo.toml            # cfst-engine：lib(cdylib+rlib) + bin(需 cli feature)
├── .cargo/config.toml    # Android 目标 16KB 对齐等链接参数
├── build-so.sh           # 一键构建三 ABI .so → app/src/main/jniLibs
├── src/
│   ├── lib.rs            # 库入口（Android 用）
│   ├── ffi.rs            # C ABI 导出（cfst_*）
│   ├── jni_bridge.rs     # JNI 桥接（仅 Android 编译：Java_ 符号 → ByteArray 策略）
│   ├── engine.rs         # 配置/状态机/runner（含单元测试）
│   ├── metrics.rs        # 下载阶段结构化指标
│   ├── log.rs            # 共享日志助手
│   ├── main.rs           # CLI 入口（需 cli feature）
│   ├── args.rs           # 参数定义 + 共享校验（CLI 解析按 feature 门控）
│   ├── ip.rs             # IP/CIDR 解析与流式生成（上游算法）
│   ├── tcping.rs / httping.rs / icmp.rs  # 三种延迟测速
│   ├── download.rs       # 下载测速（上游算法 + 停滞超时）
│   ├── common.rs         # 编排/排序（z-score）
│   ├── hyper.rs          # TLS/HTTP 连接层（上游）
│   ├── interface.rs      # 网络绑定（源 IP / 接口名）
│   ├── pool.rs           # 会话级并发限流器
│   ├── csv.rs            # CSV 导出
│   └── progress/         # mod(cli|headless 双模式 Bar)
└── tests/ffi_smoke.rs    # FFI 端到端测试（需网络：cargo test -- --ignored）
```

## 八、验证记录（本仓库落地时）

- `cargo check`（默认 / cli / cli+icmp）：零警告通过
- `cargo test`：6 个单元测试通过（配置解析/校验/钳制/ICMP 门控）
- `tests/ffi_smoke.rs`（真实网络）：TCPing 4 目标端到端 done + stop 收尾，debug/release 均通过
- CLI 全流程真实测速：`1.0.0.0/24` 采样 → TCPing → 下载测速（speed.cloudflare.com）
  → 排序输出 + CSV，延迟 4~6ms、下载 2.9~3.9 MB/s、HKG 数据中心识别正常
- Android 交叉编译与 16KB 对齐：由 CI（`.github/workflows/ci.yml`）把关
