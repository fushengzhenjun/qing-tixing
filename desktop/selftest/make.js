/* 生成自检副本：把内部函数挂到 window.__qx，仅用于自动化验证。
   正式文件 轻提醒.html 不含任何测试代码。 */
const fs = require("fs");
const path = require("path");

const SRC = path.join(__dirname, "..", "..", "轻提醒.html");   /* 根目录才是编辑源，build 时同步到工程目录 */
const OUT = path.join(__dirname, "..", "..", "_audit", "轻提醒.selftest.html");

let html = fs.readFileSync(SRC, "utf8");
/* 与正式构建一致：把 package.json 的版本号注入进去 */
const VERSION = require(path.join(__dirname, "..", "package.json")).version;
html = html.split("{{VERSION}}").join(VERSION);
const tail = html.lastIndexOf("})();");
if (tail < 0) {
  console.error("找不到 IIFE 结尾");
  process.exit(1);
}

const hook = `
/* ==== SELFTEST HOOK（仅自检副本）==== */
window.__qx = {
  catchUpMissed: catchUpMissed,
  check: check,
  isPaused: isPaused,
  pauseText: pauseText,
  pauseUntilFrom: pauseUntilFrom,
  applyPause: applyPause,
  openSettings: openSettings,
  nextFire: nextFire,
  closeAlarm: closeAlarm,
  getR: function () { return reminders; },
  setR: function (a) { reminders = a; },
  getS: function () { return settings; },
  isWorkday: isWorkday,
  repeatDayMatches: repeatDayMatches,
  loopDayOk: loopDayOk,
  holidayYears: function () { return HOLIDAY_YEARS.slice(); },
  holidayDesc: holidayDesc,
  syncHolidayUI: syncHolidayUI,
  syncQuickUI: syncQuickUI,
  /* 屏幕独立弹窗 / 自定义背景（v1.5） */
  handleAlarmAction: handleAlarmAction,
  screenAlarmMode: screenAlarmMode,
  applyTheme: applyTheme,
  applyAppBg: applyAppBg,
  applyAlarmBg: applyAlarmBg,
  syncBgUI: syncBgUI,
  syncScreenAlarmUI: syncScreenAlarmUI,
  syncRepSub: syncRepSub,
  addQuickReminder: addQuickReminder,
  /* 周视图 / 清单 */
  renderWeek: renderWeek,
  renderTodo: renderTodo,
  toggleDone: toggleDone,
  setView: setView,
  getView: function () { return activeView; },
  weekStart: weekStart,
  dayEvents: dayEvents,
  assignLanes: assignLanes,
  render: render,
  dayKey: dayKey,
  /* 日历 */
  openCal: openCal,
  closeCal: closeCal,
  renderCal: renderCal,
  goToDay: goToDay,
  calDayCats: calDayCats,
  getWeekOffset: function () { return weekOffset; },
  getWeekFocus: function () { return weekFocus; },
  /* 反馈与联系 */
  feedbackInfo: feedbackInfo,
  mailtoLink: mailtoLink,
  copyText: copyText,
  getVersion: function () { return APP_VERSION; },
  feedbackUrl: function () { return FEEDBACK_URL; },
  contactEmail: function () { return CONTACT_EMAIL; },
  /* 备份 / 恢复 / 导出 */
  buildIcs: buildIcs,
  icsFileName: icsFileName,
  icsFold: icsFold,
  applyBackupJson: applyBackupJson,
  refreshBackupUI: refreshBackupUI,
  backupDayText: backupDayText,
  saveStore: saveStore,
  checkRestoreOnStart: checkRestoreOnStart,
  getRestoreName: function () { return restoreName; }
};
/* ==== /SELFTEST HOOK ==== */
`;

html = html.slice(0, tail) + hook + html.slice(tail);
fs.writeFileSync(OUT, html, "utf8");
console.log("已生成 " + OUT + " (" + html.length + " 字节)");
