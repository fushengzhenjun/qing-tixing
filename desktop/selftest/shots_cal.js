/* 日历截图验证：点时钟展开月历 → 翻月 → 点某天跳周视图
   运行：electron selftest/shots_cal.js */
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

app.disableHardwareAcceleration();
app.setPath("userData", path.join(app.getPath("temp"), "qx-cal-" + Date.now()));
app.on("window-all-closed", () => {});
process.on("unhandledRejection", (e) => {
  console.error("FAIL: " + (e && e.message ? e.message : e));
  setTimeout(() => app.quit(), 300);
});

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const OUT = path.join(__dirname, "..", "..", "_audit", "shots");
const pad = (n) => (n < 10 ? "0" : "") + n;
const iso = (d) => d.getFullYear() + "-" + pad(d.getMonth() + 1) + "-" + pad(d.getDate());
const dk = (d) => d.getFullYear() + "-" + d.getMonth() + "-" + d.getDate();

const today = new Date();
const tm = new Date(today.getTime() + 86400000);
const d3 = new Date(today.getTime() + 3 * 86400000);
const d9 = new Date(today.getTime() + 9 * 86400000);

const DATA = [
  { id: "a1", title: "服药", cat: "health", ring: "soft", vibrate: true, enabled: true,
    mutedDates: [], doneDates: [dk(today)], mode: "repeat", repTime: "08:00", rmode: "daily" },
  { id: "a2", title: "起身活动", cat: "health", ring: "soft", vibrate: true, enabled: true,
    mutedDates: [], doneDates: [], mode: "loop", startH: 9, startM: 0, endH: 18, endM: 0,
    firstH: 9, firstM: 0, every: 45, days: [1, 2, 3, 4, 5] },
  { id: "a4", title: "交周报", cat: "work", ring: "chime", vibrate: true, enabled: true,
    mutedDates: [], doneDates: [], mode: "once", at: iso(d3) + "T15:00" },
  { id: "a5", title: "牙医复诊", cat: "health", ring: "alarm", vibrate: true, enabled: true,
    mutedDates: [], doneDates: [], mode: "once", at: iso(d9) + "T10:30" },
  { id: "a6", title: "跑步", cat: "life", ring: "soft", vibrate: true, enabled: true,
    mutedDates: [], doneDates: [], mode: "repeat", repTime: "19:30", rmode: "week", days: [1, 3, 5] },
  { id: "a7", title: "读书 30 分钟", cat: "study", ring: "soft", vibrate: true, enabled: true,
    mutedDates: [], doneDates: [], mode: "repeat", repTime: "21:00", rmode: "daily" },
  { id: "a8", title: "交房租", cat: "life", ring: "soft", vibrate: true, enabled: true,
    mutedDates: [], doneDates: [], mode: "repeat", repTime: "10:00", rmode: "monthly", monthDay: 1 },
  { id: "a9", title: "给爸妈打电话", cat: "life", ring: "soft", vibrate: true, enabled: true,
    mutedDates: [], doneDates: [], mode: "repeat", repTime: "20:00", rmode: "week", days: [0] },
  { id: "a10", title: "团队晨会", cat: "work", ring: "chime", vibrate: true, enabled: true,
    mutedDates: [], doneDates: [], mode: "repeat", repTime: "09:30", rmode: "weekday" }
];

async function shoot(win, name) {
  await sleep(450);
  try {
    const st = await win.webContents.executeJavaScript(
      "(function(){var c=document.querySelector('#calSheet');" +
      "return [c.classList.contains('show')?'CAL':'-'," +
      "document.querySelector('#calTitle').textContent," +
      "document.querySelectorAll('#calGrid .cal-day').length," +
      "document.querySelectorAll('#calGrid .cal-day.is-today').length," +
      "document.querySelector('#viewTitle').textContent].join(' ');})()"
    );
    console.log("  state " + name + ": " + st);
  } catch (e) { console.log("  state " + name + ": ERR " + e.message); }
  await sleep(400);
  const img = await win.webContents.capturePage();
  const p = path.join(OUT, name + ".png");
  fs.writeFileSync(p, img.toPNG());
  console.log("shot " + p + " " + img.getSize().width + "x" + img.getSize().height);
}

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
  await win.loadFile(path.join(__dirname, "..", "轻铃.html"));
  await sleep(600);
  await win.webContents.executeJavaScript(
    "localStorage.setItem('qingtx.reminders.v2', " + JSON.stringify(JSON.stringify(DATA)) + ");" +
    "localStorage.setItem('qingtx.settings.v1', " + JSON.stringify(JSON.stringify({ theme: "system", holidayAware: true, quickAdd: true })) + ");"
  );
  await win.reload();
  await sleep(1100);

  /* 1. 点时钟展开日历 */
  await win.webContents.executeJavaScript("document.querySelector('#nowCard').click();");
  await shoot(win, "20_cal");

  /* 2. 翻到下个月 */
  await win.webContents.executeJavaScript("document.querySelector('#calNext').click();");
  await shoot(win, "21_cal_next");
  await win.webContents.executeJavaScript("document.querySelector('#calToday').click();");
  await sleep(300);

  /* 3. 点某一天 → 跳到那一周并高亮 */
  await win.webContents.executeJavaScript(`
    (function(){
      var days=document.querySelectorAll('#calGrid .cal-day:not(.out)');
      days[Math.min(days.length-1, 4)].click();
    })();
  `);
  await shoot(win, "22_cal_jump_week");

  /* 4. 深色模式日历 */
  await win.webContents.executeJavaScript(`
    document.documentElement.setAttribute('data-theme','dark');
    document.querySelector('#nowCard').click();
  `);
  await shoot(win, "23_cal_dark");
  await win.webContents.executeJavaScript("document.querySelector('#calClose').click();document.documentElement.removeAttribute('data-theme');");

  /* 5. 窄屏日历 */
  win.setBounds({ width: 402, height: 800 });
  await sleep(400);
  await win.webContents.executeJavaScript("document.querySelector('#nowCard').click();");
  await shoot(win, "24_cal_narrow");

  const errs = await win.webContents.executeJavaScript("window.__errs || []");
  console.log("js errors: " + JSON.stringify(errs));
  app.quit();
});
