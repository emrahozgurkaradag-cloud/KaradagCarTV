#!/usr/bin/env python3
"""DHU oturum yöneticisi (CI). Gerçek araç DEĞİLDİR.

Desktop Head Unit'i başlatır, telefon (adb) ve araç ekranını (DHU
"screenshot" + tesseract OCR) izler; Android Auto başlatıcısında
KaradagCarTV'yi bulup DHU "tap" komutuyla açar, kanalı başlatır, park
kilidini DHU "restrict all/none" ile sınar, bağlantıyı kesip yeniden kurar.
Her adımın kanıtı OUT dizinine yazılır; özet OUT/summary.txt.
"""
import os, re, subprocess, sys, time

OUT = os.environ.get("DHU_OUT", "dhu-launch-out")
DHU = os.environ["DHU_BIN"]
CFG = os.environ.get("DHU_CFG", "")
PKG = os.environ.get("APP_PKG", "com.karadaglabs.cartv.projection")
GH = "com.google.android.projection.gearhead"
FAKETIME = os.environ.get("DHU_FAKETIME", "")
os.makedirs(OUT, exist_ok=True)
summary = []


def log(msg):
    print(f"[dhu-session] {msg}", flush=True)


def note(key, ok, detail=""):
    line = f"{key}: {'EVET' if ok else 'HAYIR'} {detail}".rstrip()
    summary.append(line)
    log("SONUÇ " + line)


def sh(cmd, timeout=60):
    try:
        return subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout).stdout
    except subprocess.TimeoutExpired:
        return ""


def logcat():
    return sh("adb logcat -d -v time", timeout=60)


def phone_tap_text(pattern):
    """Telefon ekranında metni bul ve dokun (uiautomator)."""
    sh("adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1")
    try:
        xml = open("/tmp/ui.xml", encoding="utf-8", errors="ignore").read()
    except OSError:
        return False
    for m in re.finditer(r'<node [^>]*>', xml):
        node = m.group(0)
        t = re.search(r'text="([^"]*)"', node)
        d = re.search(r'content-desc="([^"]*)"', node)
        label = (t.group(1) if t else "") or (d.group(1) if d else "")
        if label and re.fullmatch(pattern, label.strip(), re.I):
            b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
            if b:
                x1, y1, x2, y2 = map(int, b.groups())
                sh(f"adb shell input tap {(x1+x2)//2} {(y1+y2)//2}")
                log(f"telefon dokunma '{label}'")
                return True
    return False


