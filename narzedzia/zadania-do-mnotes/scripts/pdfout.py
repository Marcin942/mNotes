#!/usr/bin/env python3
"""Zapis wynikowego PDF: skalowanie oryginałów (wektorowo) i strony zadań napisane w czystym Pythonie.

Strony zadań to kratka A5 (linie 0,6 pt, kolor jak w aplikacji) z obrazkami; PDF składa się z dwóch części
(oryginały przeskalowane + strony zadań), które łączy pierwszy dostępny backend: pypdf, PyMuPDF, pdfunite, ghostscript.
"""
import os
import shutil
import subprocess
import tempfile
import zlib

from zm_common import GRID_LINE_RGB, GRID_SPACING, A5_H, A5_W, import_fitz, have_module, which


def _fmt(v):
    s = "%.3f" % v
    return s.rstrip("0").rstrip(".") if "." in s else s


def write_task_pdf(path, pages):
    """pages: lista {'images': [(PIL.Image, cx, cy, w, h)]} — pozycje w pt od lewego górnego rogu strony A5."""
    objs = []  # bajty ciał obiektów; numer obiektu = indeks + 1

    def add(body):
        objs.append(body)
        return len(objs)

    cat = add(b"")
    pages_id = add(b"")
    page_ids = []
    r, g, b = [c / 255.0 for c in GRID_LINE_RGB]
    sp = GRID_SPACING
    x0 = (A5_W % sp) / 2.0
    y0 = (A5_H % sp) / 2.0
    grid = ["%s %s %s RG 0.6 w" % (_fmt(r), _fmt(g), _fmt(b))]
    x = x0
    while x <= A5_W + 0.01:
        grid.append("%s 0 m %s %s l S" % (_fmt(x), _fmt(x), _fmt(A5_H)))
        x += sp
    y = y0
    while y <= A5_H + 0.01:
        grid.append("0 %s m %s %s l S" % (_fmt(y), _fmt(A5_W), _fmt(y)))
        y += sp
    grid_ops = "\n".join(grid)

    for page in pages:
        xobjs = []
        ops = [grid_ops]
        for i, (im, cx, cy, w, h) in enumerate(page["images"]):
            gray = im.mode == "L"
            rgb = im if gray else im.convert("RGB")
            data = zlib.compress(rgb.tobytes(), 6)
            head = ("<< /Type /XObject /Subtype /Image /Width %d /Height %d /ColorSpace /%s /BitsPerComponent 8 "
                    "/Filter /FlateDecode /Length %d >>\nstream\n" % (rgb.width, rgb.height,
                                                                      "DeviceGray" if gray else "DeviceRGB", len(data)))
            oid = add(head.encode("ascii") + data + b"\nendstream")
            name = "Im%d" % i
            xobjs.append("/%s %d 0 R" % (name, oid))
            left = cx - w / 2.0
            bottom = A5_H - (cy + h / 2.0)
            ops.append("q %s 0 0 %s %s %s cm /%s Do Q" % (_fmt(w), _fmt(h), _fmt(left), _fmt(bottom), name))
        content = "\n".join(ops).encode("ascii")
        cdata = zlib.compress(content, 6)
        cid = add(("<< /Filter /FlateDecode /Length %d >>\nstream\n" % len(cdata)).encode("ascii") + cdata + b"\nendstream")
        pid = add(("<< /Type /Page /Parent %d 0 R /MediaBox [0 0 %s %s] /Resources << /XObject << %s >> >> /Contents %d 0 R >>"
                   % (pages_id, _fmt(A5_W), _fmt(A5_H), " ".join(xobjs), cid)).encode("ascii"))
        page_ids.append(pid)

    objs[cat - 1] = ("<< /Type /Catalog /Pages %d 0 R >>" % pages_id).encode("ascii")
    objs[pages_id - 1] = ("<< /Type /Pages /Kids [%s] /Count %d >>" % (" ".join("%d 0 R" % p for p in page_ids), len(page_ids))).encode("ascii")

    out = bytearray(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n")
    offsets = []
    for n, body in enumerate(objs, 1):
        offsets.append(len(out))
        out += ("%d 0 obj\n" % n).encode("ascii") + body + b"\nendobj\n"
    xref = len(out)
    out += ("xref\n0 %d\n0000000000 65535 f \n" % (len(objs) + 1)).encode("ascii")
    for off in offsets:
        out += ("%010d 00000 n \n" % off).encode("ascii")
    out += ("trailer\n<< /Size %d /Root %d 0 R >>\nstartxref\n%d\n%%%%EOF\n" % (len(objs) + 1, cat, xref)).encode("ascii")
    with open(path, "wb") as f:
        f.write(out)


def target_size(w, h):
    """Rozmiar po skalowaniu do A5 (ta sama orientacja): jednolity współczynnik, bez zniekształceń."""
    tw, th = (A5_W, A5_H) if h >= w else (A5_H, A5_W)
    k = min(tw / w, th / h)
    return w * k, h * k, k


def scale_backend():
    """Nazwa backendu skalującego PDF wektorowo albo None."""
    if have_module("pypdf"):
        return "pypdf"
    if import_fitz() is not None:
        return "pymupdf"
    if which("gs"):
        return "ghostscript"
    return None


def scale_pdf(src, dst, sizes):
    """Skaluje każdą stronę src do A5 (wektory zostają wektorami). sizes: lista (w, h) po obrocie, jak widzi je czytnik."""
    be = scale_backend()
    if be == "pypdf":
        from pypdf import PdfReader, PdfWriter, Transformation
        rd = PdfReader(src)
        wr = PdfWriter()
        for pg, (w, h) in zip(rd.pages, sizes):
            tw, th, k = target_size(w, h)
            if pg.get("/Rotate", 0) % 360:
                pg.transfer_rotation_to_content()
            # po transfer_rotation_to_content mediabox ma już wymiary widoczne (w, h)
            pg.add_transformation(Transformation().scale(k, k))
            box = pg.mediabox
            left, bottom = float(box.left) * k, float(box.bottom) * k
            for name in ("mediabox", "cropbox", "trimbox", "bleedbox", "artbox"):
                try:
                    getattr(pg, name).left = left
                    getattr(pg, name).bottom = bottom
                    getattr(pg, name).right = left + tw
                    getattr(pg, name).top = bottom + th
                except Exception:
                    pass
            wr.add_page(pg)
        with open(dst, "wb") as f:
            wr.write(f)
        return be
    if be == "pymupdf":
        fitz = import_fitz()
        s = fitz.open(src)
        out = fitz.open()
        for i, (w, h) in enumerate(sizes):
            tw, th, _ = target_size(w, h)
            pg = out.new_page(width=tw, height=th)
            pg.show_pdf_page(pg.rect, s, i)
        out.save(dst)
        return be
    if be == "ghostscript":
        with tempfile.TemporaryDirectory() as d:
            parts = []
            for i, (w, h) in enumerate(sizes, 1):
                tw, th, _ = target_size(w, h)
                part = os.path.join(d, "s%04d.pdf" % i)
                subprocess.run(["gs", "-q", "-dNOPAUSE", "-dBATCH", "-sDEVICE=pdfwrite", "-dFIXEDMEDIA", "-dPDFFitPage",
                                "-dDEVICEWIDTHPOINTS=%s" % _fmt(tw), "-dDEVICEHEIGHTPOINTS=%s" % _fmt(th),
                                "-dFirstPage=%d" % i, "-dLastPage=%d" % i, "-sOutputFile=" + part, src],
                               check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                parts.append(part)
            merge_pdfs(parts, dst)
        return be
    return None


def merge_pdfs(files, dst):
    """Łączy pliki PDF w jeden. Zwraca nazwę użytego backendu."""
    if have_module("pypdf"):
        from pypdf import PdfWriter
        wr = PdfWriter()
        for f in files:
            wr.append(f)
        with open(dst, "wb") as fh:
            wr.write(fh)
        return "pypdf"
    fitz = import_fitz()
    if fitz is not None:
        out = fitz.open()
        for f in files:
            out.insert_pdf(fitz.open(f))
        out.save(dst)
        return "pymupdf"
    if which("pdfunite"):
        subprocess.run(["pdfunite"] + list(files) + [dst], check=True)
        return "pdfunite"
    if which("gs"):
        subprocess.run(["gs", "-q", "-dNOPAUSE", "-dBATCH", "-sDEVICE=pdfwrite", "-sOutputFile=" + dst] + list(files),
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        return "ghostscript"
    shutil.copyfile(files[0], dst)
    return None
