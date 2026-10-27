轻铃 v1.6.0 —— 改了个名字，功能没动，数据没丢。

## 下载

**点下面的文件名直接下载**（国内节点，速度快）：

- **安装版（推荐）**：[QingLing-Setup-1.6.0.exe](https://gitee.com/fushengbanrizhenjun/qing-tixing/releases/download/v1.6.0/QingLing-Setup-1.6.0.exe) —— 77 MB，双击安装，可以自己选装到哪，不用管理员权限
- 校验值：[SHA256-1.6.0.txt](https://gitee.com/fushengbanrizhenjun/qing-tixing/releases/download/v1.6.0/SHA256-1.6.0.txt)

绿色版 zip 约 101 MB，超过 Gitee 单文件上限，只在 GitHub 提供：<https://github.com/fushengzhenjun/qing-tixing/releases>

## 为什么改名

原来叫「轻提醒」。这名字其实挺合适——轻量、不打扰，一眼知道是干什么的。问题只有一个：已经有两个同类产品在用这个名字，搜的时候会撞在一起。

换名字不改变任何功能。选「轻铃」，保留原来那个「轻」字，把「提醒」换成「铃」：看到铃知道是提醒，看到轻知道不吵。

## 升级要注意两件事

**1. 你的提醒不会丢。** 首次启动会自动把旧目录（`%APPDATA%\轻提醒`）的数据复制到新目录（`%APPDATA%\轻铃`）。原目录**不会被删除**，万一有问题还能退回去。

**2. 旧的桌面快捷方式会失效**，因为程序文件名从 `轻提醒.exe` 变成了 `轻铃.exe`。删掉旧快捷方式，右键新的 `轻铃.exe` → 发送到 → 桌面快捷方式，就可以了。

## v1.5.0 的功能都还在

- 提醒弹在屏幕上（独立窗口，压在所有窗口之上，全屏看视频也盖得住）
- 四种类型：单次、重复、循环、倒计时
- 日历 / 周视图 / 待办清单，可导出 .ics
- 自定义背景（主界面和弹窗各一张）
- 夜间免打扰、全部暂停、开机自启
- 数据只在本机，每天自动备份，保留 30 天

## 其他

- 安装包仍是向导式，可自选安装目录，按当前用户安装，不需要管理员权限
- 安装包没有代码签名，首次运行可能出现 SmartScreen 提示，点「更多信息 → 仍要运行」
- 隐私政策：https://fushengzhenjun.github.io/qing-tixing/privacy.html
