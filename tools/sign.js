/* 轻铃 · 代码签名工具（Windows Authenticode）
   ================================================================
   用法：
     node tools/sign.js <文件> [更多文件...]      有证书就签，没证书静默跳过
     node tools/sign.js --require <文件> [...]    没证书算失败（以后强制签名时用）
     node tools/sign.js --check   <文件> [...]    只查签名状态，不改文件

   证书来源（优先级从高到低）：
     1. 环境变量 QXT_CERT_PFX + QXT_CERT_PASSWORD（或 QXT_CERT_SHA1）
     2. 项目根目录 .signing.json（推荐，填一次就行，已在 .gitignore 里排除）
     3. 当前用户证书库里有效的代码签名证书（云签名客户端装好后会自动出现在这里）

   三种都没有 → 打印「未配置证书，跳过签名」并返回 0，打包流程照常走完。
   这样 release.js 可以无条件调用它，没买证书时行为和以前完全一样。
*/
const fs = require("fs");
const path = require("path");
const { spawnSync } = require("child_process");

const ROOT = path.resolve(__dirname, "..");
const CONFIG_FILE = path.join(ROOT, ".signing.json");

const C = {
  g: (s) => "\x1b[32m" + s + "\x1b[0m",
  y: (s) => "\x1b[33m" + s + "\x1b[0m",
  r: (s) => "\x1b[31m" + s + "\x1b[0m"
};
const log = (m) => console.log("[sign] " + m);
const warn = (m) => console.log("[sign] " + C.y(m));
const err = (m) => console.log("[sign] " + C.r(m));

/* 时间戳服务器：按可达性从高到低排队，前一个超时就换下一个。
   /tr 要求 RFC3161，这几个都支持；签名带时间戳，证书过期后旧版本仍然有效。 */
const TIMESTAMP_SERVERS = [
  "http://timestamp.digicert.com",
  "http://timestamp.acs.microsoft.com",
  "http://timestamp.sectigo.com",
  "http://rfc3161timestamp.globalsign.com/advanced"
];

/* ---------- 找 signtool ----------
   优先用 electron 官方签名包里自带的单文件版本（随 electron-builder 一起装的，
   不依赖用户装 Windows SDK）；找不到再退到 PATH / Windows Kits。 */
function findSignTool() {
  const local = path.join(
    ROOT, "desktop", "node_modules", "@electron", "windows-sign", "vendor", "signtool.exe"
  );
  if (fs.existsSync(local)) return local;

  const kits = "C:\\Program Files (x86)\\Windows Kits\\10\\bin";
  if (fs.existsSync(kits)) {
    const versions = fs.readdirSync(kits).filter((v) => /^10\./.test(v)).sort().reverse();
    for (const v of versions) {
      const p = path.join(kits, v, "x64", "signtool.exe");
      if (fs.existsSync(p)) return p;
    }
  }
  const which = spawnSync("where", ["signtool.exe"], { encoding: "utf8" });
  if (which.status === 0) {
    const first = (which.stdout || "").split(/\r?\n/).filter(Boolean)[0];
    if (first && fs.existsSync(first)) return first;
  }
  return null;
}

/* ---------- 读证书配置 ---------- */
function loadConfig(env) {
  if (env.QXT_CERT_PFX || env.QXT_CERT_SHA1) {
    return {
      source: "环境变量",
      pfx: env.QXT_CERT_PFX || "",
      password: env.QXT_CERT_PASSWORD || "",
      sha1: (env.QXT_CERT_SHA1 || "").replace(/\s/g, ""),
      timestamp: env.QXT_CERT_TS || ""
    };
  }
  if (fs.existsSync(CONFIG_FILE)) {
    try {
      const j = JSON.parse(fs.readFileSync(CONFIG_FILE, "utf8"));
      return {
        source: ".signing.json",
        pfx: j.pfx || "",
        password: j.password || "",
        sha1: (j.sha1 || "").replace(/\s/g, ""),
        timestamp: j.timestamp || ""
      };
    } catch (e) {
      warn(".signing.json 格式有误，已忽略：" + e.message);
    }
  }
  return { source: "", pfx: "", password: "", sha1: "", timestamp: "" };
}

/* ---------- 证书库里自动找一张可用的代码签名证书 ----------
   云签名（Certum SimplySign 等）装好后，证书会出现在这里，私钥由客户端代理，
   所以这条路径对「不寄 UKey 的云签名」同样有效。

   只认正式证书：Issuer 与 Subject 相同的是自签名证书，装了也没人信，
   自动挑中它只会让人以为已经签好了。想用自签名测试，就显式写进
   .signing.json 或 QXT_CERT_SHA1 —— 显式指定的一律照办。 */
