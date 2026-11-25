package com.harddayz.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.minecraft.util.Mth;

/**
 * План фронтовой карты по сиду: базы, город, сёла, точки, река, дороги, рельеф. Только математика, без блоков.
 * Точки и базы стоят симметрично относительно центра, чтобы у сторон были равные условия.
 */
public final class FrontMap {
    /** Половина стороны карты. */
    public static final int HALF = 1800;
    public static final int WATER = 63;
    public static final double RIVER_HALF = 14;

    public enum Type {
        BASE, CITY, VILLAGE, POWER, OIL, AIRFIELD, TOWER
    }

    /** Место: центр, полуразмер ровной площадки, высота земли (верхний блок). */
    public record Site(Type type, String name, int x, int z, int radius, int blend, int y) {
        /** Расстояние с учётом формы: у города и сёл граница неровная, у площадок — скруглённый квадрат. */
        public double dist(double px, double pz) {
            double dx = px - x, dz = pz - z;
            if (type == Type.VILLAGE || type == Type.CITY) {
                return Math.hypot(dx, dz) / outline(Math.atan2(dz, dx));
            }
            return Math.sqrt(Math.sqrt(dx * dx * dx * dx + dz * dz * dz * dz));
        }

        /** Множитель радиуса в направлении t. */
        public double outline(double t) {
            double a = x * 0.37 + z * 0.91 + y, b = x * 1.13 - z * 0.53 + y * 3.1, c = x * 0.71 + z * 0.29 + y * 7.7;
            return 1 + 0.13 * Math.sin(2 * t + a) + 0.1 * Math.sin(3 * t + b) + 0.06 * Math.sin(5 * t + c);
        }
    }

    public enum Zone {
        CENTER("Центр", 0xB89A70), OLD("Старый город", 0xA87858), SLEEP("Спальный район", 0x8E9CA8), INDUSTRY("Промзона", 0x8A7F8C),
        STATION("Вокзальный", 0x9A8468), PRIVATE("Частный сектор", 0x8FA868), RIVERSIDE("Заречье", 0x88A098);

        public final String title;
        public final int color;

        Zone(String title, int color) {
            this.title = title;
            this.color = color;
        }
    }

    /** Район города: центр и вид застройки. */
    public record District(Zone zone, double x, double z) {
    }

    public final List<District> districts = new ArrayList<>();

    /** Улица: ось с изгибом. river — набережная, повторяет русло. */
    private record Street(double at, double amp, double len, double phase, int river) {
    }

    private final List<Street> vertical = new ArrayList<>();
    private final List<Street> horizontal = new ArrayList<>();

    /** Расстояния до осей ближайших улиц обоих направлений. */
    public static final class StreetHit {
        public double v = 1.0E9;
        public double h = 1.0E9;
        public int hi;
    }

    /** Дорога: ломаная x, z, высота полотна. */
    public static final class Road {
        final double[] x;
        final double[] z;
        final double[] y;

        Road(int n) {
            x = new double[n];
            z = new double[n];
            y = new double[n];
        }
    }

    /** Ближайшая дорога к столбцу: расстояние до оси, высота полотна, путь вдоль дороги. */
    public static final class RoadHit {
        public double d = 1.0E9;
        public double y;
        public double along;
    }

    private record Seg(Road r, int i, double start) {
    }

    private static final String[] VILLAGES = {"Каменка", "Липовка", "Степное", "Заречье", "Вишневое", "Роща", "Криница", "Подгорное", "Ивановка", "Сосновка"};

    public final long seed;
    public final List<Site> sites = new ArrayList<>();
    /** Линия надолбов перед окопами: 1 — здесь стоит надолб. */
    public boolean teeth(int x, int z) {
        if (Math.abs(z) > 850 || Math.floorMod(z, 3) != 0 || noise(0, z, 130, 80) < 0.5) {
            return false;
        }
        for (int sign = -1; sign <= 1; sign += 2) {
            int w = (int) (sign * 340 + (noise(0, z, 200, 81 + sign) - 0.5) * 60);
            if (x == w || x == w + 3 && Math.floorMod(z, 2) == 0) {
                return true;
            }
        }
        return false;
    }

