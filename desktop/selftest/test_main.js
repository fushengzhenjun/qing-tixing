/* 轻铃 · 补提醒 / 暂停 自动化验证
   运行：electron selftest/test_main.js */
const { app, BrowserWindow, ipcMain } = require("electron");
const path = require("path");
const fs = require("fs");

/* 自检自带主进程，这里用桩确认「渲染层 → preload → 主进程」这条链路通 */
ipcMain.handle("window:alert", () => "bridge-ok");
ipcMain.handle("auto-launch:get", () => false);
ipcMain.handle("auto-launch:set", (_e, v) => !!v);
ipcMain.handle("shortcut:get", () => ({ enabled: true, registered: true, accel: "CommandOrControl+Alt+N" }));
ipcMain.handle("shortcut:set", (_e, v) => ({ enabled: !!v, registered: !!v, accel: "CommandOrControl+Alt+N" }));
ipcMain.handle("quick:submit", (_e, p) => ({ ok: true, title: p && p.title }));
ipcMain.handle("quick:cancel", () => true);
/* 反馈按钮的出口：记录页面请求打开的地址，不外真开浏览器 */
let openedUrl = "";
ipcMain.handle("shell:open-external", (_e, url) => { openedUrl = String(url || ""); return true; });

/* 备份与导出：只记录调用，不落盘 */
const backupWrites = [];
let backupListReply = [];
let icsSaved = null;
ipcMain.handle("backup:save", (_e, json, count) => { backupWrites.push({ len: String(json || "").length, count: count }); return { ok: true }; });
ipcMain.handle("backup:list", () => backupListReply);
ipcMain.handle("backup:read", () => JSON.stringify([{ id: "bk1", title: "从备份回来的", mode: "once", cat: "other", enabled: true, at: new Date(Date.now() + 3600000).toISOString(), mutedDates: [], doneDates: [] }]));
ipcMain.handle("backup:stash", (_e, json, count) => { backupWrites.push({ stash: true, len: String(json || "").length, count: count }); return { ok: true }; });
ipcMain.handle("backup:reveal", () => true);
ipcMain.handle("app:paths", () => ({ data: "", backup: "" }));
ipcMain.handle("export:ics", (_e, arg) => { icsSaved = arg; return { ok: true, filePath: "x.ics" }; });

/* 屏幕独立提醒窗（v1.5 新增）：只记录页面要求显示的次数与内容 */
const alarmShows = [];
ipcMain.handle("alarm:show", (_e, p) => { alarmShows.push(p || {}); return true; });
ipcMain.handle("alarm:hide", () => true);
ipcMain.handle("alarm:size", () => true);
ipcMain.handle("alarm:action", () => true);

/* 自定义背景：返回一张 1x1 PNG，验证「主进程给 dataURL → 页面贴上去」这条链路 */
const PNG_1PX =
  "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";
const bgLoads = [];
ipcMain.handle("bg:load", (_e, kind) => { bgLoads.push(kind); return PNG_1PX; });
ipcMain.handle("bg:pick", () => ({ ok: false, canceled: true }));
ipcMain.handle("bg:clear", () => ({ ok: true }));

app.disableHardwareAcceleration();
app.setPath("userData", path.join(app.getPath("temp"), "qx-selftest-data"));
app.on("window-all-closed", () => {});

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

