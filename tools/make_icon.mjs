/**
 * Builds the mod-list icon from the mod's own 16x16 in-game textures:
 *   - the tank block texture (src/main/resources/.../textures/block/xp_payment_tank.png)
 *     is projected onto the three visible faces of an orthographic cube,
 *   - one frame of the experience-fluid still texture is tiled behind it.
 *
 * usage: node tools/make_icon.mjs [output.png] [backgroundDarken=1]
 */
import { readFileSync, writeFileSync } from 'node:fs'
import { inflateSync, deflateSync } from 'node:zlib'
import { createHash } from 'node:crypto'

const TANK_PATH = new URL('../src/main/resources/assets/waystone_xp_payment/textures/block/xp_payment_tank.png', import.meta.url)

/** frame 0 of experience_still.png (create-enchantment-industry), 16x16 RGBA, base64 */
const FLUID_B64 = 'v/4c/xnaAP8Z2QD/tv0a/7v/K/+2/x//w/8d/xnZAP8WtQD/F8QA/xnWAP8Z2gD/XPIL/7b9Gv9c8gv/xv8d/8b/Hf9r9A7/JuwC/x7rAf/A/x7/o/sX/8X/Hf8Z1wD/GdkA/8b/Hf+7/x7/GdoA/x7rAf+9/x7/t/8f/7z/Hv/C/x7/wP8e/0rwCP8Z2gD/GdUA/xa5AP8YzAD/GOoA/xjRAP+//hz/wv8e/xnWAP8Z2gD/uf8f//L/vf/a/3f/wv8e/5P5FP8Z2QD/GdQA/xjLAP8XvgD/GuoA/8f/Hf+2/Rr/GMwA/xe8AP8Z1AD/SvAI/9j/cf/c/3v/1f9r/7/+HP8Z1gD/GdoA/xnUAP8Z1AD/GdkA/7//Hv+4/x//w/8d/1HxCf897gb/GdQA/1zyC/+7/yv/wP8e/7n/H/999hH/GdgA/xjqAP8Z2AD/GdgA/7/+HP+4/x//u/8e/yLrAv8a6gD/vP8e/8X/Hf8Z1gD/ffYR/2v0Dv/F/x3/SvAI/xjiAP999hH/SvAI/6b7GP+2/x//yf9N/8b/Hf8XvgD/nfoW/8D/N/+9/x7/GMwA/6P7F//D/x3/k/kU/ybsAv8Y4gD/GOIA/xnZAP/D/x3/4P+E/9v/ef++/x7/F8EA/xjQAP+j+xf/GdYA/xazAP8Z1gD/GdYA/xnZAP+//hz/uf8f/5P5FP8Z2AD/GNIA/77/Hv+5/x//rPwY/xfEAP8XvAD/F7oA/xnYAP8Z1gD/FrcA/xjMAP8Y6gD/u/8e/7f/H/+2/x//XPIL/xjJAP8Z1wD/JuwC/xjMAP8WswD/FrMA/xjMAP/a/3f/0P9d/xnUAP8Z1AD/w/8d/7n/H/+5/x//0/9m/7/+HP8Y0QD/GMwA/xa1AP8WswD/FrcA/xe8AP8e6wH/7f+k/9P/Zv8Z1AD/GMwA/8f/Hf8Z1gD/GOoA/ybsAv8e6wH/vP8e/7b/H/8Y0QD/FrMA/xfEAP8Z1AD/GMkA/5P5FP8Y6gD/FrcA/xjMAP8Y0QD/HusB/xnaAP8Z1AD/GOIA/7n/Jv/c/3v/GdoA/xe8AP8Z1AD/HusB/8X/Hf8Z2AD/FrUA/xa1AP8Z2AD/a/QO/9P/Zv+8/x7/GOIA/332Ef9c8gv/Ne0F/xa5AP8XwgD/GOoA/0rwCP+9/x7/GdgA/xnUAP8XwgD/GOoA/8f/SP/g/4T/4P+E/8P/Hf+5/x//HusB/xjMAP8WswD/FrcA/xjMAP8Z1gD/GOoA/0rwCP+7/x7/SvAI/xnZAP+5/x//xv8d/8P/Hf/H/x3/t/8i/7z/Hv999hH/GdkA/xfCAP8XvgD/GMwA/xnaAP/C/x7/tv8f/73/Hv8Y4gD/SvAI/w=='

const SRC = 16 // texture edge, in texels
const N = 64 // logical canvas
const S = 4 // nearest-neighbour upscale -> 256x256

