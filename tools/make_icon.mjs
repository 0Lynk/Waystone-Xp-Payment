// Generates src/main/resources/waystone_xp_payment.png — the mod icon referenced by
// `logoFile` in src/main/resources/META-INF/neoforge.mods.toml.
//
//   node tools/make_icon.mjs [output.png]
//
// The art is drawn on a 64x64 grid using the palette of the mod's own tank texture
// (assets/waystone_xp_payment/textures/block/xp_payment_tank.png: #3A4046 frame,
// #5C626A glass, #56D67C experience fluid) and then upscaled 4x with nearest
// neighbour, so the result is crisp pixel art at 256x256 with no dependencies.
import fs from 'node:fs';
import zlib from 'node:zlib';
import crypto from 'node:crypto';

const N = 64, S = 4;
const OUT = process.argv[2] || 'waystone_xp_payment.png';
const px = new Float32Array(N * N * 3);

const hex = (h) => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];
const C = {
  bgTop: '#1E2329', bgBot: '#2E343B', ink: '#161A1F', ink2: '#20262C',
  stone: '#4A5158', stoneHi: '#5F666E', stoneFace: '#343A41',
  slab: '#6A727C', slabHi: '#7E868F', slabSh: '#4E555E', panel: '#5E666F', panelInk: '#3A4046',
  frame: '#3A4046', glass: '#5C626A', window: '#2A3138',
  fluid: '#56D67C', fluidHi: '#8CF5A8', fluidSh: '#3B9A5C', core: '#E4FFF0',
  runeInk: '#1F5C38', orbRim: '#255F3C',
};

function blend(x, y, c, a = 1) {
  if (a <= 0 || x < 0 || y < 0 || x >= N || y >= N) return;
  const [r, g, b] = c, i = (y * N + x) * 3;
  px[i] = px[i] * (1 - a) + r * a;
  px[i + 1] = px[i + 1] * (1 - a) + g * a;
  px[i + 2] = px[i + 2] * (1 - a) + b * a;
}
function rect(x, y, w, h, c, a = 1) {
  for (let j = y; j < y + h; j++) for (let i = x; i < x + w; i++) blend(i, j, c, a);
}
function disc(cx, cy, r, c, a = 1, falloff = 0) {
  const R = Math.ceil(r + falloff + 1);
  for (let j = cy - R; j <= cy + R; j++) for (let i = cx - R; i <= cx + R; i++) {
    const d = Math.hypot(i - cx, j - cy);
    if (d <= r) blend(i, j, c, a);
    else if (falloff > 0 && d <= r + falloff) blend(i, j, c, a * (1 - (d - r) / falloff) * 0.45);
  }
}
// an experience orb: dark rim, glowing body, bright core
function orb(cx, cy, r) {
  disc(cx, cy, r + 1.8, hex(C.fluid), 0.10, 2.2);
  disc(cx, cy, r, hex(C.orbRim), 1);
  disc(cx, cy, r - 0.5, hex(C.fluid), 1);
  disc(cx - r * 0.3, cy - r * 0.3, r * 0.5, hex(C.fluidHi), 1);
  disc(cx - r * 0.45, cy - r * 0.45, r * 0.26, hex(C.core), 1);
}

// ---------------------------------------------------------------- background
for (let y = 0; y < N; y++) {
  for (let x = 0; x < N; x++) {
    blend(x, y, hex(C.bgTop), 1);
    blend(x, y, hex(C.bgBot), y / (N - 1));
  }
}
disc(32, 42, 14, hex(C.fluid), 0.10, 22);                       // green pool of light
disc(32, 18, 16, hex(C.glass), 0.10, 20);                       // cool light behind the slab
for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {       // vignette
  const d = Math.hypot(x - 31.5, y - 31.5) / 45;
  blend(x, y, hex(C.ink), Math.min(0.30, Math.max(0, d - 0.55) * 0.8));
}

// ------------------------------------------------------------------- ground
rect(0, 54, N, 10, hex(C.stoneFace));
rect(0, 54, N, 3, hex(C.stone));
rect(0, 54, N, 1, hex(C.stoneHi));
rect(0, 57, N, 1, hex(C.ink2));
for (const sx of [0, 16, 32, 48]) rect(sx, 54, 1, 10, hex(C.ink2));
for (const sx of [8, 24, 40, 56]) rect(sx, 58, 1, 6, hex(C.ink2), 0.7);

