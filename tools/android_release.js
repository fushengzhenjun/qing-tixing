/* 轻铃 · 安卓出包脚本（node tools/android_release.js）
   1) 用 JDK 17 调 gradlew assembleRelease
   2) 把 APK 复制到 发布\安卓\QingLing-<版本>.apk
   3) 校验签名、算 SHA256，写 SHA256-<版本>.txt
   4) 重新生成商店图标与特色图

   和电脑版分开跑：电脑版是 tools\release.js，这边只碰 android 和 发布\安卓，
   互不影响。截图是手工拍的（见 发布\安卓\文档\上架指南-安卓.md），脚本不代劳。 */
const fs = require("fs");
const path = require("path");
const crypto = require("crypto");
const { spawnSync } = require("child_process");

const ROOT = path.resolve(__dirname, "..");
const ANDROID = path.join(ROOT, "android");
const REL = path.join(ROOT, "发布", "安卓");

const C = {
  g: (s) => "\x1b[32m" + s + "\x1b[0m",
  y: (s) => "\x1b[33m" + s + "\x1b[0m",
  r: (s) => "\x1b[31m" + s + "\x1b[0m"
};
const log = (m) => console.log(C.g("[android]") + " " + m);
const warn = (m) => console.log(C.y("[注意] ") + m);
function die(m) {
  console.log(C.r("[失败] ") + m);
  process.exit(1);
}

/** 版本号就一个来源：build.gradle.kts，别在别处再写一遍 */
function readVersion() {
  const g = fs.readFileSync(path.join(ANDROID, "app", "build.gradle.kts"), "utf8");
  const name = /versionName\s*=\s*"([^"]+)"/.exec(g);
  const code = /versionCode\s*=\s*(\d+)/.exec(g);
  if (!name || !code) die("读不到 versionName / versionCode");
  return { name: name[1], code: code[1] };
}

/**
 * 找 JDK 17。
 * 本机默认 JAVA_HOME 是 JDK 8，AGP 起不来，所以这里自己找：
 * 先看 JAVA_HOME，再看 AndroidDev 下的 jdk-17*，最后试 Android Studio 自带的 jbr。
 */
function findJavaHome() {
  const candidates = [];
  if (process.env.JAVA_HOME) candidates.push(process.env.JAVA_HOME);

  const devRoot = path.join(process.env.USERPROFILE || "C:/Users/Administrator", "AndroidDev");
  if (fs.existsSync(devRoot)) {
    for (const d of fs.readdirSync(devRoot)) {
      if (/^jdk-?17/i.test(d)) candidates.push(path.join(devRoot, d));
    }
  }
  candidates.push("C:/Program Files/Android/Android Studio/jbr");

  for (const c of candidates) {
    const java = path.join(c, "bin", process.platform === "win32" ? "java.exe" : "java");
    if (!fs.existsSync(java)) continue;
    const r = spawnSync(java, ["-version"], { encoding: "utf8" });
    const v = (r.stderr || "") + (r.stdout || "");
    const m = /version "(\d+)/.exec(v);
    if (m && Number(m[1]) >= 17) return c;
  }
  die("没找到 JDK 17。装一个，或者把 JAVA_HOME 指过去再跑。");
}

function sh(cmd, args, cwd, extraEnv) {
  const r = spawnSync(cmd, args, {
    cwd,
    stdio: "inherit",
    shell: true,
    env: Object.assign({}, process.env, extraEnv || {})
  });
  if (r.status !== 0) die(cmd + " " + args.join(" ") + " 退出码 " + r.status);
}

/** 找 build-tools 里最新的 apksigner / aapt2 */
function buildTools() {
  const sdk = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT ||
    (fs.existsSync(path.join(ANDROID, "local.properties"))
      ? /sdk\.dir=(.*)/.exec(fs.readFileSync(path.join(ANDROID, "local.properties"), "utf8"))[1]
          .replace(/\\\\/g, "\\").replace(/\\:/g, ":")
      : null);
  if (!sdk) return null;
  const dir = path.join(sdk.trim(), "build-tools");
  if (!fs.existsSync(dir)) return null;
  const versions = fs.readdirSync(dir).sort((a, b) => {
    const pa = a.split(".").map(Number), pb = b.split(".").map(Number);
    for (let i = 0; i < 3; i++) if ((pa[i] || 0) !== (pb[i] || 0)) return (pa[i] || 0) - (pb[i] || 0);
    return 0;
  });
  const latest = path.join(dir, versions[versions.length - 1]);
  return {
    apksigner: path.join(latest, "apksigner.bat"),
    aapt2: path.join(latest, "aapt2.exe")
  };
}

