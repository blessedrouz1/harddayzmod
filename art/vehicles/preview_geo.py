"""Software preview of an INSTALLED launcher geo, posed like LauncherRenderer / HydraulicLift (no Blockbench needed).

usage: python3 preview_geo.py NAME out.png [elev=0] [arm=0..26] [view=side|front|back|top] [zoom=1] [cx cy cz]
Mirrors GeoModel (x negated, ZYX rotations, per-face UV) and the lid / missile / ram animation of LauncherRenderer.
"""
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ASSETS = Path(__file__).resolve().parent.parent.parent / 'src/main/resources/assets/harddayz'
ARM_TICKS = 26


def rot_zyx(x, y, z):
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


def mat(pos, rot):
    m = np.eye(4)
    m[:3, :3] = rot_zyx(*rot)
    m[:3, 3] = pos
    return m


class Bone:
    def __init__(self, d):
        self.name = d['name']
        self.parent = d.get('parent')
        p = d.get('pivot', [0, 0, 0])
        self.pivot = np.array([-p[0], p[1], p[2]], float)
        r = d.get('rotation', [0, 0, 0])
        self.rot0 = np.array([math.radians(-r[0]), math.radians(-r[1]), math.radians(r[2])])
        self.cubes = d.get('cubes', [])
        self.pos = np.zeros(3)      # offset added by animation (px)
        self.rot = np.zeros(3)      # rotation added by animation (rad)
        self.visible = True


def faces_uv(cube):
    uv, s = cube['uv'], cube['size']
    out = {}
    if isinstance(uv, dict):
        for k, f in uv.items():
            u, v = f['uv']
            w, h = f.get('uv_size', [0, 0])
            u0, v0, u1, v1 = u, v, u + w, v + h
            out[k] = (u1, v1, u0, v0) if k in ('up', 'down') else (u0, v0, u1, v1)
    else:
        u, v = uv
        w, h, d = (math.floor(x) for x in s)
        out = {'east': (u, v + d, u + d, v + d + h), 'north': (u + d, v + d, u + d + w, v + d + h), 'west': (u + d + w, v + d, u + 2 * d + w, v + d + h),
               'south': (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h), 'up': (u + d + w, v + d, u + d, v), 'down': (u + d + 2 * w, v, u + d + w, v + d)}
    return out


