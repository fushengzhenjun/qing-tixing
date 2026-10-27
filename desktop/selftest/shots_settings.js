/* 设置面板底部「反馈与联系」截图验证
   运行：electron selftest/shots_settings.js */
const { app, BrowserWindow, ipcMain } = require("electron");
const path = require("path");
const fs = require("fs");

ipcMain.handle("window:alert", () => "ok");
ipcMain.handle("auto-launch:get", () => false);
ipcMain.handle("auto-launch:set", (_e, v) => !!v);
ipcMain.handle("shortcut:get", () => ({ enabled: true, registered: true, accel: "CommandOrControl+Alt+N" }));
ipcMain.handle("shortcut:set", (_e, v) => ({ enabled: !!v, registered: !!v, accel: "CommandOrControl+Alt+N" }));
ipcMain.handle("quick:submit", () => true);
ipcMain.handle("quick:cancel", () => true);
ipcMain.handle("shell:open-external", () => true);   // 截图时不真的开浏览器
/* 备份信息用一份假数据，截图里能看到「最近：X月X日 · N 条」的真实排版 */
ipcMain.handle("backup:list", () => ([
  { name: "reminders-2026-10-27.json", at: Date.now(), count: 12, size: 2048 },
  { name: "reminders-2026-10-26.json", at: Date.now() - 86400000, count: 11, size: 2000 },
  { name: "reminders-2026-10-25.json", at: Date.now() - 2 * 86400000, count: 9, size: 1800 }
]));
ipcMain.handle("backup:save", () => ({ ok: true }));
ipcMain.handle("backup:read", () => null);
ipcMain.handle("backup:stash", () => ({ ok: true }));
ipcMain.handle("backup:reveal", () => true);
ipcMain.handle("app:paths", () => ({ data: "", backup: "" }));
ipcMain.handle("export:ics", () => ({ ok: false, canceled: true }));

app.disableHardwareAcceleration();
app.setPath("userData", path.join(app.getPath("temp"), "qx-set-" + Date.now()));
app.on("window-all-closed", () => {});
process.on("unhandledRejection", (e) => {
  console.error("FAIL: " + (e && e.message ? e.message : e));
  setTimeout(() => app.quit(), 300);
});

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const OUT = path.join(__dirname, "..", "..", "_audit", "shots");

async function shoot(win, name) {
  /* 隐藏窗口容易抓到上一帧：等两次 rAF 再截 */
  await win.webContents.executeJavaScript(
    "new Promise(function(r){requestAnimationFrame(function(){requestAnimationFrame(r);});})"
  );
  await sleep(420);
  const info = await win.webContents.executeJavaScript(
    "(function(){var s=document.querySelector('#settings .sheet');" +
    "return {show:document.querySelector('#settings').classList.contains('show')," +
    "scroll:s.scrollTop, h:s.scrollHeight, c:s.clientHeight};})()"
  );
  console.log("  state " + name + ": " + JSON.stringify(info));
  const img = await win.webContents.capturePage();
  fs.writeFileSync(path.join(OUT, name + ".png"), img.toPNG());
  console.log("shot " + name + ".png " + img.getSize().width + "x" + img.getSize().height);
}

/* 打开设置并滚到底 */
const openAndScrollBottom = `
(function(){
  document.querySelector('#settingsBtn').click();
  var s = document.querySelector('#settings .sheet');
  s.scrollTop = s.scrollHeight;
})();
`;

app.whenReady().then(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const win = new BrowserWindow({
    show: false, width: 1160, height: 820, backgroundColor: "#F6F5F1",
    webPreferences: {
      preload: path.join(__dirname, "..", "preload.js"),
      contextIsolation: true, nodeIntegration: false, sandbox: true, offscreen: true
    }
  });
  win.webContents.on("console-message", (_e, lv, msg) => { if (lv >= 2) console.log("[renderer] " + msg); });

  await win.webContents.session.clearStorageData({ storages: ["localstorage", "indexdb"] });
  const file = path.join(__dirname, "..", "轻铃.html");
  await win.loadFile(file);
  await sleep(900);

  await win.webContents.executeJavaScript(openAndScrollBottom);
  await shoot(win, "30_settings_feedback");

  /* 深色 */
  await win.webContents.executeJavaScript(
    "document.documentElement.setAttribute('data-theme','dark');document.querySelector('#settingsClose').click();"
  );
  await sleep(200);
  await win.webContents.executeJavaScript(openAndScrollBottom);
  await shoot(win, "31_settings_feedback_dark");
  await win.webContents.executeJavaScript(
    "document.querySelector('#settingsClose').click();document.documentElement.removeAttribute('data-theme');"
  );

  /* 窄屏（底部抽屉） */
  win.setBounds({ width: 402, height: 800 });
  await sleep(500);
  await win.webContents.executeJavaScript(openAndScrollBottom);
  await shoot(win, "32_settings_feedback_narrow");

  const errs = await win.webContents.executeJavaScript("window.__errs || []");
  if (errs.length) console.log("renderer errors: " + JSON.stringify(errs));
  console.log("done");
  app.quit();
}).catch((e) => {
  console.error("FAIL " + (e && e.stack || e));
  app.exit(1);
});
