/* 轻提醒 · 一键出包脚本（node tools/release.js）
   1) 打绿色版（electron-packager，带目录自检）
   2) 打安装包（electron-builder → NSIS，静默安装 /S）
   3) 汇总到 发布\：安装包、绿色版、绿色版 zip、商店素材、文档、SHA256 校验清单
   任何一步失败立刻停下，不会留下半成品。 */
const fs = require("fs");
const path = require("path");
const zlib = require("zlib");
const crypto = require("crypto");
const { spawnSync } = require("child_process");

const ROOT = path.resolve(__dirname, "..");
const DESKTOP = path.join(ROOT, "desktop");
const REL = path.join(ROOT, "发布");
const VERSION = require(path.join(DESKTOP, "package.json")).version;

const C = {
  g: (s) => "\x1b[32m" + s + "\x1b[0m",
  y: (s) => "\x1b[33m" + s + "\x1b[0m",
  r: (s) => "\x1b[31m" + s + "\x1b[0m"
};
const log = (m) => console.log(C.g("[release]") + " " + m);
function die(m) {
  console.log(C.r("[失败] ") + m);
  process.exit(1);
}

function sh(cmd, args, cwd) {
  const r = spawnSync(cmd, args, { cwd, stdio: "inherit", shell: true });
  if (r.status !== 0) die(cmd + " " + args.join(" ") + " 退出码 " + r.status);
}

/* 静默执行，只返回退出码（用于需要重试的步骤） */
function trySh(cmd, args, cwd, extraEnv) {
  const r = spawnSync(cmd, args, {
    cwd,
    stdio: "inherit",
    shell: true,
    env: Object.assign({}, process.env, extraEnv || {})
  });
  return r.status;
}

function copyDir(src, dst, filter) {
  fs.mkdirSync(dst, { recursive: true });
  for (const name of fs.readdirSync(src)) {
    const s = path.join(src, name);
    const d = path.join(dst, name);
    const st = fs.statSync(s);
    if (st.isDirectory()) copyDir(s, d, filter);
    else if (!filter || filter(s, name)) fs.copyFileSync(s, d);
  }
}

function rmrf(p) {
  fs.rmSync(p, { recursive: true, force: true });
}

/* ---------- 最小 ZIP 打包（deflate，UTF-8 文件名） ---------- */
const CRC_TABLE = (() => {
  const t = new Int32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c;
  }
  return t;
})();
function crc32(buf) {
  let c = -1;
  for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  return (c ^ -1) >>> 0;
}
function dosTime(d) {
  return {
    time: (d.getHours() << 11) | (d.getMinutes() << 5) | (d.getSeconds() >> 1),
    date: ((d.getFullYear() - 1980) << 9) | ((d.getMonth() + 1) << 5) | d.getDate()
  };
}

function writeZip(outFile, entries) {
  const fd = fs.openSync(outFile, "w");
  const central = [];
  let offset = 0;
  const now = dosTime(new Date());

  for (const [name, data] of entries) {
    const nameBuf = Buffer.from(name, "utf8");
    const comp = zlib.deflateRawSync(data, { level: 9 });
    const crc = crc32(data);

    const local = Buffer.alloc(30);
    local.writeUInt32LE(0x04034b50, 0);
    local.writeUInt16LE(20, 4);
    local.writeUInt16LE(0x0800, 6); // UTF-8 文件名
    local.writeUInt16LE(8, 8); // deflate
    local.writeUInt16LE(now.time, 10);
    local.writeUInt16LE(now.date, 12);
    local.writeUInt32LE(crc, 14);
    local.writeUInt32LE(comp.length, 18);
    local.writeUInt32LE(data.length, 22);
    local.writeUInt16LE(nameBuf.length, 26);
    local.writeUInt16LE(0, 28);
    fs.writeSync(fd, local);
    fs.writeSync(fd, nameBuf);
    fs.writeSync(fd, comp);

    const cd = Buffer.alloc(46);
    cd.writeUInt32LE(0x02014b50, 0);
    cd.writeUInt16LE(20, 4);
    cd.writeUInt16LE(20, 6);
    cd.writeUInt16LE(0x0800, 8);
    cd.writeUInt16LE(8, 10);
    cd.writeUInt16LE(now.time, 12);
    cd.writeUInt16LE(now.date, 14);
    cd.writeUInt32LE(crc, 16);
    cd.writeUInt32LE(comp.length, 20);
    cd.writeUInt32LE(data.length, 24);
    cd.writeUInt16LE(nameBuf.length, 28);
    cd.writeUInt32LE(offset, 42);
    central.push(Buffer.concat([cd, nameBuf]));

    offset += 30 + nameBuf.length + comp.length;
  }

  const cdBuf = Buffer.concat(central);
  fs.writeSync(fd, cdBuf);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0);
  end.writeUInt16LE(entries.length, 8);
  end.writeUInt16LE(entries.length, 10);
  end.writeUInt32LE(cdBuf.length, 12);
  end.writeUInt32LE(offset, 16);
  fs.writeSync(fd, end);
  fs.closeSync(fd);
}

