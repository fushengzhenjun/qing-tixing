#!/usr/bin/env node
/**
 * 把仓库镜像到 Gitee，并上传发布附件。
 *
 * 用法：
 *   GITEE_TOKEN=xxxx node tools/gitee_mirror.js            # 完整执行
 *   GITEE_TOKEN=xxxx node tools/gitee_mirror.js --check    # 只验证令牌、不写入
 *
 * 令牌需要的最小权限：projects（仓库读写）、releases（发行版）。
 * 令牌只从环境变量读，不落盘、不写进 .git/config。
 *
 * 为什么用脚本而不是手点：Gitee 的「导入仓库」只搬代码，Release 附件要另外传；
 * 而且导入后 tag 不会带过来，得手动推。一次跑完省事，也方便以后发版重复使用。
 */
const fs = require("fs");
const path = require("path");
const crypto = require("crypto");
const { execFileSync } = require("child_process");

const API = "https://gitee.com/api/v5";
const ROOT = path.join(__dirname, "..");
const REPO = "qing-tixing";
const TOKEN = process.env.GITEE_TOKEN || "";
const CHECK_ONLY = process.argv.includes("--check");

if (!TOKEN) {
  console.error("缺少 GITEE_TOKEN 环境变量。\n  GITEE_TOKEN=你的令牌 node tools/gitee_mirror.js");
  process.exit(1);
}

const step = (n, s) => console.log(`\n[${n}] ${s}`);
const ok = (s) => console.log(`    ✔ ${s}`);
const warn = (s) => console.log(`    ! ${s}`);

async function api(method, p, body) {
  const url = API + p;
  const init = { method, headers: {} };
  if (body) {
    init.headers["Content-Type"] = "application/json;charset=UTF-8";
    init.body = JSON.stringify(body);
  }
  const res = await fetch(url, init);
  const text = await res.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch (e) { data = text; }
  return { status: res.status, ok: res.ok, data };
}

