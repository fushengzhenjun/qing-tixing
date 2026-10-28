/* 轻铃 · 绿色版打包脚本
   1) 同步最新 HTML 到工程目录
   2) 用 sharp 由 SVG 生成多尺寸图标，再合成 Windows ico
   3) 用 @electron/packager 产出免安装的绿色程序（输出 desktop\release\轻铃-win32-x64）
   4) 自检：确认没有把 node_modules / dist / 构建脚本 / 安装包打进程序目录 */
const path = require("path");
const fs = require("fs");
const sharp = require("sharp");
const pngToIco = require("png-to-ico");
const packager = require("@electron/packager");

const ROOT = __dirname;
const SRC_HTML = path.join(path.dirname(ROOT), "轻铃.html"); // D:\轻提醒\轻铃.html
const ICON_ICO = path.join(ROOT, "icon.ico");
const OUT_DIR = path.join(ROOT, "release");
const VERSION = require("./package.json").version;

/* 墨绿渐变方块 + 白色铃铛图标 */
const SVG =
  "<svg xmlns='http://www.w3.org/2000/svg' width='256' height='256' viewBox='0 0 256 256'>" +
  "<defs><linearGradient id='g' x1='0' y1='0' x2='1' y2='1'>" +
  "<stop offset='0' stop-color='#15886E'/><stop offset='1' stop-color='#0C5E4B'/>" +
  "</linearGradient></defs>" +
  "<rect width='256' height='256' rx='58' fill='url(#g)'/>" +
  "<g transform='translate(54,54) scale(6.2)' fill='none' stroke='#FFFFFF' stroke-width='1.9' stroke-linecap='round' stroke-linejoin='round'>" +
  "<path d='M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9'/>" +
  "<path d='M13.73 21a2 2 0 0 1-3.46 0'/></g></svg>";

async function ensureIcon() {
  if (fs.existsSync(ICON_ICO)) {
    console.log("[icon] 已存在，跳过");
    return;
  }
  const sizes = [16, 24, 32, 48, 64, 128, 256];
  const pngs = await Promise.all(
    sizes.map((s) => sharp(Buffer.from(SVG)).resize(s, s).png().toBuffer())
  );
  const ico = await pngToIco(pngs);
  fs.writeFileSync(ICON_ICO, ico);
  console.log("[icon] 已生成 icon.ico");
}

/* 打包后自检：程序目录只允许有 electron 运行时 + 应用文件 */
function verify(appDir) {
  const bad = [];
  (function walk(dir, rel) {
    rel = rel || "";
    for (const name of fs.readdirSync(dir)) {
      const full = path.join(dir, name);
      const r = rel ? rel + "/" + name : name;
      const st = fs.statSync(full);
      if (st.isDirectory()) {
        if (["node_modules", "dist", "release", "_shots", "selftest", ".git"].indexOf(name) >= 0) {
          bad.push(r + "/  (不该出现的目录)");
        } else {
          walk(full, r);
        }
      } else if (/^(Qing(Ling|TiXing)-Setup|setup|Uninstall).*\.exe$/i.test(name)) {
        bad.push(r + "  (安装包被打进程序目录)");
      } else if (/\.(7z|blockmap|nsh|ps1)$/i.test(name)) {
        bad.push(r + "  (构建中间产物)");
      } else if (/^build(-|_)?.*\.js$/i.test(name) || /^after_pack\.js$/i.test(name)) {
        bad.push(r + "  (构建脚本)");
      } else if (st.size > 250 * 1024 * 1024) {
        bad.push(r + "  (异常大的单文件)");
      }
    }
  })(appDir, "");

  /* 版本号自检：打包进去的界面必须带正确版本，且不能残留占位符
     注意：界面文件在 resources/app 下，不是程序根目录 */
  const htmlInApp = path.join(appDir, "resources", "app", "轻铃.html");
  if (fs.existsSync(htmlInApp)) {
    const h = fs.readFileSync(htmlInApp, "utf8");
    if (h.indexOf("{{VERSION}}") >= 0) bad.push("轻铃.html  (版本占位符没替换)");
    if (h.indexOf('APP_VERSION="' + VERSION + '"') < 0) {
      bad.push("轻铃.html  (界面版本号不是 " + VERSION + ")");
    }
  } else {
    bad.push("轻铃.html  (程序里找不到界面文件)");
  }

  let total = 0;
  (function size(dir) {
    for (const n of fs.readdirSync(dir)) {
      const f = path.join(dir, n);
      const st = fs.statSync(f);
      if (st.isDirectory()) size(f);
      else total += st.size;
    }
  })(appDir);

  if (bad.length) {
    console.error("[check] 程序目录不干净，已中止：\n  " + bad.join("\n  "));
    process.exit(1);
  }
  const mb = total / 1048576;
  console.log("[check] 程序目录干净：" + mb.toFixed(1) + " MB");
  if (mb > 400) {
    console.error("[check] 体积异常（正常约 230MB），请检查 ignore 规则");
    process.exit(1);
  }

  /* 绿色版标记：有这个文件就走便携模式，数据写在 exe 同目录 */
  fs.writeFileSync(
    path.join(appDir, "portable.flag"),
    "轻铃 便携模式标记\r\n有本文件时，提醒数据保存在本目录的「数据」文件夹，可随 U 盘带走。\r\n"
  );
  return total;
}

