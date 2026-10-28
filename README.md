# 轻铃 QingLing

一款简单、干净、**完全离线**的到点提醒工具。不注册、不联网、不收集任何数据。

- **Windows 桌面版**：常驻托盘，到点弹在屏幕上；画面和交互都按"轻"来做，不打扰你干活
- **Android 版**：原生 Kotlin 实现，用系统闹钟调度，应用被杀、手机重启也照常响

> 项目全部源码在此仓库。桌面版界面是单个 HTML 文件，安卓版是原生 Kotlin，没有隐藏的第三方统计或后台上报。

## 下载

**国内访问 GitHub 常不稳定，建议走 Gitee 镜像，点下面的文件名直接下载：**

- 安装版（推荐）：[QingLing-Setup-1.6.0.exe](https://gitee.com/fushengbanrizhenjun/qing-tixing/releases/download/v1.6.0/QingLing-Setup-1.6.0.exe)
- 校验值：[SHA256-1.6.0.txt](https://gitee.com/fushengbanrizhenjun/qing-tixing/releases/download/v1.6.0/SHA256-1.6.0.txt)
- 镜像仓库：https://gitee.com/fushengbanrizhenjun/qing-tixing

两个地址的文件内容完全一致（可用 SHA256 互校）。镜像只托管安装包与源码副本，**不涉及任何联网功能**。

绿色版 zip 约 101 MB，超过 Gitee 单文件 100 MB 的上限，只在 GitHub 提供。前往 [Releases](https://github.com/fushengzhenjun/qing-tixing/releases) 下载：

| 版本 | 文件 | 说明 |
|---|---|---|
| 安装版（推荐） | `QingLing-Setup-x.y.z.exe` | 向导式安装，可自选安装目录，当前用户安装、无需管理员权限 |
| 绿色版 | `QingLing_Portable_x.y.z.zip` | 解压即用，数据写在程序目录 |
| 安卓版 | `QingLing-x.y.z.apk` | 侧载安装，需允许"未知来源" |

**官方发布渠道只有上述两个仓库的 Releases 和 Pages 站点。** 其他任何地方下载到的都非官方版本。

## 桌面版功能

- 单次、每天、工作日、每周、每月、每年、每隔 N 天
- 按间隔循环：指定几点到几点、每隔 N 分钟（喝水、久坐提醒）
- 倒计时提醒；到点弹窗 + 铃声 + 振动，可贪睡 5 / 10 分钟
- **提醒弹到屏幕上**：独立小窗盖在最上层，不受主窗口位置影响；位置可选中央与四角，多屏时出现在鼠标所在屏
- 四档弹窗大小；自定义背景（主界面与弹窗可分别设置）
- 五大分类、一键模板、多种铃声、夜间免打扰、全部暂停
- 工作日可跟随法定节假日；浅色 / 深色 / 跟随系统
- 系统托盘常驻、开机自启动、`Ctrl + Alt + N` 快速添加
- 自动备份（每日一份保留 30 天）、JSON 导入导出、导出 `.ics` 到手机日历

## 安卓版功能

- 单次 / 每天 / 工作日 / 每周 / 每月 / 每年 / 每隔 N 天 / 循环（几点到几点每隔 N 分钟）
- **到点全屏提醒**：锁屏或别的应用开着时整个抬到前台，带「5 分钟后」「10 分钟后」
- 首页三视图：列表 / 周（周条 + 当天时间轴）/ 清单（可勾选，带完成进度）
- 大日历居中，每天的提醒用分类色小圆点标出，点某天即筛选那天
- 通知带「5 分钟后」「10 分钟后」；三档铃声；可关振动
- 到点是否亮屏弹出可关（开会、开车时只走通知栏）
- 夜间免打扰：时段内静音、不振动，提醒仍会记录
- 全部暂停：1 小时 / 4 小时 / 明早 8 点 / 手动恢复
- 工作日跟随法定节假日（放假不提醒，调休照常提醒）
- 外观：跟随系统 / 浅色 / 深色；主界面与提醒页可各设一张背景图
- 常用模板：喝水、起身活动、护眼远眺、服药、午睡、睡觉，新建时点一下填好
- 数据互通：导出 JSON 后电脑版可直接导入，电脑版导出的文件也能导进手机
- 导出 `.ics` 日历；每天自动备份一份（留 14 天），可立即备份，清空前另存带时分秒的快照
- 「试发一条提醒」：10 秒后真响一次，用来确认通知链路通不通
- 开机、改时间、应用升级后自动重排闹钟
- 权限引导：通知、精确闹钟、电池优化（三项都正常时收成一行，点开可复查）
- 设置页可一键复制版本 / 系统 / 提醒条数，反馈时直接贴上
- **没有任何联网权限**，包里连 `INTERNET` 都没有

## 目录结构

```
轻铃.html          桌面版界面（唯一编辑源，单文件）
desktop/             Electron 外壳、打包脚本、自测
android/             安卓版（原生 Kotlin + Compose）
tools/               出包、签名、同步、素材生成脚本
```

`desktop/轻铃.html` 由 `desktop/build.js` 从根目录那份注入版本号生成，不进仓库。

## 构建

**桌面版**

```bash
cd desktop
npm install
npm start                 # 直接运行
node ../tools/release.js  # 出安装包 + 绿色版 + SHA256
```

**安卓版**（需要 JDK 17、Android SDK 35）

```bash
cd android
./gradlew :app:assembleRelease
```

签名信息放在 `android/keystore.properties`（已在 `.gitignore` 中排除，自行生成）。

## 隐私

完全免费、开源、无广告、无账号、不联网，提醒数据只保存在本机。

- 桌面版数据：`%APPDATA%\轻铃\`
- 安卓版数据：应用私有目录 `filesDir/reminders.json`

完整说明见 [隐私政策](https://fushengzhenjun.github.io/qing-tixing/privacy.html)。

## 反馈

- 问题与建议：[Issues](https://github.com/fushengzhenjun/qing-tixing/issues)
- 邮箱：894233371@qq.com

## 许可证

[MIT](LICENSE) © 2026 fushengzhenjun

可自由使用、修改、分发，包括商业用途，保留版权声明即可。