function scanStore() {
  const ps = [
    "[Console]::OutputEncoding=[Text.Encoding]::UTF8;",
    "Get-ChildItem Cert:\\CurrentUser\\My -CodeSigningCert -ErrorAction SilentlyContinue |",
    "Where-Object { $_.HasPrivateKey -and $_.NotAfter -gt (Get-Date) } |",
    "Sort-Object NotAfter -Descending |",
    "ForEach-Object { $_.Thumbprint + '|' + $_.Subject + '|' + $_.NotAfter.ToString('yyyy-MM-dd') + '|' + $_.Issuer }"
  ].join(" ");
  const r = spawnSync("powershell", ["-NoProfile", "-NonInteractive", "-Command", ps], {
    encoding: "utf8",
    timeout: 30000
  });
  const lines = (r.stdout || "").split(/\r?\n/).map((s) => s.trim()).filter(Boolean);
  const skipped = [];
  for (const line of lines) {
    const parts = line.split("|");
    if (parts.length < 4) continue;
    const [thumb, subject, notAfter, issuer] = parts;
    if (subject.replace(/\s/g, "") === issuer.replace(/\s/g, "")) {
      skipped.push(notAfter);
      continue;
    }
    return { sha1: thumb.trim(), subject, notAfter, skipped };
  }
  return { sha1: "", skipped };
}

