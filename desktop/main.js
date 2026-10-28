/* 轻铃 · Electron 主进程（桌面壳）
   纯本地、离线、无广告；数据默认存 %APPDATA%\轻铃，绿色版可切便携模式。
   点关闭 → 最小化到系统托盘，后台继续到点提醒；托盘菜单可退出。 */
const { app, BrowserWindow, Menu, Tray, nativeImage, ipcMain, globalShortcut, screen, shell, dialog } = require("electron");
const path = require("path");
const fs = require("fs");

/* 允许从应用内唤起的站外地址（反馈页 / 项目主页）。其余一律拒绝。 */
const ALLOW_HOSTS = ["github.com", "www.github.com", "gitee.com", "www.gitee.com"];

/* 用户数据目录
   · 安装版：%APPDATA%\轻铃（跟随当前用户，不依赖 D 盘，任何电脑都能用）
   · 绿色版：exe 同目录放 portable.flag → 数据写在 exe 同目录的「数据」文件夹，可随 U 盘带走
   · 旧版 v1.0.0 曾把数据硬编码在 D:\轻提醒\轻提醒数据，首次启动自动迁移，老用户不丢数据 */
let DATA_DIR = null;
(function initUserData() {
  try {
    const exeDir = path.dirname(process.execPath);
    const portable = app.isPackaged && fs.existsSync(path.join(exeDir, "portable.flag"));
    const dataDir = portable
      ? path.join(exeDir, "数据")
      : path.join(app.getPath("appData"), "轻铃");

    fs.mkdirSync(dataDir, { recursive: true });

    /* 仅在目标目录还没有数据时迁移，绝不覆盖用户现有数据。
       两个历史位置都要认，否则改名对老用户等于清空了数据：
         · v1.5.0 及以前：%APPDATA%\轻提醒（改名为轻铃后这个目录不再被读取）
         · v1.0.0：把数据硬编码在 D:\轻提醒\轻提醒数据 */
    const LEGACY_DIRS = [
      path.join(app.getPath("appData"), "轻提醒"),
      "D:\\轻提醒\\轻提醒数据"
    ];
    try {
      const hasData = (d) => fs.existsSync(path.join(d, "Local Storage"));
      if (!portable && !hasData(dataDir)) {
        for (const from of LEGACY_DIRS) {
          if (path.resolve(from) === path.resolve(dataDir)) continue;
          if (hasData(from)) {
            fs.cpSync(from, dataDir, { recursive: true, force: false });
            break;
          }
        }
      }
    } catch (e) {}

    app.setPath("userData", dataDir);
    DATA_DIR = dataDir;
  } catch (e) {}
})();