function sha256(p) {
  const h = crypto.createHash("sha256");
  const buf = fs.readFileSync(p);
  h.update(buf);
  return h.digest("hex");
}

/* ---------- 主流程 ---------- */
log("版本 " + VERSION);

/* 先探一次证书：决定后面要不要走「签名 + 重打」这条多花一分钟的路 */
const cert = probeCert();
if (cert.mode === "none") {
  log("未配置代码签名证书，出包流程与以前一致（产物未签名）");
} else {
  log("检测到签名证书（" + cert.mode + "），启用签名流程");
}

/* 1. 绿色版 */
log("① 打绿色版 …");
let rcP = trySh("node", ['"build.js"'], DESKTOP);
if (rcP !== 0) {
  /* 同样是下载 Electron 运行时被网络掐断，换镜像重试 */
  console.log(C.y("[提示] 绿色版首次打包失败（多为下载 Electron 运行时失败），改用镜像重试 …"));
  rcP = trySh("node", ['"build.js"'], DESKTOP, {
    ELECTRON_MIRROR: process.env.ELECTRON_MIRROR || "https://npmmirror.com/mirrors/electron/"
  });
  if (rcP !== 0) die("绿色版构建失败，详见上方日志");
}
const portableSrc = path.join(DESKTOP, "release", "轻提醒-win32-x64");
if (!fs.existsSync(path.join(portableSrc, "轻提醒.exe"))) die("绿色版产物缺失：" + portableSrc);

/* 1.5 签名绿色版主程序。
   必须赶在打 zip 之前签，否则压缩包里装的仍是未签名的那份。 */
if (cert.mode !== "none") {
  log("①.5 签名绿色版主程序 …");
  signQuiet(path.join(portableSrc, "轻提醒.exe"));
}

/* 2. 安装包 */
log("② 打安装包（NSIS）…");
rmrf(path.join(DESKTOP, "dist"));
const ebuild = path.join(DESKTOP, "node_modules", ".bin", "electron-builder.cmd");
if (!fs.existsSync(ebuild)) die("electron-builder 未安装，请先在 desktop 执行 npm install");
let rc = trySh('"' + ebuild + '"', ["--win", "nsis", "--x64"], DESKTOP);
if (rc !== 0) {
  /* 第一次失败最常见的原因是下载 Electron 运行时被网络掐断（502/超时），
     换国内镜像重试一次，成功则继续 */
  console.log(C.y("[提示] 首次打包失败，可能是下载 Electron 运行时网络中断，改用镜像重试 …"));
  rc = trySh('"' + ebuild + '"', ["--win", "nsis", "--x64"], DESKTOP, {
    ELECTRON_MIRROR: process.env.ELECTRON_MIRROR || "https://npmmirror.com/mirrors/electron/"
  });
  if (rc !== 0) die("安装包构建失败（网络或签名配置问题），详见上方日志");
}

const setupName = "QingTiXing-Setup-" + VERSION + ".exe";
const setupPath = path.join(DESKTOP, "dist", setupName);
if (!fs.existsSync(setupPath)) die("安装包产物缺失：" + setupPath);

