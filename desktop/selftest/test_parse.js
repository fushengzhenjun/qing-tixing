/* 快速添加时间解析自测：从真实 quick.html 里取出 parse，用桩环境跑一批例句 */
const fs = require("fs");

let src = fs.readFileSync("D:/轻提醒/desktop/quick.html", "utf8");
const m = /<script>([\s\S]*?)<\/script>/.exec(src);
let code = m[1].replace(
  "if(window.qingtx&&window.qingtx.onQuickShow)",
  "globalThis.__parse=parse;\n  if(window.qingtx&&window.qingtx.onQuickShow)"
);

const el = () => ({
  addEventListener() {}, focus() {}, disabled: false, value: "", dataset: {},
  classList: { add() {}, remove() {}, toggle() {} },
  style: {}, setSelectionRange() {}
});
global.window = { qingtx: null };
global.document = { getElementById: el, addEventListener() {} };

eval(code);

const cases = [
  "明天下午三点 开会",
  "30 分钟后 喝水",
  "每天早上 8:00 吃药",
  "工作日 9点 站会",
  "10月30日 14:30 交报告",
  "周五 18:00 健身",
  "今晚八点 倒垃圾",
  "9点 开会",
  "半小时后 关火",
  "下周一 9:30 周会",
  "下午三点 接孩子",
  "2 小时后 取快递",
  "把报告发给老王",
  "11点 睡觉",
  "每天早上八点半 量血压",
  "每周三 14:00 复盘",
  "每个工作日下午六点 打卡",
  "明天早上七点半 跑步",
  "下下周五 10:00 体检"
];

const now = new Date();
console.log("当前时间 " + now.toLocaleString("zh-CN") + "\n");
let bad = 0;
for (const c of cases) {
  const r = global.__parse(c);
  if (r.ok) {
    console.log(("「" + c + "」").padEnd(24, " ") + "→ " + r.summary + "　| " + r.title +
      "　| " + (r.mode === "once" ? r.at : r.rmode + " " + r.repTime));
  } else {
    console.log(("「" + c + "」").padEnd(24, " ") + "→ 拒绝：" + r.msg);
  }
}