// ---- minimal PNG decoder (IHDR/PLTE/tRNS/IDAT, bit depth 8, no interlace) ----
function decodePng(buf) {
  let p = 8
  let w = 0
  let h = 0
  let depth = 0
  let ctype = 0
  let interlace = 0
  let plte = null
  let trns = null
  const idat = []
  while (p + 8 <= buf.length) {
    const len = buf.readUInt32BE(p)
    const type = buf.toString('ascii', p + 4, p + 8)
    const data = buf.subarray(p + 8, p + 8 + len)
    if (type === 'IHDR') {
      w = data.readUInt32BE(0)
      h = data.readUInt32BE(4)
      depth = data[8]
      ctype = data[9]
      interlace = data[12]
    } else if (type === 'PLTE') {
      plte = data
    } else if (type === 'tRNS') {
      trns = data
    } else if (type === 'IDAT') {
      idat.push(data)
    } else if (type === 'IEND') {
      break
    }
    p += 12 + len
  }
  if (depth !== 8) throw new Error(`unsupported bit depth ${depth}`)
  if (interlace !== 0) throw new Error('interlaced PNG is not supported')
  const chan = ctype === 0 ? 1 : ctype === 2 ? 3 : ctype === 3 ? 1 : ctype === 4 ? 2 : 4
  const stride = w * chan
  const raw = inflateSync(Buffer.concat(idat))
  const lines = Buffer.alloc(stride * h)
  let prev = Buffer.alloc(stride)
  for (let y = 0; y < h; y++) {
    const filter = raw[y * (stride + 1)]
    const cur = Buffer.from(raw.subarray(y * (stride + 1) + 1, (y + 1) * (stride + 1)))
    for (let i = 0; i < stride; i++) {
      const a = i >= chan ? cur[i - chan] : 0
      const b = prev[i]
      const c = i >= chan ? prev[i - chan] : 0
      if (filter === 1) cur[i] = (cur[i] + a) & 255
      else if (filter === 2) cur[i] = (cur[i] + b) & 255
      else if (filter === 3) cur[i] = (cur[i] + ((a + b) >> 1)) & 255
      else if (filter === 4) {
        const pp = a + b - c
        const pa = Math.abs(pp - a)
        const pb = Math.abs(pp - b)
        const pc = Math.abs(pp - c)
        cur[i] = (cur[i] + (pa <= pb && pa <= pc ? a : pb <= pc ? b : c)) & 255
      } else if (filter !== 0) throw new Error(`bad row filter ${filter}`)
    }
    cur.copy(lines, y * stride)
    prev = cur
  }
  const rgba = Buffer.alloc(w * h * 4)
  for (let i = 0; i < w * h; i++) {
    if (ctype === 6) lines.copy(rgba, i * 4, i * 4, i * 4 + 4)
    else if (ctype === 2) {
      rgba[i * 4] = lines[i * 3]
      rgba[i * 4 + 1] = lines[i * 3 + 1]
      rgba[i * 4 + 2] = lines[i * 3 + 2]
      rgba[i * 4 + 3] = 255
    } else if (ctype === 3) {
      const idx = lines[i]
      rgba[i * 4] = plte[idx * 3]
      rgba[i * 4 + 1] = plte[idx * 3 + 1]
      rgba[i * 4 + 2] = plte[idx * 3 + 2]
      rgba[i * 4 + 3] = trns && idx < trns.length ? trns[idx] : 255
    } else {
      const g = lines[i * chan]
      rgba[i * 4] = g
      rgba[i * 4 + 1] = g
      rgba[i * 4 + 2] = g
      rgba[i * 4 + 3] = ctype === 4 ? lines[i * 2 + 1] : 255
    }
  }
  return { width: w, height: h, data: rgba }
}

// ---- minimal PNG encoder ----
const CRC_TABLE = (() => {
  const t = new Int32Array(256)
  for (let n = 0; n < 256; n++) {
    let c = n
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1
    t[n] = c
  }
  return t
})()
const crc32 = (buf) => {
  let c = 0xffffffff
  for (const b of buf) c = CRC_TABLE[(c ^ b) & 0xff] ^ (c >>> 8)
  return (c ^ 0xffffffff) >>> 0
}
function encodePng(width, height, rgba) {
  const ihdr = Buffer.alloc(13)
  ihdr.writeUInt32BE(width, 0)
  ihdr.writeUInt32BE(height, 4)
  ihdr[8] = 8
  ihdr[9] = 6
  const raw = Buffer.alloc(height * (width * 4 + 1))
  for (let y = 0; y < height; y++) {
    raw[y * (width * 4 + 1)] = 0
    raw.set(rgba.subarray(y * width * 4, (y + 1) * width * 4), y * (width * 4 + 1) + 1)
  }
  const chunk = (type, data) => {
    const out = Buffer.alloc(data.length + 12)
    out.writeUInt32BE(data.length, 0)
    out.write(type, 4, 'ascii')
    data.copy(out, 8)
    out.writeUInt32BE(crc32(out.subarray(4, 8 + data.length)), 8 + data.length)
    return out
  }
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ])
}