    public final List<Road> roads = new ArrayList<>();
    private final Map<Long, List<Seg>> segs = new HashMap<>();
    private final Map<Long, List<Seg>> railSegs = new HashMap<>();
    public Road rail;
    private final double riverA;
    private final double riverB;
    private final double riverP;
    public Site west;
    public Site east;
    public Site city;

    public FrontMap(long seed) {
        this.seed = seed;
        Random r = new Random(seed * 31 + 7);
        riverA = (90 + r.nextDouble() * 110) * (r.nextBoolean() ? 1 : -1);
        riverB = 25 + r.nextDouble() * 30;
        riverP = r.nextDouble() * 6;
        plan(r);
    }

    // ------------------------------------------------------------------ места

    private Site site(Type t, String name, int x, int z, int radius, int blend) {
        int y = (int) Math.round(Mth.clamp(base(x, z), t == Type.CITY ? 70 : 68, t == Type.CITY ? 74 : 84));
        Site s = new Site(t, name, x, z, radius, blend, y);
        sites.add(s);
        return s;
    }

    private boolean free(double x, double z, int radius) {
        for (Site s : sites) {
            if (Math.hypot(s.x - x, s.z - z) < (s.radius + radius) * 1.2 + 120) {
                return false;
            }
        }
        return Math.abs(x - riverX(z)) > radius * 1.25 + 70;
    }

    private void plan(Random r) {
        int zb = r.nextInt(801) - 400;
        city = site(Type.CITY, "Город", 0, 0, 232, 70);
        streets(r);
        west = site(Type.BASE, "База «Запад»", -1400, zb, 66, 60);
        east = site(Type.BASE, "База «Восток»", 1400, -zb, 66, 60);
        // на севере и юге по нейтральной точке, на сторону реки их кидает случай
        int side = r.nextBoolean() ? 1 : -1;
        int ax = side * (300 + r.nextInt(150));
        int az = 950 + r.nextInt(250);
        site(Type.AIRFIELD, "Аэродром", ax, -az, 140, 60);
        site(Type.POWER, "Электростанция", -ax, az, 58, 50);
        // зеркальные пары: у каждой стороны своя
        pair(r, Type.OIL, "Нефтебаза", 44, 600, 1050, 1000);
        pair(r, Type.TOWER, "Вышка связи", 22, 450, 900, 1200);
        int n = 4;
        int name = r.nextInt(VILLAGES.length);
        for (int i = 0; i < n; i++) {
            pairNamed(r, VILLAGES[(name + i * 2) % VILLAGES.length], VILLAGES[(name + i * 2 + 1) % VILLAGES.length]);
        }
        roads(r);
        for (Road rd : roads) {
            index(rd, segs);
        }
        railway(r);
        index(rail, railSegs);
    }

    private void pair(Random r, Type t, String name, int radius, int x0, int x1, int zMax) {
        for (int i = 0; i < 300; i++) {
            int x = -(x0 + r.nextInt(x1 - x0)), z = r.nextInt(zMax * 2) - zMax;
            if (free(x, z, radius) && free(-x, -z, radius)) {
                site(t, name + " (запад)", x, z, radius, 40);
                site(t, name + " (восток)", -x, -z, radius, 40);
                return;
            }
        }
    }

    private void pairNamed(Random r, String a, String b) {
        for (int i = 0; i < 300; i++) {
            int x = -(330 + r.nextInt(820)), z = r.nextInt(2600) - 1300;
            if (free(x, z, 85) && free(-x, -z, 85)) {
                site(Type.VILLAGE, a, x, z, 85, 45);
                site(Type.VILLAGE, b, -x, -z, 85, 45);
                return;
            }
        }
    }

