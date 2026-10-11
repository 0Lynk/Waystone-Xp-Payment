/**
 * Generates the mod icon (src/main/resources/waystone_xp_payment.png).
 *
 * Foreground: the payment tank block as a flat orthographic (dimetric) cube built
 *             from the block texture's palette.
 * Background: one frame of the Create: Enchantment Industry "experience" fluid
 *             still texture (16x16, frame 0 of its 20-frame animation), scaled 4x
 *             and darkened so the block stays readable. The frame is embedded
 *             below as raw RGBA, so this generator takes no external input.
 *
 * Usage: node tools/make_icon.mjs [output.png]
 * No third-party dependencies: a minimal PNG writer on top of node:zlib.
 */
import { writeFileSync } from 'node:fs'
import { deflateSync } from 'node:zlib'
import { createHash } from 'node:crypto'

const N = 64 // logical pixel grid
const S = 4 // integer upscale -> 256x256

// --- palette (block texture + experience-fluid greens) -----------------------
const INK = '#23282E'
const FRAME = '#3A4046'
const FRAME_LO = '#2B3037'
const GLASS = '#5C626A'
const GLASS_HI = '#7B838D'
const STONE = '#4E555E'
const STONE_LO = '#3E444B'
const FLUID = '#56D67C'
const FLUID_HI = '#8CF5A8'
const FLUID_CORE = '#E4FFF0'
const FLUID_LO = '#3B9A5C'

// --- experience-fluid frame (16x16 RGBA, frame 0 of experience_still.png) ----
const FLUID_B64 = 'v/4c/xnaAP8Z2QD/tv0a/7v/K/+2/x//w/8d/xnZAP8WtQD/F8QA/xnWAP8Z2gD/XPIL/7b9Gv9c8gv/xv8d/8b/Hf9r9A7/JuwC/x7rAf/A/x7/o/sX/8X/Hf8Z1wD/GdkA/8b/Hf+7/x7/GdoA/x7rAf+9/x7/t/8f/7z/Hv/C/x7/wP8e/0rwCP8Z2gD/GdUA/xa5AP8YzAD/GOoA/xjRAP+//hz/wv8e/xnWAP8Z2gD/uf8f//L/vf/a/3f/wv8e/5P5FP8Z2QD/GdQA/xjLAP8XvgD/GuoA/8f/Hf+2/Rr/GMwA/xe8AP8Z1AD/SvAI/9j/cf/c/3v/1f9r/7/+HP8Z1gD/GdoA/xnUAP8Z1AD/GdkA/7//Hv+4/x//w/8d/1HxCf897gb/GdQA/1zyC/+7/yv/wP8e/7n/H/999hH/GdgA/xjqAP8Z2AD/GdgA/7/+HP+4/x//u/8e/yLrAv8a6gD/vP8e/8X/Hf8Z1gD/ffYR/2v0Dv/F/x3/SvAI/xjiAP999hH/SvAI/6b7GP+2/x//yf9N/8b/Hf8XvgD/nfoW/8D/N/+9/x7/GMwA/6P7F//D/x3/k/kU/ybsAv8Y4gD/GOIA/xnZAP/D/x3/4P+E/9v/ef++/x7/F8EA/xjQAP+j+xf/GdYA/xazAP8Z1gD/GdYA/xnZAP+//hz/uf8f/5P5FP8Z2AD/GNIA/77/Hv+5/x//rPwY/xfEAP8XvAD/F7oA/xnYAP8Z1gD/FrcA/xjMAP8Y6gD/u/8e/7f/H/+2/x//XPIL/xjJAP8Z1wD/JuwC/xjMAP8WswD/FrMA/xjMAP/a/3f/0P9d/xnUAP8Z1AD/w/8d/7n/H/+5/x//0/9m/7/+HP8Y0QD/GMwA/xa1AP8WswD/FrcA/xe8AP8e6wH/7f+k/9P/Zv8Z1AD/GMwA/8f/Hf8Z1gD/GOoA/ybsAv8e6wH/vP8e/7b/H/8Y0QD/FrMA/xfEAP8Z1AD/GMkA/5P5FP8Y6gD/FrcA/xjMAP8Y0QD/HusB/xnaAP8Z1AD/GOIA/7n/Jv/c/3v/GdoA/xe8AP8Z1AD/HusB/8X/Hf8Z2AD/FrUA/xa1AP8Z2AD/a/QO/9P/Zv+8/x7/GOIA/332Ef9c8gv/Ne0F/xa5AP8XwgD/GOoA/0rwCP+9/x7/GdgA/xnUAP8XwgD/GOoA/8f/SP/g/4T/4P+E/8P/Hf+5/x//HusB/xjMAP8WswD/FrcA/xjMAP8Z1gD/GOoA/0rwCP+7/x7/SvAI/xnZAP+5/x//xv8d/8P/Hf/H/x3/t/8i/7z/Hv999hH/GdkA/xfCAP8XvgD/GMwA/xnaAP/C/x7/tv8f/73/Hv8Y4gD/SvAI/w=='

