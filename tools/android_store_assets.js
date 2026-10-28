/* 生成安卓商店素材（图标 + 特色图）
   用法：node tools/android_store_assets.js
   输出：发布\安卓\商店素材\

   为什么单独写一个：应用商店要的是位图，而包里只有矢量的自适应图标；
   拿模拟器截图当图标会带状态栏和圆角遮罩，不能直接用。 */
const fs = require("fs");
const path = require("path");
const sharp = require(path.join(__dirname, "..", "desktop", "node_modules", "sharp"));

const OUT = path.join(__dirname, "..", "发布", "安卓", "商店素材");
const GREEN = "#137A63";
const GREEN_DARK = "#0C5E4B";

/* 铃铛路径和 android/app/src/main/res/drawable/ic_launcher_foreground.xml 保持一致，
   两边不一致的话，装到手机上的图标和商店里看到的会是两个东西 */
const BELL =
  "<path d='M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9'/>" +
  "<path d='M13.73 21a2 2 0 0 1-3.46 0'/>";

function iconSvg(size) {
  const r = size * 0.22;
  const scale = (size * 0.46) / 24;
  const t = (size - 24 * scale) / 2;
  return (
    `<svg xmlns='http://www.w3.org/2000/svg' width='${size}' height='${size}' viewBox='0 0 ${size} ${size}'>` +
    `<rect width='${size}' height='${size}' rx='${r}' fill='${GREEN}'/>` +
    `<g transform='translate(${t},${t}) scale(${scale})' fill='none' stroke='#FFFFFF' ` +
    `stroke-width='1.9' stroke-linecap='round' stroke-linejoin='round'>${BELL}</g></svg>`
  );
}

/* 特色图（1024×500）：商店列表顶部那条横幅 */
function featureSvg() {
  return (
    "<svg xmlns='http://www.w3.org/2000/svg' width='1024' height='500' viewBox='0 0 1024 500'>" +
    "<defs><linearGradient id='g' x1='0' y1='0' x2='1' y2='1'>" +
    `<stop offset='0' stop-color='#15886E'/><stop offset='1' stop-color='${GREEN_DARK}'/>` +
    "</linearGradient></defs>" +
    "<rect width='1024' height='500' fill='url(#g)'/>" +
    /* 右上角一层很淡的圆，避免整块纯色显得平 */
    "<circle cx='906' cy='72' r='190' fill='#FFFFFF' opacity='0.05'/>" +
    "<circle cx='120' cy='470' r='160' fill='#000000' opacity='0.06'/>" +
    /* 图标画成一块半透明的圆角方块，和手机上的图标形状对得上 */
    "<rect x='92' y='148' width='184' height='184' rx='44' fill='#FFFFFF' opacity='0.13'/>" +
    "<g transform='translate(135,187) scale(4.1)' fill='none' stroke='#FFFFFF' stroke-width='1.9' " +
    `stroke-linecap='round' stroke-linejoin='round'>${BELL}</g>` +
    "<text x='336' y='236' font-family='Microsoft YaHei, Noto Sans CJK SC, sans-serif' " +
    "font-size='96' font-weight='700' fill='#FFFFFF'>轻铃</text>" +
    "<text x='340' y='300' font-family='Microsoft YaHei, Noto Sans CJK SC, sans-serif' " +
    "font-size='34' fill='#D8EFE7'>到点提醒 · 完全离线 · 没有广告</text>" +
    "<text x='340' y='352' font-family='Microsoft YaHei, Noto Sans CJK SC, sans-serif' " +
    "font-size='26' fill='#9FC9BC'>锁屏也能弹，闹钟走系统，杀进程照样响</text>" +
    "</svg>"
  );
}

async function main() {
  fs.mkdirSync(OUT, { recursive: true });

  for (const size of [512, 1024]) {
    const p = path.join(OUT, `icon-${size}.png`);
    await sharp(Buffer.from(iconSvg(size))).png().toFile(p);
    console.log("icon", size, p);
  }

  const f = path.join(OUT, "feature-1024x500.png");
  await sharp(Buffer.from(featureSvg())).png().toFile(f);
  console.log("feature", f);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