// ----------------------------------------------------------------- waystone
rect(24, 3, 16, 3, hex(C.ink));                                 // stepped outline
rect(23, 5, 18, 4, hex(C.ink));
rect(22, 8, 20, 30, hex(C.ink));
rect(25, 4, 14, 1, hex(C.slabHi));                              // lit top edge
rect(24, 6, 16, 3, hex(C.slab));                                // crown
rect(23, 9, 18, 28, hex(C.slab));                               // body
rect(23, 9, 1, 28, hex(C.slabSh), 0.45);
rect(22, 9, 2, 28, hex(C.slabHi));                              // lit edge
rect(39, 9, 2, 28, hex(C.slabSh));                              // shaded edge
rect(26, 12, 12, 21, hex(C.panelInk));                          // engraved panel
rect(27, 13, 10, 19, hex(C.panel));
rect(27, 13, 1, 19, hex(C.slabHi));
disc(32, 19, 10, hex(C.fluid), 0.16, 8);                        // rune glow
for (let dy = -4; dy <= 4; dy++) {                              // carved rune diamond
  for (let dx = -4; dx <= 4; dx++) {
    const d = Math.abs(dx) + Math.abs(dy);
    if (d > 4) continue;
    blend(32 + dx, 19 + dy, d === 4 ? hex(C.runeInk) : hex(C.fluid));
  }
}
for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) {
  if (Math.abs(dx) + Math.abs(dy) <= 1) blend(32 + dx, 19 + dy, hex(C.core));
}
rect(22, 33, 20, 3, hex(C.fluid), 0.18);                        // light spilling from the tank

// --------------------------------------------------------------------- tank
rect(19, 30, 26, 25, hex(C.ink));                               // outline
rect(20, 31, 24, 22, hex(C.frame));                             // frame
rect(20, 31, 24, 2, hex(C.glass));                              // top rim
rect(22, 35, 20, 16, hex(C.window));                            // glass interior
rect(22, 37, 20, 14, hex(C.fluid));                             // fluid
rect(22, 37, 20, 1, hex(C.fluidHi));                            // surface
rect(22, 47, 20, 4, hex(C.fluidSh));                            // depth
rect(22, 35, 20, 1, hex(C.fluid), 0.18);                        // green light in the glass
rect(24, 36, 1, 16, hex(C.fluidHi), 0.28);                      // shine on the glass
rect(40, 41, 1, 8, hex(C.core), 0.12);
rect(20, 52, 24, 1, hex(C.ink2));                               // base lip
rect(19, 30, 26, 1, hex(C.fluidSh), 0.35);                      // charged rim

// -------------------------------------------------------------- XP orbs
orb(14, 32, 2.4);
orb(49, 25, 2.1);
orb(16, 19, 1.4);
for (const [x, y, a] of [[11, 41, 0.5], [52, 35, 0.45], [9, 22, 0.35], [55, 17, 0.4], [19, 46, 0.3]]) {
  blend(x, y, hex(C.core), a);
  blend(x + 1, y, hex(C.fluidHi), a * 0.5);
  blend(x, y + 1, hex(C.fluidHi), a * 0.5);
}

// --------------------------------------------------------------------- PNG
const W = N * S, H = N * S;
const raw = Buffer.alloc((W * 4 + 1) * H);
for (let y = 0; y < H; y++) {
  const off = y * (W * 4 + 1) + 1;
  for (let x = 0; x < W; x++) {
    const si = (Math.floor(y / S) * N + Math.floor(x / S)) * 3;
    raw[off + x * 4] = Math.round(px[si]);
    raw[off + x * 4 + 1] = Math.round(px[si + 1]);
    raw[off + x * 4 + 2] = Math.round(px[si + 2]);
    raw[off + x * 4 + 3] = 255;
  }
}
const CRC = (() => {
  const t = new Int32Array(256);
  for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1; t[n] = c; }
  return (buf) => { let c = -1; for (const b of buf) c = t[(c ^ b) & 0xFF] ^ (c >>> 8); return (c ^ -1) >>> 0; };
})();
function chunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(CRC(body));
  return Buffer.concat([len, body, crc]);
}
const ihdr = Buffer.alloc(13);
ihdr.writeUInt32BE(W, 0); ihdr.writeUInt32BE(H, 4); ihdr[8] = 8; ihdr[9] = 6;
const png = Buffer.concat([
  Buffer.from([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]),
  chunk('IHDR', ihdr),
  chunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
  chunk('IEND', Buffer.alloc(0)),
]);
fs.writeFileSync(OUT, png);
console.log(`${OUT}: ${W}x${H}, ${png.length} bytes, sha256=${crypto.createHash('sha256').update(png).digest('hex')}`);