const hexToRgb = (hex) => {
  const h = hex.replace('#', '')
  return [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16)]
}

const buf = new Uint8ClampedArray(N * N * 4)
function put(x, y, rgb, alpha = 1) {
  if (x < 0 || y < 0 || x >= N || y >= N || alpha <= 0) return
  const i = (y * N + x) * 4
  if (alpha >= 1) {
    buf[i] = rgb[0]; buf[i + 1] = rgb[1]; buf[i + 2] = rgb[2]; buf[i + 3] = 255
    return
  }
  buf[i] = buf[i] * (1 - alpha) + rgb[0] * alpha
  buf[i + 1] = buf[i + 1] * (1 - alpha) + rgb[1] * alpha
  buf[i + 2] = buf[i + 2] * (1 - alpha) + rgb[2] * alpha
  buf[i + 3] = 255
}
const rect = (x, y, w, h, rgb, a = 1) => {
  for (let dy = 0; dy < h; dy++) for (let dx = 0; dx < w; dx++) put(x + dx, y + dy, rgb, a)
}
const hline = (x0, x1, y, rgb) => { for (let x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) put(x, y, rgb) }
const vline = (x, y0, y1, rgb) => { for (let y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) put(x, y, rgb) }
/** 45-degree-ish connector (used for the cube's isometric edges) */
const dline = (x0, y0, x1, y1, rgb) => {
  const n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0))
  for (let i = 0; i <= n; i++) {
    put(Math.round(x0 + ((x1 - x0) * i) / n), Math.round(y0 + ((y1 - y0) * i) / n), rgb)
  }
}
/** scanline fill of a convex quad p1->p2->p3->p4 */
function quad(p1, p2, p3, p4, rgb) {
  const pts = [p1, p2, p3, p4]
  const edges = [[p1, p2], [p2, p3], [p3, p4], [p4, p1]]
  const y0 = Math.min(...pts.map((p) => p[1]))
  const y1 = Math.max(...pts.map((p) => p[1]))
  for (let y = y0; y < y1; y++) {
    const xs = []
    for (const [a, b] of edges) {
      if (a[1] === b[1]) continue
      const lo = Math.min(a[1], b[1]), hi = Math.max(a[1], b[1])
      if (y + 0.5 < lo || y + 0.5 > hi) continue
      xs.push(a[0] + ((b[0] - a[0]) * (y + 0.5 - a[1])) / (b[1] - a[1]))
    }
    if (xs.length < 2) continue
    for (let x = Math.round(Math.min(...xs)); x < Math.max(...xs); x++) put(x, y, rgb)
  }
}

// --- background: fluid frame, scaled 4x, darkened + vignette -----------------
const fluid = Buffer.from(FLUID_B64, 'base64')
const SRC = 16
if (fluid.length !== SRC * SRC * 4) throw new Error('embedded fluid frame is not 16x16 RGBA: ' + fluid.length)
const BS = 2 // background preview scale: the 16x16 frame is tiled 2x2 at 2x
const DARK = 0.7 // keep the block readable on top of the fluid
for (let y = 0; y < N; y++) {
  for (let x = 0; x < N; x++) {
    const o = ((((y / BS) | 0) % SRC) * SRC + (((x / BS) | 0) % SRC)) * 4
    const dx = (x - (N - 1) / 2) / (N / 2)
    const dy = (y - (N - 1) / 2) / (N / 2)
    const vig = 1 - 0.45 * Math.min(1, Math.hypot(dx, dy) ** 1.7)
    const k = DARK * vig
    put(x, y, [fluid[o] * k, fluid[o + 1] * k, fluid[o + 2] * k])
  }
}

