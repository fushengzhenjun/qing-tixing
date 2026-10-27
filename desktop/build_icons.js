/* 生成 Microsoft Store 用方形图标（1:1），矢量高清 */
const sharp = require("sharp");
const OUT = "D:\\轻提醒\\store-shots";

function svg(size) {
  return (
    "<svg xmlns='http://www.w3.org/2000/svg' width='" + size + "' height='" + size + "' viewBox='0 0 256 256'>" +
    "<defs><linearGradient id='g' x1='0' y1='0' x2='1' y2='1'>" +
    "<stop offset='0' stop-color='#15886E'/><stop offset='1' stop-color='#0C5E4B'/>" +
    "</linearGradient></defs>" +
    "<rect width='256' height='256' rx='58' fill='url(#g)'/>" +
    "<g transform='translate(54,54) scale(6.2)' fill='none' stroke='#FFFFFF' stroke-width='1.9' stroke-linecap='round' stroke-linejoin='round'>" +
    "<path d='M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9'/>" +
    "<path d='M13.73 21a2 2 0 0 1-3.46 0'/></g></svg>"
  );
}

async function main() {
  for (const size of [300, 512, 1080]) {
    const p = OUT + "\\icon-" + size + ".png";
    await sharp(Buffer.from(svg(size))).png().toFile(p);
    console.log("icon", size, p);
  }
}
main().catch((e) => { console.error(e); process.exit(1); });
