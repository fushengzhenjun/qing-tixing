/* 轻提醒 · 软著申请材料生成
 *
 * 产出两份 PDF（中国版权保护中心的要求）：
 *   软件源代码.pdf  —— 前 30 页 + 后 30 页，每页 50 行，页眉带软件名称/版本/页码
 *   操作说明书.pdf  —— 图文并茂的用户手册
 *
 * 用法：
 *   node tools/make_copyright.js src     只出源代码
 *   node tools/make_copyright.js manual  只出说明书
 *   node tools/make_copyright.js all
 *
 * 为什么要用 Electron 打印：这台机器没有装无头浏览器，
 * 而 desktop/node_modules 里正好有 Electron，用它 printToPDF 最省事。
 */
const fs = require("fs");
const path = require("path");
const os = require("os");
/* 用 createRequire 从 desktop/ 目录解析 electron：Electron 只对模块名 "electron" 做特判，
   直接写路径拿到的是字符串（二进制路径），不是主进程 API。 */
const { createRequire } = require("module");
const { pathToFileURL } = require("url");
const reqDesktop = createRequire(path.join(__dirname, "..", "desktop", "package.json"));
const { app, BrowserWindow } = reqDesktop("electron");

const ROOT = path.join(__dirname, "..");
const OUT_DIR = path.join(ROOT, "发布", "软著");
const SHOT_DIR = path.join(OUT_DIR, "截图");

const SOFT_NAME = "轻提醒定时软件";
const SOFT_VER = "V1.0";
const LINES_PER_PAGE = 50;
const PAGE_FRONT = 30;   /* 前 30 页 */
const PAGE_BACK = 30;    /* 后 30 页 */

const SRC_FILES = [
  "轻提醒.html",
  "desktop/main.js",
  "desktop/alarm.html",
  "desktop/quick.html",
  "desktop/preload.js",
  "desktop/build.js"
];

const esc = (s) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");

/* ---------------- 源代码 ---------------- */

function collectSource() {
  const lines = [];
  for (const rel of SRC_FILES) {
    const abs = path.join(ROOT, rel);
    if (!fs.existsSync(abs)) continue;
    lines.push(`/* ==================== ${rel} ==================== */`);
    const content = fs.readFileSync(abs, "utf8").split(/\r?\n/);
    for (let l of content) {
      /* 超长行截断，否则会把版面撑破（最长的一行有 800 多字符） */
      if (l.length > 160) l = l.slice(0, 160) + "  …";
      lines.push(l.replace(/\t/g, "    "));
    }
    lines.push("");
  }
  return lines;
}

function buildSrcHtml(lines) {
  const total = PAGE_FRONT + PAGE_BACK;
  const totalNeeded = total * LINES_PER_PAGE;
  let picked;
  if (lines.length <= totalNeeded) {
    picked = lines;                                  /* 不足 60 页就全部提交 */
  } else {
    const front = lines.slice(0, PAGE_FRONT * LINES_PER_PAGE);
    const back = lines.slice(lines.length - PAGE_BACK * LINES_PER_PAGE);
    picked = front.concat(back);
  }
  const pages = [];
  for (let i = 0; i < picked.length; i += LINES_PER_PAGE) {
    pages.push(picked.slice(i, i + LINES_PER_PAGE));
  }

  let n = 0;
  const body = pages.map((pageLines) => {
    n++;
    const rows = pageLines.map((l) => `<div class="ln">${esc(l) || "&nbsp;"}</div>`).join("");
    return `<section class="page">
      <div class="hdr"><span>${SOFT_NAME} ${SOFT_VER}</span><span>第 ${n} 页 / 共 ${pages.length} 页</span></div>
      <div class="code">${rows}</div>
    </section>`;
  }).join("");

  return `<!doctype html><html><head><meta charset="utf-8"><style>
    @page { size: A4; margin: 0; }
    *{margin:0;padding:0;box-sizing:border-box}
    body{font-family:"Microsoft YaHei",sans-serif}
    .page{width:210mm;height:297mm;padding:14mm 12mm 12mm;page-break-after:always;display:flex;flex-direction:column}
    .page:last-child{page-break-after:auto}
    .hdr{display:flex;justify-content:space-between;font-size:8.5pt;color:#333;
         border-bottom:.4mm solid #999;padding-bottom:1.6mm;margin-bottom:2.4mm}
    .code{flex:1;font-family:Consolas,"Courier New",monospace;font-size:7.4pt;color:#111}
    .ln{height:4.85mm;line-height:4.85mm;white-space:pre;overflow:hidden}
  </style></head><body>${body}</body></html>`;
}