// ---- textures ----
const tank = decodePng(readFileSync(TANK_PATH))
if (tank.width !== SRC || tank.height !== SRC) throw new Error(`tank texture is ${tank.width}x${tank.height}, expected ${SRC}x${SRC}`)
const fluid = Buffer.from(FLUID_B64, 'base64')
if (fluid.length !== SRC * SRC * 4) throw new Error(`embedded fluid frame is not 16x16 RGBA: ${fluid.length}`)

const texel = (tex, u, v) => {
  const x = Math.max(0, Math.min(SRC - 1, u | 0))
  const y = Math.max(0, Math.min(SRC - 1, v | 0))
  const o = (y * SRC + x) * 4
  return [tex[o], tex[o + 1], tex[o + 2], tex[o + 3]]
}

// ---- composition: orthographic cube on a fluid backdrop ----
const FS = 32 // front face edge, px (texture at 2x)
const D = 10 // depth offset, px
const X0 = 11
const Y0 = 21
const BG_TILE = 2 // fluid texel scale behind the cube
const INK = [0x23, 0x28, 0x2e]
const TOP_LIT = 1.12
const RIGHT_LIT = 0.72
const DEFAULT_BG_DARKEN = 0.8 // slight dimming so the cube reads against the fluid

function compose(bgDarken) {
  const px = new Uint8Array(N * N * 4)
  const put = (x, y, rgb, alpha = 255) => {
    if (x < 0 || y < 0 || x >= N || y >= N) return
    const o = (y * N + x) * 4
    px[o] = rgb[0]
    px[o + 1] = rgb[1]
    px[o + 2] = rgb[2]
    px[o + 3] = alpha
  }
  const shade = (rgb, lit) => [rgb[0] * lit, rgb[1] * lit, rgb[2] * lit].map((v) => Math.max(0, Math.min(255, Math.round(v))))

  for (let y = 0; y < N; y++) {
    for (let x = 0; x < N; x++) {
      const [r, g, b] = texel(fluid, (x / BG_TILE) % SRC, (y / BG_TILE) % SRC)
      put(x, y, shade([r, g, b], bgDarken))
    }
  }

  for (let j = 0; j < FS; j++) {
    for (let i = 0; i < FS; i++) {
      put(X0 + i, Y0 + j, texel(tank.data, i / 2, j / 2))
    }
  }
  for (let t = 0; t < D; t++) {
    const v = ((D - 1 - t) / D) * SRC
    for (let i = 0; i < FS; i++) {
      put(X0 + t + i, Y0 - 1 - t, shade(texel(tank.data, i / 2, v), TOP_LIT))
    }
  }
  for (let t = 0; t < D; t++) {
    const u = (t / D) * SRC
    for (let j = 0; j < FS; j++) {
      put(X0 + FS + t, Y0 - t + j, shade(texel(tank.data, u, j / 2), RIGHT_LIT))
    }
  }

  const line = (xa, ya, xb, yb) => {
    const steps = Math.max(Math.abs(xb - xa), Math.abs(yb - ya))
    for (let s = 0; s <= steps; s++) {
      put(Math.round(xa + ((xb - xa) * s) / steps), Math.round(ya + ((yb - ya) * s) / steps), INK)
    }
  }
  const A = [X0, Y0]
  const B = [X0 + FS, Y0]
  const C = [X0 + FS + D, Y0 - D]
  const Dd = [X0 + D, Y0 - D]
  const E = [X0 + FS, Y0 + FS]
  const F = [X0 + FS + D, Y0 + FS - D]
  const G = [X0, Y0 + FS]
  line(...A, ...Dd)
  line(...Dd, ...C)
  line(...C, ...F)
  line(...F, ...E)
  line(...E, ...G)
  line(...G, ...A)

  const big = new Uint8Array(N * S * N * S * 4)
  for (let y = 0; y < N * S; y++) {
    for (let x = 0; x < N * S; x++) {
      const src = (((y / S) | 0) * N + ((x / S) | 0)) * 4
      big.set(px.subarray(src, src + 4), (y * N * S + x) * 4)
    }
  }
  return encodePng(N * S, N * S, big)
}

const out = process.argv[2] ?? new URL('../src/main/resources/waystone_xp_payment.png', import.meta.url)
const darken = process.argv[3] === undefined ? DEFAULT_BG_DARKEN : Number(process.argv[3])
const png = compose(darken)
writeFileSync(out, png)
console.log(`${out} ${N * S}x${N * S} ${png.length} B sha256 ${createHash('sha256').update(png).digest('hex')} bgDarken=${darken}`)
