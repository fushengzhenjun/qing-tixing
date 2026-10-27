# 轻铃 · 安卓版

原生 Kotlin + Jetpack Compose。和电脑版共用同一套视觉（墨绿主色、圆角卡片、五个分类色），
但**不是网页套壳**：提醒由系统闹钟调度，应用被杀、手机重启都能照常响。

## 目录

```
android/
├─ app/src/main/java/com/qingtx/qingtiixing/
│   ├─ MainActivity.kt          界面入口：三个页面（列表 / 编辑器 / 设置）+ 权限流转
│   ├─ QtxApp.kt                启动时建通知渠道、补排所有闹钟
│   ├─ data/
│   │   ├─ Reminder.kt          数据模型 + 下一次触发时间计算
│   │   ├─ Store.kt             JSON 文件存储（filesDir/reminders.json）
│   │   └─ Fmt.kt               文案格式化
│   ├─ alarm/
│   │   ├─ Scheduler.kt         AlarmManager.setAlarmClock 排程
│   │   ├─ AlarmReceiver.kt     到点：发通知 + 排下一次
│   │   ├─ SnoozeReceiver.kt    通知上的「5 / 10 分钟后」
│   │   ├─ BootReceiver.kt      开机 / 改时间 / 升级后重排
│   │   └─ Notifier.kt          通知渠道与通知构建
│   └─ ui/                      Compose 界面（主题 / 列表 / 编辑器 / 设置）
└─ app/src/main/res/            图标、主题、备份规则
```

## 构建

```bash
# 需要 JDK 17 + Android SDK（platform 35 / build-tools 35）
cd android
./gradlew :app:assembleRelease     # 正式签名包
./gradlew :app:assembleDebug       # 调试包（包名带 .debug 后缀，可与正式版共存）
```

本机（开发机）的 SDK 与签名位置：

- SDK：`C:\Users\Administrator\AndroidDev\sdk`
- 签名证书：`D:\轻提醒\.signing-android\qingtixing.jks`
- 口令与别名：`android/keystore.properties`（不进仓库）

> ⚠️ 证书丢了就再也发不了更新。请把 `.signing-android\qingtixing.jks` 和口令
> 另存一份到网盘 / 密码管理器。上架 Google Play 后建议开启 Play App Signing，把这张
> 作为「上传密钥」，系统会另存一份真正的签名密钥。

## 关键设计

**为什么用 AlarmManager.setAlarmClock**
提醒必须准点。WorkManager 最小周期 15 分钟且会合并任务；应用内定时器一旦进程被回收就停。
`setAlarmClock` 是系统给闹钟类应用的通道，打盹模式下也能精确唤醒。

**为什么 Android 8 之后按铃声分了三个通知渠道**
渠道的声音、振动、重要性是不可变的，代码里对单条通知设置已无效。所以建了
「清脆（闹钟音）/ 柔和（通知音）/ 静音」三个渠道，提醒只是引用其中一个。

**数据存在哪**
`filesDir/reminders.json`，卸载才清除，升级不丢。备份规则只备份这一个文件。

**权限（上架时需要交代）**
| 权限 | 用途 |
|---|---|
| POST_NOTIFICATIONS | 发提醒通知 |
| SCHEDULE_EXACT_ALARM | 准点响（Android 12+ 需用户授权） |
| RECEIVE_BOOT_COMPLETED | 重启后恢复排程 |
| VIBRATE | 振动 |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | 引导用户关闭省电限制 |

**没有任何联网权限**，包里也没有 INTERNET；这是可以写进商店文案的硬事实。

## 单元测试

```bash
./gradlew :app:testUnitTest      # 或 :app:testDebugUnitTest
```

`NextFireTest` 覆盖「下一次什么时候响」的 14 个用例（跨天、跨周、周末跳过、周日=7、月末跨月、
时间刚好等于当前时刻）。

> ⚠️ 本机（工程路径含中文 `D:\轻提醒\android`）跑 `gradlew test` 会报
> `ClassNotFoundException: com.qingtx.qingtiixing.NextFireTest`。
> 这是 Gradle 测试 worker 解析 classpath 的编码问题，**不是用例失败**——
> 把 class 文件和依赖复制到纯英文路径下用 JUnitCore 直跑，14 项全绿。
> 换到英文路径或用 Android Studio 打开工程即可正常跑。

## 真机验证记录（2026-10-27，Android 15 模拟器）

跑通的链路：

| 场景 | 结果 |
|---|---|
| 首次启动 → 申请通知权限 → 列表/空状态 | ✅ |
| 新建单次提醒 → 排程 | ✅ `RTC_WAKEUP window=0 exactAllowReason=permission`（`setAlarmClock`） |
| 未授权精确闹钟时的降级 | ✅ 自动退到 `setAndAllowWhileIdle`，界面顶部提示「精确闹钟未允许」 |
| 到点触发 → 通知 | ✅ 标题/正文/「5 分钟后」「10 分钟后」两个动作都在 |
| 通知动作「5 分钟后」 | ✅ 排到 5 分钟后，且**单次提醒也能再响一次** |
| 重复提醒（每天 09:00） | ✅ 排到次日 09:00，列表显示「每天 09:00 · 明天 09:00」 |
| 重启手机（未打开 App） | ✅ `BootReceiver` 自动重排，闹钟仍在 |
| 深色模式 | ✅ |
| 设置页三项权限检测/跳转 | ✅ 通知、精确闹钟、电池优化都能正确显示状态并跳转 |

过程中修掉的问题：

1. **列表不随数据刷新**：单次提醒响过之后被置为停用，但界面只在 onResume 重读，
   开关还显示为开。→ `Store` 增加 `StateFlow`，界面改为订阅。
2. **单次提醒点「稍后」不再响**：响过一次后 `enabled=false`，`AlarmReceiver` 直接 return，
   把「稍后提醒」也一起拦掉了。→ 消噪广播放行 `ACTION_SNOOZE_FIRE`。
3. **单次提醒副标题重复**：显示成「10月26日 20:45 · 今天 20:45」。→ 单次只显示相对时间。
4. **选中色跑成紫色**：Material3 的 `FilterChip` 选中态用 `secondaryContainer`，没定义就退回框架默认紫。
   → 两个配色方案里显式给成品牌绿。

## 已知待办（按优先级）

1. **响过的单次提醒会一直留在列表里**（显示「已停用」）。建议过一天自动归档到「已完成」，否则列表会越攒越脏。
2. **没有提前提醒**：电脑版可以「提前 5 分钟」，安卓版还没有。
3. **删除没有撤销**：电脑版有 6 秒撤销，这里一点就没了。
4. **没有备份/导出**：数据只在本机，换手机就没了。电脑版有 JSON / .ics 导出，可以对齐。
5. **工作日不含法定节假日**：电脑版有节假日表（`holidayAware`），安卓版还是纯周一到周五。
6. 通知点开只回到列表，不会定位到那条提醒。
7. 没有桌面小组件 / 快捷开关。

## 上架前还缺的东西

1. Google Play：512×512 图标、至少 2 张截图、隐私政策 URL、数据安全表单、内容分级
2. 国内商店（华为/小米/OPPO/vivo/应用宝）：软件著作权证书（需提前申请，周期数周）+ 企业资质
3. 首次发布的 targetSdk 需跟上商店当年要求（本次 35，到期前需升级）
4. 真实设备上的通知可靠性测试（不同 ROM 差异很大）
