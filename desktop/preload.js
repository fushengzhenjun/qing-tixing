/* 轻提醒 · 预加载桥
   在保持 contextIsolation / sandbox 安全隔离的前提下，
   仅把桌面端（Electron）专属能力暴露给页面；浏览器打开时 window.qingtx 不存在。 */
const { contextBridge, ipcRenderer } = require("electron");

contextBridge.exposeInMainWorld("qingtx", {
  isDesktop: true,
  /* 开机自启动 */
  getAutoLaunch: () => ipcRenderer.invoke("auto-launch:get"),
  setAutoLaunch: (enabled) => ipcRenderer.invoke("auto-launch:set", !!enabled),
  /* 到点把窗口叫到前台（通知权限不可用时的兜底） */
  alertWindow: () => ipcRenderer.invoke("window:alert"),
  /* 快速添加：小窗提交 → 主进程 → 主窗口；主窗口用 onQuickAdd 接收 */
  quickSubmit: (payload) => ipcRenderer.invoke("quick:submit", payload),
  quickCancel: () => ipcRenderer.invoke("quick:cancel"),
  onQuickShow: (cb) => ipcRenderer.on("quick:show", () => { try { cb(); } catch (e) {} }),
  onQuickAdd: (cb) => ipcRenderer.on("quick:added", (_e, p) => { try { cb(p); } catch (e) {} }),
  /* 全局快捷键开关与状态 */
  getQuickAdd: () => ipcRenderer.invoke("shortcut:get"),
  setQuickAdd: (enabled) => ipcRenderer.invoke("shortcut:set", !!enabled),
  /* 打开外部链接（反馈页 / 邮件），主进程会校验白名单 */
  openExternal: (url) => ipcRenderer.invoke("shell:open-external", String(url || "")),
  /* 数据备份：按天滚动存到 <数据目录>\backups，页面只负责喂 JSON */
  backupSave: (json, count) => ipcRenderer.invoke("backup:save", String(json || ""), Number(count) || 0),
  backupList: () => ipcRenderer.invoke("backup:list"),
  backupRead: (name) => ipcRenderer.invoke("backup:read", String(name || "")),
  backupStash: (json, count) => ipcRenderer.invoke("backup:stash", String(json || ""), Number(count) || 0),
  backupReveal: () => ipcRenderer.invoke("backup:reveal"),
  getPaths: () => ipcRenderer.invoke("app:paths"),
  /* 窗口状态：区分「用户点开的」与「开机自启静默拉起的」 */
  getWindowState: () => ipcRenderer.invoke("app:window-state"),
  /* 导出 .ics 日历（手机 / Outlook / 系统日历都能导入） */
  exportIcs: (name, content) => ipcRenderer.invoke("export:ics", { name: String(name || ""), content: String(content || "") }),
  /* 屏幕独立提醒窗：主窗口把「要显示什么」交出去，弹窗把「点的是哪个按钮」交回来 */
  alarmShow: (payload) => ipcRenderer.invoke("alarm:show", payload || {}),
  alarmHide: () => ipcRenderer.invoke("alarm:hide"),
  alarmSize: (w, h) => ipcRenderer.invoke("alarm:size", Number(w) || 0, Number(h) || 0),
  alarmAction: (a) => ipcRenderer.invoke("alarm:action", a || {}),
  onAlarmShow: (cb) => ipcRenderer.on("alarm:show", (_e, p) => { try { cb(p || {}); } catch (e) {} }),
  onAlarmAction: (cb) => ipcRenderer.on("alarm:action", (_e, a) => { try { cb(a || {}); } catch (e) {} }),
  /* 自定义背景：选图 / 移除 / 取 dataURL。页面不接触文件路径 */
  bgPick: (kind) => ipcRenderer.invoke("bg:pick", String(kind || "app")),
  bgClear: (kind) => ipcRenderer.invoke("bg:clear", String(kind || "app")),
  bgLoad: (kind) => ipcRenderer.invoke("bg:load", String(kind || "app"))
});