class Model:
    def __init__(self, name):
        g = json.loads((ASSETS / f'geo/{name}.geo.json').read_text())['minecraft:geometry'][0]
        self.tw, self.th = g['description']['texture_width'], g['description']['texture_height']
        self.bones = {b['name']: Bone(b) for b in g['bones']}
        self.tex = np.array(Image.open(ASSETS / f'textures/entity/{name}.png').convert('RGBA'))

    def chain(self, name):
        c = []
        while name:
            c.append(name)
            name = self.bones[name].parent if self.bones[name].parent in self.bones else None
        return c[::-1]

    def bone_matrix(self, name, upto_parent=False):
        """Matrix taking the bone's local px frame (origin at its pivot) to model px space."""
        m = np.eye(4)
        prev = np.zeros(3)
        ch = self.chain(name)
        for n in (ch[:-1] if upto_parent else ch):
            b = self.bones[n]
            m = m @ mat(b.pivot - prev + b.pos, b.rot0 + b.rot)
            prev = b.pivot
        return m

    def reset(self):
        for b in self.bones.values():
            b.pos = np.zeros(3)
            b.rot = np.zeros(3)
            b.visible = True

    # ---- the animation of LauncherRenderer.GeoLauncherModel.setupAnim for rack / lifts
    def lifts(self):
        for n in self.bones:
            if n.endswith('_mount') and n[:-6] + '_stage' in self.bones and n[:-6] + '_rod' in self.bones:
                name = n[:-6]
                b, st, rd = self.bones[name], self.bones[name + '_stage'], self.bones[name + '_rod']
                parent = b.parent
                target = self.bone_matrix(n)[:3, 3]
                base = self.bone_matrix(parent) if parent else np.eye(4)
                delta = (np.linalg.inv(base) @ np.append(target, 1))[:3] - (b.pivot - self.bones[parent].pivot if parent else b.pivot) - b.pos
                rest = np.linalg.norm(self.bones[n].pivot - b.pivot)
                ln = np.linalg.norm(delta)
                b.rot[0] = math.asin(max(-1, min(1, delta[1] / ln)))
                b.rot[1] = math.atan2(-delta[0], -delta[2])
                stroke = (ln - rest) / 2
                st.pos[2] -= stroke
                rd.pos[2] -= stroke

    def triangles(self):
        """(verts[4x3 model px], uv[4x2 texels]) for every visible face."""
        quads = []
        for name, b in self.bones.items():
            vis = all(self.bones[n].visible for n in self.chain(name))
            if not vis:
                continue
            M = self.bone_matrix(name)
            for cube in b.cubes:
                o, s = cube['origin'], cube['size']
                mn = np.array([-(o[0] + s[0]), o[1], o[2]], float)
                mx = mn + np.array(s, float)
                C = M
                if cube.get('rotation') and any(cube['rotation']):
                    r = cube['rotation']
                    cp = np.array(cube['pivot'], float)
                    cp[0] = -cp[0]
                    off = cp - b.pivot
                    C = M @ mat(off, (math.radians(-r[0]), math.radians(-r[1]), math.radians(r[2])))
                    mn, mx = mn - cp, mx - cp
                else:
                    mn, mx = mn - b.pivot, mx - b.pivot
                x0, y0, z0 = mn
                x1, y1, z1 = mx
                fq = {'north': [(x1, y1, z0), (x1, y0, z0), (x0, y0, z0), (x0, y1, z0)], 'south': [(x0, y1, z1), (x0, y0, z1), (x1, y0, z1), (x1, y1, z1)],
                      'east': [(x1, y1, z1), (x1, y0, z1), (x1, y0, z0), (x1, y1, z0)], 'west': [(x0, y1, z0), (x0, y0, z0), (x0, y0, z1), (x0, y1, z1)],
                      'up': [(x0, y1, z0), (x0, y1, z1), (x1, y1, z1), (x1, y1, z0)], 'down': [(x0, y0, z1), (x0, y0, z0), (x1, y0, z0), (x1, y0, z1)]}
                for k, (u0, v0, u1, v1) in faces_uv(cube).items():
                    v4 = np.array([(C @ np.array([*p, 1.0]))[:3] for p in fq[k]])
                    quads.append((v4, np.array([[u0, v0], [u0, v1], [u1, v1], [u1, v0]], float)))
        return quads


def render(model, out, yaw, pitch, dist, target, size=(900, 700), fov=45):
    W, H = size
    img = np.zeros((H, W, 4), np.uint8)
    img[:] = (205, 214, 224, 255)
    zbuf = np.full((H, W), 1e18)
    cy_, sy_ = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp_, sp_ = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    eye = np.array(target) + dist * np.array([sy_ * cp_, sp_, cy_ * cp_])
    fwd = np.array(target) - eye
    fwd /= np.linalg.norm(fwd)
    right = np.cross(fwd, [0, 1, 0])
    right /= np.linalg.norm(right)
    up = np.cross(right, fwd)
    f = 0.5 * H / math.tan(math.radians(fov) / 2)

    def proj(p):
        d = p - eye
        z = d @ fwd
        return np.array([W / 2 + f * (d @ right) / z, H / 2 - f * (d @ up) / z, z])

    tex = model.tex
    th, tw = tex.shape[:2]
    for v4, uv in model.triangles():
        P = [proj(p) for p in v4]
        if min(p[2] for p in P) <= 0.1:
            continue
        for tri in ((0, 1, 2), (0, 2, 3)):
            a, b, c = (P[i] for i in tri)
            ua, ub, uc = (uv[i] for i in tri)
            x0, x1 = int(max(0, math.floor(min(a[0], b[0], c[0])))), int(min(W - 1, math.ceil(max(a[0], b[0], c[0]))))
            y0, y1 = int(max(0, math.floor(min(a[1], b[1], c[1])))), int(min(H - 1, math.ceil(max(a[1], b[1], c[1]))))
            if x1 < x0 or y1 < y0:
                continue
            den = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
            if abs(den) < 1e-9:
                continue
            xs, ys = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            w0 = ((b[1] - c[1]) * (xs - c[0]) + (c[0] - b[0]) * (ys - c[1])) / den
            w1 = ((c[1] - a[1]) * (xs - c[0]) + (a[0] - c[0]) * (ys - c[1])) / den
            w2 = 1 - w0 - w1
            m = (w0 >= 0) & (w1 >= 0) & (w2 >= 0)
            if not m.any():
                continue
            iz = w0 / a[2] + w1 / b[2] + w2 / c[2]          # perspective-correct
            z = 1 / np.where(iz == 0, 1e-9, iz)
            u = (w0 * ua[0] / a[2] + w1 * ub[0] / b[2] + w2 * uc[0] / c[2]) * z
            v = (w0 * ua[1] / a[2] + w1 * ub[1] / b[2] + w2 * uc[1] / c[2]) * z
            ui = np.clip(np.floor(u).astype(int), 0, tw - 1)
            vi = np.clip(np.floor(v).astype(int), 0, th - 1)
            col = tex[vi, ui]
            sub = zbuf[y0:y1 + 1, x0:x1 + 1]
            ok = m & (z < sub) & (col[..., 3] > 127)
            sub[ok] = z[ok]
            img[y0:y1 + 1, x0:x1 + 1][ok] = col[ok]
    Image.fromarray(img[..., :3]).save(out)


