/* 轻铃 · 安装包图片素材生成
   NSIS 只吃 24 位 BMP，这里把品牌 SVG 光栅化成像素再手写 BMP 头：
     · installerSidebar.bmp    164×314  安装向导左侧竖图
     · uninstallerSidebar.bmp  164×314  卸载向导左侧竖图
     · installerHeader.bmp     150×57   向导右上角横图
   同时输出 PNG 预览到 _audit\shots，方便肉眼确认（BMP 不好直接看）。
   用法：node tools/gen_installer_assets.js */
const fs = require("fs");
const path = require("path");
const sharp = require(path.join(__dirname, "..", "desktop", "node_modules", "sharp"));

const OUT = path.join(__dirname, "..", "desktop");
const SHOTS = path.join(__dirname, "..", "_audit", "shots");

const BELL =
  "M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" +
  "M13.73 21a2 2 0 0 1-3.46 0";

/* 左侧竖图：墨绿渐变 + 光斑 + 铃铛 + 品牌名 */
function sidebarSvg(title, sub) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="164" height="314" viewBox="0 0 164 314">
  <defs>
    <linearGradient id="g" x1="0" y1="0" x2="0.6" y2="1">
      <stop offset="0" stop-color="#1A9B7E"/>
      <stop offset="0.55" stop-color="#127760"/>
      <stop offset="1" stop-color="#0A5546"/>
    </linearGradient>
    <radialGradient id="glow" cx="0.5" cy="0.28" r="0.62">
      <stop offset="0" stop-color="#7FE3C7" stop-opacity="0.34"/>
      <stop offset="1" stop-color="#7FE3C7" stop-opacity="0"/>
    </radialGradient>
  </defs>
  <rect width="164" height="314" fill="url(#g)"/>
  <rect width="164" height="314" fill="url(#glow)"/>
  <circle cx="82" cy="104" r="42" fill="#FFFFFF" fill-opacity="0.10"/>
  <circle cx="82" cy="104" r="32" fill="#FFFFFF" fill-opacity="0.10"/>
  <g transform="translate(56,78) scale(2.16)" fill="none" stroke="#FFFFFF" stroke-opacity="0.95"
     stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
    <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
    <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
  </g>
  <text x="82" y="196" text-anchor="middle" font-family="Microsoft YaHei, PingFang SC, sans-serif"
        font-size="21" font-weight="700" fill="#FFFFFF" letter-spacing="1.5">${title}</text>
  <text x="82" y="220" text-anchor="middle" font-family="Segoe UI, Arial, sans-serif"
        font-size="10.5" fill="#FFFFFF" fill-opacity="0.62" letter-spacing="2.6">QING LING</text>
  <rect x="52" y="238" width="60" height="1" fill="#FFFFFF" fill-opacity="0.22"/>
  <text x="82" y="262" text-anchor="middle" font-family="Microsoft YaHei, PingFang SC, sans-serif"
        font-size="10" fill="#FFFFFF" fill-opacity="0.72">${sub}</text>
</svg>`;
}

/* 右上角横图：白底（贴合 NSIS 头部背景）+ 品牌标识
   注意文字别超出 150 宽：之前右对齐 + 字号偏大，左边被裁掉了 */
function headerSvg() {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="150" height="57" viewBox="0 0 150 57">
  <rect width="150" height="57" fill="#FFFFFF"/>
  <rect x="12" y="14" width="30" height="30" rx="9" fill="#137A63"/>
  <g transform="translate(18,20) scale(0.75)" fill="none" stroke="#FFFFFF" stroke-width="2.1"
     stroke-linecap="round" stroke-linejoin="round">
    <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
    <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
  </g>
  <text x="50" y="28" font-family="Microsoft YaHei, PingFang SC, sans-serif"
        font-size="13" font-weight="700" fill="#1D211F" letter-spacing="0.5">轻铃</text>
  <text x="50" y="41" font-family="Segoe UI, Arial, sans-serif"
        font-size="6.5" fill="#8A918C" letter-spacing="1.1">SIMPLE REMINDER</text>
</svg>`;
}

/* SVG → 24 位 BMP（BI_RGB，行按 4 字节对齐，自下而上） */
async function writeBmp(svg, file, w, h) {
  const { data } = await sharp(Buffer.from(svg))
    .flatten({ background: "#ffffff" })      /* 去掉透明通道，BMP 不支持 alpha */
    .removeAlpha()
    .raw()
    .toBuffer({ resolveWithObject: true });
  const channels = data.length / (w * h);
  if (channels !== 3) throw new Error("像素通道数不是 3，实际 " + channels);

  const rowSize = Math.floor((24 * w + 31) / 32) * 4;
  const pixelBytes = rowSize * h;
  const buf = Buffer.alloc(54 + pixelBytes);
  buf.write("BM", 0, "ascii");
  buf.writeUInt32LE(54 + pixelBytes, 2);
  buf.writeUInt32LE(54, 10);
  buf.writeUInt32LE(40, 14);
  buf.writeInt32LE(w, 18);
  buf.writeInt32LE(h, 22);
  buf.writeUInt16LE(1, 26);
  buf.writeUInt16LE(24, 28);
  buf.writeUInt32LE(pixelBytes, 34);
  buf.writeInt32LE(2835, 38);
  buf.writeInt32LE(2835, 42);

  for (let y = 0; y < h; y++) {
    const srcRow = (h - 1 - y) * w * 3;          /* BMP 自下而上 */
    const dstRow = 54 + y * rowSize;
    for (let x = 0; x < w; x++) {
      const s = srcRow + x * 3;
      const d = dstRow + x * 3;
      buf[d] = data[s + 2];        /* B */
      buf[d + 1] = data[s + 1];    /* G */
      buf[d + 2] = data[s];        /* R */
    }
  }
  fs.writeFileSync(file, buf);
  await sharp(Buffer.from(svg)).png().toFile(file.replace(/\.bmp$/, ".png").replace(OUT, SHOTS));
  return buf.length;
}

(async () => {
  fs.mkdirSync(SHOTS, { recursive: true });
  const jobs = [
    ["installerSidebar.bmp", sidebarSvg("轻铃", "简单 · 干净 · 不联网"), 164, 314],
    ["uninstallerSidebar.bmp", sidebarSvg("轻铃", "卸载向导"), 164, 314],
    ["installerHeader.bmp", headerSvg(), 150, 57]
  ];
  for (const [name, svg, w, h] of jobs) {
    const size = await writeBmp(svg, path.join(OUT, name), w, h);
    console.log("[asset] " + name + "  " + w + "×" + h + "  " + (size / 1024).toFixed(1) + " KB");
  }
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