/* ---------- 查签名状态（PowerShell 读，返回 ASCII 状态词，不受编码影响） ---------- */
function signatureStatus(file) {
  const ps =
    "[Console]::OutputEncoding=[Text.Encoding]::UTF8; " +
    "$s=Get-AuthenticodeSignature -LiteralPath '" + file.replace(/'/g, "''") + "'; " +
    "$s.Status.ToString() + '|' + $(if($s.SignerCertificate){$s.SignerCertificate.Subject}else{''}) + '|' + " +
    "$(if($s.SignerCertificate){$s.SignerCertificate.NotAfter.ToString('yyyy-MM-dd')}else{''})";
  const r = spawnSync("powershell", ["-NoProfile", "-NonInteractive", "-Command", ps], {
    encoding: "utf8",
    timeout: 60000
  });
  const out = (r.stdout || "").trim();
  if (!out) return { status: "Unknown", subject: "", notAfter: "" };
  const [status, subject, notAfter] = out.split("|");
  return { status: (status || "Unknown").trim(), subject: subject || "", notAfter: notAfter || "" };
}

/* 文件是否被占用：签之前先探一下，被占用时 signtool 会报一个很难懂的错误 */
function isLocked(file) {
  try {
    const fd = fs.openSync(file, "r+");
    fs.closeSync(fd);
    return false;
  } catch (e) {
    return e.code === "EBUSY" || e.code === "EPERM";
  }
}

/* ---------- 签一个文件 ---------- */
function signOne(signtool, file, cert, servers) {
  const args = ["sign", "/fd", "SHA256", "/td", "SHA256", "/v"];

  if (cert.pfx) {
    args.push("/f", cert.pfx);
    if (cert.password) args.push("/p", cert.password);
  } else {
    args.push("/sha1", cert.sha1);
  }

  const list = cert.timestamp ? [cert.timestamp] : servers;

  for (let i = 0; i < list.length; i++) {
    const ts = list[i];
    const r = spawnSync(signtool, args.concat(["/tr", ts, file]), {
      encoding: "utf8",
      timeout: 180000
    });
    const out = ((r.stdout || "") + (r.stderr || "")).trim();
    if (r.status === 0) {
      return { ok: true, ts, out };
    }
    const tail = out.split(/\r?\n/).filter(Boolean).slice(-2).join(" ");
    if (i < list.length - 1) {
      warn("时间戳 " + ts + " 失败，换下一个（" + tail.slice(0, 120) + "）");
    } else {
      return { ok: false, ts, out, tail };
    }
  }
  return { ok: false, ts: "", out: "", tail: "没有可用的时间戳服务器" };
}

/* ---------- 主流程 ---------- */
function main() {
  const argv = process.argv.slice(2);
  const require = argv.includes("--require");
  const checkOnly = argv.includes("--check");
  const quiet = argv.includes("--quiet");
  const files = argv.filter((a) => !a.startsWith("--"));

  /* --probe：只报告当前能用哪张证书，输出一行 JSON 给 release.js 读。
     打包时要靠它决定要不要给 electron-builder 传签名参数。 */
  if (argv.includes("--probe")) {
    const cfg = loadConfig(process.env);
    let out = { mode: "none", skipped: [] };
    if (cfg.pfx) {
      out = { mode: "pfx", pfx: path.resolve(cfg.pfx), password: cfg.password, timestamp: cfg.timestamp };
    } else if (cfg.sha1) {
      out = { mode: "sha1", sha1: cfg.sha1, timestamp: cfg.timestamp };
    } else {
      const found = scanStore();
      if (found.sha1) {
        out = { mode: "sha1", sha1: found.sha1, timestamp: cfg.timestamp, source: "store" };
      } else {
        out.skipped = found.skipped || [];
      }
    }
    process.stdout.write(JSON.stringify(out));
    process.exit(0);
  }

  if (!files.length) {
    console.log(
      "用法：node tools/sign.js [--require|--check|--quiet] <文件> [更多文件...]\n" +
        "  证书配置写在项目根目录 .signing.json，或设环境变量 QXT_CERT_PFX / QXT_CERT_SHA1"
    );
    process.exit(1);
  }

  /* 只查状态 */
  if (checkOnly) {
    let bad = 0;
    for (const f of files) {
      if (!fs.existsSync(f)) {
        err("文件不存在：" + f);
        bad++;
        continue;
      }
      const s = signatureStatus(path.resolve(f));
      const ok = s.status === "Valid";
      const line = path.basename(f) + "  →  " + s.status +
        (s.subject ? "  签名者：" + s.subject : "") +
        (s.notAfter ? "  到期：" + s.notAfter : "");
      console.log((ok ? C.g("✔ ") : C.y("· ")) + line);
      if (s.status === "NotSigned" || s.status === "HashMismatch") bad++;
    }
    process.exit(bad ? 1 : 0);
  }

  /* 找证书 */
  const cfg = loadConfig(process.env);
  let cert = null;
  let skippedNotes = [];

  if (cfg.pfx) {
    const p = path.resolve(cfg.pfx);
    if (!fs.existsSync(p)) {
      err("证书文件不存在：" + p);
      process.exit(1);
    }
    cert = { kind: "pfx", pfx: p, password: cfg.password, sha1: "", source: cfg.source };
  } else if (cfg.sha1) {
    cert = { kind: "sha1", pfx: "", password: "", sha1: cfg.sha1, source: cfg.source };
  } else {
    const found = scanStore();
    skippedNotes = found.skipped || [];
    if (found.sha1) {
      cert = {
        kind: "sha1", pfx: "", password: "", sha1: found.sha1,
        source: "证书库", subject: found.subject, notAfter: found.notAfter
      };
    }
  }

  if (!cert) {
    if (require) {
      err("启用了 --require 但本机没有可用的正式证书。");
      err("请先在项目根目录创建 .signing.json（模板见 发布\\签名\\签名说明.txt）。");
      process.exit(1);
    }
    if (!quiet) {
      log("未配置证书，跳过签名（这不影响打包，产出的仍是未签名版本）");
      for (const n of skippedNotes) {
        log("  证书库里有一张 " + n + " 到期的自签名证书，已忽略（自签名对用户没有信任效果）");
      }
      log("要开始签名：把证书信息填进 " + CONFIG_FILE);
    }
    process.exit(0);
  }

  const signtool = findSignTool();
  if (!signtool) {
    err("找不到 signtool.exe。");
    err("通常随 npm install 一起装好了（@electron/windows-sign）；若确实缺失，");
    err("可改用 发布\\签名\\sign.bat（走 Windows 自带的 Set-AuthenticodeSignature）。");
    process.exit(1);
  }

  log("证书来源：" + cert.source +
      (cert.subject ? "  " + cert.subject + "（到期 " + cert.notAfter + "）" : ""));

  /* 先逐个检查文件可用性，别签到一半才发现有个文件被占用 */
  const targets = [];
  for (const f of files) {
    if (!fs.existsSync(f)) {
      err("文件不存在：" + f);
      process.exit(1);
    }
    const abs = path.resolve(f);
    if (isLocked(abs)) {
      err("文件被占用，请先退出正在运行的程序再签名：" + abs);
      process.exit(1);
    }
    targets.push(abs);
  }

  let signed = 0;
  for (const abs of targets) {
    const name = path.basename(abs);
    const r = signOne(signtool, abs, cert, TIMESTAMP_SERVERS);
    if (!r.ok) {
      err("签名失败：" + name + "  " + (r.tail || ""));
      process.exit(1);
    }

    const s = signatureStatus(abs);
    if (s.status === "NotSigned") {
      err("签名后仍是未签名状态：" + name);
      process.exit(1);
    }
    if (s.status === "HashMismatch") {
      err("签名后文件哈希不匹配（文件被改动）：" + name);
      process.exit(1);
    }

    if (s.status === "Valid") {
      log(C.g("✔ " + name + "  已签名，本机信任"));
    } else {
      /* 自签名证书、或用个人证书签但本机没装根证书时走这里。
         签名本身是写进去了，只是这台电脑不认可这张证书。 */
      warn("✔ " + name + "  已写入签名（" + s.status + "），但本机不信任该证书");
      warn("    自签名证书属于正常现象；正式证书出现这个状态说明证书链有问题。");
    }
    signed++;
  }

  log("完成：签了 " + signed + " 个文件，时间戳 " + TIMESTAMP_SERVERS[0]);
  process.exit(0);
}

main();