(async () => {
  /* ---------- 1. 验证令牌 ---------- */
  step(1, "验证令牌");
  const me = await api("GET", `/user?access_token=${TOKEN}`);
  if (!me.ok || !me.data || !me.data.login) {
    console.error("    令牌无效：", me.status, JSON.stringify(me.data).slice(0, 300));
    process.exit(1);
  }
  const login = me.data.login;
  const name = me.data.name || login;
  ok(`账号 ${login}（${name}）`);

  /* ---------- 2. 检查/创建仓库 ---------- */
  step(2, `仓库 ${login}/${REPO}`);
  let repo = await api("GET", `/repos/${login}/${REPO}?access_token=${TOKEN}`);
  if (repo.ok && repo.data && repo.data.full_name) {
    ok(`已存在：${repo.data.html_url}`);
  } else if (repo.status === 404) {
    if (CHECK_ONLY) {
      warn("仓库不存在（--check 模式不创建）");
    } else {
      const created = await api("POST", `/user/repos`, {
        access_token: TOKEN,
        name: REPO,
        path: REPO,
        description: "轻提醒 · 到点了就该看得见的桌面提醒工具（国内镜像，源码 MIT）",
        homepage: "https://fushengzhenjun.github.io/qing-tixing/",
        has_issues: true,
        has_wiki: false,
        auto_init: false,
        gitignore_template: null,
        license_template: null,
        private: false
      });
      if (!created.ok) {
        console.error("    创建失败：", created.status, JSON.stringify(created.data).slice(0, 400));
        console.error("    常见原因：未实名认证，或令牌缺少 projects 权限。");
        process.exit(1);
      }
      ok(`已创建：${created.data.html_url}`);
      repo = created;
    }
  } else {
    console.error("    查询仓库失败：", repo.status, JSON.stringify(repo.data).slice(0, 300));
    process.exit(1);
  }

  if (CHECK_ONLY) {
    console.log("\n--check 完成，未做任何写入。");
    return;
  }

  /* ---------- 3. 推代码与 tag ---------- */
  step(3, "推送代码与 tag");
  const auth = `https://${login}:${TOKEN}@gitee.com/${login}/${REPO}.git`;
  const git = (args) => execFileSync("git", args, { cwd: ROOT, stdio: "pipe", encoding: "utf8" });
  try {
    /* 不写 remote，避免令牌留在 .git/config 里；直接把带凭据的地址交给 push */
    git(["push", auth, "main", "--force"]);
    ok("main 已推送");
  } catch (e) {
    console.error("    推送失败：", (e.stderr || e.message || "").toString().slice(0, 400));
    process.exit(1);
  }
  try {
    git(["push", auth, "--tags", "--force"]);
    ok("tag 已推送");
  } catch (e) {
    warn("tag 推送失败（可忽略，Release 会自己建 tag）：" + (e.stderr || "").toString().slice(0, 120));
  }

  /* ---------- 3.5 默认分支与可见性 ----------
     Gitee 新建仓库的默认分支是 master，而我们推的是 main，不改的话首页显示「还没有文件」。
     另外 API 建出来的仓库有可能是私有的（实测过一次），私有状态下匿名下载会 403，
     所以这里一并把 private 置为 false。
     注意：Gitee 的更新接口要求必须带 name 字段，少了会报 "name is missing"。 */
  step("3.5", "设置默认分支与可见性");
  const patched = await api("PATCH", `/repos/${login}/${REPO}`, {
    access_token: TOKEN,
    name: REPO,
    private: false,
    default_branch: "main"
  });
  if (patched.ok) {
    ok(`默认分支 ${patched.data.default_branch || "main"}，私有 ${patched.data.private}`);
  } else {
    warn(`设置失败（可手动在仓库设置里改）：${patched.status} ${JSON.stringify(patched.data).slice(0, 160)}`);
  }

  /* ---------- 4. 建发行版 ---------- */
  step(4, "创建发行版");
  const pkg = JSON.parse(fs.readFileSync(path.join(ROOT, "desktop", "package.json"), "utf8"));
  const tag = "v" + pkg.version;
  const notesPath = path.join(ROOT, "release_notes.md");
  const body = fs.existsSync(notesPath) ? fs.readFileSync(notesPath, "utf8") : `轻提醒 ${tag}`;

  let rel = await api("GET", `/repos/${login}/${REPO}/releases/tags/${tag}?access_token=${TOKEN}`);
  if (rel.ok && rel.data && rel.data.id) {
    ok(`发行版 ${tag} 已存在（id ${rel.data.id}）`);
  } else {
    const created = await api("POST", `/repos/${login}/${REPO}/releases`, {
      access_token: TOKEN,
      tag_name: tag,
      name: `${tag} 提醒弹到屏幕上`,
      body,
      target_commitish: "main",
      prerelease: false
    });
    if (!created.ok) {
      console.error("    创建失败：", created.status, JSON.stringify(created.data).slice(0, 400));
      process.exit(1);
    }
    rel = created;
    ok(`已创建 ${tag}（id ${created.data.id}）`);
  }
  const releaseId = rel.data.id;

  /* ---------- 5. 上传附件 ---------- */
  step(5, "上传附件");
  const files = [
    ["发布/安装包/QingTiXing-Setup-1.5.0.exe", "安装包 · Windows 10/11"],
    ["发布/SHA256-1.5.0.txt", "校验值"]
  ];
  for (const [relPath, label] of files) {
    const abs = path.join(ROOT, relPath);
    if (!fs.existsSync(abs)) { warn(`跳过（不存在）：${relPath}`); continue; }
    const size = fs.statSync(abs).size;
    if (size > 100 * 1024 * 1024) {
      warn(`跳过（超过 Gitee 单文件 100MB）：${path.basename(abs)} ${(size / 1048576).toFixed(1)}MB`);
      continue;
    }
    console.log(`    上传 ${label} ${(size / 1048576).toFixed(1)}MB …`);
    try {
      /* 大文件用 curl，走 multipart 流式上传，不会把整个文件读进内存 */
      const out = execFileSync("curl", [
        "-s", "--max-time", "900",
        "-F", `file=@${abs}`,
        "-F", `access_token=${TOKEN}`,
        `${API}/repos/${login}/${REPO}/releases/${releaseId}/attach_files`
      ], { encoding: "utf8" });
      const j = JSON.parse(out);
      if (j && (j.browser_download_url || j.id)) ok(`${label} 上传完成`);
      else warn(`${label} 返回异常：${out.slice(0, 200)}`);
    } catch (e) {
      warn(`${label} 上传失败：${(e.stderr || e.message || "").toString().slice(0, 200)}`);
    }
  }

  /* ---------- 6. 结果 ---------- */
  console.log("\n完成：");
  console.log(`  仓库   https://gitee.com/${login}/${REPO}`);
  console.log(`  发行版 https://gitee.com/${login}/${REPO}/releases/tag/${tag}`);
  console.log("\n下一步（脚本不做）：把上面两个地址告诉我，我加到落地页和 README。");
})().catch((e) => {
  console.error("\n执行中断：", e && e.stack ? e.stack : e);
  process.exit(1);
});