/* 2.5 签名安装包（要签两层）
   ────────────────────────────────────────────────────────────
   用户双击的是外层 Setup，SmartScreen 看的就是它；
   而 Setup 里还裹着一个程序主 exe，安装后那个 exe 才是「发布者」的落脚处。
   两层都得签，但顺序不能反：NSIS 是把 win-unpacked 整个压进去的，
   所以要先签内层、再重打、最后签外层。

   为什么不用 electron-builder 自带的证书通道：它会先改写 exe 的 asar
   完整性资源再签名，而 afterPack 里签过名的 exe 会被它当成「已签名二进制」
   拒绝解析（Parsing signed executable binary is not allowed by default）；
   直接让它签时，它对含中文的路径又调不动 signtool（Access is denied）。
   所以改由 tools/sign.js 在两端各签一次，中间用 --prepackaged 重打。

   签完才轮到算 SHA256，所以这一步不能挪到汇总之后。 */
if (cert.mode !== "none") {
  log("②.5 签名安装包（内层程序 + 外层 Setup）…");

  const innerExe = path.join(DESKTOP, "dist", "win-unpacked", "轻提醒.exe");
  signQuiet(innerExe);

  /* NSIS 的中间产物必须清掉。
     不清的话下一句 prepackaged 会命中缓存（skipped archiving,
     Archive file is up to date），打出来的 Setup 里装的还是签名前那份 exe。 */
  for (const f of fs.readdirSync(path.join(DESKTOP, "dist"))) {
    if (/\.nsis\.7z$|\.blockmap$/.test(f)) {
      fs.rmSync(path.join(DESKTOP, "dist", f), { force: true });
    }
  }

  log("   用已签名的程序目录重打安装包 …");
  const rcRepack = trySh(
    '"' + ebuild + '"',
    ["--prepackaged", "dist/win-unpacked", "--win", "nsis", "--x64"],
    DESKTOP
  );
  if (rcRepack !== 0) die("重打安装包失败（签名后的目录可能不完整）");

  signQuiet(setupPath);

  /* 签完再核对一次，避免出现「日志说签了、文件其实没签」 */
  const st = verifySignature(setupPath);
  if (st !== "Valid" && st !== "UnknownError" && st !== "NotTrusted") {
    die("安装包签名校验未通过（状态 " + st + "），已中止，不发布半签名版本");
  }
}

/* 3. 汇总 */
log("③ 汇总到发布目录 …");
const dirSetup = path.join(REL, "安装包");
const dirPort = path.join(REL, "绿色版");
const dirMedia = path.join(REL, "商店素材");
const dirDoc = path.join(REL, "文档");

/* 安装包、绿色版每次重建；商店素材只刷新文案类文件，图片素材长期存放 */
[dirSetup, dirPort].forEach(rmrf);
mkdirs([dirSetup, dirPort, dirMedia, dirDoc, path.join(dirMedia, "商店截图")]);

/* 安装包 */
fs.copyFileSync(setupPath, path.join(dirSetup, setupName));
cpIfExists(path.join(dirDoc, "安装说明.txt"), path.join(dirSetup, "安装说明.txt"));

/* 绿色版（目录直用 + zip 分发） */
copyDir(portableSrc, dirPort);
cpIfExists(path.join(dirDoc, "绿色版说明.txt"), path.join(dirPort, "readme.txt"));

const zipName = "QingTiXing_Portable_" + VERSION + ".zip";
const zipPath = path.join(REL, zipName);
const entries = [];
const TOP = "QingTiXing_Portable";
(function collect(dir, rel) {
  for (const name of fs.readdirSync(dir).sort()) {
    const full = path.join(dir, name);
    const r = rel ? rel + "/" + name : name;
    const st = fs.statSync(full);
    if (st.isDirectory()) collect(full, r);
    else entries.push([TOP + "/" + r, fs.readFileSync(full)]);
  }
})(dirPort, "");
writeZip(zipPath, entries);
log("   绿色版 zip：" + zipName + "（" + entries.length + " 个文件）");

/* 商店素材：图片常驻 商店素材\ 目录，这里只刷新会随版本变化的文案文件 */
for (const n of ["商店文案.txt", "隐私政策.html"]) {
  cpIfExists(path.join(dirDoc, n), path.join(dirMedia, n));
}
cpIfExists(path.join(dirDoc, "隐私政策.html"), path.join(dirMedia, "index.html")); // Pages 直接可用