    // ------------------------------------------------------------------ город

    private void streets(Random r) {
        for (int side = -1; side <= 1; side += 2) {
            vertical.add(new Street(side * 24, 0, 1, 0, side));
            double at = 24;
            while (at < 300) {
                at += 50 + r.nextInt(20);
                vertical.add(new Street(26 + side * at, 4 + r.nextDouble() * 5, 55 + r.nextDouble() * 60, r.nextDouble() * 6, 0));
            }
        }
        double z = -305 + r.nextInt(20);
        while (z < 305) {
            horizontal.add(new Street(z, 4 + r.nextDouble() * 5, 60 + r.nextDouble() * 70, r.nextDouble() * 6, 0));
            z += 50 + r.nextInt(20);
        }
        vertical.sort(java.util.Comparator.comparingDouble(s -> s.river != 0 ? 26 + s.at : s.at));
        // районы: центр у реки, остальные вокруг по кругу в случайном порядке, промзона и вокзал — к железной дороге на севере
        districts.add(new District(Zone.CENTER, -45, (r.nextDouble() - 0.5) * 40));
        districts.add(new District(Zone.OLD, -130, 40 + r.nextInt(50)));
        districts.add(new District(Zone.STATION, 95, -165));
        districts.add(new District(Zone.INDUSTRY, r.nextBoolean() ? -130 : 175, r.nextBoolean() ? -170 : -150));
        districts.add(new District(Zone.SLEEP, 130, 20 + r.nextInt(50)));
        districts.add(new District(Zone.SLEEP, -60 - r.nextInt(60), 150 + r.nextInt(30)));
        districts.add(new District(Zone.RIVERSIDE, 90, 160));
        districts.add(new District(Zone.PRIVATE, -190, 130 - r.nextInt(220)));
    }

    public double streetX(int k, double z) {
        Street s = vertical.get(k);
        return s.river != 0 ? riverX(z) + s.at : s.at + s.amp * Math.sin(z / s.len + s.phase);
    }

    public double streetZ(int j, double x) {
        Street s = horizontal.get(j);
        return s.at + s.amp * Math.sin(x / s.len + s.phase);
    }

    public int streetsV() {
        return vertical.size();
    }

    public int streetsH() {
        return horizontal.size();
    }

    /** По этой поперечной улице через реку есть мост. */
    public boolean bridge(int j) {
        return j % 2 == 0;
    }

    public StreetHit street(double x, double z) {
        StreetHit h = new StreetHit();
        for (int k = 0; k < vertical.size(); k++) {
            double slope = streetX(k, z + 1) - streetX(k, z);
            h.v = Math.min(h.v, Math.abs(x - streetX(k, z)) / Math.sqrt(1 + slope * slope));
        }
        for (int j = 0; j < horizontal.size(); j++) {
            double slope = streetZ(j, x + 1) - streetZ(j, x);
            double d = Math.abs(z - streetZ(j, x)) / Math.sqrt(1 + slope * slope);
            if (d < h.h) {
                h.h = d;
                h.hi = j;
            }
        }
        return h;
    }

    public boolean inCity(double x, double z) {
        return city.dist(x, z) <= city.radius;
    }

    /** Район в точке (границы слегка извилистые). */
    public District district(double x, double z) {
        double px = x + (noise(x, z, 60, 95) - 0.5) * 34, pz = z + (noise(x, z, 60, 96) - 0.5) * 34;
        District best = null;
        double bd = 1.0E9;
        for (District d : districts) {
            double q = Math.hypot(px - d.x, pz - d.z) + (d.zone == Zone.CENTER ? -18 : 0);
            // река делит районы
            if ((d.x > 26) != (x > riverX(z))) {
                q += 140;
            }
            if (q < bd) {
                bd = q;
                best = d;
            }
        }
        return best;
    }

