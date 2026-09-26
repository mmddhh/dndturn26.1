# 180556 查询取消修复与验证

变更类别：target-only；Minecraft 26.1 / NeoForge 26.1.2.84 / JDK 25.0.3。依据根目录 `DNDTurn_180556_ACCEPTANCE_AND_VISUAL_INVESTIGATION.md` 第 2.1 节，修复未提交地面查询的取消时序；common、协议与依赖版本未改变。

## 客户端合同

- `ClientTacticalPlan.target(..., autoSubmit)` 为每个查询明确指定是否自动提交，并失效旧 query/revision/options/自动提交标志。公开 discovery 调用默认不自动提交。
- 自动提交查询进入 `QUERYING`，由现有 `ClientControl.escape` 非 IDLE 分支消费 Esc；右键同样取消。连续选目标可以替换查询，旧回复仍受 query/revision 校验。
- 取消先失效查询再回到 IDLE。迟到 Options 无权恢复选择或发送 Request；已提交计划仍使用既有 CANCEL_PENDING 与服务器终态合同。
- 没有新增或修改原版 Mixin 接入。测试复用 `ClientPacketProbeMixin` 的 `Connection.send(Packet)` 观察点，按 `.84` patched 源码的 `ServerboundCustomPayloadPacket.payload()` 识别实际 `TacticalNetwork.Request`，而非仅检查客户端 phase。

## 本轮实际执行

构建、运行世界与日志隔离到根 `build/180556-query-validation/`。本地 init script `build/180556-query-validation.gradle` 重定向输出与运行目录，将客户端连接地址设为 `127.0.0.1:25576`，并设置 `dndturn.controlProbe.queryOnly=true`。

在 `targets/neoforge-26.1` 使用 JDK 25.0.3：

```powershell
.\gradlew.bat -I ../../build/180556-query-validation.gradle clean build
.\gradlew.bat -I ../../build/180556-query-validation.gradle runServer
.\gradlew.bat -I ../../build/180556-query-validation.gradle runClient -PdndturnControlProbe -PcontrolProbeActive
```

- `build/180556-query-build.log`：clean build 通过，包含 common 测试。
- `build/180556-query-client2.log` 与隔离 target 的 `control-probe/result.txt`：真实客户端连接专服，原键鼠回调与镜头拾取回归 PASS。
- 空闲左键地面后立即 Esc，探针扣住真实 Options，随后重复投递两次：无新 Request、无运行计划/移动许可、身体位置与预算不变、选择和菜单不复活。
- 另一次地面查询由右键取消，随后右键菜单发现：保持 CONTEXT_MENU、不继承自动提交。最后不取消的地面点击实际移动并扣预算，作为正向对照。
- 首次全新客户端停在初始设置界面，未进入回归；停止该测试客户端，复制既有隔离探针的 options.txt 后运行通过。未改用户日常客户端设置。

此测试为应用层延迟/重复投递与真实输入回调，不是 OS 键鼠、传输层丢包、双客户端、全部生命周期或视觉恢复验收。本轮没有执行无关的全套服务端 GameTest。视觉调查的后续范围统一见 G36。
