/* 把 发布\绿色版 同步到本机运行目录（桌面快捷方式指向这里）
   - 跳过 portable.flag，并确保运行目录里没有它（否则会切成便携模式，数据跑到 exe 旁边）
   - 逐个文件复制，被占用的记下来单独报告，不整体中断
   用法：node tools/sync_run_dir.js [目标目录] */
const fs = require("fs");
const path = require("path");

const ROOT = path.join(__dirname, "..");
const SRC = path.join(ROOT, "发布", "绿色版");
const DST = process.argv[2] || path.join(ROOT, "轻提醒电脑版");

if (!fs.existsSync(SRC)) {
  console.error("找不到绿色版目录：" + SRC + "（先跑一次 tools/release.js）");
  process.exit(1);
}
if (!fs.existsSync(DST)) {
  console.error("找不到运行目录：" + DST);
  process.exit(1);
}

const copied = [];
const locked = [];
const skipped = [];

function walk(rel) {
  for (const name of fs.readdirSync(path.join(SRC, rel)).sort()) {
    const r = rel ? path.join(rel, name) : name;
    if (name === "portable.flag") { skipped.push(r); continue; }
    const s = path.join(SRC, r);
    const d = path.join(DST, r);
    const st = fs.statSync(s);
    if (st.isDirectory()) { fs.mkdirSync(d, { recursive: true }); walk(r); continue; }
    try {
      fs.copyFileSync(s, d);
      copied.push(r);
    } catch (e) {
      /* 被占用（EBUSY/EPERM）就记下来，等下次或重启后再补 */
      locked.push(r + "  (" + e.code + ")");
    }
  }
}
walk("");

/* 便携标记必须不存在 */
try {
  fs.rmSync(path.join(DST, "portable.flag"), { force: true });
} catch (e) {}

/* 版本自证：写一份纯文本，方便不看界面也能确认 */
const pkg = JSON.parse(fs.readFileSync(path.join(DST, "resources", "app", "package.json"), "utf8"));
const html = fs.readFileSync(path.join(DST, "resources", "app", "轻提醒.html"), "utf8");
const inHtml = (html.match(/APP_VERSION="([^"]*)"/) || [])[1] || "?";
fs.writeFileSync(
  path.join(DST, "版本.txt"),
  [
    "轻提醒 " + pkg.version,
    "",
    "本目录当前版本：" + pkg.version,
    "界面里显示的版本：v" + inHtml,
    "同步时间：" + new Date().toLocaleString("zh-CN"),
    "",
    "两处一致即为同一版；如不一致，说明界面文件没有更新，重新跑一次 tools\\release.js。"
  ].join("\r\n") + "\r\n",
  "utf8"
);

console.log("目标目录：" + DST);
console.log("复制文件： " + copied.length + " 个");
if (skipped.length) console.log("跳过：     " + skipped.join(", "));
if (locked.length) {
  console.log("未更新（被占用）：");
  locked.forEach((l) => console.log("  - " + l));
  console.log("  → 关掉程序后重跑本脚本，或重启电脑后再跑");
}
console.log("版本：package.json=" + pkg.version + "  界面 v" + inHtml + (pkg.version === inHtml ? "  一致 ✔" : "  不一致 ✘"));
process.exit(locked.length || pkg.version !== inHtml ? 2 : 0);