    /** Территория: место (точка или база), к которому относится земля вокруг. Сёла входят в территорию ближайшей точки. */
    public Site region(double x, double z) {
        double px = x + (noise(x, z, 280, 97) - 0.5) * 260, pz = z + (noise(x, z, 280, 98) - 0.5) * 260;
        Site best = city;
        double bd = 1.0E9;
        for (Site s : sites) {
            if (s.type == Type.VILLAGE) {
                continue;
            }
            double d = Math.hypot(px - s.x, pz - s.z) - (s.type == Type.CITY ? 160 : s.type == Type.BASE ? 60 : 0);
            if (d < bd) {
                bd = d;
                best = s;
            }
        }
        return best;
    }

    /** Концы городских улиц на границе города — сюда приходят дороги. */
    private double[] cityGate(double tx, double tz) {
        double[] best = {0, 0};
        double bd = 1.0E9;
        for (int k = 0; k < vertical.size() * 2 + horizontal.size() * 2; k++) {
            boolean v = k < vertical.size() * 2;
            int i = v ? k / 2 : (k - vertical.size() * 2) / 2;
            int sign = k % 2 == 0 ? 1 : -1;
            double x = 0, z = 0, t = 0;
            for (t = 320; t > 60; t -= 2) {
                x = v ? streetX(i, sign * t) : sign * t;
                z = v ? sign * t : streetZ(i, sign * t);
                if (inCity(x, z)) {
                    break;
                }
            }
            if (t <= 60 || river(x, z) < RIVER_HALF + 14) {
                continue;
            }
            double d = Math.hypot(x - tx, z - tz);
            if (d < bd) {
                bd = d;
                best = new double[] {x, z};
            }
        }
        return best;
    }