/* ---------------- 说明书 ---------------- */

function img(name) {
  const p = path.join(SHOT_DIR, name);
  if (!fs.existsSync(p)) return "";
  return `<figure><img src="${pathToFileURL(p).href}"><figcaption>图：${name.replace(/^\d+-/, "").replace(/\.png$/, "")}</figcaption></figure>`;
}

function buildManualHtml() {
  const version = require(path.join(ROOT, "desktop", "package.json")).version;

  /* 每章单独成页：软著说明书按章节分页读起来清楚，也避免跨页断开图片 */
  const sec = (t, html) => `<section class="chapter"><h2>${t}</h2>${html}</section>`;
  const p = (t) => `<p>${t}</p>`;
  const ul = (items) => `<ul>${items.map((i) => `<li>${i}</li>`).join("")}</ul>`;

  const body = `
  <section class="cover">
    <div class="logo">轻提醒</div>
    <h1>${SOFT_NAME}<br>操作说明书</h1>
    <table class="meta">
      <tr><td>软件名称</td><td>${SOFT_NAME}</td></tr>
      <tr><td>版 本 号</td><td>${SOFT_VER}</td></tr>
      <tr><td>开发完成日期</td><td>2026 年 10 月 27 日</td></tr>
      <tr><td>开 发 者</td><td>fushengzhenjun</td></tr>
    </table>
  </section>

  <section class="toc">
    <h2>目 录</h2>
    <ol>
      <li>软件概述</li><li>运行环境</li><li>安装与卸载</li><li>主界面说明</li>
      <li>新建提醒</li><li>提醒弹窗</li><li>视图切换</li><li>设置</li>
      <li>数据备份与导出</li><li>常见问题</li>
    </ol>
  </section>

  ${sec("一、软件概述", `
    ${p(`${SOFT_NAME}（以下简称"本软件"）是一款运行于 Windows 桌面系统的到点提醒工具。用户可按单次、每天、工作日、每周等规则设置提醒事项，到达设定时刻后，本软件以独立窗口在屏幕上弹出提醒，并伴随铃声与振动提示。`)}
    ${p(`本软件为纯本地应用：不需要注册账号，不包含任何网络通信功能，不上传、不收集任何用户数据；用户创建的全部提醒内容仅保存在本机数据目录中。`)}
    <h3>主要功能</h3>
    ${ul([
      "支持单次、重复（每天 / 工作日 / 每周 / 每隔 N 天 / 每月某日）、循环、倒计时四种提醒类型",
      "循环提醒可在指定时段内每隔 N 分钟触发一次，适合喝水、久坐等场景",
      "支持倒计时提醒与快捷添加（Ctrl + Alt + N）",
      "提醒弹窗可脱离主窗口，直接显示在屏幕指定位置，支持四档尺寸",
      "支持五大分类、四档提示音、夜间免打扰与全部暂停",
      "支持浅色 / 深色 / 跟随系统三种外观，支持自定义背景图片",
      "支持每日自动备份、数据导入导出与日历（.ics）导出",
      "系统托盘常驻、开机自启动，关闭窗口后仍在后台守候"
    ])}
  `)}

  ${sec("二、运行环境", `
    ${ul([
      "操作系统：Windows 10（版本 1809 及以上）或 Windows 11，64 位",
      "内存：2 GB 及以上",
      "硬盘空间：约 250 MB（安装版）",
      "运行库：无需额外安装，安装包已包含全部运行环境",
      "网络：不需要联网。本软件不发起任何网络请求"
    ])}
    ${p(`本软件提供两种分发形式：安装版（Setup 程序，向导式安装，可自选安装目录）与绿色版（解压即用，数据写在程序所在目录）。`)}
  `)}

  ${sec("三、安装与卸载", `
    <h3>3.1 安装</h3>
    ${p(`双击安装程序，按向导依次完成：欢迎页 → 选择安装位置 → 安装 → 完成。安装过程按当前用户进行，不需要管理员权限。安装完成后，桌面与开始菜单会生成"轻提醒"快捷方式。`)}
    <h3>3.2 卸载</h3>
    ${p(`打开「设置 → 应用 → 已安装的应用」，找到"轻提醒"，选择卸载；或从开始菜单中的"轻提醒"文件夹点击卸载。卸载过程中，程序会询问是否一并删除提醒数据：`)}
    ${ul([
      "选择「是」：删除全部提醒、设置与历史备份，不可恢复",
      "选择「否」（默认）：仅移除程序文件，提醒数据保留，重新安装后可继续使用"
    ])}
  `)}

  ${sec("四、主界面说明", `
    ${p(`启动后进入主界面。左侧为功能导航，中部为提醒列表，右下角为新建按钮。列表按"下次触发时间"排序，已停用的提醒排在最后。`)}
    ${img("01-主界面.png")}
    ${ul([
      "顶部：显示软件名称、版本号，右侧为设置入口",
      "视图切换：列表 / 周视图 / 待办清单三种查看方式",
      "提醒卡片：显示提醒标题、重复规则与下次触发时间，右侧开关可临时启用或停用该条提醒",
      "右下角「+」：新建提醒"
    ])}
  `)}

  ${sec("五、新建提醒", `
    ${p(`点击右下角「+」进入新建界面。依次填写提醒内容、选择类型与时间、选择分类与铃声，点击右上角「保存」即可。`)}
    ${img("04-新建提醒.png")}
    <h3>5.1 提醒类型</h3>
    ${p(`界面提供四种类型：`)}
    ${ul([
      "<b>单次</b>：选择具体日期与时刻，只提醒一次",
      "<b>重复</b>：设定时刻后选择重复方式——每天、工作日（周一至周五）、每周（自选星期）、每隔 N 天、每月某日",
      "<b>循环</b>：在指定时段内每隔 N 分钟提醒一次，适合喝水、久坐、用药这类间隔提醒",
      "<b>倒计时</b>：从当前时刻起经过指定分钟数后提醒一次"
    ])}
    ${img("05-重复设置.png")}
    <h3>5.2 分类与提示音</h3>
    ${p(`分类用于区分提醒的性质，提供健康、工作、学习、生活、其他五类，不同分类在列表中显示为不同颜色的小圆点。提示音提供柔和、门铃、闹钟、静音四档，可配合振动使用。`)}
  `)}

  ${sec("六、提醒弹窗", `
    ${p(`到达提醒时刻，本软件会弹出一个独立窗口显示提醒内容。该窗口不属于主界面，可直接显示在屏幕中央或四个角落；即使主窗口已最小化到托盘、或正在全屏播放视频，提醒依然可见。`)}
    ${p(`弹窗上有「知道了」「5 分钟后」「10 分钟后」三个按钮。选择稍后提醒后，本软件会在指定时间再次提醒，且不会改变原提醒本身的重复规则。`)}
    ${p(`弹窗尺寸可设置为紧凑、标准、醒目、全屏四档；位置支持中央、左上、右上、左下、右下五种。多显示器环境下，弹窗出现在鼠标光标所在的那块屏幕上。`)}
    ${p(`若不需要独立弹窗，可在设置中关闭「提醒到屏幕上」，此时提醒改为在主窗口内弹出。`)}
  `)}

  ${sec("七、视图切换", `
    <h3>7.1 周视图</h3>
    ${p(`以一周为单位展示提醒分布，便于查看每天的安排。可切换上一周 / 下一周。`)}
    ${img("02-周视图.png")}
    <h3>7.2 待办清单</h3>
    ${p(`把提醒当作待办事项使用，可逐条勾选完成，界面顶部显示完成进度。`)}
    ${img("03-待办清单.png")}
  `)}

  ${sec("八、设置", `
    ${p(`点击主界面右上角齿轮进入设置。设置分为「常规」「提醒」「数据」「关于」四页，每组设置项下方有简短的说明文字，点击标题可展开查看详细说明。`)}
    <h3>8.1 常规</h3>
    ${p(`包含全部暂停、外观（跟随系统 / 浅色 / 深色 / 自定义背景）、开机自启动、工作日跟随法定节假日、快速添加开关。`)}
    ${img("06-设置-常规.png")}
    <h3>8.2 提醒</h3>
    ${p(`包含「提醒弹到屏幕上」开关、弹窗位置与大小、弹窗背景、夜间免打扰时段。夜间免打扰时段内提醒静音且不振动，但仍会记录。`)}
    ${img("07-设置-提醒.png")}
    <h3>8.3 数据</h3>
    ${p(`包含自动备份（每日一份，保留 30 天）、从备份恢复、导出日历（.ics）、导出 / 导入数据文件（JSON）、清空全部提醒。`)}
    ${img("08-设置-数据.png")}
    <h3>8.4 关于</h3>
    ${p(`显示软件版本、联系邮箱与问题反馈入口，并提供"复制反馈信息"按钮，便于用户反馈问题时一并提供运行环境。`)}
    ${img("09-设置-关于.png")}
  `)}

  ${sec("九、数据备份与导出", `
    <h3>9.1 自动备份</h3>
    ${p(`每次修改提醒后，本软件都会把整份数据写入备份目录，每日保留一份，滚动保留最近 30 天。在「设置 → 数据」中可查看最近一次备份的时间与条数，并可直接打开备份所在文件夹。`)}
    ${h3note()}
    <h3>9.2 从备份恢复</h3>
    ${p(`当本机提醒为空而备份中仍有数据时，主界面顶部会给出提示，点击即可一键恢复。也可在设置中手动选择某个备份文件进行恢复。`)}
    <h3>9.3 导出日历</h3>
    ${p(`将提醒导出为 .ics 日历文件，可在手机或其他日历软件中导入，实现电脑与手机的提醒同步（无需联网）。`)}
    <h3>9.4 导出 / 导入数据文件</h3>
    ${p(`整份提醒可导出为 JSON 文件用于留档或迁移到另一台电脑，导入时覆盖当前全部提醒。`)}
  `)}

  ${sec("十、常见问题", `
    <h3>10.1 关掉主窗口后还会提醒吗？</h3>
    ${p(`会。关闭窗口只是隐藏到系统托盘，本软件仍在后台运行，到点照常提醒。若要彻底退出，请右键点击托盘图标选择「退出」。`)}
    <h3>10.2 提醒没有准时弹出怎么办？</h3>
    ${p(`请依次检查：程序是否仍在托盘运行；是否处于"全部暂停"状态；是否处于夜间免打扰时段；系统是否将本软件加入了节能或通知限制列表。`)}
    <h3>10.3 数据保存在哪里？</h3>
    ${p(`安装版保存在 <code>%APPDATA%\\轻提醒\\</code>，绿色版保存在程序所在目录。备份文件位于该目录下的 <code>backups</code> 子目录。`)}
    <h3>10.4 换电脑后提醒还在吗？</h3>
    ${p(`在旧电脑上「导出数据文件」，把生成的 JSON 文件拷到新电脑，在新电脑上「导入数据文件」即可。`)}
    <h3>10.5 本软件会联网吗？</h3>
    ${p(`不会。本软件不包含任何网络通信代码，不发起任何网络请求，也不收集任何使用数据。反馈入口仅在你主动点击时才会打开邮件程序或浏览器。`)}
  `)}

  <section class="end">
    <p>—— 本说明书到此结束 ——</p>
    <p class="muted">${SOFT_NAME} ${SOFT_VER}　当前软件版本 v${version}</p>
  </section>`;

  return `<!doctype html><html><head><meta charset="utf-8"><style>
    @page { size: A4; margin: 0; }
    *{margin:0;padding:0;box-sizing:border-box}
    body{font-family:"Microsoft YaHei",sans-serif;color:#1d211f;font-size:10.5pt;line-height:1.85}
    section{width:210mm;min-height:297mm;padding:18mm 18mm 16mm;page-break-after:always;
      display:flex;flex-direction:column;gap:2mm}
    section:last-child{page-break-after:auto}
    .cover{justify-content:center;align-items:center;text-align:center;
      background:linear-gradient(160deg,#137a63 0%,#0a5546 100%);color:#fff}
    .cover .logo{font-size:13pt;letter-spacing:6px;opacity:.85;margin-bottom:8mm}
    .cover h1{font-size:26pt;line-height:1.5;font-weight:700;letter-spacing:2px}
    .cover .meta{margin-top:14mm;border-collapse:collapse;font-size:10.5pt}
    .cover .meta td{padding:2.2mm 6mm;border-bottom:.3mm solid rgba(255,255,255,.35);text-align:left}
    .cover .meta td:first-child{opacity:.8;padding-right:10mm}
    h2{font-size:15pt;color:#0f6350;border-left:3.5mm solid #137a63;padding-left:4mm;
      margin:4mm 0 3mm;font-weight:700}
    h3{font-size:11.5pt;color:#1d211f;margin:4mm 0 1.5mm;font-weight:700}
    p{margin:2mm 0;text-indent:2em}
    ul{margin:2mm 0 2mm 6mm}
    li{margin:1.2mm 0}
    code{background:#efeee9;padding:.5mm 1.2mm;border-radius:1mm;font-family:Consolas,monospace;font-size:9.5pt}
    figure{margin:3mm 0;text-align:center}
    figure img{width:100%;max-width:168mm;border:.3mm solid #dcdad3;border-radius:2mm}
    figcaption{font-size:8.5pt;color:#6b726d;margin-top:1.5mm}
    .toc ol{margin:6mm 0 0 8mm;font-size:11pt;line-height:2.4}
    .end{align-items:center;justify-content:center;color:#6b726d}
    .muted{font-size:9pt;opacity:.8;text-indent:0}
  </style></head><body>${body}</body></html>`;
}