/* 单实例：重复打开只唤起已有窗口 */
const gotLock = app.requestSingleInstanceLock();
if (!gotLock) {
  app.quit();
} else {
  let win = null;
  let tray = null;
  let isQuiting = false;
  let closeHinted = false;
  let quickWin = null;
  const pendingQuick = [];

  function showWindow() {
    if (!win) return;
    if (win.isMinimized()) win.restore();
    win.show();
    win.focus();
  }

  function createWindow() {
    const iconPath = path.join(__dirname, "icon.ico");
    win = new BrowserWindow({
      width: 1020,
      height: 720,
      minWidth: 860,
      minHeight: 560,
      title: "轻铃",
      backgroundColor: "#F6F5F1",
      autoHideMenuBar: true,
      show: false,
      icon: fs.existsSync(iconPath) ? iconPath : undefined,
      webPreferences: {
        contextIsolation: true,
        nodeIntegration: false,
        sandbox: true,
        preload: path.join(__dirname, "preload.js"),
        backgroundThrottling: false,   // 隐藏到托盘后，定时器/声音不被系统节流，保证到点准时
        autoplayPolicy: "no-user-gesture-required", // 开机自启后没有用户交互，也要能响铃
        spellcheck: false              // 关掉拼写检查：Chromium 可能为此在后台下载词典，程序必须零请求
      }
    });

    Menu.setApplicationMenu(null);
    win.setMenuBarVisibility(false);

    /* 开机自启（带 --hidden）只驻留托盘，不把窗口弹到桌面上；
       用户双击图标启动时不带该参数，照常显示。 */
    win.once("ready-to-show", () => {
      if (!startHidden) win.show();
    });

    /* 关闭按钮：最小化到托盘而非退出 */
    win.on("close", (e) => {
      if (isQuiting) return;
      e.preventDefault();
      win.hide();
      if (!closeHinted) {
        closeHinted = true;
        try {
          tray.displayBalloon({
            iconType: "info",
            title: "轻铃仍在后台运行",
            content: "已最小化到托盘，到点会照常提醒你；右键托盘图标可退出。"
          });
        } catch (err) {}
      }
    });

    /* 页面里的链接不在应用内开新窗口，交给系统浏览器（同样走白名单） */
    win.webContents.setWindowOpenHandler(({ url }) => {
      try {
        const u = new URL(url);
        if (u.protocol === "https:" && ALLOW_HOSTS.indexOf(u.hostname) >= 0) shell.openExternal(u.href);
      } catch (e) {}
      return { action: "deny" };
    });

    win.loadFile("轻铃.html");

    /* 快速添加的提醒如果比页面更早到达，先存下来，加载完再补发 */
    win.webContents.on("did-finish-load", () => {
      while (pendingQuick.length) win.webContents.send("quick:added", pendingQuick.shift());
    });
  }

  /* ---------- 快速添加 ----------
     全局快捷键 → 屏幕中央弹出一个无边框小窗，敲一句话（「明天下午三点 开会」）回车就建好。
     解析在小窗里做，主进程只负责把结果转给主窗口，所以主窗口没打开也能记提醒。 */
  const QUICK_ACCEL = "CommandOrControl+Alt+N";
  let quickEnabled = true;
  let quickRegistered = false;

  function quickStatus() {
    return { enabled: quickEnabled, registered: quickRegistered, accel: QUICK_ACCEL };
  }

  function createQuickWindow() {
    quickWin = new BrowserWindow({
      width: 560,
      height: 186,
      frame: false,
      transparent: true,
      hasShadow: true,
      resizable: false,
      minimizable: false,
      maximizable: false,
      fullscreenable: false,
      skipTaskbar: true,
      alwaysOnTop: true,
      show: false,
      backgroundColor: "#00000000",
      webPreferences: {
        contextIsolation: true,
        nodeIntegration: false,
        sandbox: true,
        spellcheck: false,
        preload: path.join(__dirname, "preload.js")
      }
    });

    quickWin.loadFile("quick.html");
    quickWin.setAlwaysOnTop(true, "pop-up-menu");
    quickWin.on("blur", () => { if (quickWin && !quickWin.isDestroyed()) quickWin.hide(); });
    quickWin.on("closed", () => { quickWin = null; });
  }

  function showQuickAdd() {
    if (!quickWin || quickWin.isDestroyed()) createQuickWindow();
    const pt = screen.getCursorScreenPoint();
    const area = screen.getDisplayNearestPoint(pt).workArea;
    const w = 560, h = 186;
    const x = Math.round(area.x + (area.width - w) / 2);
    const y = Math.round(area.y + Math.max(40, area.height * 0.22));
    quickWin.setBounds({ x, y, width: w, height: h });
    quickWin.show();
    quickWin.focus();
    quickWin.webContents.send("quick:show");
  }

  function deliverQuick(payload) {
    if (!win || win.isDestroyed()) return false;
    if (win.webContents.isLoading()) { pendingQuick.push(payload); return true; }
    win.webContents.send("quick:added", payload);
    return true;
  }

  function applyQuickShortcut(on) {
    try { globalShortcut.unregister(QUICK_ACCEL); } catch (e) {}
    quickEnabled = !!on;
    quickRegistered = false;
    if (!quickEnabled) return quickStatus();
    try {
      quickRegistered = globalShortcut.register(QUICK_ACCEL, () => showQuickAdd());
    } catch (e) {
      quickRegistered = false;
    }
    return quickStatus();
  }

  ipcMain.handle("quick:submit", (_e, payload) => deliverQuick(payload));
  ipcMain.handle("quick:cancel", () => {
    if (quickWin && !quickWin.isDestroyed()) quickWin.hide();
    return true;
  });
  ipcMain.handle("shortcut:get", () => quickStatus());
  ipcMain.handle("shortcut:set", (_e, on) => applyQuickShortcut(!!on));

  function createTray() {
    const iconPath = path.join(__dirname, "icon.ico");
    let img = nativeImage.createFromPath(iconPath);
    if (!img.isEmpty()) img = img.resize({ width: 16, height: 16 });
    tray = new Tray(img.isEmpty() ? nativeImage.createEmpty() : img);
    tray.setToolTip("轻铃 · 后台运行中");

    function buildMenu() {
      const openAtLogin = isAutoLaunchOn();
      return Menu.buildFromTemplate([
        { label: "打开轻铃", click: showWindow },
        { label: "快速添加提醒（Ctrl+Alt+N）", click: () => showQuickAdd() },
        {
          label: "开机自动启动",
          type: "checkbox",
          checked: openAtLogin,
          click: (item) => {
            app.setLoginItemSettings(loginItemSettings(item.checked));
            refreshMenu();
          }
        },
        { type: "separator" },
        {
          label: "退出轻铃",
          click: () => {
            isQuiting = true;
            app.quit();
          }
        }
      ]);
    }

    function refreshMenu() {
      tray.setContextMenu(buildMenu());
    }

    tray.on("click", () => {
      if (!win) return;
      if (win.isVisible()) win.hide();
      else showWindow();
    });
    tray.on("right-click", refreshMenu);
    tray.on("mouse-down", () => {});
    refreshMenu();
  }

  /* 开机自启动
     Windows 下写入 HKCU\...\Run 的值名，固定用 AppUserModelID。
     显式指定 path = 当前 exe，避免旧版本把启动项写成开发目录路径。
     args 里带 --hidden：开机启动只驻留托盘，不把窗口弹到桌面上。 */
  const LOGIN_ITEM_NAME = "com.qingtx.qingtiixing";
  const HIDDEN_FLAG = "--hidden";
  const startHidden = process.argv.indexOf(HIDDEN_FLAG) >= 0;

  function loginItemSettings(enabled) {
    return {
      openAtLogin: !!enabled,
      name: LOGIN_ITEM_NAME,
      path: process.execPath,
      args: enabled ? [HIDDEN_FLAG] : []
    };
  }

  function isAutoLaunchOn() {
    try {
      return !!app.getLoginItemSettings({ path: process.execPath, args: [HIDDEN_FLAG] }).openAtLogin;
    } catch (e) {
      return false;
    }
  }

  /* 迁移旧版本写下的启动项
     v1.3.x 及更早写入的启动项没有 --hidden，开机时会把主窗口直接弹出来。
     这里判断「有启动项、但参数对不上」，原地重写成新参数，用户不用再去设置里点一遍。
     原本没开自启的不动。 */
  function repairAutoLaunch() {
    if (!app.isPackaged) return;
    try {
      const s = app.getLoginItemSettings({ path: process.execPath, args: [HIDDEN_FLAG] });
      if (s && s.openAtLogin && !s.executableWillLaunchAtLogin) {
        app.setLoginItemSettings(loginItemSettings(true));
      }
    } catch (e) {}
  }

  /* 开机静默启动时，用户开机后看不到任何界面，容易以为没启动。
     第一次静默启动给一条托盘气泡说明，之后不再打扰。 */
  function hintHiddenOnce() {
    if (!DATA_DIR) return;
    const flag = path.join(DATA_DIR, ".login-hint");
    try { if (fs.existsSync(flag)) return; } catch (e) { return; }
    try {
      fs.writeFileSync(flag, String(Date.now()));
      setTimeout(() => {
        if (!tray || tray.isDestroyed()) return;
        tray.displayBalloon({
          iconType: "info",
          title: "轻铃已在后台运行",
          content: "开机启动时不弹窗，到点会照常提醒。点这个托盘图标可以打开主界面。"
        });
      }, 2500);
    } catch (e) {}
  }

  /* 设置面板经 preload 桥查询 / 设置 */
  ipcMain.handle("auto-launch:get", () => isAutoLaunchOn());
  ipcMain.handle("auto-launch:set", (_e, enabled) => {
    app.setLoginItemSettings(loginItemSettings(!!enabled));
    return isAutoLaunchOn();
  });

  /* 到点把窗口叫到前台
     场景：用户拒绝过通知权限、或系统不支持通知时，弹层只会显示在隐藏窗口上，
     等于没提醒。这里恢复窗口 + 置顶 + 任务栏闪烁，确保人能看到。 */
  let topTimer = null;
  ipcMain.handle("window:alert", () => {
    if (!win || win.isDestroyed()) return false;
    try {
      if (win.isMinimized()) win.restore();
      win.show();
      win.setAlwaysOnTop(true, "screen-saver");
      win.focus();
      if (typeof win.flashFrame === "function") win.flashFrame(true);
      clearTimeout(topTimer);
      topTimer = setTimeout(() => {
        try { win.setAlwaysOnTop(false); } catch (e) {}
      }, 10000);
      return true;
    } catch (e) {
      return false;
    }
  });

  /* 反馈页 / 邮件：只允许 https 与 mailto，且 https 限定白名单域名。
     页面里就算被塞进别的地址，也打不开——避免任何形式的任意链接唤起。 */
  ipcMain.handle("shell:open-external", async (_e, raw) => {
    let u;
    try { u = new URL(String(raw || "")); } catch (e) { return false; }
    try {
      if (u.protocol === "mailto:") {
        await shell.openExternal(u.href);
        return true;
      }
      if (u.protocol === "https:" && ALLOW_HOSTS.indexOf(u.hostname) >= 0) {
        await shell.openExternal(u.href);
        return true;
      }
    } catch (e) {}
    return false;
  });

  /* ---------- 数据备份 ----------
     页面数据只存在 localStorage 一份，leveldb 一旦损坏或被误清空就全没了。
     这里按天滚动存一份明文 JSON：
       · 同一天里每次保存都覆盖当天那一份，所以「今天最新的」始终存在
       · 只保留最近 30 天，更早的自动清掉
       · 空数据不覆盖非空备份 —— 用户误清空时，这份就是救命的那份
       · 「清空全部」之前另存一份 manual-*，不参与 30 天滚动 */
  const BACKUP_DIR = DATA_DIR ? path.join(DATA_DIR, "backups") : null;
  const BACKUP_KEEP = 30;
  const BACKUP_MAX_BYTES = 8 * 1024 * 1024;
  const BACKUP_NAME_RE = /^(reminders|manual)-\d{4}-\d{2}-\d{2}(-\d{6})?\.json$/;

  function pad2(n) { return (n < 10 ? "0" : "") + n; }
  function dayStamp(d) { return d.getFullYear() + "-" + pad2(d.getMonth() + 1) + "-" + pad2(d.getDate()); }

  function ensureBackupDir() {
    if (!BACKUP_DIR) return false;
    try { fs.mkdirSync(BACKUP_DIR, { recursive: true }); return true; } catch (e) { return false; }
  }

  /* 数一数这份 JSON 里到底有几条提醒；解析不出来返回 -1，宁可不写也不写坏 */
  function countIn(json) {
    try {
      const v = JSON.parse(json);
      return Array.isArray(v) ? v.length : -1;
    } catch (e) { return -1; }
  }

  function pruneBackups() {
    let names = [];
    try {
      names = fs.readdirSync(BACKUP_DIR).filter((n) => /^reminders-\d{4}-\d{2}-\d{2}\.json$/.test(n));
    } catch (e) { return; }
    names.sort();                                    /* 文件名即日期，字典序就是时间序 */
    if (names.length <= BACKUP_KEEP) return;
    for (const n of names.slice(0, names.length - BACKUP_KEEP)) {
      try { fs.unlinkSync(path.join(BACKUP_DIR, n)); } catch (e) {}
    }
  }

  function listBackups() {
    if (!ensureBackupDir()) return [];
    let names = [];
    try { names = fs.readdirSync(BACKUP_DIR).filter((n) => BACKUP_NAME_RE.test(n)); } catch (e) { return []; }
    const out = [];
    for (const name of names) {
      try {
        const full = path.join(BACKUP_DIR, name);
        const st = fs.statSync(full);
        out.push({
          name: name,
          size: st.size,
          at: st.mtimeMs,
          count: name.startsWith("manual-") ? -1 : countIn(fs.readFileSync(full, "utf8"))
        });
      } catch (e) {}
    }
    out.sort((a, b) => b.at - a.at);
    return out;
  }

  ipcMain.handle("backup:save", (_e, raw, count) => {
    if (!ensureBackupDir()) return { ok: false, reason: "nodir" };
    const text = String(raw || "");
    if (!text || text.length > BACKUP_MAX_BYTES) return { ok: false, reason: "size" };
    const n = typeof count === "number" ? count : countIn(text);
    const name = "reminders-" + dayStamp(new Date()) + ".json";
    const full = path.join(BACKUP_DIR, name);
    try {
      if (n === 0 && fs.existsSync(full) && countIn(fs.readFileSync(full, "utf8")) > 0) {
        return { ok: true, skipped: true, name: name };
      }
    } catch (e) {}
    try {
      fs.writeFileSync(full, text, "utf8");
      pruneBackups();
      return { ok: true, name: name, count: n };
    } catch (e) { return { ok: false, reason: "write" }; }
  });

  ipcMain.handle("backup:list", () => listBackups().slice(0, 12));

  ipcMain.handle("backup:read", (_e, name) => {
    if (!ensureBackupDir()) return null;
    const safe = String(name || "");
    if (!BACKUP_NAME_RE.test(safe)) return null;      /* 挡掉 ../ 之类的路径穿越 */
    try { return fs.readFileSync(path.join(BACKUP_DIR, safe), "utf8"); } catch (e) { return null; }
  });

  ipcMain.handle("backup:stash", (_e, raw, count) => {
    if (!ensureBackupDir()) return { ok: false };
    const text = String(raw || "");
    if (!text || text.length > BACKUP_MAX_BYTES) return { ok: false };
    const d = new Date();
    const name = "manual-" + dayStamp(d) + "-" + pad2(d.getHours()) + pad2(d.getMinutes()) + pad2(d.getSeconds()) + ".json";
    try {
      fs.writeFileSync(path.join(BACKUP_DIR, name), text, "utf8");
      return { ok: true, name: name, count: typeof count === "number" ? count : countIn(text) };
    } catch (e) { return { ok: false }; }
  });

  ipcMain.handle("backup:reveal", async () => {
    if (!ensureBackupDir()) return false;
    try { const err = await shell.openPath(BACKUP_DIR); return !err; } catch (e) { return false; }
  });

  ipcMain.handle("app:paths", () => ({
    data: DATA_DIR || "",
    backup: BACKUP_DIR || ""
  }));

  /* ---------- 自定义背景 ----------
     用户选一张图，主进程负责「校验 → 缩放 → 转存」，页面永远只拿到 dataURL。
     这样做的好处：
       · 页面不接触文件路径，文件被挪走/改名也不会白屏
       · 换图直接覆盖同名文件，不会越攒越多
       · 页面是 file:// 协议，用 dataURL 绕开本地文件加载的各种限制
     两种图分开存：app.*（主界面）、alarm.*（提醒弹窗）。 */
  const WALL_DIR = DATA_DIR ? path.join(DATA_DIR, "wallpaper") : null;
  const BG_MAX_BYTES = 8 * 1024 * 1024;   /* 原图上限 8MB，超过直接拒绝，避免卡死页面 */
  const BG_MAX_WIDTH = 2560;              /* 超宽图缩到这个宽度，4K 屏也够用 */
  const bgCache = {};                     /* { app: {mtimeMs,url}, alarm: {...} } */

  function wallKind(kind) { return kind === "alarm" ? "alarm" : "app"; }

  function wallFile(k) {
    if (!WALL_DIR) return null;
    for (const ext of ["jpg", "png"]) {
      const p = path.join(WALL_DIR, k + "." + ext);
      try { if (fs.existsSync(p)) return p; } catch (e) {}
    }
    return null;
  }

  function dropWall(k) {
    if (!WALL_DIR) return;
    for (const ext of ["jpg", "png"]) {
      try { fs.unlinkSync(path.join(WALL_DIR, k + "." + ext)); } catch (e) {}
    }
  }

  ipcMain.handle("bg:pick", async (_e, kind) => {
    const k = wallKind(kind);
    if (!WALL_DIR) return { ok: false, reason: "nodir" };
    let src = "";
    try {
      const ret = await dialog.showOpenDialog(win, {
        title: "选择背景图片",
        properties: ["openFile"],
        filters: [{ name: "图片", extensions: ["jpg", "jpeg", "png", "webp", "bmp"] }]
      });
      if (ret.canceled || !ret.filePaths || !ret.filePaths[0]) return { ok: false, canceled: true };
      src = ret.filePaths[0];
    } catch (e) { return { ok: false, reason: "dialog" }; }

    try {
      const st = fs.statSync(src);
      if (st.size > BG_MAX_BYTES) return { ok: false, reason: "size" };
    } catch (e) { return { ok: false, reason: "io" }; }

    let img;
    try { img = nativeImage.createFromPath(src); } catch (e) { img = null; }
    if (!img || img.isEmpty()) return { ok: false, reason: "decode" };

    try {
      fs.mkdirSync(WALL_DIR, { recursive: true });
      const sz = img.getSize();
      const isPng = /\.png$/i.test(src);
      dropWall(k);
      if (!isPng || sz.width > BG_MAX_WIDTH) {
        /* 统一转 JPEG：体积小、解码快；PNG 只有「不需要缩放」时才原样保留以留住透明 */
        if (sz.width > BG_MAX_WIDTH) img = img.resize({ width: BG_MAX_WIDTH, quality: "good" });
        fs.writeFileSync(path.join(WALL_DIR, k + ".jpg"), img.toJPEG(86));
      } else {
        fs.copyFileSync(src, path.join(WALL_DIR, k + ".png"));
      }
      delete bgCache[k];
      return { ok: true, name: k + (isPng && sz.width <= BG_MAX_WIDTH ? ".png" : ".jpg") };
    } catch (e) { return { ok: false, reason: "write" }; }
  });

  ipcMain.handle("bg:clear", (_e, kind) => {
    const k = wallKind(kind);
    dropWall(k);
    delete bgCache[k];
    return { ok: true };
  });

  ipcMain.handle("bg:load", (_e, kind) => {
    const k = wallKind(kind);
    const file = wallFile(k);
    if (!file) return "";
    try {
      const mt = fs.statSync(file).mtimeMs;
      const hit = bgCache[k];
      if (hit && hit.mtimeMs === mt && hit.url) return hit.url;
      const mime = /\.png$/i.test(file) ? "image/png" : "image/jpeg";
      const url = "data:" + mime + ";base64," + fs.readFileSync(file).toString("base64");
      bgCache[k] = { mtimeMs: mt, url: url };
      return url;
    } catch (e) { return ""; }
  });

  /* ---------- 屏幕独立提醒窗 ----------
     以前提醒弹层画在主窗口里面，用户把窗口挪到角落或最小化到托盘，弹层就跟着跑。
     现在单独开一个无边框透明小窗，直接落在屏幕上，全屏看视频/玩游戏也能盖住。
     调度逻辑仍然全在主窗口里：这里只负责「显示 / 定位 / 把点击结果传回去」。 */
  let alarmWin = null;
  let alarmMeta = { size: "standard", pos: "center", shown: false, waiting: false };
  const ALARM_CARD_W = { small: 300, standard: 400, large: 520 };
  const ALARM_CARD_H = { small: 330, standard: 400, large: 440 };
  const ALARM_PAD = 20;                 /* 卡片四周留给投影的透明边距 */

  function ensureAlarmWindow() {
    if (alarmWin && !alarmWin.isDestroyed()) return alarmWin;
    alarmWin = new BrowserWindow({
      width: 440, height: 420,
      frame: false, transparent: true, hasShadow: false,
      resizable: false, movable: true, minimizable: false, maximizable: false,
      fullscreenable: false, skipTaskbar: true, alwaysOnTop: true,
      show: false, backgroundColor: "#00000000",
      webPreferences: {
        contextIsolation: true,
        nodeIntegration: false,
        sandbox: true,
        spellcheck: false,
        preload: path.join(__dirname, "preload.js")
      }
    });
    alarmWin.setAlwaysOnTop(true, "screen-saver");
    try { alarmWin.setVisibleOnAllWorkspaces(true); } catch (e) {}
    alarmWin.loadFile("alarm.html");
    alarmWin.on("closed", () => { alarmWin = null; });
    return alarmWin;
  }

  /* 以「鼠标所在的那块屏」为准：提醒弹出时人的注意力就在鼠标附近。
     workArea 已经是 DIP 单位，和 setBounds 同一套坐标，不需要再乘 scaleFactor。 */
  function alarmDisplay() {
    try {
      const pt = screen.getCursorScreenPoint();
      return screen.getDisplayNearestPoint(pt);
    } catch (e) {
      return screen.getPrimaryDisplay();
    }
  }

  function computeAlarmBounds(display, size, w, h) {
    const area = display.workArea;
    const gap = 18;
    if (size === "full") {
      return { x: area.x, y: area.y, width: area.width, height: area.height };
    }
    const W = Math.max(160, Math.min(w || 440, area.width - 8));
    const H = Math.max(160, Math.min(h || 420, area.height - 8));
    let x, y;
    switch (alarmMeta.pos) {
      case "tl": x = area.x + gap; y = area.y + gap; break;
      case "tr": x = area.x + area.width - W - gap; y = area.y + gap; break;
      case "bl": x = area.x + gap; y = area.y + area.height - H - gap; break;
      case "br": x = area.x + area.width - W - gap; y = area.y + area.height - H - gap; break;
      case "follow": {
        const b = (win && !win.isDestroyed() && win.isVisible()) ? win.getBounds() : null;
        if (b) {
          const disp = screen.getDisplayMatching(b);
          const a = disp.workArea;
          x = Math.round(b.x + (b.width - W) / 2);
          y = Math.round(b.y + (b.height - H) / 2);
          x = Math.max(a.x, Math.min(x, a.x + a.width - W));
          y = Math.max(a.y, Math.min(y, a.y + a.height - H));
          return { x: Math.round(x), y: Math.round(y), width: Math.round(W), height: Math.round(H) };
        }
        x = Math.round(area.x + (area.width - W) / 2);
        y = Math.round(area.y + (area.height - H) / 2);
        break;
      }
      default:
        x = Math.round(area.x + (area.width - W) / 2);
        y = Math.round(area.y + (area.height - H) / 2);
    }
    x = Math.max(area.x, Math.min(x, area.x + area.width - W));
    y = Math.max(area.y, Math.min(y, area.y + area.height - H));
    return { x: Math.round(x), y: Math.round(y), width: Math.round(W), height: Math.round(H) };
  }

  /* 发给弹窗的数据里不含任何设置，页面只负责画。
     页面渲染完会把真实尺寸报回来（alarm:size），这里等它报完再 show，
     避免先按估算高度弹出来、再抖一下。万一页面没报（加载失败等），400ms 后照常显示。 */
  let alarmShowTimer = null;
  function showAlarmWindow(w0) {
    if (!w0 || w0.isDestroyed()) return;
    clearTimeout(alarmShowTimer);
    alarmShowTimer = null;
    alarmMeta.waiting = false;
    alarmMeta.shown = true;
    try { w0.show(); w0.focus(); } catch (e) {}
  }

  ipcMain.handle("alarm:show", (_e, p) => {
    const data = p || {};
    alarmMeta.size = data.size === "full" || data.size === "small" || data.size === "large" ? data.size : "standard";
    alarmMeta.pos = ["center", "tl", "tr", "bl", "br", "follow"].indexOf(data.pos) >= 0 ? data.pos : "center";

    const w0 = ensureAlarmWindow();
    const estW = alarmMeta.size === "full" ? 0 : (ALARM_CARD_W[alarmMeta.size] || 400) + ALARM_PAD * 2;
    const estH = alarmMeta.size === "full" ? 0 : (ALARM_CARD_H[alarmMeta.size] || 400) + ALARM_PAD * 2;
    /* 关键：窗口刚创建时还没套用显示器缩放，此时摆位会被按 1:1 解释，
       于是页面的 CSS 视口比预期窄一截（125% 缩放下 440 会被当成 352）。
       所以摆位要在 did-finish-load 之后再套一次，页面才量得准。 */
    const place = () => {
      try { w0.setBounds(computeAlarmBounds(alarmDisplay(), alarmMeta.size, estW, estH)); } catch (e) {}
    };
    place();

    const push = () => { place(); try { w0.webContents.send("alarm:show", data); } catch (e) {} };
    if (w0.webContents.isLoading()) w0.webContents.once("did-finish-load", push);
    else push();

    /* 窗口已经可见时直接显示（换内容而已），首次出现才等尺寸 */
    if (w0.isVisible()) { showAlarmWindow(w0); return true; }
    alarmMeta.waiting = true;
    clearTimeout(alarmShowTimer);
    alarmShowTimer = setTimeout(() => showAlarmWindow(w0), 400);
    return true;
  });

  /* 页面量完真实尺寸后校正窗口：只在保持锚点的前提下改大小，不会跳来跳去 */
  ipcMain.handle("alarm:size", (_e, w, h) => {
    if (!alarmWin || alarmWin.isDestroyed()) return false;
    if (alarmMeta.size !== "full") {
      const W = Math.round((Number(w) || 0) + ALARM_PAD * 2);
      const H = Math.round((Number(h) || 0) + ALARM_PAD * 2);
      if (W >= 120 && H >= 120) {
        try { alarmWin.setBounds(computeAlarmBounds(alarmDisplay(), alarmMeta.size, W, H)); } catch (e) {}
      }
    }
    if (alarmMeta.waiting) showAlarmWindow(alarmWin);
    return true;
  });

  ipcMain.handle("alarm:hide", () => {
    alarmMeta.shown = false;
    alarmMeta.waiting = false;
    clearTimeout(alarmShowTimer);
    alarmShowTimer = null;
    if (alarmWin && !alarmWin.isDestroyed()) {
      try { alarmWin.hide(); } catch (e) {}
    }
    return true;
  });

  /* 弹窗里的点击（知道了 / 稍后 / 停止循环）交回主窗口，由主窗口决定后续 */
  ipcMain.handle("alarm:action", (_e, a) => {
    if (win && !win.isDestroyed()) win.webContents.send("alarm:action", a || {});
    return true;
  });

  /* 窗口当前状态。页面用它区分「用户点开的」和「开机自启静默拉起的」 */
  ipcMain.handle("app:window-state", () => ({
    visible: !!(win && !win.isDestroyed() && win.isVisible()),
    startedHidden: startHidden
  }));

  /* ---------- 导出日历 ----------
     用户要的是「以后能和手机联动」。.ics 是唯一不花钱、不联网、手机自带日历都能认的格式：
     存成文件 → 发到手机 / 拖进日历，就同步过去了。 */
  ipcMain.handle("export:ics", async (_e, arg) => {
    const content = String((arg && arg.content) || "");
    const suggest = String((arg && arg.name) || "轻铃.ics");
    if (!content) return { ok: false, reason: "empty" };
    try {
      const ret = await dialog.showSaveDialog(win, {
        title: "导出日历文件",
        defaultPath: path.join(app.getPath("documents"), suggest),
        filters: [{ name: "日历文件", extensions: ["ics"] }]
      });
      if (ret.canceled || !ret.filePath) return { ok: false, canceled: true };
      fs.writeFileSync(ret.filePath, content, "utf8");
      return { ok: true, filePath: ret.filePath };
    } catch (e) { return { ok: false, reason: "write" }; }
  });

  app.on("second-instance", () => showWindow());

  app.whenReady().then(() => {
    /* 这里刻意不调用 app.setAppUserModelId()。
       踩过的坑：设了 AUMID 之后，Windows 会去注册表里找同 ID 的快捷方式来解析任务栏
       图标；绿色版没有快捷方式，安装版的快捷方式一旦失效，解析失败就退回系统那张
       空白占位图（v1.6.0 在绿色版上实测：任务栏是个白底蓝块的默认图标）。
       不设的话任务栏直接用 exe 自带的图标，一切正常；安装版从快捷方式启动时，
       系统会自动把快捷方式里写的 AUMID 套到进程上，通知归类照样对。 */
    repairAutoLaunch();
    createTray();
    createWindow();
    if (startHidden) hintHiddenOnce();
    applyQuickShortcut(true);
    app.on("activate", () => {
      if (BrowserWindow.getAllWindows().length === 0) createWindow();
    });
  });

  /* 窗口关闭后仍驻留托盘（退出由托盘菜单控制） */
  app.on("window-all-closed", () => {
    // 不退出：轻铃需要后台常驻
  });

  /* 退出时注销全局快捷键，避免占用 */
  app.on("will-quit", () => {
    try { globalShortcut.unregisterAll(); } catch (e) {}
  });
}
