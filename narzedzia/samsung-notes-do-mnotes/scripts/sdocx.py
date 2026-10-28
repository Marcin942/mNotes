"""Własny dekoder .sdocx / .page (Samsung Notes) na potrzeby testów konwersji do mNotes.

Napisany na podstawie opisu formatu (twangodev/sdocx, docs/reverse-engineering), bez kopiowania kodu (GPL-3.0).
"""
import struct
import sys
import zipfile
from dataclasses import dataclass, field


class R:
    def __init__(self, b, p=0):
        self.b = b
        self.p = p

    def u8(self):
        v = self.b[self.p]
        self.p += 1
        return v

    def u16(self):
        v = struct.unpack_from('<H', self.b, self.p)[0]
        self.p += 2
        return v

    def i16(self):
        v = struct.unpack_from('<h', self.b, self.p)[0]
        self.p += 2
        return v

    def u32(self):
        v = struct.unpack_from('<I', self.b, self.p)[0]
        self.p += 4
        return v

    def i32(self):
        v = struct.unpack_from('<i', self.b, self.p)[0]
        self.p += 4
        return v

    def i64(self):
        v = struct.unpack_from('<q', self.b, self.p)[0]
        self.p += 8
        return v

    def f32(self):
        v = struct.unpack_from('<f', self.b, self.p)[0]
        self.p += 4
        return v

    def f64(self):
        v = struct.unpack_from('<d', self.b, self.p)[0]
        self.p += 8
        return v

    def raw(self, n):
        v = self.b[self.p:self.p + n]
        self.p += n
        return v

    def s16(self):
        n = self.u16()
        return self.raw(2 * n).decode('utf-16le')


def mask(r):
    n = r.u8()
    return int.from_bytes(r.raw(n), 'little')


def read_string_table(note):
    """Tablica napisów z note.note (bit 10): u32 rozmiar, u16 liczba, pary (u32 id, napis utf16_u16)."""
    n = len(note)
    for o in range(0, n - 6):
        size, cnt = struct.unpack_from('<IH', note, o)
        if cnt == 0 or cnt > 200 or o + 4 + size > n or size < 2:
            continue
        r = R(note, o + 6)
        out = {}
        try:
            for _ in range(cnt):
                i = r.u32()
                ln = r.u16()
                out[i] = r.raw(2 * ln).decode('utf-16le')
        except Exception:
            continue
        if r.p == o + 4 + size and out and any('samsung' in v or v.endswith(';') for v in out.values()):
            return out
    return {}


@dataclass
class Stroke:
    pts: list            # (x, y, pressure, t)
    props: int
    color: int = None    # ARGB
    size: float = None
    pen: str = None
    adv: str = None
    fixed_width: float = None
    extra: dict = field(default_factory=dict)
    bbox: tuple = None
    uuid: str = ''
    layer: int = 0


@dataclass
class Obj:
    type: int
    payload: bytes
    children: list
    off: int
    layer: int = 0


@dataclass
class Page:
    uuid: str
    width: int
    height: int
    orientation: int
    mask: int
    bg_color: int = None
    template_type: int = None
    template_uri: str = None
    bg_image: int = None
    bg_mode: int = None
    bg_width: int = None
    bg_rot: int = None
    content_rect: tuple = None
    layers: list = field(default_factory=list)   # lista list obiektów najwyższego poziomu
    layer_info: list = field(default_factory=list)
    header_end: int = 0
    unparsed: bytes = b''
    cache_raw: bytes = b''


def _dq(raw, scale):
    s = -1 if raw & 0x8000 else 1
    return s * (raw & 0x7fff) / scale


def parse_stroke(b, strings):
    """Dekoduje obiekt typu 1 (pociągnięcie): ramka bazowa + ramka pociągnięcia."""
    r = R(b, 0)
    base_size = r.u32()
    btype = r.i16()
    assert btype == 0, btype
    bflex = r.u32()
    bprops = mask(r)
    bfmask = mask(r)
    ver = r.u32()
    ulen = r.u16()
    uuid = r.raw(ulen).decode('ascii', 'replace')
    mod = r.i64()
    bbox = (r.f64(), r.f64(), r.f64(), r.f64())
    fs = base_size
    r = R(b, fs)
    fsize = r.u32()
    ftype = r.i16()
    assert ftype == 1, ftype
    flex = r.u32()
    props = mask(r)
    fmask = mask(r)
    n = r.u16()
    xs = ys = pr = ts = None
    if props & 1:
        x = r.f64()
        y = r.f64()
        xs, ys = [x], [y]
        for _ in range(n - 1):
            dx = r.u16()
            dy = r.u16()
            x += _dq(dx, 32.0)
            y += _dq(dy, 32.0)
            xs.append(x)
            ys.append(y)
        p = r.f32()
        pr = [p]
        for _ in range(n - 1):
            p += _dq(r.u16(), 4096.0)
            pr.append(p)
        t = r.i32()
        ts = [t]
        for _ in range(n - 1):
            t += r.u16()
            ts.append(t)
        if props & 4:
            for _ in range(2):
                r.f32()
                for _ in range(n - 1):
                    r.u16()
        r.raw(2)
    else:
        xs, ys, pr, ts = [], [], [], []
        for _ in range(n):
            xs.append(r.f64())
            ys.append(r.f64())
        pr = [r.f32() for _ in range(n)]
        ts = [r.i32() for _ in range(n)]
        if props & 4:
            for _ in range(2 * n):
                r.f32()
        r.raw(2)
    if r.p != fs + flex:
        raise ValueError(f'koniec tablic {r.p - fs} != flex {flex} (n={n}, props={props:#x})')
    r = R(b, fs + flex)
    st = Stroke(pts=list(zip(xs, ys, pr, ts)), props=props, bbox=bbox, uuid=uuid)
    ex = st.extra

    def sid(i):
        return strings.get(i) if i != -1 else None

    for bit in range(26):
        if not fmask >> bit & 1:
            continue
        if bit == 0:
            ex['legacy_pen'] = sid(r.i32())
        elif bit == 1:
            st.adv = sid(r.i32())
        elif bit == 2:
            st.color = r.u32()
        elif bit == 3:
            st.size = r.f32()
        elif bit == 4:
            ex['b4'] = r.u8()
        elif bit == 5:
            raise NotImplementedError('bit 5')
        elif bit == 7:
            st.pen = sid(r.i32())
        elif bit == 8:
            st.fixed_width = r.f32()
        elif bit in (9, 10, 11, 12, 19, 21, 22):
            ex[f'b{bit}'] = r.i32()
        elif bit in (13, 15, 17, 18, 20, 23):
            ex[f'b{bit}'] = r.f32()
        elif bit in (14, 16, 25):
            ex[f'b{bit}'] = r.u16()
        elif bit == 24:
            c = r.u16()
            ex['grad'] = [r.u32() for _ in range(c)]
    if r.p != fs + fsize:
        ex['_flex_leftover'] = fs + fsize - r.p
    return st