/* 校验清单 */
const sums = [
  ["安装包/" + setupName, path.join(dirSetup, setupName)],
  [zipName, zipPath]
];
const lines = sums.map(([name, p]) => sha256(p) + "  " + name);
fs.writeFileSync(path.join(REL, "SHA256SUMS.txt"), lines.join("\r\n") + "\r\n", "utf8");

/* 同步到本机运行目录（桌面快捷方式指向这里，出包后最容易忘记更新）
   统一交给 tools/sync_run_dir.js：逐文件复制、被占用只报告不中断，
   并且会排除 portable.flag（否则会切成便携模式，数据跑到 exe 旁边，看着像提醒全丢了）。
   加 --no-sync 可跳过（只想出包、不想动本机那份时用）。 */
const syncScript = path.join(ROOT, "tools", "sync_run_dir.js");
const noSync = process.argv.indexOf("--no-sync") >= 0;
let runState = C.y("跳过（找不到 tools\\sync_run_dir.js）");
if (noSync) {
  runState = C.y("已按 --no-sync 跳过");
} else if (fs.existsSync(syncScript)) {
  const r = require("child_process").spawnSync(process.execPath, [syncScript], { encoding: "utf8" });
  const out = ((r.stdout || "") + (r.stderr || "")).trim().split(/\r?\n/).filter(Boolean);
  out.forEach((line) => console.log("   " + line));
  runState = r.status === 0 ? C.g("已同步到 轻提醒电脑版") : C.y("未完全同步，见上方提示");
}
log("本机运行目录：" + runState);

/* 结果 */
const mb = (p) => (fs.statSync(p).size / 1048576).toFixed(1) + " MB";
console.log("");
log("完成 ✔");
console.log("   安装包   " + "安装包/" + setupName + "  " + mb(path.join(dirSetup, setupName)));
console.log("   绿色版   " + "绿色版\\  " + sizeOfDir(dirPort));
console.log("   绿色版zip " + zipName + "  " + mb(zipPath));
console.log("   校验值   SHA256SUMS.txt");
console.log("");
console.log(C.y("   下一步：把 安装包\\" + setupName + " 上传到 GitHub Release，商店表单里填新的直链。"));

function mkdirs(list) {
  list.forEach((d) => fs.mkdirSync(d, { recursive: true }));
}

/* 调 tools/sign.js 签名。没配证书时它自己跳过并说明，不算失败；
   配了证书却签不动才中止，避免打出「一半已签一半没签」的版本。 */
function signQuiet(file) {
  if (!fs.existsSync(file)) return;
  const script = path.join(ROOT, "tools", "sign.js");
  if (!fs.existsSync(script)) {
    console.log("   （找不到 tools\\sign.js，跳过签名）");
    return;
  }
  const r = spawnSync(process.execPath, [script, file], { stdio: "inherit" });
  if (r.status !== 0) die("签名失败：" + path.basename(file));
}

/* 问 tools/sign.js 当前能用哪张证书。mode 为 none 表示没配，整条签名流程跳过。 */
function probeCert() {
  const script = path.join(ROOT, "tools", "sign.js");
  if (!fs.existsSync(script)) return { mode: "none" };
  const r = spawnSync(process.execPath, [script, "--probe"], { encoding: "utf8" });
  const out = (r.stdout || "").trim();
  if (!out) return { mode: "none" };
  try {
    return JSON.parse(out);
  } catch (e) {
    return { mode: "none" };
  }
}

/* 读签名状态：Valid / NotSigned / UnknownError / NotTrusted / HashMismatch */
function verifySignature(file) {
  const ps =
    "$s=Get-AuthenticodeSignature -LiteralPath '" + file.replace(/'/g, "''") + "'; $s.Status.ToString()";
  const r = spawnSync("powershell", ["-NoProfile", "-NonInteractive", "-Command", ps], {
    encoding: "utf8",
    timeout: 60000
  });
  return (r.stdout || "").trim() || "Unknown";
}
function cpIfExists(src, dst) {
  if (fs.existsSync(src)) fs.copyFileSync(src, dst);
}
function sizeOfDir(dir) {
  let t = 0;
  (function walk(d) {
    for (const n of fs.readdirSync(d)) {
      const f = path.join(d, n);
      const st = fs.statSync(f);
      if (st.isDirectory()) walk(f);
      else t += st.size;
    }
  })(dir);
  return (t / 1048576).toFixed(1) + " MB";
}