app.whenReady().then(async () => {
  const win = new BrowserWindow({
    show: false,
    width: 1000,
    height: 760,
    webPreferences: {
      preload: path.join(__dirname, "..", "preload.js"),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  });

  win.webContents.on("console-message", (_e, _lvl, msg) => console.log("[page]", msg));

  const file = path.join(__dirname, "..", "..", "_audit", "轻铃.selftest.html");
  if (!fs.existsSync(file)) throw new Error("缺少自检副本，先运行 node selftest/make.js");
  await win.loadFile(file);
  await sleep(600);

  const res = await win.webContents.executeJavaScript(
    `(async function(){
      var Q = window.__qx, out = {};
      if(!Q) return {error:"hook 未注入"};

      /* 这一段测的是「窗口内弹层」老路径，所以先把「弹到屏幕上」关掉；
         屏幕独立弹窗的行为在最后一段单独验证 */
      Q.getS().screenAlarm = false;

      function alarmVisible(){ return document.querySelector("#alarm").classList.contains("show"); }
      function alarmTime(){ return document.querySelector("#alarmTime").textContent; }
      function reset(){
        Q.setR([]);
        Q.closeAlarm();
        document.querySelector("#alarm").classList.remove("show");
      }

      /* ---- 1. 单次提醒：原定 2 小时前（模拟合盖错过）应补提 ---- */
      reset();
      var past = new Date(Date.now() - 2*3600*1000);
      Q.setR([{id:"t1",title:"补提单次",mode:"once",at:past.toISOString(),
               enabled:true,cat:"other",ring:"none",vibrate:false}]);
      var from = Date.now() - 130*60*1000;
      out.onceFired      = Q.catchUpMissed(from);
      out.onceAlarmShown = alarmVisible();
      out.onceTitle      = document.querySelector("#alarmTitle").textContent;
      out.onceTimeText   = alarmTime();
      out.onceMarked     = alarmTime().indexOf("补提醒") >= 0;
      out.onceDisabled   = Q.getR()[0].enabled === false;

      /* ---- 2. 同一断层再跑一次，不应重复补提 ---- */
      out.dupGuard = Q.catchUpMissed(from);

      /* ---- 3. 每日重复错过 3 天：只补最近一次，不能弹 3 次 ---- */
      reset();
      Q.setR([{id:"t2",title:"补提每天",mode:"repeat",rmode:"daily",repTime:"09:00",
               enabled:true,cat:"other",ring:"none",vibrate:false,mutedDates:[]}]);
      out.dailyFired = Q.catchUpMissed(Date.now() - 72*3600*1000);
      out.dailyTitle = document.querySelector("#alarmTitle").textContent;

      /* ---- 4. 暂停中不应补提 ---- */
      reset();
      Q.setR([{id:"t4",title:"暂停中不该响",mode:"once",at:past.toISOString(),
               enabled:true,cat:"other",ring:"none",vibrate:false}]);
      Q.applyPause(true, Date.now() + 3600*1000);
      out.pausedCatchUp  = Q.catchUpMissed(from);
      out.pausedIsPaused = Q.isPaused();
      out.pausedText     = Q.pauseText();
      out.pauseBanner    = !document.querySelector("#pauseBanner").classList.contains("hidden");
      out.pauseDurShown  = document.querySelector("#fPauseDur").style.display !== "none";
      out.pauseDesc      = document.querySelector("#fPauseDesc").textContent;

      /* ---- 5. 暂停到期后自动视为恢复 ---- */
      Q.applyPause(true, Date.now() - 1000);
      out.expiredIsPaused = Q.isPaused();
      Q.applyPause(false);
      out.offIsPaused    = Q.isPaused();
      out.offBannerHidden= document.querySelector("#pauseBanner").classList.contains("hidden");

      /* ---- 6. 暂停恢复后同一条提醒应正常补提 ---- */
      out.afterResumeFired = Q.catchUpMissed(from);

      /* ---- 7. 主进程窗口唤起接口是否通 ---- */
      try { out.alertWindowApi = await window.qingtx.alertWindow(); }
      catch(e) { out.alertWindowApi = "ERR:" + e.message; }

      /* ---- 8. 法定节假日与调休 ---- */
      reset();
      function D(s){ return new Date(s+"T09:00:00"); }
      out.years           = Q.holidayYears();
      out.nationalDayWork = Q.isWorkday(D("2026-10-01"));   // 国庆 → 不上班
      out.makeupSatWork   = Q.isWorkday(D("2026-10-10"));   // 调休周六 → 上班
      out.plainSatWork    = Q.isWorkday(D("2026-08-15"));   // 普通周六 → 休息
      out.plainMonWork    = Q.isWorkday(D("2026-08-17"));   // 普通周一 → 上班

      var rule = {id:"h1",title:"节假日用例",mode:"repeat",rmode:"weekday",repTime:"09:00",
                  enabled:true,cat:"other",ring:"none",vibrate:false,mutedDates:[]};
      out.weekdayOnHoliday = Q.repeatDayMatches(rule, D("2026-10-01"));   // false
      out.weekdayOnMakeup  = Q.repeatDayMatches(rule, D("2026-10-10"));   // true
      Q.getS().holidayAware = false;
      out.holidayAwareOff  = Q.repeatDayMatches(rule, D("2026-10-01"));   // true（关掉后按周一到周五）
      Q.getS().holidayAware = true;

      out.loopHoliday   = Q.loopDayOk([1,2,3,4,5], D("2026-10-02"));      // false
      out.loopMakeupSat = Q.loopDayOk([1,2,3,4,5], D("2026-10-10"));      // true
      out.loopOtherDays = Q.loopDayOk([0,6], D("2026-10-03"));            // true（自定义星期不受影响）

      var nf = Q.nextFire(rule, new Date("2026-09-30T20:00:00"));
      out.nextAfterHoliday = nf ? nf.toISOString() : null;                // 应为 10-08 09:00
      out.desc = Q.holidayDesc();

      /* ---- 9. 快速添加写回数据 ---- */
      var before = Q.getR().length;
      out.quickOnce   = Q.addQuickReminder({title:"快速添加用例",mode:"once",at:"2026-10-27T15:00"});
      out.quickRepeat = Q.addQuickReminder({title:"每周复盘",mode:"repeat",rmode:"week",days:[3],repTime:"14:00"});
      out.quickBad    = Q.addQuickReminder({title:"坏时间",mode:"once",at:"不是时间"});
      var list = Q.getR();
      out.quickAddedCount = list.length - before;
      var rep = list.filter(function(r){return r.title==="每周复盘";})[0];
      out.quickRepeatRule = rep ? (rep.rmode + "/" + (rep.days||[]).join(",") + "/" + rep.repTime) : null;

      /* ---- 10. 设置项文案随开关变化 ---- */
      Q.getS().holidayAware = false;
      Q.syncHolidayUI();
      out.descOff = document.querySelector("#fHolidayDesc").textContent;
      Q.getS().holidayAware = true;
      Q.syncHolidayUI();
      out.descOn = document.querySelector("#fHolidayDesc").textContent;

      Q.getS().quickAdd = false;
      Q.syncQuickUI({ enabled: false, registered: false });
      out.quickOffText = document.querySelector("#fQuickDesc").textContent;
      out.quickOffSwitch = document.querySelector("#fQuick").classList.contains("on");
      Q.getS().quickAdd = true;
      Q.syncQuickUI({ enabled: true, registered: false });
      out.quickBusyText = document.querySelector("#fQuickDesc").textContent;
      Q.getS().quickAdd = true;
      Q.syncQuickUI({ enabled: true, registered: true });
      out.quickOnText = document.querySelector("#fQuickDesc").textContent;
      out.quickOnSwitch = document.querySelector("#fQuick").classList.contains("on");

      /* ---- 11. 今日清单：条目 / 勾选 / 进度 / 撤销 ---- */
      reset();
      var t0 = new Date(); t0.setHours(0,0,0,0);
      function at(h,m){ var d = new Date(t0); d.setHours(h,m,0,0); return d.toISOString(); }
      Q.setR([
        {id:"v1",title:"早上打卡",mode:"once",at:at(9,0),enabled:true,cat:"work",
         ring:"none",vibrate:false,doneDates:[]},
        {id:"v2",title:"中午复盘",mode:"repeat",rmode:"daily",repTime:"12:30",enabled:true,cat:"life",
         ring:"none",vibrate:false,mutedDates:[],doneDates:[]},
        {id:"v3",title:"工作时段",mode:"loop",days:[0,1,2,3,4,5,6],firstH:8,firstM:0,endH:18,endM:0,every:30,
         enabled:true,cat:"other",ring:"none",vibrate:false,mutedDates:[],doneDates:[]}
      ]);
      Q.setView("list"); Q.setView("todo");
      out.todoItems  = document.querySelectorAll("#todoView .td-item").length;   // 3
      out.todoGroups = document.querySelectorAll("#todoView .td-group").length;  // 2（早上/下午）
      out.todoOrder  = Array.prototype.map.call(document.querySelectorAll("#todoView .td-tt"),
                        function(n){return n.textContent;}).join(">");
      out.todoProg0  = document.querySelector("#todoView .td-prog .n span").textContent;

      document.querySelector('#todoView .td-tick[data-done="v1"]').click();
      out.tickDoneCls = document.querySelectorAll("#todoView .td-item.done").length;      // 1
      out.tickProg    = document.querySelector("#todoView .td-prog .n span").textContent; // 已完成 1 / 3
      out.tickBarW    = document.querySelector("#todoView .td-bar i").style.width;        // 33%
      out.tickHint    = document.querySelector("#countHint").textContent;                 // 完成 1 / 3
      var r1 = Q.getR().filter(function(x){return x.id==="v1";})[0];
      out.tickDates   = (r1.doneDates||[]).length;   // 1
      out.tickDisable = r1.enabled === false;        // 单次提醒勾掉后停用

      /* ---- 12. 周视图：列数 / 今天高亮 / 已勾掉的不再出现 ---- */
      Q.setView("week");
      out.weekCols  = document.querySelectorAll("#weekView .wk-col").length;            // 7
      out.weekToday = document.querySelectorAll("#weekView .wk-col.is-today").length;   // 1
      out.weekEv    = document.querySelectorAll("#weekView .wkev").length;              // >0
      out.weekHint  = document.querySelector("#countHint").textContent;
      out.weekTodayEmpty = Q.dayEvents(new Date()).filter(function(x){return x.r.id==="v1";}).length === 0;

      /* ---- 13. 撤销勾选应完整复原 ---- */
      Q.setView("todo");
      Q.toggleDone("v1");
      var r1b = Q.getR().filter(function(x){return x.id==="v1";})[0];
      out.untickEnabled = r1b.enabled === true;
      out.untickDates   = (r1b.doneDates||[]).length;                                  // 0
      out.untickDoneCls = document.querySelectorAll("#todoView .td-item.done").length; // 0

      /* 循环提醒勾掉 = 今天静音，规则本身仍启用 */
      Q.toggleDone("v3");
      var r3 = Q.getR().filter(function(x){return x.id==="v3";})[0];
      out.loopStillEnabled = r3.enabled === true;
      out.loopMutedToday   = (r3.mutedDates||[]).length === 1;
      out.loopMutedFromWeek = Q.dayEvents(new Date()).filter(function(x){return x.r.id==="v3";}).length === 0;

      /* ---- 14. 周视图重叠分道 + 每周从周一开始 ---- */
      var lanes = Q.assignLanes([{s:540,e:560},{s:550,e:570},{s:600,e:620}]);
      out.laneOrder = lanes.map(function(x){return x.lane;}).join(",");   // 0,1,0
      out.laneCount = lanes[0].lanes;                                     // 2
      out.weekStartIsMon = Q.weekStart(new Date("2026-10-28T12:00:00")).getDay() === 1;
      out.weekStartIsSun = Q.weekStart(new Date("2026-11-01T12:00:00")).getDay() === 1;  // 周日应归到本周

      /* ---- 15. 日历：点时钟展开 / 42 格 / 今天唯一 / 关闭 ---- */
      reset();
      var t2 = new Date(); t2.setHours(0,0,0,0);
      function at2(h,m){ var d = new Date(t2); d.setHours(h,m,0,0); return d.toISOString(); }
      Q.setR([
        {id:"c1",title:"每天服药",mode:"repeat",rmode:"daily",repTime:"08:00",enabled:true,cat:"health",
         ring:"none",vibrate:false,mutedDates:[],doneDates:[]},
        {id:"c2",title:"每周三复盘",mode:"repeat",rmode:"week",days:[3],repTime:"14:00",enabled:true,cat:"work",
         ring:"none",vibrate:false,mutedDates:[],doneDates:[]},
        {id:"c3",title:"今天一次",mode:"once",at:at2(16,0),enabled:true,cat:"life",
         ring:"none",vibrate:false,mutedDates:[],doneDates:[]}
      ]);
      var cats = Q.calDayCats(t2);
      out.calCatsToday = cats.length;                                    // 每天 + 今天一次（周三则再 +1）
      out.calMildFirst = cats[0] && cats[0].mild === true;               // 每天重复 → 淡点
      out.calSheetBefore = document.querySelector("#calSheet").classList.contains("show");
      Q.openCal();
      out.calOpen     = document.querySelector("#calSheet").classList.contains("show");
      out.calCells    = document.querySelectorAll("#calGrid .cal-day").length;   // 42
      out.calToday    = document.querySelectorAll("#calGrid .cal-day.is-today").length; // 1
      out.calTitle    = document.querySelector("#calTitle").textContent;
      out.calDotsToday = document.querySelectorAll("#calGrid .cal-day.is-today .cal-dots i").length;
      Q.closeCal();
      out.calClosed   = !document.querySelector("#calSheet").classList.contains("show");

      /* ---- 16. 点日历某天 → 跳到那一周并高亮那天 ---- */
      Q.goToDay(new Date(t2.getTime() + 14*86400000));
      out.jumpView   = Q.getView();                    // week
      out.jumpOffset = Q.getWeekOffset();              // 2（两周后）
      out.jumpFocus  = Q.getWeekFocus() ? 1 : 0;       // 有高亮目标
      out.jumpClosed = !document.querySelector("#calSheet").classList.contains("show");
      out.jumpCols   = document.querySelectorAll("#weekView .wk-col").length;   // 7
      out.jumpFocusHead = document.querySelectorAll("#weekView .wk-dayh.is-focus").length; // 1

      /* ---- 17. 版本号：必须与 package.json 一致，且不能残留占位符 ---- */
      var vt = document.querySelector("#verText");
      var vt2 = document.querySelector("#verText2");
      out.uiVersion  = vt ? vt.textContent : null;
      out.uiVersion2 = vt2 ? vt2.textContent : null;
      out.uiVersionTitle = vt ? vt.getAttribute("title") : null;
      out.uiHasToken = vt ? vt.textContent.indexOf("{{") >= 0 : true;

      /* ---- 18. 反馈与联系：邮箱为主渠道、入口齐全、诊断信息可用 ---- */
      out.fbBtnExists   = !!document.querySelector("#feedbackBtn");
      out.copyBtnExists = !!document.querySelector("#copyInfoBtn");
      out.fbUrl         = Q.feedbackUrl();
      out.fbUrlOk       = /^https:\\/\\/github\\.com\\//.test(Q.feedbackUrl());
      out.contactEmail  = Q.contactEmail();
      out.contactEmailOk = /^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$/.test(Q.contactEmail());
      out.contactVisible = document.querySelector("#contactRow").style.display !== "none";
      out.contactMail    = document.querySelector("#contactMail").textContent;
      out.contactCopyBtn = !!document.querySelector("#contactCopyBtn");
      var mt = Q.mailtoLink();
      out.mailtoPrefix = mt.indexOf("mailto:" + Q.contactEmail() + "?subject=") === 0;
      out.mailtoHasBody = mt.indexOf("&body=") > 0;
      out.mailtoEncoded = /%E8%BD%BB%E9%93%83/.test(mt);   /* 「轻铃」的 URL 编码 */
      var diag = Q.feedbackInfo();
      out.diagHasVer  = diag.indexOf("v" + Q.getVersion()) >= 0;
      out.diagHasOS   = /系统：\\S+/.test(diag);
      out.diagHasCnt  = diag.indexOf("提醒：共 " + Q.getR().length + " 条") >= 0;
      out.diagLines   = diag.split("\\n").length;                       // 4
      /* 点「反馈」应交给主进程打开白名单地址（实际地址在主进程侧回填） */
      document.querySelector("#feedbackBtn").click();
      await new Promise(function(r){ setTimeout(r, 300); });
      /* 区块位置：清空之后，且邮箱排在 GitHub 之前（国内用户先看到能用的那条） */
      var rows = Array.prototype.slice.call(document.querySelectorAll("#settings .set-row"));
      var idx = function(sel){
        return rows.findIndex(function(x){ return x.querySelector(sel); });
      };
      var clrIdx = idx("#clearBtn"), mailIdx = idx("#contactCopyBtn"), fbIdx = idx("#feedbackBtn");
      out.fbAfterClear = clrIdx >= 0 && mailIdx > clrIdx && fbIdx > mailIdx;
      out.fbAtBottom   = (rows.length - 1 - fbIdx) <= 2;

      /* ---- 19. 备份 / 删除撤销 / 导出 .ics ---- */
      window.confirm = function(){ return true; };     /* 删掉原生确认框，避免卡住自测 */

      /* 保存后应当触发一次备份写入（防抖 900ms） */
      Q.setR([{ id: "bk-a", title: "备份测试", mode: "once", cat: "work", enabled: true,
                at: new Date(Date.now() + 7200000).toISOString(), mutedDates: [], doneDates: [] }]);
      Q.saveStore();
      await new Promise(function(r){ setTimeout(r, 1300); });

      /* 恢复：把一份 JSON 灌回去 */
      out.restoredCount = Q.applyBackupJson(JSON.stringify([
        { id: "r1", title: "恢复一", mode: "once", cat: "other", enabled: true, at: new Date(Date.now() + 3600000).toISOString() },
        { id: "r2", title: "恢复二", mode: "repeat", cat: "life", enabled: true, repTime: "09:00", rmode: "daily" }
      ]));
      out.restoredTitles = Q.getR().map(function(x){ return x.title; }).join(",");
      out.restoreBannerExists = !!document.querySelector("#restoreBanner");
      out.restoreBannerHidden = document.querySelector("#restoreBanner").classList.contains("hidden");

      /* 备份信息条：没有备份时恢复按钮要禁用 */
      Q.refreshBackupUI();
      await new Promise(function(r){ setTimeout(r, 220); });
      out.restoreDescEmpty = document.querySelector("#restoreDesc").textContent;
      out.restoreBtnDisabled = document.querySelector("#backupRestoreBtn").disabled;

      /* 删除 → 撤销 */
      Q.setView("list");                                /* 前面跳到过周视图，这里要回到列表才渲染卡片 */
      Q.setR([{ id: "del-1", title: "待删除的", mode: "once", cat: "other", enabled: true,
                at: new Date(Date.now() + 7200000).toISOString(), mutedDates: [], doneDates: [] }]);
      Q.render();
      var delBtn = document.querySelector('#list .card[data-id="del-1"] [data-act="del"]');
      out.delBtnFound = !!delBtn;
      if (delBtn) delBtn.click();
      await new Promise(function(r){ setTimeout(r, 160); });
      out.afterDelete = Q.getR().length;
      var undoBtn = document.querySelector("#toast .toast-act");
      out.undoShown = !!undoBtn;
      out.undoLabel = undoBtn ? undoBtn.textContent : "";
      if (undoBtn) undoBtn.click();
      await new Promise(function(r){ setTimeout(r, 160); });
      out.afterUndo = Q.getR().length;
      out.undoTitle = Q.getR()[0] ? Q.getR()[0].title : "";

      /* 清空全部 → 撤销 */
      Q.setR([{ id: "c1", title: "清空前", mode: "once", cat: "other", enabled: true,
                at: new Date(Date.now() + 3600000).toISOString(), mutedDates: [], doneDates: [] }]);
      Q.render();
      document.querySelector("#clearBtn").click();
      await new Promise(function(r){ setTimeout(r, 200); });
      out.afterClear = Q.getR().length;
      var undo2 = document.querySelector("#toast .toast-act");
      out.clearUndoShown = !!undo2;
      if (undo2) undo2.click();
      await new Promise(function(r){ setTimeout(r, 200); });
      out.afterClearUndo = Q.getR().length;

      /* .ics 生成：单次 / 每周 / 每天 各一条，循环与已关闭的跳过 */
      Q.setR([
        { id: "i1", title: "交房租", mode: "once", cat: "life", enabled: true, at: "2026-11-01T15:00:00" },
        { id: "i2", title: "周会", mode: "repeat", cat: "work", enabled: true, repTime: "08:00", rmode: "week", days: [1, 3] },
        { id: "i3", title: "每天喝水", mode: "repeat", cat: "health", enabled: true, repTime: "09:30", rmode: "daily" },
        { id: "i4", title: "久坐提醒", mode: "loop", cat: "other", enabled: true, repTime: "09:00", rmode: "loop",
          startH: 9, startM: 0, endH: 18, endM: 0, firstH: 9, firstM: 0, every: 60, days: [1] },
        { id: "i5", title: "已关闭的", mode: "once", cat: "other", enabled: false, at: "2026-11-01T15:00:00" }
      ]);
      var ics = Q.buildIcs();
      out.icsCount    = ics.count;                                  /* 3 */
      out.icsSkipped  = ics.skipped;                                /* 2 */
      out.icsHasCal   = ics.text.indexOf("BEGIN:VCALENDAR") === 0;
      out.icsHasEnd   = ics.text.indexOf("END:VCALENDAR") > 0;
      out.icsEvents   = (ics.text.match(/BEGIN:VEVENT/g) || []).length;
      out.icsUtc      = /DTSTART:20\\d{6}T\\d{6}Z/.test(ics.text);
      out.icsWeekly   = ics.text.indexOf("FREQ=WEEKLY;BYDAY=MO,WE") >= 0;
      out.icsDaily    = ics.text.indexOf("FREQ=DAILY") >= 0;
      out.icsAlarm    = ics.text.indexOf("BEGIN:VALARM") >= 0;
      out.icsCrlfOnly = !/[^\\r]\\n/.test(ics.text);
      out.icsFolded   = Q.icsFold(new Array(120).join("长")).indexOf("\\r\\n ") > 0;
      out.icsTitleZh  = ics.text.indexOf("SUMMARY:交房租") > 0;

      /* 点导出按钮 → 应把内容交给主进程保存 */
      document.querySelector("#exportIcsBtn").click();
      await new Promise(function(r){ setTimeout(r, 300); });

      /* ---- v1.5：屏幕独立弹窗 ---- */
      Q.setR([]);
      Q.closeAlarm();
      document.querySelector("#alarm").classList.remove("show");
      var S = Q.getS();
      S.screenAlarm = true;
      S.alarmPos = "tr";
      S.alarmSize = "large";
      S.theme = "system";
      Q.setR([{ id:"sc1", title:"屏幕弹窗", mode:"once", cat:"other", enabled:true, ring:"none",
                at: new Date(Date.now() - 5000).toISOString(), mutedDates:[], doneDates:[] }]);
      Q.check(new Date());
      await new Promise(function(r){ setTimeout(r, 250); });
      out.screenModeUsed     = Q.screenAlarmMode();          /* true：走独立窗 */
      out.screenInlineHidden = !alarmVisible();              /* 窗口内弹层不该再出现 */
      out.screenReminderDone = Q.getR()[0].enabled === false; /* 单次提醒照常置为已触发 */

      /* 稍后提醒：独立窗回传 snooze，应把 snoozeUntil 写回去 */
      Q.handleAlarmAction({ type:"snooze", min:5 });
      await new Promise(function(r){ setTimeout(r, 120); });
      out.screenSnoozeSet = Q.getR()[0].snoozeUntil > Date.now();
      out.screenClosed    = document.querySelector("#alarm").classList.contains("show") === false;

      /* 知道了：清空队列，不应留下打开状态 */
      Q.handleAlarmAction({ type:"ok" });
      await new Promise(function(r){ setTimeout(r, 120); });
      out.screenOkClosed = !document.querySelector("#alarm").classList.contains("show");

      /* 关掉开关 → 必须退回窗口内弹层（不能因为开关关了就丢提醒） */
      S.screenAlarm = false;
      Q.setR([{ id:"sc2", title:"关掉开关后", mode:"once", cat:"other", enabled:true, ring:"none",
                at: new Date(Date.now() - 5000).toISOString(), mutedDates:[], doneDates:[] }]);
      Q.check(new Date());
      await new Promise(function(r){ setTimeout(r, 200); });
      out.screenOffFallbackIn = alarmVisible();
      out.screenOffTitle      = document.querySelector("#alarmTitle").textContent;
      Q.closeAlarm();
      await new Promise(function(r){ setTimeout(r, 300); });

      /* ---- v1.5：自定义背景 ---- */
      S.theme = "custom";
      S.appBg = "app.jpg";
      S.alarmBg = "alarm.jpg";
      Q.applyTheme();
      await new Promise(function(r){ setTimeout(r, 250); });
      out.bgAppClass  = document.body.classList.contains("bg-custom");
      out.bgAppVar    = (document.body.style.getPropertyValue("--app-bg") || "").indexOf("data:image") > 0;
      var card = document.querySelector("#alarm .alarm-card");
      out.bgAlarmClass = card.classList.contains("has-bg");
      out.bgAlarmVar   = (card.style.getPropertyValue("--alarm-bg") || "").indexOf("url(") === 0;

      /* 移除背景后必须彻底还原，不能留个空 url() 让整块变透明 */
      S.theme = "system"; S.appBg = null; S.alarmBg = null;
      Q.applyTheme();
      await new Promise(function(r){ setTimeout(r, 250); });
      out.bgAppCleared   = !document.body.classList.contains("bg-custom");
      out.bgAppVarGone   = (document.body.style.getPropertyValue("--app-bg") || "") === "";
      out.bgAlarmCleared = !document.querySelector("#alarm .alarm-card").classList.contains("has-bg");

      return out;
    })()`,
    true
  );

  res.fbOpened = openedUrl;
  res.fbOpenedOk = !!openedUrl && openedUrl === res.fbUrl;

  /* 主进程侧统计（渲染进程读不到这些变量） */
  res.backupWrites = backupWrites.length;
  res.backupStash  = backupWrites.filter(function (w) { return w.stash; }).length;
  res.alarmShows   = alarmShows.length;
  res.alarmShowFirst = alarmShows[0] || null;
  res.alarmShowOk  = !!(alarmShows.length &&
                        alarmShows[0].title === "屏幕弹窗" &&
                        alarmShows[0].size === "large" &&
                        alarmShows[0].pos === "tr" &&
                        alarmShows[0].hasBg === false &&
                        /^\d/.test(String(alarmShows[0].timeText || "")));
  res.bgLoadsSeen  = bgLoads.length;
  res.icsSavedName = icsSaved && icsSaved.name;
  res.icsSavedOk   = !!(icsSaved && /^轻铃-\d{4}-\d{2}-\d{2}\.ics$/.test(icsSaved.name) &&
                        /BEGIN:VCALENDAR/.test(icsSaved.content) &&
                        /BEGIN:VEVENT/.test(icsSaved.content));

  console.log("RESULT_JSON_START");
  console.log(JSON.stringify(res, null, 2));
  console.log("RESULT_JSON_END");

  app.exit(0);
}).catch((e) => {
  console.error("FAIL", e && e.stack || e);
  app.exit(1);
});