/* 精简语言包：Electron 自带 50+ 个语言，本项目界面只有中文，删掉其余可省约 40MB */
const KEEP_LOCALES = ["zh-CN.pak", "en-US.pak"];
function pruneLocales(appDir) {
  const dir = path.join(appDir, "locales");
  if (!fs.existsSync(dir)) return 0;
  let removed = 0;
  for (const n of fs.readdirSync(dir)) {
    if (KEEP_LOCALES.indexOf(n) < 0) {
      fs.rmSync(path.join(dir, n), { force: true });
      removed++;
    }
  }
  if (removed) {
    console.log("[locale] 已移除 " + removed + " 个语言包，仅保留 " + KEEP_LOCALES.join(" / "));
  }
  return removed;
}

async function main() {
  /* 同步最新界面，并把版本号注入进去（唯一来源：desktop/package.json） */
  if (fs.existsSync(SRC_HTML)) {
    let html = fs.readFileSync(SRC_HTML, "utf8");
    const hits = (html.match(/\{\{VERSION\}\}/g) || []).length;
    if (!hits) {
      console.error("[copy] 界面里没有 {{VERSION}} 占位符，版本号会显示不出来，已中止");
      process.exit(1);
    }
    html = html.split("{{VERSION}}").join(VERSION);
    fs.writeFileSync(path.join(ROOT, "轻铃.html"), html, "utf8");
    console.log("[copy] 已同步最新 轻铃.html（版本 " + VERSION + " 注入 " + hits + " 处）");
  }

  await ensureIcon();

  fs.rmSync(OUT_DIR, { recursive: true, force: true });

  const appPaths = await packager({
    dir: ROOT,
    name: "轻铃",
    platform: "win32",
    arch: "x64",
    out: OUT_DIR,
    overwrite: true,
    appVersion: VERSION,
    icon: ICON_ICO,
    prune: false,
    ignore: [
      /^\/node_modules($|\/)/,   /* 依赖已打进 electron 运行时，node_modules 绝不能进包 */
      /^\/dist($|\/)/,           /* electron-builder 的安装包输出，进包会变成 700MB */
      /^\/release($|\/)/,        /* 上一次的打包产物 */
      /^\/_shots($|\/)/,
      /^\/\.git($|\/)/,
      /^\/build\.js$/,
      /^\/build_icons\.js$/,
      /^\/after_pack\.js$/,     /* electron-builder 签名钩子，不属于程序本体 */
      /^\/installer\.nsh$/,
      /\.ps1$/,
      /^\/package-lock\.json$/,
      /^\/selftest/,
      /^\/(installer|uninstaller)[A-Za-z]*\.(bmp|ico)$/   /* 安装向导素材，只给 NSIS 用，不该进绿色版 */
    ],
    /* 版权信息只认顶层 appCopyright：@electron/packager 内部把
       opts.appCopyright 映射成版本资源里的 LegalCopyright，
       写在 win32metadata 里的 LegalCopyright 会被静默丢掉，
       结果是 exe 属性显示 Electron 自带的 "GitHub, Inc."（踩过一次）。 */
    appCopyright: "Copyright © 2026 fushengzhenjun",
    win32metadata: {
      CompanyName: "fushengzhenjun",
      FileDescription: "轻铃 · 简洁无广告的定时提醒",
      ProductName: "轻铃",
      OriginalFilename: "轻铃.exe",
      InternalName: "轻铃",
      FileVersion: VERSION,
      ProductVersion: VERSION
    }
  });

  const appDir = appPaths[0];
  pruneLocales(appDir);
  verify(appDir);
  console.log("[done] 绿色版已生成：" + appDir);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