class Dhu:
    def __init__(self, tag):
        self.tag = tag
        self.n = 0
        pre = f"faketime '{FAKETIME}' " if FAKETIME else ""
        cmd = f"{pre}'{DHU}' --headless -a 127.0.0.1:5277" + (f" -c '{CFG}'" if CFG else "")
        env = dict(os.environ, SDL_VIDEODRIVER="dummy", SDL_AUDIODRIVER="dummy")
        self.out = open(f"{OUT}/dhu-{tag}.txt", "w")
        self.p = subprocess.Popen(cmd, shell=True, stdin=subprocess.PIPE, stdout=self.out,
                                  stderr=subprocess.STDOUT, text=True, env=env)
        log(f"DHU başlatıldı ({tag})")

    def alive(self):
        return self.p.poll() is None

    def send(self, c):
        if not self.alive():
            return
        try:
            self.p.stdin.write(c + "\n"); self.p.stdin.flush(); log(f"DHU <- {c}")
        except (BrokenPipeError, OSError):
            pass

    def shot(self, name):
        path = os.path.abspath(f"{OUT}/car-{self.tag}-{name}.png")
        self.send(f"screenshot {path}")
        for _ in range(20):
            if os.path.exists(path) and os.path.getsize(path) > 0:
                time.sleep(0.5)
                return path
            time.sleep(0.5)
        return None

    def ocr(self, path):
        """[(text, cx, cy)] tesseract TSV ile; satır bazında birleştirir."""
        if not path:
            return []
        tsv = sh(f"tesseract '{path}' stdout --psm 11 tsv 2>/dev/null", timeout=60)
        words = []
        for line in tsv.splitlines()[1:]:
            f = line.split("\t")
            if len(f) < 12 or not f[11].strip():
                continue
            try:
                conf = float(f[10])
            except ValueError:
                continue
            if conf < 30:
                continue
            x, y, w, h = map(int, f[6:10])
            words.append((f[11].strip(), x + w // 2, y + h // 2))
        open(path + ".ocr.txt", "w").write("\n".join(f"{t}\t{x}\t{y}" for t, x, y in words))
        return words

    def find(self, path, pattern):
        for t, x, y in self.ocr(path):
            if re.search(pattern, t, re.I):
                return t, x, y
        return None

    def tap(self, x, y):
        self.send(f"tap {x} {y}")

    def quit(self):
        self.send("quit"); time.sleep(3)
        if self.alive():
            self.p.kill()
        self.out.close()


def wait_log(pattern, secs):
    end = time.time() + secs
    while time.time() < end:
        if re.search(pattern, logcat()):
            return True
        time.sleep(2)
    return False


def size_of(path):
    out = sh(f"identify -format '%w %h' '{path}'") if path else ""
    try:
        w, h = map(int, out.split())
        return w, h
    except ValueError:
        return 800, 480


def saturated_fraction(path):
    """Doymuş renkli piksel oranı (test yayını renkli çubuklar; ÖK arayüzü griye yakın)."""
    if not path:
        return 0.0
    v = sh(f"convert '{path}' -colorspace HSL -channel G -separate +channel -threshold 45% -format '%[fx:mean]' info:")
    try:
        return float(v.strip())
    except ValueError:
        return 0.0


def session(tag, first):
    sh("adb logcat -c")
    d = Dhu(tag)
    time.sleep(10)
    d.send("restrict none")  # park hâli (sürüş kısıtı yok)
    connected = wait_log(r"Handoff complete|PROJECTION_STARTED|CAR\.SERVICE.*connected|onCarConnected|car connected", 60)
    note(f"[{tag}] DHU-telefon oturumu kuruldu", connected)

    opened = False
    for i in range(1, 25):
        if not d.alive():
            break
        # Telefon tarafı kurulum / izin ekranları
        phone_tap_text(r"(Get started|Accept|Continue|Next|Got it|OK|Allow|Allow all the time|While using the app|Agree|I agree|Done|Turn on|Not now|Skip)")
        img = d.shot(f"{i:02d}")
        if i <= 4 or i % 6 == 0:  # telefon tarafının kanıtı (ilk adımlar)
            sh(f"adb exec-out screencap -p > '{OUT}/phone-{tag}-{i:02d}.png'; cp /tmp/ui.xml '{OUT}/phone-{tag}-{i:02d}.xml' 2>/dev/null")
        if re.search(r"CAR VIDEO ACTIVITY", logcat()):
            opened = True
            break
        hit = d.find(img, r"^Karadag|KaradagCarTV|CarTV")
        if hit:
            log(f"araç ekranında '{hit[0]}' bulundu -> dokunma")
            d.tap(hit[1], hit[2]); time.sleep(6)
            continue
        btn = d.find(img, r"^(Continue|Get|started|Accept|OK|Got|Done|Allow|Agree|Close|Dismiss)$")
        if btn:
            d.tap(btn[1], btn[2]); time.sleep(4); continue
        # Uygulama ızgarasını aç: rayın alt/üst köşeleri ve "keycode home".
        w, h = size_of(img)
        cands = [(w * 5 // 100, h * 92 // 100), (w * 5 // 100, h * 8 // 100), (w // 2, h * 95 // 100)]
        x, y = cands[i % len(cands)]
        if i % 4 == 0:
            d.send("keycode home")
        else:
            d.tap(x, y)
        time.sleep(4)

    if not opened:
        opened = wait_log(r"CAR VIDEO ACTIVITY", 10)
    lc = logcat()
    open(f"{OUT}/logcat-{tag}-open.txt", "w").write(lc)
    note(f"[{tag}] Android Auto CarVideoService'e bağlandı", bool(re.search(r"CAR VIDEO SERVICE ON BIND|EVT=SERVICE_BIND", lc)))
    note(f"[{tag}] host kodu yüklendi", bool(re.search(r"HOST READY|EVT=HOST_LOAD.*ok=true", lc)))
    note(f"[{tag}] CarVideoActivity açıldı", opened)
    if not opened:
        d.shot("not-opened"); d.quit()
        return False

    window = re.findall(r"EVT=WINDOW.*", lc)
    if window:
        log("WINDOW " + window[-1][:300])

    # Kanal: OCR ile kartı bul, yoksa medya tuşu (sonraki kanal = PLAYING).
    time.sleep(6)
    img = d.shot("list")
    hit = d.find(img, r"DHU|Test|Yay")
    if hit:
        d.tap(hit[1], hit[2])
    else:
        d.send("keycode media_next")
    surface = wait_log(r"CAR VIDEO SURFACE CREATED", 30)
    bound = wait_log(r"CAR VIDEO SURFACE BOUND", 20)
    frame = wait_log(r"CAR VIDEO FIRST FRAME", 40)
    time.sleep(6)
    img = d.shot("playing")
    sat = saturated_fraction(img)
    lc = logcat()
    open(f"{OUT}/logcat-{tag}-play.txt", "w").write(lc)
    gate = re.findall(r"VIDEO GATE allowed=\S+ reason=\S+", lc)
    note(f"[{tag}] video Surface oluştu", surface)
    note(f"[{tag}] ExoPlayer Surface'e bağlandı", bound)
    note(f"[{tag}] ilk video karesi çizildi", frame)
    note(f"[{tag}] park kilidi kararı (restrict none)", bool(gate) and "allowed=true" in gate[-1], gate[-1] if gate else "")
    note(f"[{tag}] DHU araç ekranı görüntüsünde video", sat > 0.25, f"doymuş piksel oranı={sat:.2f} ({os.path.basename(img or '')})")

    # Park kilidi: sürüş kısıtı -> görüntü kapanmalı, ses sürebilir.
    d.send("restrict all")
    locked = wait_log(r"VIDEO GATE allowed=false", 15)
    time.sleep(3)
    img2 = d.shot("restricted")
    sat2 = saturated_fraction(img2)
    note(f"[{tag}] sürüş kısıtında görüntü kilitlendi", locked and sat2 < 0.1, f"oran={sat2:.2f}")
    d.send("restrict none")
    unlocked = wait_log(r"VIDEO GATE allowed=true", 20)
    time.sleep(5)
    img3 = d.shot("unrestricted")
    sat3 = saturated_fraction(img3)
    note(f"[{tag}] park edilince görüntü geri geldi", unlocked and sat3 > 0.25, f"oran={sat3:.2f}")

    # Bağlantı kesme: DHU kapanır, uygulama çökmemeli.
    d.quit()
    time.sleep(8)
    lc = logcat()
    open(f"{OUT}/logcat-{tag}-disconnect.txt", "w").write(lc)
    crashed = bool(re.search(rf"FATAL EXCEPTION.*\n.*Process: {re.escape(PKG)}", lc))
    alive = bool(sh(f"adb shell pidof {PKG}").strip())
    note(f"[{tag}] bağlantı kesilince çökme yok", not crashed, f"süreç {'açık' if alive else 'kapalı'}")
    return True


def main():
    ok = session("s1", True)
    if ok:
        # Yeniden bağlanma: aynı akış ikinci kez.
        sh("adb forward tcp:5277 tcp:5277")
        session("s2", False)
    open(f"{OUT}/summary.txt", "w").write("\n".join(summary) + "\n")
    print("\n".join(summary))


if __name__ == "__main__":
    main()