// --- foreground: the tank block as an orthographic cube ----------------------
const FX = 15, FY = 23, FS = 26, DX = 7, DY = 7
const RX = FX + FS + DX, RY = FY - DY // right face's top-back corner
quad([FX, FY], [FX + FS, FY], [RX, RY], [FX + DX, RY], hexToRgb(STONE)) // top face
quad([FX + FS, FY], [RX, RY], [RX, RY + FS], [FX + FS, FY + FS], hexToRgb(STONE_LO)) // right face
hline(FX + DX, RX, RY, hexToRgb(GLASS_HI)) // back edge catches the light
dline(FX, FY, FX + DX, RY, hexToRgb(GLASS_HI))
rect(FX, FY, FS, FS, hexToRgb(FRAME)) // front face
hline(FX, FX + FS - 1, FY, hexToRgb(GLASS_HI)) // top bevel
rect(FX, FY + FS - 2, FS, 2, hexToRgb(FRAME_LO)) // bottom shade
rect(FX + 3, FY + 3, FS - 6, FS - 6, hexToRgb(GLASS)) // bezel glass
const wx = FX + 5, wy = FY + 5, ww = FS - 10, wh = FS - 10 // fluid window
rect(wx, wy, ww, wh, hexToRgb(FLUID))
hline(wx, wx + ww - 1, wy, hexToRgb(FLUID_HI)) // window reflection
rect(wx, wy + wh - 3, ww, 3, hexToRgb(FLUID_LO)) // depth at the bottom
put(wx + 2, wy + 6, hexToRgb(FLUID_CORE)); put(wx + 3, wy + 6, hexToRgb(FLUID_CORE))
put(wx + 10, wy + 4, hexToRgb(FLUID_CORE)); put(wx + 11, wy + 5, hexToRgb(FLUID_HI))
put(wx + 5, wy + 9, hexToRgb(FLUID_HI)); put(wx + 12, wy + 10, hexToRgb(FLUID_HI))
vline(wx + ww, wy + 1, wy + wh - 1, hexToRgb(FLUID_LO))
vline(wx - 1, wy, wy + wh - 1, hexToRgb(FRAME_LO))
// silhouette outline
dline(FX - 1, FY - 1, FX + DX - 1, RY - 1, hexToRgb(INK))
hline(FX + DX - 1, RX, RY - 1, hexToRgb(INK))
vline(RX, RY - 1, RY + FS, hexToRgb(INK))
dline(RX, RY + FS - 1, FX + FS, FY + FS - 1, hexToRgb(INK))
hline(FX - 1, FX + FS - 1, FY + FS, hexToRgb(INK))
vline(FX - 1, FY, FY + FS - 1, hexToRgb(INK))
vline(FX + FS, FY + 1, FY + FS, hexToRgb(INK)) // seam between front and side face

// --- encode PNG --------------------------------------------------------------
function crc32(bytes) {
  let c = ~0
  for (let i = 0; i < bytes.length; i++) {
    c ^= bytes[i]
    for (let k = 0; k < 8; k++) c = (c >>> 1) ^ (0xedb88320 & -(c & 1))
  }
  return (~c) >>> 0
}
function chunk(type, data) {
  const len = Buffer.alloc(4)
  len.writeUInt32BE(data.length)
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data])
  const crc = Buffer.alloc(4)
  crc.writeUInt32BE(crc32(body))
  return Buffer.concat([len, body, crc])
}
const W = N * S // output pixels (logical grid upscaled by S, nearest neighbour)
const big = new Uint8ClampedArray(W * W * 4)
for (let y = 0; y < W; y++) {
  for (let x = 0; x < W; x++) {
    const si = (((y / S) | 0) * N + ((x / S) | 0)) * 4
    const di = (y * W + x) * 4
    big[di] = buf[si]; big[di + 1] = buf[si + 1]; big[di + 2] = buf[si + 2]; big[di + 3] = buf[si + 3]
  }
}
const raw = Buffer.alloc(W * (W * 4 + 1))
for (let y = 0; y < W; y++) {
  raw[y * (W * 4 + 1)] = 0 // filter: none
  for (let x = 0; x < W * 4; x++) raw[y * (W * 4 + 1) + 1 + x] = big[y * W * 4 + x]
}
const ihdr = Buffer.alloc(13)
ihdr.writeUInt32BE(N * S, 0)
ihdr.writeUInt32BE(N * S, 4)
ihdr[8] = 8; ihdr[9] = 6
const png = Buffer.concat([
  Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
  chunk('IHDR', ihdr),
  chunk('IDAT', deflateSync(raw, { level: 9 })),
  chunk('IEND', Buffer.alloc(0)),
])

const out = process.argv[2] || 'waystone_xp_payment.png'
writeFileSync(out, png)
console.log(`${out}: ${N * S}x${N * S}, ${png.length} bytes, sha256=${createHash('sha256').update(png).digest('hex')}`)