function h3note() {
  return `<p>备份文件不会因为"提醒被清空"而被覆盖：空数据永远不会写入备份，避免误清空后连备份一起丢掉。</p>`;
}

/* ---------------- 打印 ---------------- */

async function htmlToPdf(html, outFile) {
  /* 顺手存一份 HTML，便于排查版面问题（.gitignore 里已排除整个 发布/ 目录） */
  fs.writeFileSync(path.join(OUT_DIR, "__preview-" + path.basename(outFile, ".pdf") + ".html"), html, "utf8");
  const tmp = path.join(OUT_DIR, "__preview-" + path.basename(outFile, ".pdf") + ".html");
  fs.writeFileSync(tmp, html, "utf8");
  await new Promise((r) => setTimeout(r, 250));
  const win = new BrowserWindow({
    show: false,
    width: 1240,
    height: 1754,
    webPreferences: { offscreen: false, javascript: false }
  });
  await win.loadURL(pathToFileURL(tmp).href);
  await new Promise((r) => setTimeout(r, 600));
  const pdf = await win.webContents.printToPDF({
    pageSize: "A4",
    printBackground: true,
    margins: { marginType: "none" }
  });
  fs.writeFileSync(outFile, pdf);
  win.destroy();

}

app.whenReady().then(async () => {
  fs.mkdirSync(OUT_DIR, { recursive: true });
  const mode = process.argv[process.argv.length - 1];

  if (mode === "src" || mode === "all") {
    const lines = collectSource();
    const html = buildSrcHtml(lines);
    const pages = Math.ceil(Math.min(lines.length, (PAGE_FRONT + PAGE_BACK) * LINES_PER_PAGE) / LINES_PER_PAGE);
    await htmlToPdf(html, path.join(OUT_DIR, "软件源代码.pdf"));
    console.log(`源代码.pdf 已生成：共 ${lines.length} 行，取 ${pages} 页`);
  }

  if (mode === "manual" || mode === "all") {
    await htmlToPdf(buildManualHtml(), path.join(OUT_DIR, "操作说明书.pdf"));
    console.log("操作说明书.pdf 已生成");
  }

  app.quit();
}).catch((e) => { console.error(e); app.exit(1); });