def quat_from_matrix(R):
    t = R[0, 0] + R[1, 1] + R[2, 2]
    if t > 0:
        r = math.sqrt(1 + t); w = 0.5 * r; r = 0.5 / r
        return np.array([w, (R[2, 1] - R[1, 2]) * r, (R[0, 2] - R[2, 0]) * r, (R[1, 0] - R[0, 1]) * r])
    i = int(np.argmax([R[0, 0], R[1, 1], R[2, 2]])); j, k = (i + 1) % 3, (i + 2) % 3
    r = math.sqrt(1 + R[i, i] - R[j, j] - R[k, k]); q = np.zeros(4)
    q[1 + i] = 0.5 * r; r = 0.5 / r
    q[0] = (R[k, j] - R[j, k]) * r; q[1 + j] = (R[j, i] + R[i, j]) * r; q[1 + k] = (R[k, i] + R[i, k]) * r
    return q


def qmul(a, b):
    w1, x1, y1, z1 = a; w2, x2, y2, z2 = b
    return np.array([w1*w2-x1*x2-y1*y2-z1*z2, w1*x2+x1*w2+y1*z2-z1*y2, w1*y2-x1*z2+y1*w2+z1*x2, w1*z2+x1*y2-y1*x2+z1*w2])


def qaxis(ang, ax):
    ax = np.array(ax, float); ax /= np.linalg.norm(ax)
    return np.concatenate([[math.cos(ang / 2)], ax * math.sin(ang / 2)])


def qmat(q):
    w, x, y, z = q / np.linalg.norm(q)
    return np.array([[1-2*(y*y+z*z), 2*(x*y-z*w), 2*(x*z+y*w)], [2*(x*y+z*w), 1-2*(x*x+z*z), 2*(y*z-x*w)], [2*(x*z-y*w), 2*(y*z+x*w), 1-2*(x*x+y*y)]])


def slerp(a, b, t):
    d = float(a @ b)
    if d < 0: b, d = -b, -d
    if d > 0.9995:
        r = a + t * (b - a); return r / np.linalg.norm(r)
    th = math.acos(d)
    return (math.sin((1 - t) * th) * a + math.sin(t * th) * b) / math.sin(th)