function sha256(file) {
  const h = crypto.createHash("sha256");
  h.update(fs.readFileSync(file));
  return h.digest("hex");
}

function main() {
  const v = readVersion();
  log(`版本 ${v.name}（versionCode ${v.code}）`);

  if (!fs.existsSync(path.join(ANDROID, "keystore.properties"))) {
    warn("没有 android/keystore.properties，打出来的包会用 debug 签名，不能上架。");
  }

  const javaHome = findJavaHome();
  log("JDK: " + javaHome);

  log("编译 release APK…");
  sh(path.join(ANDROID, process.platform === "win32" ? "gradlew.bat" : "./gradlew"),
    [":app:assembleRelease", "--console=plain"], ANDROID, { JAVA_HOME: javaHome });

  const src = path.join(ANDROID, "app", "build", "outputs", "apk", "release", "app-release.apk");
  if (!fs.existsSync(src)) die("没找到 " + src);

  fs.mkdirSync(REL, { recursive: true });
  const dest = path.join(REL, `QingLing-${v.name}.apk`);
  fs.copyFileSync(src, dest);
  log("已复制 → " + dest);

  /* 签名校验：debug 签名和 release 签名从这里能一眼看出来 */
  const bt = buildTools();
  if (bt && fs.existsSync(bt.apksigner)) {
    /* 必须 shell:true：apksigner 在 Windows 上是 .bat，直接 spawn 会 EINVAL */
    const r = spawnSync(bt.apksigner, ["verify", "--print-certs", dest], {
      encoding: "utf8",
      shell: true
    });
    const out = (r.stdout || "") + (r.stderr || "");
    const dn = /certificate DN: (.*)/.exec(out);
    const sha = /certificate SHA-256 digest: (.*)/.exec(out);
    if (r.status === 0 && dn) {
      log("签名: " + dn[1].trim());
      log("证书 SHA-256: " + (sha ? sha[1].trim() : "?"));
    } else {
      die("APK 签名校验没通过：" + (out.trim().split("\n")[0] || (r.error && r.error.message)));
    }
    if (bt.aapt2 && fs.existsSync(bt.aapt2)) {
      const b = spawnSync(bt.aapt2, ["dump", "badging", dest], { encoding: "utf8", shell: true });
      const pkg = /package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'/.exec(b.stdout || "");
      const sdk = /minSdkVersion:'(\d+)'/.exec(b.stdout || "");
      if (pkg) log(`包名 ${pkg[1]} · versionCode ${pkg[2]} · 最低 Android ${sdk ? sdk[1] : "?"}`);
      if (!/INTERNET/.test(b.stdout || "")) log("权限里没有 INTERNET ✓");
      else warn("权限里出现了 INTERNET，检查一下 AndroidManifest。");
    }
  } else {
    warn("没找到 apksigner，跳过签名校验。");
  }

  const hash = sha256(dest);
  const size = (fs.statSync(dest).size / 1024 / 1024).toFixed(1);
  fs.writeFileSync(
    path.join(REL, `SHA256-${v.name}.txt`),
    `${hash} *QingLing-${v.name}.apk\n`,
    "utf8"
  );
  log(`大小 ${size} MB`);
  log("SHA256 " + hash);
  log("已写 " + path.join("发布", "安卓", `SHA256-${v.name}.txt`));

  log("生成商店图标 / 特色图…");
  sh("node", [path.join("tools", "android_store_assets.js")], ROOT);

  console.log("");
  console.log(C.g("完成。") + " 接下来：");
  console.log("  1. 真机装一遍，重点看：锁屏等提醒 / 从最近任务划掉后等提醒 / 重启后等提醒");
  console.log("  2. 截图放 发布\\安卓\\商店素材\\商店截图\\（1080×2400，见上架指南）");
  console.log("  3. 上传 APK 到 Release，再把直链填进商店后台");
}

main();