    public Site siteAt(double x, double z, double margin) {
        for (Site s : sites) {
            if (s.dist(x, z) <= s.radius + margin) {
                return s;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ дороги

    private void roads(Random r) {
        List<Site> west = new ArrayList<>();
        for (Site s : sites) {
            if (s.type == Type.CITY || s.type == Type.AIRFIELD || s.type == Type.POWER || s.x < 0) {
                west.add(s);
            }
        }
        // кратчайшее дерево западной половины + короткие перемычки, потом зеркально на восток
        List<Site[]> edges = new ArrayList<>();
        List<Site> in = new ArrayList<>(List.of(west.get(0)));
        List<Site> out = new ArrayList<>(west.subList(1, west.size()));
        while (!out.isEmpty()) {
            Site ba = null, bb = null;
            double best = 1.0E9;
            for (Site a : in) {
                for (Site b : out) {
                    double d = Math.hypot(a.x - b.x, a.z - b.z) * (crosses(a, b) ? 2.5 : 1);
                    if (d < best) {
                        best = d;
                        ba = a;
                        bb = b;
                    }
                }
            }
            edges.add(new Site[] {ba, bb});
            in.add(bb);
            out.remove(bb);
        }
        for (Site a : west) {
            Site b = null;
            double best = 800;
            for (Site o : west) {
                double d = Math.hypot(a.x - o.x, a.z - o.z);
                if (o != a && d < best && !has(edges, a, o) && !crosses(a, o)) {
                    best = d;
                    b = o;
                }
            }
            if (b != null && r.nextFloat() < 0.6f) {
                edges.add(new Site[] {a, b});
            }
        }
        for (Site[] e : new ArrayList<>(edges)) {
            Site a = mirror(e[0]), b = mirror(e[1]);
            if (a != null && b != null && a != b && !has(edges, a, b)) {
                edges.add(new Site[] {a, b});
            }
        }
        // две дороги из одного места почти в одну сторону: дальняя отходит от ближней, а не идёт рядом
        for (int pass = 0; pass < 3; pass++) {
            for (int i = 0; i < edges.size(); i++) {
                Site[] e = edges.get(i);
                for (int k = 0; k < 2; k++) {
                    Site a = e[k], b = e[1 - k];
                    for (Site[] o : edges) {
                        Site c = o == e ? null : o[0] == a ? o[1] : o[1] == a ? o[0] : null;
                        if (c == null || c == b || Math.hypot(b.x - a.x, b.z - a.z) < Math.hypot(c.x - a.x, c.z - a.z)) {
                            continue;
                        }
                        double diff = Math.atan2(b.z - a.z, b.x - a.x) - Math.atan2(c.z - a.z, c.x - a.x);
                        diff = Math.abs(Math.atan2(Math.sin(diff), Math.cos(diff)));
                        if (diff < 0.42) {
                            e[k] = c;
                            a = c;
                        }
                    }
                }
            }
            List<Site[]> uniq = new ArrayList<>();
            for (Site[] e : edges) {
                if (e[0] != e[1] && !has(uniq, e[0], e[1])) {
                    uniq.add(e);
                }
            }
            edges = uniq;
        }
        for (Site[] e : edges) {
            road(e[0], e[1], r);
        }
    }

    /** Прямая между местами проходит через третье. */
    private boolean crosses(Site a, Site b) {
        double dx = b.x - a.x, dz = b.z - a.z, l2 = dx * dx + dz * dz;
        for (Site s : sites) {
            if (s == a || s == b) {
                continue;
            }
            double t = Mth.clamp(((s.x - a.x) * dx + (s.z - a.z) * dz) / l2, 0, 1);
            if (Math.hypot(a.x + dx * t - s.x, a.z + dz * t - s.z) < s.radius * 1.2 + 25) {
                return true;
            }
        }
        return false;
    }

    private Site mirror(Site s) {
        if (s.type == Type.CITY || s.type == Type.AIRFIELD || s.type == Type.POWER) {
            return s;
        }
        for (Site o : sites) {
            if (o.type == s.type && o.x == -s.x && o.z == -s.z) {
                return o;
            }
        }
        return null;
    }

    private static boolean has(List<Site[]> edges, Site a, Site b) {
        for (Site[] e : edges) {
            if (e[0] == a && e[1] == b || e[0] == b && e[1] == a) {
                return true;
            }
        }
        return false;
    }

    /** Откуда дорога входит в место: ворота города по его улицам, середина стороны у площадок, центр у села. */
    private double[] gate(Site s, double tx, double tz) {
        if (s.type == Type.VILLAGE) {
            return new double[] {s.x, s.z};
        }
        if (s.type == Type.CITY) {
            return cityGate(tx, tz);
        }
        double dx = tx - s.x, dz = tz - s.z;
        double r = s.type == Type.CITY ? 208 : s.radius - 8;
        if (s.type == Type.BASE) { // ворота базы всегда смотрят на фронт
            return new double[] {s.x + Math.signum(-s.x) * (s.radius - 4), s.z};
        }
        if (s.type == Type.AIRFIELD) { // въезд со стороны ангаров, не через полосу
            return new double[] {s.x, s.z + Math.signum(-s.z) * 60};
        }
        double off = s.type == Type.CITY ? 104 * Math.signum(Math.abs(dx) > Math.abs(dz) ? dz : dx) : 0;
        if (Math.abs(dx) > Math.abs(dz)) {
            return new double[] {s.x + Math.signum(dx) * r, s.z + (Math.abs(dz) > 100 ? off : 0)};
        }
        return new double[] {s.x + (Math.abs(dx) > 100 || s.type == Type.CITY ? off : 0), s.z + Math.signum(dz) * r};
    }

    private void road(Site a, Site b, Random r) {
        double[] p = gate(a, b.x, b.z), q = gate(b, a.x, a.z);
        double len = Math.hypot(q[0] - p[0], q[1] - p[1]);
        int n = Math.max(3, (int) (len / 24) + 1);
        Road rd = new Road(n);
        double nx = -(q[1] - p[1]) / len, nz = (q[0] - p[0]) / len;
        double a1 = (r.nextDouble() - 0.5) * Math.min(120, len * 0.16), a2 = (r.nextDouble() - 0.5) * Math.min(70, len * 0.1);
        for (int i = 0; i < n; i++) {
            double t = i / (double) (n - 1);
            double off = a1 * Math.sin(Math.PI * t) + a2 * Math.sin(2 * Math.PI * t);
            rd.x[i] = Mth.lerp(t, p[0], q[0]) + nx * off;
            rd.z[i] = Mth.lerp(t, p[1], q[1]) + nz * off;
            // объезд чужих площадок
            for (int k = 0; k < 3; k++) {
                for (Site s : sites) {
                    if (s == a || s == b || s.type == Type.VILLAGE) {
                        continue;
                    }
                    double d = Math.hypot(rd.x[i] - s.x, rd.z[i] - s.z), need = s.radius * 1.3 + 12;
                    if (d < need && d > 1) {
                        rd.x[i] += (rd.x[i] - s.x) / d * (need - d);
                        rd.z[i] += (rd.z[i] - s.z) / d * (need - d);
                    }
                }
            }
        }
        for (int i = 0; i < n; i++) {
            rd.y[i] = ground(rd.x[i], rd.z[i]);
        }
        for (int pass = 0; pass < 8; pass++) {
            for (int i = 0; i < n; i++) {
                if (i > 0 && i < n - 1) {
                    rd.y[i] = (rd.y[i - 1] + rd.y[i] * 2 + rd.y[i + 1]) / 4;
                }
                Site s = siteAt(rd.x[i], rd.z[i], 2);
                if (s != null) {
                    rd.y[i] = s.y;
                }
                if (Math.abs(rd.x[i] - riverX(rd.z[i])) < 70) {
                    rd.y[i] = Math.max(rd.y[i], 69);
                }
            }
        }
        roads.add(rd);
    }

    /** Железная дорога с запада на восток мимо северной окраины города. */
    private void railway(Random r) {
        int n = FrontMap.HALF * 2 / 20 + 1;
        rail = new Road(n);
        double a = 60 + r.nextDouble() * 120, ph = r.nextDouble() * 6;
        for (int i = 0; i < n; i++) {
            double x = -HALF + i * 20;
            double k = smooth((Math.abs(x) - 250) / 350);
            rail.x[i] = x;
            rail.z[i] = city.z - 328 - k * (40 + a * (1 + Math.sin(x / 330 + ph)));
        }
        for (int pass = 0; pass < 6; pass++) { // объезд площадок плавной дугой
            for (int i = 0; i < n; i++) {
                for (Site s : sites) {
                    double d = Math.hypot(rail.x[i] - s.x, rail.z[i] - s.z), need = s.radius + 40;
                    if (s.type != Type.CITY && d < need) {
                        rail.z[i] += (rail.z[i] >= s.z ? 1 : -1) * (need - d);
                    }
                }
            }
            for (int i = 1; i < n - 1; i++) {
                if (Math.abs(rail.x[i]) > 240) {
                    rail.z[i] = (rail.z[i - 1] + 2 * rail.z[i] + rail.z[i + 1]) / 4;
                }
            }
        }
        for (int i = 0; i < n; i++) {
            rail.y[i] = ground(rail.x[i], rail.z[i]);
        }
        for (int pass = 0; pass < 40; pass++) {
            for (int i = 0; i < n; i++) {
                if (i > 0 && i < n - 1) {
                    rail.y[i] = (rail.y[i - 1] + rail.y[i] * 2 + rail.y[i + 1]) / 4;
                }
                if (Math.abs(rail.x[i]) < 230) {
                    rail.y[i] = city.y;
                } else if (Math.abs(rail.x[i] - riverX(rail.z[i])) < 70) {
                    rail.y[i] = Math.max(rail.y[i], 70);
                }
            }
        }
    }

    /** Ось пути в столбце x. */
    public double railZ(double x) {
        double t = Mth.clamp((x + HALF) / 20, 0, rail.x.length - 1.001);
        int i = (int) t;
        return Mth.lerp(t - i, rail.z[i], rail.z[i + 1]);
    }

    public RoadHit rail(double x, double z) {
        return nearest(railSegs, x, z);
    }

    private void index(Road rd, Map<Long, List<Seg>> segs) {
        double start = 0;
        for (int i = 0; i + 1 < rd.x.length; i++) {
            Seg s = new Seg(rd, i, start);
            start += Math.hypot(rd.x[i + 1] - rd.x[i], rd.z[i + 1] - rd.z[i]);
            int x0 = Mth.floor(Math.min(rd.x[i], rd.x[i + 1]) - 24) >> 6, x1 = Mth.floor(Math.max(rd.x[i], rd.x[i + 1]) + 24) >> 6;
            int z0 = Mth.floor(Math.min(rd.z[i], rd.z[i + 1]) - 24) >> 6, z1 = Mth.floor(Math.max(rd.z[i], rd.z[i + 1]) + 24) >> 6;
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    segs.computeIfAbsent(key(x, z), k -> new ArrayList<>()).add(s);
                }
            }
        }
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    /** Ближайшая дорога (точно в пределах ~24 блоков). */
    public RoadHit road(double x, double z) {
        return nearest(segs, x, z);
    }

    private static RoadHit nearest(Map<Long, List<Seg>> segs, double x, double z) {
        RoadHit h = new RoadHit();
        List<Seg> list = segs.get(key(Mth.floor(x) >> 6, Mth.floor(z) >> 6));
        if (list == null) {
            return h;
        }
        for (Seg s : list) {
            double x0 = s.r.x[s.i], z0 = s.r.z[s.i], dx = s.r.x[s.i + 1] - x0, dz = s.r.z[s.i + 1] - z0;
            double l2 = dx * dx + dz * dz;
            double t = l2 < 1.0E-6 ? 0 : Mth.clamp(((x - x0) * dx + (z - z0) * dz) / l2, 0, 1);
            double d = Math.hypot(x - x0 - dx * t, z - z0 - dz * t);
            if (d < h.d) {
                h.d = d;
                h.y = Mth.lerp(t, s.r.y[s.i], s.r.y[s.i + 1]);
                h.along = s.start + Math.sqrt(l2) * t;
            }
        }
        return h;
    }

    // ------------------------------------------------------------------ рельеф

    public double riverX(double z) {
        double k = smooth((Math.abs(z) - 320) / 260); // в городе русло спокойное, между набережными
        return 26 + 11 * Math.sin(z / 95 + riverP) * (1 - k) + k * (riverA * Math.sin(z / 430) + riverB * Math.sin(z / 170 + riverP));
    }

    /** Расстояние до оси реки по перпендикуляру. */
    public double river(double x, double z) {
        double slope = (riverX(z + 2) - riverX(z - 2)) / 4;
        return Math.abs(x - riverX(z)) / Math.sqrt(1 + slope * slope);
    }

    /** Холмы без площадок, реки и дорог. */
    public double base(double x, double z) {
        double h = 74 + (noise(x, z, 430, 1) - 0.5) * 30 + (noise(x, z, 190, 2) - 0.5) * 12 + (noise(x, z, 60, 3) - 0.5) * 3;
        double edge = Math.max(Math.abs(x), Math.abs(z)) - (HALF - 60);
        return Math.max(67, h) + (edge > 0 ? edge * 0.25 : 0); // у края карты холмы прикрывают границу
    }

    /** С площадками. */
    public double ground(double x, double z) {
        double h = base(x, z);
        for (Site s : sites) {
            double d = s.dist(x, z) - s.radius;
            if (d < s.blend) {
                h = Mth.lerp(smooth(d / s.blend), s.y, h);
            }
        }
        return h;
    }

    /** Итоговая высота земли: площадки, дороги, русло. */
    public double height(double x, double z, RoadHit road) {
        double h = ground(x, z);
        RoadHit rail = rail(x, z);
        if (rail.d < 20) {
            h = Mth.lerp(smooth((rail.d - 3.5) / 16), rail.y, h);
        }
        if (road.d < 22) {
            h = Mth.lerp(smooth((road.d - 4.5) / 17), road.y, h);
        }
        double r = river(x, z);
        double bank = city.dist(x, z) < city.radius ? 1.5 : 30;
        if (r < RIVER_HALF) {
            h = 57 + 5 * (r / RIVER_HALF) * (r / RIVER_HALF);
        } else if (r < RIVER_HALF + bank) {
            h = Math.min(h, Mth.lerp(smooth((r - RIVER_HALF) / bank), 62, h));
        }
        return h;
    }

    // ------------------------------------------------------------------ шум

    public static double smooth(double t) {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    public double hash(int x, int z, int salt) {
        long h = seed ^ x * 341873128712L ^ z * 132897987541L ^ salt * 42317861L;
        h = (h ^ h >>> 33) * 0xff51afd7ed558ccdL;
        h = (h ^ h >>> 33) * 0xc4ceb9fe1a85ec53L;
        return ((h ^ h >>> 33) >>> 11) / (double) (1L << 53);
    }

    public double noise(double x, double z, double scale, int salt) {
        x /= scale;
        z /= scale;
        int ix = Mth.floor(x), iz = Mth.floor(z);
        double fx = smooth(x - ix), fz = smooth(z - iz);
        return Mth.lerp(fz, Mth.lerp(fx, hash(ix, iz, salt), hash(ix + 1, iz, salt)), Mth.lerp(fx, hash(ix, iz + 1, salt), hash(ix + 1, iz + 1, salt)));
    }

    // ------------------------------------------------------------------ поля и окопы

    /** Поле в столбце: id поля и расстояние до межи. */
    public static final class Field {
        public int cx;
        public int cz;
        public int nx;
        public int nz;
        public double edge;
    }

    public Field field(double x, double z) {
        double s = 115;
        int gx = Mth.floor(x / s), gz = Mth.floor(z / s);
        Field f = new Field();
        double d1 = 1.0E9, d2 = 1.0E9;
        for (int i = gx - 1; i <= gx + 1; i++) {
            for (int j = gz - 1; j <= gz + 1; j++) {
                double px = (i + 0.2 + 0.6 * hash(i, j, 11)) * s, pz = (j + 0.2 + 0.6 * hash(i, j, 12)) * s;
                double d = Math.hypot(x - px, z - pz);
                if (d < d1) {
                    d2 = d1;
                    f.nx = f.cx;
                    f.nz = f.cz;
                    d1 = d;
                    f.cx = i;
                    f.cz = j;
                } else if (d < d2) {
                    d2 = d;
                    f.nx = i;
                    f.nz = j;
                }
            }
        }
        f.edge = (d2 - d1) / 2;
        return f;
    }

    /** Расстояние до линии окопов (по одной с каждой стороны, зигзаг с разрывами), 1e9 — нет. */
    public double trench(double x, double z) {
        if (Math.abs(z) > 900) {
            return 1.0E9;
        }
        double best = 1.0E9;
        for (int line = 0; line < 4; line++) {
            double sign = line % 2 == 0 ? -1 : 1;
            double cx = sign * (line < 2 ? 430 : 560);
            double w = cx + (noise(0, z, 220, 20 + line) - 0.5) * 120;
            if (Math.abs(x - w) > 14 || noise(0, z, 90, 30 + line) < 0.33) {
                continue;
            }
            double p = (z % 16 + 16) % 16; // зигзаг: прямоугольные траверсы
            double zig = p < 7 ? -2.5 : p < 8 ? -2.5 + (p - 7) * 5 : p < 15 ? 2.5 : 2.5 - (p - 15) * 5;
            best = Math.min(best, Math.abs(x - w - zig));
        }
        return best;
    }
}