def read_objects(b, r, count, layer):
    out = []
    for _ in range(count):
        off = r.p
        t = r.u8()
        cc = r.u16()
        ds = r.u32()
        payload = r.raw(ds - 32)
        r.raw(32)
        kids = read_objects(b, r, cc, layer)
        out.append(Obj(t, payload, kids, off, layer))
    return out


def parse_page(b, strings):
    r = R(b, 0)
    layer_off = r.u32()
    flex_off = r.u32()
    pm = mask(r)
    fm = mask(r)
    orient = r.u32()
    w = r.u32()
    h = r.u32()
    ox = r.u32()
    oy = r.u32()
    uuid = r.s16()
    mod = r.i64()
    ver = r.u32()
    minver = r.u32()
    pg = Page(uuid=uuid, width=w, height=h, orientation=orient, mask=fm)
    pg.header_end = r.p
    pg.fixed = dict(pm=pm, ox=ox, oy=oy, mod=mod, ver=ver, minver=minver, flex_off=flex_off, layer_off=layer_off)
    r.p = flex_off
    if fm & 1:
        pg.content_rect = (r.f64(), r.f64(), r.f64(), r.f64())
    if fm & 2:
        raise NotImplementedError('bit 1 (tagi)')
    if fm & 4:
        pg.template_uri = r.s16()
    if fm & 8:
        pg.bg_image = r.i32()
    if fm & 16:
        pg.bg_mode = r.i32()
    if fm & 32:
        pg.bg_color = r.u32()
    if fm & 64:
        pg.bg_width = r.i32()
    if fm & 128:
        pg.bg_rot = r.i32()
    if fm & 256:
        cnt = r.u16()
        pg.pdf = []
        for _ in range(cnt):
            pg.pdf.append((r.i32(), r.i32(), r.i32(), r.i32(), r.i32(), r.i32()))
    if fm & 512:
        pg.template_type = r.i32()
    pg.unparsed = b[r.p:layer_off]
    r = R(b, layer_off)
    lc = r.u16()
    cur = r.u16()
    pg.current_layer = cur
    for li in range(lc):
        ls = r.p
        hsize = r.u32()
        lflex = r.u32()
        lpm = mask(r)
        lfm = mask(r)
        info = dict(start=ls, hsize=hsize, lflex=lflex, pm=lpm, fm=lfm)
        r.p = ls + hsize
        cnt = r.u32()
        objs = read_objects(b, r, cnt, li)
        r.raw(32)
        pg.layers.append(objs)
        pg.layer_info.append(info)
    return pg


def walk(objs):
    for o in objs:
        yield o
        yield from walk(o.children)


def load_sdocx(path):
    z = zipfile.ZipFile(path)
    note = z.read('note.note')
    strings = read_string_table(note)
    order = []
    pid = z.read('pageIdInfo.dat')
    r = R(pid, 32)
    cnt = r.u16()
    for _ in range(cnt):
        order.append(r.s16())
        r.raw(32)
    pages = []
    for u in order:
        data = z.read(u + '.page')
        pages.append(parse_page(data, strings))
    return strings, pages, z


if __name__ == '__main__':
    strings, pages, z = load_sdocx(sys.argv[1])
    print('strings', strings)
    from collections import Counter
    for i, p in enumerate(pages):
        print(i, p.uuid, p.width, p.height, f'mask={p.mask:#x}', 'bg', p.bg_color and hex(p.bg_color),
              'tmpl', p.template_type, p.template_uri, 'img', p.bg_image, p.bg_mode, p.bg_width, p.bg_rot,
              'rect', p.content_rect, 'unparsed', p.unparsed.hex(), p.fixed)
        for li, objs in enumerate(p.layers):
            c = Counter(o.type for o in walk(objs))
            print('   layer', li, p.layer_info[li], dict(c))
