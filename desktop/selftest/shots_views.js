/* 周视图 / 今日清单 截图验证
   运行：electron selftest/shots_views.js */
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
app.setPath("userData", path.join(app.getPath("temp"), "qx-view-" + Date.now()));
app.on("window-all-closed", () => {});
process.on("unhandledRejection", (e) => {
  console.error("FAIL: " + (e && e.message ? e.message : e));
  setTimeout(() => app.quit(), 300);
});

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const OUT = path.join(__dirname, "..", "..", "_audit", "shots");

function pad(n) { return (n < 10 ? "0" : "") + n; }
function iso(d) { return d.getFullYear() + "-" + pad(d.getMonth() + 1) + "-" + pad(d.getDate()); }
function dk(d) { return d.getFullYear() + "-" + d.getMonth() + "-" + d.getDate(); }

const today = new Date();
const tm = new Date(today.getTime() + 86400000);

const DATA = [
  { id: "a1", title: "服药", cat: "health", ring: "soft", vibrate: true, enabled: true,
    mutedDates: [], doneDates: [dk(today)], mode: "repeat", repTime: "08:00", rmode: "daily" },
  { id: "a2", title: "起身活动", cat: "health", ring: "soft", vibrate: true, enabled: true,
    mutedDates: [dk(today)], doneDates: [], mode: "loop", startH: 9, startM: 0, endH: 18, endM: 0,
    firstH: 9, firstM: 0, every: 45, days: [1, 2, 3, 4, 5] },
  { id: "a3", title: "喝水", cat: "health", ring: "soft", vibrate: true, enabled: true,
    mutedDates: [], doneDates: [], mode: "loop", startH: 9, startM: 0, endH: 18, endM: 0,
    firstH: 9, firstM: 0, every: 60, days: [1, 2, 3, 4, 5] },
  { id: "a4", title: "交周报", cat: "work", ring: "chime", vibrate: true, enabled: true,
    mutedDates: [], doneDates: [], mode: "once", at: iso(today) + "T15:00" },
  { id: "a5", title: "牙医复诊", cat: "health", ring: "alarm", vibrate: true, enabled: true,
    mutedDates: [], doneDates: [], mode: "once", at: iso(tm) + "T10:30" },
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
      "(function(){var l=document.querySelector('#list'),w=document.querySelector('#weekView'),t=document.querySelector('#todoView');" +
      "return [l.classList.contains('hidden')?'-':'l',w.classList.contains('hidden')?'-':'W',t.classList.contains('hidden')?'-':'T'," +
      "document.querySelector('#viewTitle').textContent,document.querySelector('#editor').className,(window.__openTraces||[]).length].join(' ');})()"
    );
    console.log("  state " + name + ": " + st);
    if(name==="10_week"){const tr=await win.webContents.executeJavaScript("JSON.stringify((window.__openTraces||[]).slice(0,3))");console.log("  TRACE "+tr);}
  } catch (e) { console.log("  state " + name + ": ERR " + e.message); }
  /* 关掉可能残留的弹层，再强制两轮重绘，避免抓到上一帧 */
  await win.webContents.executeJavaScript(
    "document.querySelector('#editor').classList.remove('show');document.querySelector('#alarm').classList.remove('show');"
  );
  await sleep(500);
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
  win.webContents.on("console-message", (_e, lv, msg) => {
    if (lv >= 2) console.log("[renderer] " + msg);
  });

  await win.webContents.session.clearStorageData({ storages: ["localstorage", "indexdb"] });
  await win.loadFile(path.join(__dirname, "..", "轻提醒.html"));
  await sleep(600);

  await win.webContents.executeJavaScript(
    "localStorage.setItem('qingtx.reminders.v2', " + JSON.stringify(JSON.stringify(DATA)) + ");" +
    "localStorage.setItem('qingtx.settings.v1', " + JSON.stringify(JSON.stringify({ theme: "system", holidayAware: true, quickAdd: true })) + ");"
  );
  await win.webContents.executeJavaScript("window.__traceOpen=1");await win.reload();
  await sleep(1200);await win.webContents.executeJavaScript("window.__traceOpen=1");

  const switchView = async (v) => {
    await win.webContents.executeJavaScript(`document.querySelector('#viewSeg [data-view="${v}"]').click();`);
    await sleep(500);
  };

  await switchView("week");
  await shoot(win, "10_week");

  /* 切到下一周，看看跨周是否正常 */
  await win.webContents.executeJavaScript(`document.querySelector('[data-wk="1"]').click();`);
  await shoot(win, "11_week_next");

  await win.webContents.executeJavaScript(`document.querySelector('#wkToday').click();`);
  await sleep(300);

  await switchView("todo");
  await shoot(win, "12_todo");

  /* 勾掉第一条，验证完成态与进度条 */
  await win.webContents.executeJavaScript(`
    (function(){
      var t=document.querySelectorAll('#todoView .td-tick');
      if(t.length>1) t[1].click();
    })();
  `);
  await shoot(win, "13_todo_checked");

  /* 清单滚到底，确认晚上分组 */
  await win.webContents.executeJavaScript(`
    (function(){ var v=document.querySelector('#todoView'); v.scrollTop=v.scrollHeight; })();
  `);
  await shoot(win, "13b_todo_bottom");

  await win.webContents.executeJavaScript(`document.querySelector('#todoView').scrollTop=0;`);

  /* 深色模式下的两个视图 */
  await win.webContents.executeJavaScript(`document.documentElement.setAttribute('data-theme','dark');`);
  await switchView("week");
  await shoot(win, "14_week_dark");
  await switchView("todo");
  await shoot(win, "15_todo_dark");
  await win.webContents.executeJavaScript(`document.documentElement.removeAttribute('data-theme');`);

  /* 窄屏（手机）周视图 */
  win.setBounds({ width: 402, height: 800 });
  await sleep(400);
  await switchView("week");
  await shoot(win, "16_week_narrow");
  await switchView("todo");
  await shoot(win, "17_todo_narrow");

  /* 回列表，确认列表没被改坏 */
  win.setBounds({ width: 1162, height: 842 });
  await sleep(400);
  await switchView("list");
  await shoot(win, "18_list");

  const errs = await win.webContents.executeJavaScript("window.__errs || []");
  console.log("js errors: " + JSON.stringify(errs));
  app.quit();
});
