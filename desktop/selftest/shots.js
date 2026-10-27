/* 抓图验证新增 UI（暂停时长选择器、暂停横幅、补提醒弹层）
   运行：electron selftest/shots.js */
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
app.setPath("userData", path.join(app.getPath("temp"), "qx-shot-data"));
app.on("window-all-closed", () => {});

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const OUT = path.join(__dirname, "..", "..", "_audit", "shots");

async function shoot(win, name) {
  await sleep(900);
  win.webContents.invalidate();   /* 隐藏窗口不会自动重绘，否则截到上一帧 */
  await sleep(600);
  const img = await win.webContents.capturePage();
  const p = path.join(OUT, name + ".png");
  fs.writeFileSync(p, img.toPNG());
  console.log("shot " + p + " " + img.getSize().width + "x" + img.getSize().height);
}

app.whenReady().then(async () => {
  fs.mkdirSync(OUT, { recursive: true });

  const win = new BrowserWindow({
    show: false,
    width: 1020,
    height: 760,
    backgroundColor: "#F6F5F1",
    webPreferences: {
      preload: path.join(__dirname, "..", "preload.js"),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  });

  /* 每次从干净状态开始，避免上一轮测试数据影响截图 */
  await win.webContents.session.clearStorageData({ storages: ["localstorage", "indexdb"] });

  await win.loadFile(path.join(__dirname, "..", "..", "_audit", "轻铃.selftest.html"));
  await sleep(700);

  /* 1. 主界面 + 暂停横幅：用界面按钮触发，保证高亮与倒计时一致 */
  await win.webContents.executeJavaScript(`
    document.querySelector('#settings').classList.remove('show');
    document.querySelector('#fPauseSeg button[data-min="240"]').click();
  `);
  await shoot(win, "01_main_paused");

  /* 2. 设置面板：暂停时长选择器 */
  await win.webContents.executeJavaScript(`
    __qx.openSettings();
    var sc = document.querySelector('#settings .sheet');
    if (sc) sc.scrollTop = 0;
  `);
  await shoot(win, "02_settings_pause");

  /* 3. 切到「明早 8 点」 */
  await win.webContents.executeJavaScript(`
    var b = document.querySelector('#fPauseSeg button[data-min="tomorrow"]');
    if (b) b.click();
  `);
  await shoot(win, "03_settings_tomorrow");

  /* 4. 补提醒弹层（模拟合盖错过）：重新加载页面，避免受前面弹层关闭动画干扰 */
  await win.loadFile(path.join(__dirname, "..", "..", "_audit", "轻铃.selftest.html"));
  await sleep(800);
  const st4 = await win.webContents.executeJavaScript(`
    (function(){
      __qx.applyPause(false);
      document.querySelector('#settings').classList.remove('show');
      __qx.setR([{id:"s1",title:"该起来走走了",mode:"once",
        at:new Date(Date.now()-2*3600*1000).toISOString(),
        enabled:true,cat:"health",ring:"none",vibrate:false}]);
      var n = __qx.catchUpMissed(Date.now() - 130*60*1000);
      return {
        补提数量: n,
        pauseAll: __qx.getS().pauseAll,
        设置面板打开: document.querySelector('#settings').classList.contains('show'),
        弹层显示: document.querySelector('#alarm').classList.contains('show'),
        弹层时间: document.querySelector('#alarmTime').textContent
      };
    })()
  `);
  console.log("STEP4 " + JSON.stringify(st4));
  await shoot(win, "04_alarm_late");

  /* 5. 快速添加小窗：真实页面 + 例句 */
  const qw = new BrowserWindow({
    show: false,
    width: 560,
    height: 186,
    frame: false,
    resizable: false,
    backgroundColor: "#E9E7E0",
    webPreferences: {
      preload: path.join(__dirname, "..", "preload.js"),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  });
  await qw.loadFile(path.join(__dirname, "..", "quick.html"));
  await sleep(500);
  await qw.webContents.executeJavaScript(`
    var q = document.getElementById("q");
    q.value = "明天下午三点 开会";
    q.dispatchEvent(new Event("input"));
  `);
  await shoot(qw, "05_quickadd");

  await win.webContents.executeJavaScript(`
    document.querySelector('#alarmOk').click();
    __qx.closeAlarm();
    document.querySelector('#alarm').classList.remove('show');
  `);
  await sleep(500);

  /* 6. 编辑器：工作日 + 节假日提示 */
  const st6 = await win.webContents.executeJavaScript(`
    (function(){
      document.querySelector('#fab').click();
      document.querySelector('#fType button[data-type="repeat"]').click();
      document.querySelector('#fTitle').value = "站会";
      var sel = document.querySelector('#fRepMode');
      sel.value = "weekday";
      sel.dispatchEvent(new Event("change"));
      var sheet = document.querySelector('#editor .sheet');
      if (sheet) sheet.scrollTop = 210;
      return {
        面板: document.querySelector('#editor').classList.contains('show'),
        类型: document.querySelector('#fRepMode').value,
        提示可见: document.querySelector('#repHolidayTip').style.display !== 'none',
        提示: document.querySelector('#repHolidayTip').textContent
      };
    })()
  `);
  console.log("STEP6 " + JSON.stringify(st6));
  await shoot(win, "06_editor_weekday");

  app.exit(0);
}).catch((e) => {
  console.error("FAIL", e && e.stack || e);
  app.exit(1);
});
