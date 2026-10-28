#!/usr/bin/env node
/**
 * 当 github.com:443 被代理挡住、git push 不通时，用 GitHub API 把一个本地提交原样搬上去。
 *
 * 关键点：必须复现出**完全相同的 SHA**，否则本地和远端会分叉，下次 push 就冲突。
 * 做法是逐字节复用本地 git 对象：
 *   - blob 内容直接取 `git cat-file blob`（已经是 git 归一化后的字节，含换行符处理）
 *   - tree 用 base_tree 指向父提交的树，只覆盖改动的条目
 *   - commit 的 author / committer / 时间戳 / message 全部照抄
 * git 的对象哈希是内容决定的，因此上面的输入一致，算出来的 SHA 必然一致。
 *
 * 用法：node tools/push_via_api.js                    # 推 main
 *      node tools/push_via_api.js --dry              # 只打印计划
 */
const fs = require("fs");
const os = require("os");
const path = require("path");
const { execFileSync } = require("child_process");

const ROOT = path.join(__dirname, "..");
const OWNER = "fushengzhenjun";
const REPO = "qing-tixing";
const BRANCH = "main";
const DRY = process.argv.includes("--dry");

const git = (args) => execFileSync("git", args, { cwd: ROOT, encoding: "utf8" }).trim();
const gitBuf = (args) => execFileSync("git", args, { cwd: ROOT, maxBuffer: 64 * 1024 * 1024 });

function gh(args) {
  const out = execFileSync("gh", args, { cwd: ROOT, encoding: "utf8", maxBuffer: 32 * 1024 * 1024 });
  return out.trim();
}
function ghJson(args, payload) {
  if (payload === undefined) {
    const out = gh(args);
    return out ? JSON.parse(out) : null;
  }
  const tmp = path.join(os.tmpdir(), "gh-payload-" + Date.now() + ".json");
  fs.writeFileSync(tmp, JSON.stringify(payload), "utf8");
  try {
    const out = gh(args.concat(["--input", tmp]));
    return out ? JSON.parse(out) : null;
  } finally {
    try { fs.unlinkSync(tmp); } catch (e) {}
  }
}

const local = git(["rev-parse", "HEAD"]);
const parent = git(["rev-parse", local + "^"]);
const parentTree = git(["rev-parse", parent + "^{tree}"]);
const localTree = git(["rev-parse", local + "^{tree}"]);
const subject = git(["log", "-1", "--format=%s", local]);

console.log("本地提交 :", local);
console.log("父提交   :", parent);
console.log("标题     :", subject);

const changed = git(["diff", "--name-only", parent, local]).split(/\r?\n/).filter(Boolean);
console.log("改动文件 :", changed.join(", "));

if (DRY) {
  console.log("\n--dry：未写入。");
  process.exit(0);
}

/* 1. 上传 blob（内容取本地对象库，保证字节一致） */
const entries = [];
for (const file of changed) {
  const blobSha = git(["rev-parse", `${local}:${file}`]);
  const content = gitBuf(["cat-file", "blob", blobSha]);
  const mode = git(["ls-tree", local, file]).split(/\s+/)[0];
  process.stdout.write(`  blob ${file} (${mode}) … `);
  const blob = ghJson(
    ["api", "-X", "POST", `/repos/${OWNER}/${REPO}/git/blobs`],
    { content: content.toString("base64"), encoding: "base64" }
  );
  console.log("ok " + blob.sha.slice(0, 7));
  entries.push({ path: file, mode, type: "blob", sha: blob.sha });
}

/* 2. 建 tree：以父提交的树为底，只覆盖改动条目 */
const tree = ghJson(
  ["api", "-X", "POST", `/repos/${OWNER}/${REPO}/git/trees`],
  { base_tree: parentTree, tree: entries }
);
console.log(`  tree ${tree.sha}`);
if (tree.sha !== localTree) {
  console.log(`  ! 注意：生成的 tree ${tree.sha.slice(0, 7)} 与本地 ${localTree.slice(0, 7)} 不同，提交 SHA 会对不上`);
}

/* 3. 建 commit：作者、时间、说明全部照抄 */
const commit = ghJson(
  ["api", "-X", "POST", `/repos/${OWNER}/${REPO}/git/commits`],
  {
    message: git(["log", "-1", "--format=%B", local]),
    tree: tree.sha,
    parents: [parent],
    author: {
      name: git(["log", "-1", "--format=%an", local]),
      email: git(["log", "-1", "--format=%ae", local]),
      date: git(["log", "-1", "--format=%aI", local])
    },
    committer: {
      name: git(["log", "-1", "--format=%cn", local]),
      email: git(["log", "-1", "--format=%ce", local]),
      date: git(["log", "-1", "--format=%cI", local])
    }
  }
);
console.log(`  commit ${commit.sha}`);

/* 4. 更新分支引用 */
ghJson(["api", "-X", "PATCH", `/repos/${OWNER}/${REPO}/git/refs/heads/${BRANCH}`], {
  sha: commit.sha,
  force: false
});
console.log(`  已更新 refs/heads/${BRANCH}`);

const remote = ghJson(["api", `/repos/${OWNER}/${REPO}/git/refs/heads/${BRANCH}`]).object.sha;
console.log("\n远端 HEAD:", remote);

if (remote === local) {
  console.log("✔ 与本地完全一致");
  process.exit(0);
}

/* SHA 对不上，但不是"内容"对不上——最常见的原因是 GitHub 重建提交对象时
   把说明末尾的换行去掉了，于是同样的内容算出不同的 SHA。
   这会让本地和远端分叉，下次 git push 直接被拒。所以这里不退出了事：
   只要树一致，就按远端那份对象的字节重建一个本地提交，把分支指针挪过去。 */
const remoteCommit = ghJson(["api", `/repos/${OWNER}/${REPO}/git/commits/${remote}`]);
if (remoteCommit.tree.sha !== localTree) {
  console.log("✘ 树不一致（内容真的不同），需要人工检查");
  process.exit(1);
}
console.log("  树的 SHA 一致，只是提交对象不同，尝试把本地指针校正到远端");

const crypto = require("crypto");
const raw = gitBuf(["cat-file", "commit", local]).toString("utf8");
const body = raw.slice(raw.indexOf("\n\n") + 2);
const header = raw.slice(0, raw.indexOf("\n\n"));

let fixed = false;
for (const variant of [body.replace(/\n+$/, ""), body.replace(/\n+$/, "") + "\n", body]) {
  const obj = header + "\n\n" + variant;
  const sha = crypto.createHash("sha1")
    .update(`commit ${Buffer.byteLength(obj, "utf8")}\0` + obj, "utf8")
    .digest("hex");
  if (sha !== remote) continue;
  execFileSync("git", ["hash-object", "-t", "commit", "-w", "--stdin"], {
    cwd: ROOT,
    input: obj,
    encoding: "utf8"
  });
  execFileSync("git", ["update-ref", `refs/heads/${BRANCH}`, remote], { cwd: ROOT });
  fixed = true;
  break;
}

console.log(fixed ? "✔ 已把本地分支对齐到远端，两边一致" : "✘ 没能复现远端对象，需要手工处理");
process.exit(fixed ? 0 : 1);
