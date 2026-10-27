/* 量一下周视图在不同宽度下的溢出情况 */
const { app, BrowserWindow, ipcMain } = require("electron");
const path = require("path");

ipcMain.handle("window:alert", () => true);
ipcMain.handle("auto-launch:get", () => false);
ipcMain.handle("auto-launch:set", () => true);
ipcMain.handle("shortcut:get", () => ({ enabled: false, registered: false, accel: "" }));
ipcMain.handle("shortcut:set", () => ({ enabled: false, registered: false, accel: "" }));
ipcMain.handle("quick:submit", () => ({ ok: true }));
ipcMain.handle("quick:cancel", () => true);

app.disableHardwareAcceleration();
app.setPath("userData", path.join(app.getPath("temp"), "qx-measure-" + Date.now()));
app.on("window-all-closed", () => {});

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

app.whenReady().then(async () => {
  const win = new BrowserWindow({
    show: false, width: 1160, height: 820,
    webPreferences: {
      preload: path.join(__dirname, "..", "preload.js"),
      contextIsolation: true, nodeIntegration: false, sandbox: true, offscreen: true,
    },
  });
  await win.loadFile(path.join(__dirname, "..", "轻提醒.html"));
  await sleep(900);
  await win.webContents.executeJavaScript(`document.querySelector('#viewSeg [data-view="week"]').click();`);
  await sleep(600);

  for (const w of [1160, 700, 520, 400, 360]) {
    win.setContentSize(w, 780);
    await sleep(500);
    const r = await win.webContents.executeJavaScript(`(function(){
      var sc=document.querySelector('#wkScroll');
      var g=document.querySelector('.wk-grid');
      var col=document.querySelector('.wk-col');
      var cs=getComputedStyle(g);
      return {
        vw: innerWidth,
        scrollW: sc.scrollWidth, clientW: sc.clientWidth,
        scrollH: sc.scrollHeight, clientH: sc.clientHeight,
        gridW: Math.round(g.getBoundingClientRect().width),
        colW: col ? Math.round(col.getBoundingClientRect().width) : 0,
        minW: cs.minWidth, cw: cs.getPropertyValue('--wk-cw').trim(),
        firstDayh: (document.querySelector('.wk-dayh')||{}).offsetWidth || 0
      };
    })()`);
    console.log(w + " → " + JSON.stringify(r));
  }
  app.exit(0);
}).catch((e) => { console.error("FAIL", e); app.exit(1); });