def lid_pose(model, bone, idx, age):
    """Same maths as LauncherRenderer.animateLid (px instead of blocks)."""
    b = model.bones[bone]
    g, ground = 0.045 * 16, 0.03 * 16
    mp = model.bone_matrix(b.parent)
    rest = b.pivot - model.bones[b.parent].pivot
    d0 = mp @ mat(rest, (0, 0, 0))
    p0, q0 = d0[:3, 3].copy(), quat_from_matrix(d0[:3, :3])
    d = 1 if idx % 2 == 0 else -1
    v = mp[:3, :3] @ np.array([d * 0.10 * 16, 0.04 * 16, -0.42 * 16])
    h = p0[1] - ground
    t1 = 0 if h <= 0 else (v[1] + math.sqrt(v[1] ** 2 + 2 * g * h)) / g
    axis = (1, 0.4 * d, 0.6); spin = 0.35
    if age <= t1:
        pos = p0 + v * age; pos[1] = p0[1] + v[1] * age - 0.5 * g * age * age
        q = qmul(qaxis(spin * age, axis), q0)
    else:
        hx, hz = p0[0] + v[0] * t1, p0[2] + v[2] * t1
        vhit = v[1] - g * t1; vb = max(0.05 * 16, -vhit * 0.3); t2 = 2 * vb / g
        u = age - t1; uc = min(u, t2)
        y = ground + vb * u - 0.5 * g * u * u if u < t2 else ground
        pos = np.array([hx + v[0] * 0.35 * uc, y, hz + v[2] * 0.35 * uc])
        qhit = qmul(qaxis(spin * t1, axis), q0)
        flat = qmul(qaxis(idx * 1.1, (0, 1, 0)), qaxis(math.pi / 2, (1, 0, 0)))
        q = slerp(qhit, flat, min(1, u / 5))
    D = np.eye(4); D[:3, :3] = qmat(q); D[:3, 3] = pos
    L = np.linalg.inv(mp) @ D
    R = L[:3, :3]
    b.pos = L[:3, 3].copy() - rest + 0     # bone_matrix adds (pivot - prev) + pos
    b.rot = np.array([math.atan2(R[2, 1], R[2, 2]), -math.asin(max(-1, min(1, R[2, 0]))), math.atan2(R[1, 0], R[0, 0])]) - b.rot0
    return t1


def pose(model, elev=0.0, arm=0.0, ammo=None):
    """LauncherRenderer.setupAnim: rack elevation, lifts, and the container lid / missile sequence (arm = ticks since the tube opened)."""
    model.reset()
    swing = 'erector' if 'erector' in model.bones else 'rack'
    if swing in model.bones:
        model.bones[swing].rot[0] += math.radians(elev)
    model.lifts()
    msl = sorted(n for n in model.bones if n.startswith('msl') and len(n) == 4)
    if not msl:
        return model
    n_t = len(msl)
    fired = arm > 26                       # missile left, ammo dropped by one
    ammo = n_t - 1 if fired else n_t
    last = n_t - 1
    for n, b in model.bones.items():
        at = n.rfind('cap')
        if at >= 0 and at + 3 < len(n) and n[at + 3].isdigit():
            idx = int(n[at + 3])
            if idx == last and arm > 0:
                b.visible = arm < 260
                if arm < 260:
                    lid_pose(model, n, idx, arm)
        elif n in msl:
            idx = int(n[3])
            b.visible = idx < ammo
            if b.visible and idx == ammo - 1 and arm > 6:
                zs = [c['origin'][2] for c in b.cubes] + [c['origin'][2] + c['size'][2] for c in b.cubes]
                t = min(1, (arm - 6) / 16)
                b.pos[2] -= (max(zs) - min(zs)) * 0.5 * (t * t * (3 - 2 * t))
    return model


if __name__ == '__main__':
    name, out = sys.argv[1], sys.argv[2]
    elev = float(sys.argv[3]) if len(sys.argv) > 3 else 0
    arm = float(sys.argv[4]) if len(sys.argv) > 4 else 0
    view = sys.argv[5] if len(sys.argv) > 5 else 'side'
    zoom = float(sys.argv[6]) if len(sys.argv) > 6 else 1
    m = Model(name)
    m.tex = np.vstack([m.tex, np.tile(np.array([[120, 126, 112, 255]], np.uint8), (2, m.tex.shape[1], 1))])
    m.bones['ground'] = Bone({'name': 'ground', 'pivot': [0, 0, 0], 'cubes': [{'origin': [-140, -0.3, -200], 'size': [280, 0.3, 400], 'uv': {f: {'uv': [4, m.tex.shape[0] - 2], 'uv_size': [1, 1]} for f in ('north', 'south', 'east', 'west', 'up', 'down')}}]})
    pose(m, elev, arm)
    yaw, pitch = {'side': (90, 12), 'front': (0, 12), 'back': (180, 12), 'top': (90, 70), 'q': (35, 18)}.get(view, (35, 18))
    tgt = [float(a) for a in sys.argv[7:10]] if len(sys.argv) >= 10 else [0, 50, 0]
    render(m, out, yaw, pitch, 260 / zoom, tgt)
    print('wrote', out)
