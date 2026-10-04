/*
 * Port HTML/CSS/JS dari game Java (SnakeMain, MainMenuPanel, SnakePanel, GameOverPanel).
 * Semua angka (warna, koordinat, ukuran, durasi) disalin dari source Java agar
 * tampilan dan perilakunya identik.
 */
(() => {
    "use strict";

    // ===== Konstanta dari SnakePanel.java =====
    const SCREEN_W = 713;
    const SCREEN_H = 613;
    const UNIT = 25;
    const GAME_UNITS = Math.floor((SCREEN_W * SCREEN_H) / (UNIT * UNIT));
    const DELAY = 150;
    const COUNTDOWN_MS = 800;

    // Tabrakan dinding memakai 680/560 dan makanan spawn di 675/550:
    // memang begitu perilaku versi Java, bukan 713/613.
    const WALL_RIGHT = 680;
    const WALL_BOTTOM = 560;
    const APPLE_MAX_X = Math.floor(675 / UNIT);
    const APPLE_MAX_Y = Math.floor(550 / UNIT);

    const BEST_SCORE_KEY = "snakeGameBestScore";

    // ===== Helper =====
    const rgba = (c, a = 1) => `rgba(${c[0]},${c[1]},${c[2]},${a})`;
    const JA = (a) => a / 255; // alpha 0-255 gaya Java ke 0-1

    // java.awt.Color.brighter()/darker(): bagi/kali faktor 0.7 per kanal
    const FACTOR = 0.7;
    const brighter = (c) => c.map((v) => Math.min(255, Math.trunc(v / FACTOR)));
    const darker = (c) => c.map((v) => Math.max(0, Math.trunc(v * FACTOR)));

    const SNAKE_COLORS = {
        red: [255, 50, 50],
        blue: [50, 100, 255],
        pink: [255, 130, 180],
        green: [50, 220, 50],
    };
    const FOOD_COLORS = {
        apple: [255, 0, 0],
        banana: [255, 255, 0],
        grape: [128, 0, 128],
        orange: [255, 165, 0],
    };
    const FOOD_GLOW = {
        apple: [255, 0, 0],
        banana: [255, 255, 0],
        grape: [128, 0, 128],
        orange: [255, 165, 0],
    };
    const MAP_GRID_COLORS = {
        classic: [[150, 240, 130], [120, 210, 100]],
        desert: [[237, 201, 140], [210, 175, 115]],
        ocean: [[100, 180, 220], [70, 150, 195]],
    };
    const MAP_BORDER = {
        neon: { color: [0, 255, 255], alpha: 120 },
        ocean: { color: [100, 200, 255], alpha: 100 },
        desert: { color: [255, 200, 100], alpha: 100 },
        classic: { color: [255, 255, 255], alpha: 100 },
    };

    // java.util.Random (LCG) untuk titik tekstur desert yang memakai seed 42,
    // supaya polanya sama dengan versi Java.
    function javaRandom(seed) {
        const MULT = 0x5DEECE66Dn;
        const MASK = (1n << 48n) - 1n;
        let s = (BigInt(seed) ^ MULT) & MASK;
        const next = (bits) => {
            s = (s * MULT + 0xBn) & MASK;
            return Number(s >> BigInt(48 - bits));
        };
        return (bound) => {
            if ((bound & -bound) === bound) return Math.trunc((bound * next(31)) / 2 ** 31);
            let bits, val;
            do {
                bits = next(31);
                val = bits % bound;
            } while (bits - val + (bound - 1) >= 2 ** 31);
            return val;
        };
    }

    const DESERT_DOTS = (() => {
        const rnd = javaRandom(42);
        const dots = [];
        for (let i = 0; i < 100; i++) dots.push([rnd(SCREEN_W), rnd(SCREEN_H)]);
        return dots;
    })();

    // ===== DOM =====
    const mainCtx = document.getElementById("game").getContext("2d");
    const fxCtx = document.getElementById("game-fx").getContext("2d");
    let ctx = mainCtx; // helper gambar memakai ctx aktif (utama atau fx)
    const container = document.getElementById("game-container");
    const scaleWrap = document.getElementById("game-scale");
    const fadeEl = document.getElementById("fade");
    const loadingEl = document.getElementById("loading");
    const errorEl = document.getElementById("error");
    const exitEl = document.getElementById("exit");
    const gameCanvas = document.getElementById("game");
    const gifSnake = document.getElementById("gif-snake");
    const gifFlame = document.getElementById("gif-flame");
    const gifChest = document.getElementById("gif-chest");

    // ===== Aset gambar =====
    const IMG_SRC = {
        bg1: "IMG/Background1.png",
        bg2: "IMG/Background2.png",
        bg3: "IMG/Background3.png",
        start: "IMG/Start.png",
        custom: "IMG/Custom.png",
        exit: "IMG/Exit.png",
        back: "IMG/Back.png",
        rsnake: "IMG/RSnake.png",
        gsnake: "IMG/GSnake.png",
        bsnake: "IMG/BSnake.png",
        psnake: "IMG/PSnake.png",
        apple: "IMG/Apple.png",
        banana: "IMG/Banana.png",
        grape: "IMG/Grape.png",
        orange: "IMG/Orange.png",
        snake2r: "IMG/Snake2R.png",
        snake2g: "IMG/Snake2G.png",
        snake2b: "IMG/Snake2B.png",
        snake2p: "IMG/Snake2P.png",
    };
    const img = {};

    function loadAssets() {
        const jobs = Object.entries(IMG_SRC).map(
            ([key, src]) =>
                new Promise((resolve, reject) => {
                    const image = new Image();
                    image.onload = () => { img[key] = image; resolve(); };
                    image.onerror = () => reject(new Error(src));
                    image.src = src;
                })
        );
        [gifSnake, gifFlame, gifChest].forEach((el) => {
            if (el.complete && el.naturalWidth > 0) return;
            jobs.push(
                new Promise((resolve, reject) => {
                    el.onload = resolve;
                    el.onerror = () => reject(new Error(el.src));
                })
            );
        });
        return Promise.all(jobs);
    }

    // ===== Utilitas gambar =====
    function roundRectPath(c, x, y, w, h, r) {
        c.beginPath();
        if (c.roundRect) {
            c.roundRect(x, y, w, h, r);
        } else {
            c.moveTo(x + r, y);
            c.arcTo(x + w, y, x + w, y + h, r);
            c.arcTo(x + w, y + h, x, y + h, r);
            c.arcTo(x, y + h, x, y, r);
            c.arcTo(x, y, x + w, y, r);
            c.closePath();
        }
    }

    // Java memakai arcWidth (diameter sudut), canvas memakai radius: r = arc/2
    function fillRR(x, y, w, h, arcW, style, alpha = 1) {
        ctx.globalAlpha = alpha;
        ctx.fillStyle = style;
        roundRectPath(ctx, x, y, w, h, arcW / 2);
        ctx.fill();
        ctx.globalAlpha = 1;
    }

    function strokeRR(x, y, w, h, arcW, style, lineWidth, alpha = 1) {
        ctx.globalAlpha = alpha;
        ctx.strokeStyle = style;
        ctx.lineWidth = lineWidth;
        roundRectPath(ctx, x, y, w, h, arcW / 2);
        ctx.stroke();
        ctx.globalAlpha = 1;
    }

    function fillOval(x, y, w, h, style, alpha = 1) {
        ctx.globalAlpha = alpha;
        ctx.fillStyle = style;
        ctx.beginPath();
        ctx.ellipse(x + w / 2, y + h / 2, Math.max(0, w / 2), Math.max(0, h / 2), 0, 0, Math.PI * 2);
        ctx.fill();
        ctx.globalAlpha = 1;
    }

    function setFont(size, weight, style) {
        ctx.font = `${style ? style + " " : ""}${weight ? weight + " " : ""}${size}px Arial`;
    }

    function drawText(text, x, y, { size = 12, weight = "", style = "", color = [255, 255, 255], alpha = 1, align = "left", baseline = "alphabetic" } = {}) {
        setFont(size, weight, style);
        ctx.globalAlpha = alpha;
        ctx.fillStyle = rgba(color);
        ctx.textAlign = align;
        ctx.textBaseline = baseline;
        ctx.fillText(text, x, y);
        ctx.globalAlpha = 1;
        ctx.textAlign = "left";
        ctx.textBaseline = "alphabetic";
    }

    function measure(text, size, weight = "", style = "") {
        setFont(size, weight, style);
        return ctx.measureText(text).width;
    }

    // ===== State =====
    let state = "loading"; // loading | menu | game | gameover

    const menu = {
        selectedColor: "green",
        selectedFood: "apple",
        selectedMap: "classic",
        customActive: false,
        customCategory: 0, // 0=warna, 1=makanan, 2=map
        menuSelectedIndex: 0,
        hoverScale: [1, 1, 1],
        hoverTarget: [1, 1, 1],
        particles: [],
        titleGlow: 0,
        titleGlowUp: true,
        transitionAlpha: 1,
        transitioningIn: true,
        transitioningOut: false,
        transitionCallback: null,
    };

    const game = {
        x: new Int32Array(GAME_UNITS),
        y: new Int32Array(GAME_UNITS),
        displayX: new Float32Array(GAME_UNITS),
        displayY: new Float32Array(GAME_UNITS),
        prevX: new Int32Array(GAME_UNITS),
        prevY: new Int32Array(GAME_UNITS),
        bestScore: 0,
        bodyParts: 0,
        applesEaten: 0,
        appleX: 0,
        appleY: 0,
        direction: "R",
        running: false,
        paused: false,
        pauseSelectedIndex: 0,
        selectedColor: "green",
        selectedFood: "apple",
        selectedMap: "classic",
        queuedDirection: "",
        directionChangedThisTick: false,
        countdown: 3,
        countdownActive: false,
        countdownInterval: null,
        shakeX: 0,
        shakeY: 0,
        shakeMagnitude: 0,
        foodBob: 0,
        neonPulse: 0,
        neonPulseUp: true,
        transitionAlpha: 1,
        transitioningIn: true,
        foodParticles: [],
        scorePopups: [],
    };

    const GAME_OVER_MESSAGES = [
        "CUPU LU BRO!",
        "GAME OVER! SKILL ISSUE!",
        "KALAH LAGI? PAYAH!",
        "COBA LAGI DEH, MASA KALAH SAMA GAME BOCIL!",
        "ULAR AJA BISA MAKAN, KAMU KOK KALAH?",
        "UDAH KALAH, GAUSAH NANGIS!",
        "NEXT TIME PAKE OTAK YA!",
        "ULAR: 1, KAMU: 0",
        "MUNGKIN GAME INI TERLALU SUSAH BUAT KAMU",
        "COBA MAIN EPEP AJA DEH!",
    ];

    const over = {
        score: 0,
        bestScore: 0,
        fadeIn: 0,
        titleShake: 0,
        textPulse: 0,
        selectedOption: 0,
        message: "",
        particles: [],
    };

    // ===== Best score =====
    function loadBestScore() {
        try {
            const v = parseInt(localStorage.getItem(BEST_SCORE_KEY), 10);
            if (Number.isFinite(v) && v > 0) game.bestScore = v;
        } catch (e) { /* penyimpanan tidak tersedia, skor mulai dari 0 */ }
    }

    function saveBestScore() {
        try {
            localStorage.setItem(BEST_SCORE_KEY, String(game.bestScore));
        } catch (e) { /* abaikan */ }
    }

    // ===== Partikel =====
    const PARTICLE_COLORS = [[255, 255, 200], [200, 255, 200], [200, 220, 255]];

    function makeMenuParticle() {
        return {
            x: Math.random() * SCREEN_W,
            y: Math.random() * SCREEN_H,
            vx: (Math.random() - 0.5) * 0.8,
            vy: -Math.random() * 0.5 - 0.2,
            size: Math.random() * 4 + 1,
            alpha: Math.random() * 0.4 + 0.1,
            color: PARTICLE_COLORS[Math.floor(Math.random() * 3)],
        };
    }

    function resetMenuParticle(p) {
        p.x = Math.random() * SCREEN_W;
        p.y = Math.random() * SCREEN_H;
        p.vx = (Math.random() - 0.5) * 0.8;
        p.vy = -Math.random() * 0.5 - 0.2;
        p.size = Math.random() * 4 + 1;
        p.color = PARTICLE_COLORS[Math.floor(Math.random() * 3)];
    }

    const DEATH_COLORS = [[255, 50, 50], [255, 100, 50], [255, 150, 50]];

    function makeDeathParticle() {
        return {
            x: Math.random() * SCREEN_W,
            y: Math.random() * SCREEN_H,
            vx: (Math.random() - 0.5) * 1.5,
            vy: Math.random() * 1.0 + 0.5,
            size: Math.random() * 3 + 1,
            alpha: Math.random() * 0.3 + 0.05,
            color: DEATH_COLORS[Math.floor(Math.random() * 3)],
        };
    }

    function resetDeathParticle(p) {
        p.x = Math.random() * SCREEN_W;
        p.y = -10;
        p.vx = (Math.random() - 0.5) * 1.5;
        p.vy = Math.random() * 1.0 + 0.5;
        p.size = Math.random() * 3 + 1;
        p.alpha = Math.random() * 0.3 + 0.05;
        p.color = DEATH_COLORS[Math.floor(Math.random() * 3)];
    }

    // ===== Menu utama =====
    const START_BUTTON_Y = 220, CUSTOM_BUTTON_Y = 280, EXIT_BUTTON_Y = 340;
    const BUTTON_W = 250, BUTTON_H = 50;
    const BUTTON_X = Math.trunc((SCREEN_W - BUTTON_W) / 2); // 231
    const BACK_W = 151, BACK_H = 74;

    const COLOR_RECTS = {
        red: [124, 163, 40, 42],
        green: [238, 163, 40, 42],
        blue: [354, 163, 40, 42],
        pink: [475, 163, 40, 42],
    };
    const COLOR_KEYS = ["red", "green", "blue", "pink"];
    const FOOD_RECTS = {
        apple: [124, 279, 45, 45],
        banana: [238, 279, 45, 45],
        grape: [354, 279, 45, 45],
        orange: [475, 279, 45, 45],
    };
    const FOOD_KEYS = ["apple", "banana", "grape", "orange"];
    const MAP_Y = 395;
    const MAP_RECTS = {
        classic: [80, MAP_Y, 100, 50],
        desert: [200, MAP_Y, 100, 50],
        ocean: [320, MAP_Y, 100, 50],
        neon: [440, MAP_Y, 100, 50],
    };
    const MAP_KEYS = ["classic", "desert", "ocean", "neon"];
    const MAP_NAMES = { classic: "CLASSIC", desert: "DESERT", ocean: "OCEAN", neon: "NEON" };
    const MAP_DESCRIPTIONS = {
        classic: "Lapangan hijau klasik",
        desert: "Padang pasir yang panas",
        ocean: "Lautan biru yang sejuk",
        neon: "Dunia neon futuristik",
    };
    const MAP_PREVIEW = {
        classic: [[150, 240, 130], [120, 210, 100]],
        desert: [[237, 201, 140], [210, 175, 115]],
        ocean: [[100, 180, 220], [70, 150, 195]],
        neon: [[30, 30, 50], [20, 20, 40]],
    };

    function resetMenu() {
        menu.customActive = false;
        menu.customCategory = 0;
        menu.menuSelectedIndex = 0;
        menu.hoverScale = [1, 1, 1];
        menu.hoverTarget = [1, 1, 1];
        menu.particles = [];
        for (let i = 0; i < 30; i++) menu.particles.push(makeMenuParticle());
        menu.titleGlow = 0;
        menu.titleGlowUp = true;
        menu.transitionAlpha = 1;
        menu.transitioningIn = true;
        menu.transitioningOut = false;
        menu.transitionCallback = null;
    }

    function startGameWithTransition() {
        menu.transitioningOut = true;
        menu.transitionCallback = enterGame;
    }

    function updateMenu() {
        for (let i = 0; i < 3; i++) {
            menu.hoverScale[i] += (menu.hoverTarget[i] - menu.hoverScale[i]) * 0.15;
        }
        for (const p of menu.particles) {
            p.x += p.vx;
            p.y += p.vy;
            p.alpha -= 0.002;
            if (p.alpha <= 0 || p.y < -10 || p.x < -10 || p.x > SCREEN_W + 10) {
                resetMenuParticle(p);
                p.y = SCREEN_H + 10;
                p.alpha = Math.random() * 0.4 + 0.1;
            }
        }
        if (menu.titleGlowUp) {
            menu.titleGlow += 0.02;
            if (menu.titleGlow >= 1) menu.titleGlowUp = false;
        } else {
            menu.titleGlow -= 0.02;
            if (menu.titleGlow <= 0) menu.titleGlowUp = true;
        }
        if (menu.transitioningIn) {
            menu.transitionAlpha -= 0.03;
            if (menu.transitionAlpha <= 0) {
                menu.transitionAlpha = 0;
                menu.transitioningIn = false;
            }
        }
        if (menu.transitioningOut) {
            menu.transitionAlpha += 0.05;
            if (menu.transitionAlpha >= 1) {
                menu.transitionAlpha = 1;
                menu.transitioningOut = false;
                if (menu.transitionCallback) {
                    const cb = menu.transitionCallback;
                    menu.transitionCallback = null;
                    cb();
                }
            }
        }
    }

    function drawAnimatedButton(key, y, index, fallbackText) {
        const image = img[key];
        const scale = menu.hoverScale[index];
        const scaledW = Math.trunc(BUTTON_W * scale);
        const scaledH = Math.trunc(BUTTON_H * scale);
        const offsetX = BUTTON_X - Math.trunc((scaledW - BUTTON_W) / 2);
        const offsetY = y - Math.trunc((scaledH - BUTTON_H) / 2);

        if (index === menu.menuSelectedIndex) {
            const glowAlpha = 0.3 + menu.titleGlow * 0.2;
            ctx.fillStyle = rgba([255, 255, 200]);
            ctx.globalAlpha = glowAlpha;
            for (let i = 8; i > 0; i--) {
                roundRectPath(ctx, offsetX - i, offsetY - i, scaledW + i * 2, scaledH + i * 2, 7.5);
                ctx.fill();
            }
            ctx.globalAlpha = 1;
        }

        if (image) {
            ctx.drawImage(image, offsetX, offsetY, scaledW, scaledH);
        } else {
            const bg = index === 0 ? [0, 255, 0] : index === 1 ? [0, 0, 255] : [255, 0, 0];
            fillRR(offsetX, offsetY, scaledW, scaledH, 10, rgba(bg));
            const tw = measure(fallbackText, 18, "bold");
            drawText(fallbackText, offsetX + (scaledW - tw) / 2, offsetY + scaledH / 2 + 6, { size: 18, weight: "bold", color: [255, 255, 255] });
        }
    }

    function drawSelectionArrow() {
        let arrowY;
        if (menu.menuSelectedIndex === 0) arrowY = START_BUTTON_Y + BUTTON_H / 2;
        else if (menu.menuSelectedIndex === 1) arrowY = CUSTOM_BUTTON_Y + BUTTON_H / 2;
        else arrowY = EXIT_BUTTON_Y + BUTTON_H / 2;

        const pulse = Math.sin(performance.now() * 0.005) * 3;
        const ax = BUTTON_X - 35 + pulse;

        ctx.fillStyle = rgba([255, 255, 100]);
        ctx.beginPath();
        ctx.moveTo(ax, arrowY);
        ctx.lineTo(ax - 12, arrowY - 8);
        ctx.lineTo(ax - 12, arrowY + 8);
        ctx.fill();

        ctx.globalAlpha = 0.3;
        ctx.beginPath();
        ctx.moveTo(ax + 3, arrowY);
        ctx.lineTo(ax - 15, arrowY - 11);
        ctx.lineTo(ax - 15, arrowY + 11);
        ctx.fill();
        ctx.globalAlpha = 1;
    }

    function drawControlHints() {
        const hint = "[W/S] Navigasi   [ENTER] Pilih   [ESC] Keluar";
        const w = measure(hint, 11, "bold");
        fillRR((SCREEN_W - w) / 2 - 10, SCREEN_H - 32, w + 20, 22, 8, rgba([0, 0, 0]), JA(120));
        drawText(hint, (SCREEN_W - w) / 2, SCREEN_H - 16, { size: 11, weight: "bold", color: [255, 255, 255], alpha: JA(200) });
    }

    function drawCustomControlHints() {
        const hint = "[W/S] Kategori   [A/D] Pilih   [ENTER] Simpan   [ESC] Kembali";
        const w = measure(hint, 10, "bold");
        fillRR((SCREEN_W - w) / 2 - 10, SCREEN_H - 30, w + 20, 20, 8, rgba([0, 0, 0]), JA(150));
        drawText(hint, (SCREEN_W - w) / 2, SCREEN_H - 15, { size: 10, weight: "bold", color: [255, 255, 255], alpha: JA(220) });
    }

    function drawColorOrFoodItem(image, rect, key, selectedKey, glowColors) {
        const isSelected = selectedKey === key;
        if (image) {
            ctx.drawImage(image, rect[0], rect[1], rect[2], rect[3]);
        } else {
            fillRR(rect[0], rect[1], rect[2], rect[3], 0, rgba([128, 128, 128]));
        }
        if (isSelected) {
            ctx.fillStyle = rgba(glowColors[key], JA(150));
            for (let i = 5; i > 0; i--) {
                ctx.globalAlpha = (i / 5) * 0.7;
                roundRectPath(ctx, rect[0] - i, rect[1] - i, rect[2] + 2 * i, rect[3] + 2 * i, 5);
                ctx.fill();
            }
            ctx.globalAlpha = 1;
            strokeRR(rect[0] - 2, rect[1] - 2, rect[2] + 4, rect[3] + 4, 10, rgba([255, 255, 255]), 4);
        }
    }

    function drawCustomizationOptions() {
        const colorImgs = { red: img.rsnake, green: img.gsnake, blue: img.bsnake, pink: img.psnake };
        const colorGlows = { red: [255, 0, 0], green: [0, 255, 0], blue: [0, 0, 255], pink: [255, 105, 180] };
        for (const key of COLOR_KEYS) {
            drawColorOrFoodItem(colorImgs[key], COLOR_RECTS[key], key, menu.selectedColor, colorGlows);
        }
        const foodImgs = { apple: img.apple, banana: img.banana, grape: img.grape, orange: img.orange };
        const foodGlows = { apple: [255, 0, 0], banana: [255, 255, 0], grape: [128, 0, 128], orange: [255, 165, 0] };
        for (const key of FOOD_KEYS) {
            drawColorOrFoodItem(foodImgs[key], FOOD_RECTS[key], key, menu.selectedFood, foodGlows);
        }

        // Kotak putus-putus di area kategori yang sedang aktif
        let box;
        if (menu.customCategory === 0) {
            box = [COLOR_RECTS.red[0] - 10, COLOR_RECTS.red[1] - 10, COLOR_RECTS.pink[0] + COLOR_RECTS.pink[2] - COLOR_RECTS.red[0] + 20, COLOR_RECTS.red[3] + 20];
        } else if (menu.customCategory === 1) {
            box = [FOOD_RECTS.apple[0] - 10, FOOD_RECTS.apple[1] - 10, FOOD_RECTS.orange[0] + FOOD_RECTS.orange[2] - FOOD_RECTS.apple[0] + 20, FOOD_RECTS.apple[3] + 20];
        } else {
            box = [MAP_RECTS.classic[0] - 10, MAP_RECTS.classic[1] - 10, MAP_RECTS.neon[0] + MAP_RECTS.neon[2] - MAP_RECTS.classic[0] + 20, MAP_RECTS.classic[3] + 20];
        }
        ctx.strokeStyle = rgba([255, 255, 100], JA(80));
        ctx.lineWidth = 2;
        ctx.setLineDash([6, 4]);
        roundRectPath(ctx, box[0], box[1], box[2], box[3], 4);
        ctx.stroke();
        ctx.setLineDash([]);
    }

    function drawMapOptions() {
        drawText("PILIH MAP:", 80, 385, { size: 14, weight: "bold", color: [255, 255, 255], alpha: JA(220) });

        for (const key of MAP_KEYS) {
            const rect = MAP_RECTS[key];
            const isSelected = menu.selectedMap === key;
            const isCategoryActive = menu.customCategory === 2;
            const colors = MAP_PREVIEW[key];
            const cell = 10;
            const cols = Math.trunc(rect[2] / cell);
            const rows = Math.trunc(rect[3] / cell);
            for (let r = 0; r < rows; r++) {
                for (let c = 0; c < cols; c++) {
                    ctx.fillStyle = rgba((r + c) % 2 === 0 ? colors[0] : colors[1]);
                    ctx.fillRect(rect[0] + c * cell, rect[1] + r * cell, cell, cell);
                }
            }

            fillRR(rect[0], rect[1] + rect[3] - 18, rect[2], 18, 0, rgba([0, 0, 0]), JA(140));
            const name = MAP_NAMES[key];
            const nw = measure(name, 11, "bold");
            drawText(name, rect[0] + (rect[2] - nw) / 2, rect[1] + rect[3] - 5, { size: 11, weight: "bold", color: [255, 255, 255] });

            if (isSelected) {
                ctx.fillStyle = rgba([255, 255, 100], JA(150));
                for (let i = 5; i > 0; i--) {
                    ctx.globalAlpha = (i / 5) * 0.5;
                    roundRectPath(ctx, rect[0] - i, rect[1] - i, rect[2] + 2 * i, rect[3] + 2 * i, 3);
                    ctx.fill();
                }
                ctx.globalAlpha = 1;
                strokeRR(rect[0] - 2, rect[1] - 2, rect[2] + 4, rect[3] + 4, 6, rgba([255, 255, 255]), 3);
            } else if (isCategoryActive) {
                strokeRR(rect[0], rect[1], rect[2], rect[3], 4, rgba([255, 255, 255], JA(60)), 1);
            }
        }

        drawText(MAP_DESCRIPTIONS[menu.selectedMap], 80, 460, { size: 12, style: "italic", color: [255, 255, 200], alpha: JA(200) });
    }

    function drawCategoryIndicator() {
        const categories = ["▶ WARNA ULAR", "▶ MAKANAN", "▶ MAP"];
        for (let i = 0; i < 3; i++) {
            drawText(categories[i], 560, 163 + i * 116, {
                size: 11,
                weight: "bold",
                color: [255, 255, 255],
                alpha: i === menu.customCategory ? 1 : JA(120),
            });
        }
    }

    function setGifVisibility(snake, flame, chest) {
        gifSnake.style.display = snake ? "block" : "none";
        gifFlame.style.display = flame ? "block" : "none";
        gifChest.style.display = chest ? "block" : "none";
    }

    function drawMenuBase() {
        if (!menu.customActive) {
            if (img.bg1) ctx.drawImage(img.bg1, 0, 0, SCREEN_W, SCREEN_H);
            for (const p of menu.particles) {
                fillOval(Math.trunc(p.x), Math.trunc(p.y), Math.trunc(p.size), Math.trunc(p.size), rgba(p.color), p.alpha);
            }
            setGifVisibility(true, true, false);
        } else {
            if (img.bg2) ctx.drawImage(img.bg2, 0, 0, SCREEN_W, SCREEN_H);
            drawCustomizationOptions();

            const snake2Img = { red: img.snake2r, green: img.snake2g, blue: img.snake2b, pink: img.snake2p }[menu.selectedColor];
            if (snake2Img) ctx.drawImage(snake2Img, 550, 130, 100, 100);

            drawMapOptions();

            const foodImg = { apple: img.apple, banana: img.banana, grape: img.grape, orange: img.orange }[menu.selectedFood];
            const glow = FOOD_GLOW[menu.selectedFood];
            for (let i = 10; i > 0; i--) {
                fillOval(570 - i, 279 - i, 45 + 2 * i, 45 + 2 * i, rgba(glow), (i / 10) * 0.5 * JA(100));
            }
            if (foodImg) ctx.drawImage(foodImg, 570, 279, 45, 45);

            setGifVisibility(false, false, true);
        }
    }

    // Lapisan fx: elemen yang di Java digambar setelah GIF
    function drawMenuFx() {
        if (!menu.customActive) {
            drawAnimatedButton("start", START_BUTTON_Y, 0, "START");
            drawAnimatedButton("custom", CUSTOM_BUTTON_Y, 1, "CUSTOM");
            drawAnimatedButton("exit", EXIT_BUTTON_Y, 2, "EXIT");
            drawSelectionArrow();
            drawControlHints();
        } else {
            if (img.back) {
                ctx.drawImage(img.back, 0, 0, BACK_W, BACK_H);
            } else {
                fillRR(0, 0, BACK_W, BACK_H, 0, rgba([128, 128, 128]));
                drawText("Back", 30, 25, { size: 12, color: [255, 255, 255] });
            }
            drawCategoryIndicator();
            drawCustomControlHints();
        }
    }

    // ===== Game =====
    function initGame() {
        game.bodyParts = 6;
        game.applesEaten = 0;
        game.direction = "R";
        game.running = true;

        const maxStartX = Math.trunc(SCREEN_W / UNIT) - game.bodyParts;
        const maxStartY = Math.trunc(SCREEN_H / UNIT);
        const startX = Math.floor(Math.random() * maxStartX) * UNIT;
        const startY = Math.floor(Math.random() * maxStartY) * UNIT;

        for (let i = 0; i < game.bodyParts; i++) {
            game.x[i] = startX - i * UNIT;
            game.y[i] = startY;
            game.displayX[i] = game.x[i];
            game.displayY[i] = game.y[i];
            game.prevX[i] = game.x[i];
            game.prevY[i] = game.y[i];
        }
        newApple();
    }

    function enterGame() {
        state = "game";
        game.selectedColor = menu.selectedColor;
        game.selectedFood = menu.selectedFood;
        game.selectedMap = menu.selectedMap;
        game.foodParticles = [];
        game.scorePopups = [];
        game.shakeMagnitude = 0;
        game.shakeX = 0;
        game.shakeY = 0;
        game.transitionAlpha = 1;
        game.transitioningIn = true;
        game.foodBob = 0;
        game.neonPulse = 0;
        game.neonPulseUp = true;
        game.queuedDirection = "";
        game.directionChangedThisTick = false;
        game.pauseSelectedIndex = 0;
        game.tickAcc = 0;
        gradientCache.clear();
        setGifVisibility(false, false, false);
        loadBestScore();
        initGame();
        startCountdown();
    }

    function startCountdown() {
        game.countdownActive = true;
        game.countdown = 3;
        game.paused = true;
        stopCountdown();
        game.countdownInterval = setInterval(() => {
            game.countdown--;
            if (game.countdown <= 0) {
                game.countdownActive = false;
                game.paused = false;
                game.running = true; // setara startGame() di versi Java
                stopCountdown();
            }
        }, COUNTDOWN_MS);
    }

    function stopCountdown() {
        if (game.countdownInterval) {
            clearInterval(game.countdownInterval);
            game.countdownInterval = null;
        }
    }

    function newApple() {
        for (;;) {
            game.appleX = Math.floor(Math.random() * APPLE_MAX_X) * UNIT;
            game.appleY = Math.floor(Math.random() * APPLE_MAX_Y) * UNIT;
            let valid = true;
            for (let i = 0; i < game.bodyParts; i++) {
                if (game.appleX === game.x[i] && game.appleY === game.y[i]) {
                    valid = false;
                    break;
                }
            }
            if (valid) break;
        }
    }

    function move() {
        for (let i = game.bodyParts; i > 0; i--) {
            game.x[i] = game.x[i - 1];
            game.y[i] = game.y[i - 1];
        }
        if (game.direction === "U") game.y[0] -= UNIT;
        else if (game.direction === "D") game.y[0] += UNIT;
        else if (game.direction === "L") game.x[0] -= UNIT;
        else if (game.direction === "R") game.x[0] += UNIT;
    }

    function checkApple() {
        if (game.x[0] === game.appleX && game.y[0] === game.appleY) {
            game.bodyParts++;
            game.applesEaten++;
            spawnFoodParticles(game.appleX, game.appleY);
            game.scorePopups.push({ x: game.appleX, y: game.appleY, text: "+1", alpha: 1 });
            game.shakeMagnitude = 3;
            if (game.applesEaten > game.bestScore) {
                game.bestScore = game.applesEaten;
                saveBestScore();
            }
            newApple();
        }
    }

    function checkCollisions() {
        for (let i = game.bodyParts; i > 0; i--) {
            if (game.x[0] === game.x[i] && game.y[0] === game.y[i]) {
                game.running = false;
                break;
            }
        }
        if (game.x[0] < 0 || game.x[0] >= WALL_RIGHT || game.y[0] < 0 || game.y[0] >= WALL_BOTTOM) {
            game.running = false;
        }
        if (!game.running) {
            game.shakeMagnitude = 10;
            showGameOver();
        }
    }

    function doTick() {
        for (let i = 0; i < game.bodyParts; i++) {
            game.prevX[i] = game.x[i];
            game.prevY[i] = game.y[i];
        }
        if (game.queuedDirection) {
            game.direction = game.queuedDirection;
            game.queuedDirection = "";
        }
        game.directionChangedThisTick = false;
        move();
        checkApple();
        checkCollisions();
        if (state === "game") {
            for (let i = 0; i < game.bodyParts; i++) {
                game.displayX[i] = game.x[i];
                game.displayY[i] = game.y[i];
            }
        }
    }

    function updateGame(dt) {
        if (game.running && !game.paused && !game.countdownActive) {
            game.tickAcc += dt;
            while (game.tickAcc >= DELAY && state === "game" && game.running) {
                game.tickAcc -= DELAY;
                doTick();
            }
        }

        if (game.shakeMagnitude > 0) {
            game.shakeX = (Math.random() - 0.5) * game.shakeMagnitude * 2;
            game.shakeY = (Math.random() - 0.5) * game.shakeMagnitude * 2;
            game.shakeMagnitude *= 0.85;
            if (game.shakeMagnitude < 0.5) {
                game.shakeMagnitude = 0;
                game.shakeX = 0;
                game.shakeY = 0;
            }
        }

        game.foodParticles = game.foodParticles.filter((p) => p.alpha > 0);
        for (const p of game.foodParticles) {
            p.x += p.vx;
            p.y += p.vy;
            p.vy += 0.1;
            p.alpha -= 0.03;
            p.size *= 0.97;
        }

        game.scorePopups = game.scorePopups.filter((p) => p.alpha > 0);
        for (const p of game.scorePopups) {
            p.y -= 1.5;
            p.alpha -= 0.02;
        }

        if (game.transitioningIn) {
            game.transitionAlpha -= 0.04;
            if (game.transitionAlpha <= 0) {
                game.transitionAlpha = 0;
                game.transitioningIn = false;
            }
        }

        if (game.neonPulseUp) {
            game.neonPulse += 0.02;
            if (game.neonPulse >= 1) game.neonPulseUp = false;
        } else {
            game.neonPulse -= 0.02;
            if (game.neonPulse <= 0) game.neonPulseUp = true;
        }
    }

    function spawnFoodParticles(fx, fy) {
        const base = FOOD_COLORS[game.selectedFood];
        for (let i = 0; i < 12; i++) {
            game.foodParticles.push({
                x: fx + UNIT / 2,
                y: fy + UNIT / 2,
                vx: (Math.random() - 0.5) * 6,
                vy: (Math.random() - 0.5) * 6,
                size: Math.random() * 5 + 2,
                alpha: 1,
                color: [
                    Math.min(255, base[0] + Math.floor(Math.random() * 50)),
                    Math.min(255, base[1] + Math.floor(Math.random() * 50)),
                    Math.min(255, base[2] + Math.floor(Math.random() * 50)),
                ],
            });
        }
    }

    function drawGrid() {
        let light, dark;
        if (game.selectedMap === "desert") {
            light = MAP_GRID_COLORS.desert[0];
            dark = MAP_GRID_COLORS.desert[1];
        } else if (game.selectedMap === "ocean") {
            light = MAP_GRID_COLORS.ocean[0];
            dark = MAP_GRID_COLORS.ocean[1];
        } else if (game.selectedMap === "neon") {
            const pulse = game.neonPulse * 0.3;
            light = [Math.trunc(30 + pulse * 20), Math.trunc(30 + pulse * 10), Math.trunc(50 + pulse * 30)];
            dark = [Math.trunc(20 + pulse * 15), Math.trunc(20 + pulse * 8), Math.trunc(40 + pulse * 25)];
        } else {
            light = MAP_GRID_COLORS.classic[0];
            dark = MAP_GRID_COLORS.classic[1];
        }

        for (let i = 0; i < 24; i++) {
            for (let j = 0; j < 28; j++) {
                ctx.fillStyle = rgba((i + j) % 2 === 0 ? light : dark);
                ctx.fillRect(j * UNIT, i * UNIT, UNIT, UNIT);
            }
        }

        if (game.selectedMap === "neon") {
            ctx.strokeStyle = rgba([0, 255, 255], 0.08 + game.neonPulse * 0.05);
            ctx.lineWidth = 0.5;
        } else {
            ctx.strokeStyle = rgba([0, 0, 0], JA(30));
            ctx.lineWidth = 1;
        }
        ctx.beginPath();
        for (let i = 0; i <= 24; i++) {
            ctx.moveTo(0, i * UNIT);
            ctx.lineTo(SCREEN_W, i * UNIT);
        }
        for (let j = 0; j <= 28; j++) {
            ctx.moveTo(j * UNIT, 0);
            ctx.lineTo(j * UNIT, SCREEN_H);
        }
        ctx.stroke();

        if (game.selectedMap === "neon") {
            ctx.globalAlpha = 0.2 + game.neonPulse * 0.15;
            ctx.strokeStyle = rgba([0, 255, 255]);
            ctx.lineWidth = 3;
            ctx.strokeRect(0, 0, 28 * UNIT, 24 * UNIT);
            ctx.globalAlpha = 1;
        }

        if (game.selectedMap === "desert") {
            ctx.globalAlpha = 0.1;
            ctx.fillStyle = rgba([180, 140, 80]);
            for (const [tx, ty] of DESERT_DOTS) {
                ctx.beginPath();
                ctx.ellipse(tx + 1, ty + 1, 1, 1, 0, 0, Math.PI * 2);
                ctx.fill();
            }
            ctx.globalAlpha = 1;
        }

        if (game.selectedMap === "ocean") {
            ctx.globalAlpha = 0.06;
            ctx.strokeStyle = rgba([255, 255, 255]);
            ctx.lineWidth = 2;
            const time = performance.now();
            for (let i = 0; i < 5; i++) {
                const waveY = (SCREEN_H / 6.0) * (i + 1) + Math.sin(time * 0.001 + i) * 10;
                ctx.beginPath();
                ctx.moveTo(0, waveY);
                for (let wx = 0; wx < SCREEN_W; wx += 20) {
                    ctx.lineTo(wx, waveY + Math.sin(wx * 0.03 + time * 0.002 + i) * 8);
                }
                ctx.stroke();
            }
            ctx.globalAlpha = 1;
        }

        ctx.lineWidth = 1;
    }

    function drawFood() {
        game.foodBob += 0.08;
        const bobOffset = Math.sin(game.foodBob) * 3;
        const scaleOsc = 1 + Math.sin(game.foodBob * 1.5) * 0.05;
        const drawX = game.appleX;
        const drawY = game.appleY + bobOffset;
        const drawSize = Math.trunc(UNIT * scaleOsc);
        const offset = Math.trunc((drawSize - UNIT) / 2);

        const glow = FOOD_GLOW[game.selectedFood];
        for (let i = 6; i > 0; i--) {
            fillOval(
                drawX - i - offset,
                drawY - i - offset,
                drawSize + i * 2,
                drawSize + i * 2,
                rgba(glow),
                (i / 6) * 0.15 * JA(100)
            );
        }

        const foodImg = { apple: img.apple, banana: img.banana, grape: img.grape, orange: img.orange }[game.selectedFood];
        if (foodImg) {
            ctx.drawImage(foodImg, drawX - offset, drawY - offset, drawSize, drawSize);
        } else {
            fillOval(drawX - offset, drawY - offset, drawSize, drawSize, rgba(FOOD_COLORS[game.selectedFood]));
        }

        fillOval(drawX + Math.trunc(UNIT / 4), drawY + Math.trunc(UNIT / 4), Math.trunc(UNIT / 4), Math.trunc(UNIT / 4), rgba([255, 255, 255]), 0.4);
    }

    function getSnakeColor(index) {
        const base = SNAKE_COLORS[game.selectedColor] || SNAKE_COLORS.green;
        if (index > 0) {
            const factor = 1 - (index / (game.bodyParts + 5)) * 0.4;
            return [
                Math.max(0, Math.trunc(base[0] * factor)),
                Math.max(0, Math.trunc(base[1] * factor)),
                Math.max(0, Math.trunc(base[2] * factor)),
            ];
        }
        return base;
    }

    const gradientCache = new Map();

    function bodyGradient(c1, c2) {
        const key = "b:" + c1.join() + ">" + c2.join();
        let g = gradientCache.get(key);
        if (!g) {
            g = mainCtx.createLinearGradient(0, 0, UNIT, UNIT);
            g.addColorStop(0, rgba(c1));
            g.addColorStop(1, rgba(c2));
            gradientCache.set(key, g);
        }
        return g;
    }

    function headGradient(c) {
        const bright = brighter(c);
        const key = "h:" + bright.join() + ">" + c.join();
        let g = gradientCache.get(key);
        if (!g) {
            g = mainCtx.createRadialGradient(UNIT / 2, UNIT / 2, 0, UNIT / 2, UNIT / 2, UNIT / 2);
            g.addColorStop(0, rgba(bright));
            g.addColorStop(1, rgba(c));
            gradientCache.set(key, g);
        }
        return g;
    }

    function drawSnake() {
        for (let i = game.bodyParts - 1; i >= 0; i--) {
            const baseColor = getSnakeColor(i);
            const dx = game.displayX[i];
            const dy = game.displayY[i];

            if (game.selectedMap === "neon") {
                fillRR(dx - 3, dy - 3, UNIT + 6, UNIT + 6, 12, rgba(brighter(baseColor)), 0.3 + game.neonPulse * 0.1);
            }

            ctx.save();
            ctx.translate(dx, dy);
            if (i === 0) {
                ctx.fillStyle = headGradient(baseColor);
            } else {
                const br = 1 - (i / game.bodyParts) * 0.3;
                const bodyColor = [
                    Math.min(255, Math.trunc(baseColor[0] * br)),
                    Math.min(255, Math.trunc(baseColor[1] * br)),
                    Math.min(255, Math.trunc(baseColor[2] * br)),
                ];
                ctx.fillStyle = bodyGradient(bodyColor, darker(bodyColor));
            }
            roundRectPath(ctx, 0, 0, UNIT, UNIT, 5);
            ctx.fill();

            ctx.globalAlpha = 0.15;
            ctx.fillStyle = rgba([255, 255, 255]);
            roundRectPath(ctx, 2, 1, UNIT - 4, Math.trunc(UNIT / 2), 3);
            ctx.fill();
            ctx.globalAlpha = 1;
            ctx.restore();

            if (i === 0) drawSnakeEyes(dx, dy);

            if (i > 0) {
                const prevDx = game.displayX[i - 1];
                const prevDy = game.displayY[i - 1];
                if (Math.abs(dx - prevDx) < UNIT * 2 && Math.abs(dy - prevDy) < UNIT * 2) {
                    ctx.fillStyle = rgba(baseColor);
                    if (Math.abs(dx - prevDx) < 1) {
                        ctx.fillRect(Math.trunc(dx) + Math.trunc(UNIT / 4), Math.trunc(Math.min(dy, prevDy)) + Math.trunc(UNIT / 2), Math.trunc(UNIT / 2), Math.trunc(Math.abs(dy - prevDy)));
                    } else if (Math.abs(dy - prevDy) < 1) {
                        ctx.fillRect(Math.trunc(Math.min(dx, prevDx)) + Math.trunc(UNIT / 2), Math.trunc(dy) + Math.trunc(UNIT / 4), Math.trunc(Math.abs(dx - prevDx)), Math.trunc(UNIT / 2));
                    }
                }
            }
        }
        drawSnakeTongue();
    }

    function fillOvalRect(x, y, w, h) {
        ctx.beginPath();
        ctx.ellipse(x + w / 2, y + h / 2, w / 2, h / 2, 0, 0, Math.PI * 2);
        ctx.fill();
    }

    function drawSnakeEyes(dx, dy) {
        const eyeSize = Math.trunc(UNIT / 5);
        const eyeOffset = Math.trunc(UNIT / 4);
        const half = Math.trunc(eyeSize / 2);
        const quarter = Math.trunc(eyeSize / 4);
        ctx.fillStyle = rgba([255, 255, 255]);
        switch (game.direction) {
            case "R":
                fillOvalRect(dx + UNIT - eyeOffset - half, dy + eyeOffset, eyeSize, eyeSize);
                fillOvalRect(dx + UNIT - eyeOffset - half, dy + UNIT - eyeOffset - eyeSize, eyeSize, eyeSize);
                ctx.fillStyle = rgba([0, 0, 0]);
                fillOvalRect(dx + UNIT - eyeOffset, dy + eyeOffset + quarter, half, half);
                fillOvalRect(dx + UNIT - eyeOffset, dy + UNIT - eyeOffset - eyeSize + quarter, half, half);
                break;
            case "L":
                fillOvalRect(dx + eyeOffset - half, dy + eyeOffset, eyeSize, eyeSize);
                fillOvalRect(dx + eyeOffset - half, dy + UNIT - eyeOffset - eyeSize, eyeSize, eyeSize);
                ctx.fillStyle = rgba([0, 0, 0]);
                fillOvalRect(dx + eyeOffset - half, dy + eyeOffset + quarter, half, half);
                fillOvalRect(dx + eyeOffset - half, dy + UNIT - eyeOffset - eyeSize + quarter, half, half);
                break;
            case "U":
                fillOvalRect(dx + eyeOffset, dy + eyeOffset - half, eyeSize, eyeSize);
                fillOvalRect(dx + UNIT - eyeOffset - eyeSize, dy + eyeOffset - half, eyeSize, eyeSize);
                ctx.fillStyle = rgba([0, 0, 0]);
                fillOvalRect(dx + eyeOffset + quarter, dy + eyeOffset - half, half, half);
                fillOvalRect(dx + UNIT - eyeOffset - eyeSize + quarter, dy + eyeOffset - half, half, half);
                break;
            case "D":
                fillOvalRect(dx + eyeOffset, dy + UNIT - eyeOffset - half, eyeSize, eyeSize);
                fillOvalRect(dx + UNIT - eyeOffset - eyeSize, dy + UNIT - eyeOffset - half, eyeSize, eyeSize);
                ctx.fillStyle = rgba([0, 0, 0]);
                fillOvalRect(dx + eyeOffset + quarter, dy + UNIT - eyeOffset, half, half);
                fillOvalRect(dx + UNIT - eyeOffset - eyeSize + quarter, dy + UNIT - eyeOffset, half, half);
                break;
        }
    }

    function drawSnakeTongue() {
        // Lidah hanya tampak ketika sin positif, persis flicker versi Java
        if (Math.sin(performance.now() * 0.01) <= 0) return;
        ctx.fillStyle = rgba([255, 0, 0]);
        const tongueLength = Math.trunc(UNIT / 2);
        const tongueWidth = Math.trunc(UNIT / 8);
        const tongueY = game.y[0] + Math.trunc(UNIT / 2);
        const tongueX = game.x[0] + Math.trunc(UNIT / 2);
        switch (game.direction) {
            case "R":
                ctx.fillRect(game.x[0] + UNIT, tongueY - Math.trunc(tongueWidth / 2), tongueLength, tongueWidth);
                ctx.fillRect(game.x[0] + UNIT + tongueLength, tongueY - tongueWidth * 2, Math.trunc(tongueLength / 2), tongueWidth);
                ctx.fillRect(game.x[0] + UNIT + tongueLength, tongueY + tongueWidth, Math.trunc(tongueLength / 2), tongueWidth);
                break;
            case "L":
                ctx.fillRect(game.x[0] - tongueLength, tongueY - Math.trunc(tongueWidth / 2), tongueLength, tongueWidth);
                ctx.fillRect(game.x[0] - tongueLength - Math.trunc(tongueLength / 2), tongueY - tongueWidth * 2, Math.trunc(tongueLength / 2), tongueWidth);
                ctx.fillRect(game.x[0] - tongueLength - Math.trunc(tongueLength / 2), tongueY + tongueWidth, Math.trunc(tongueLength / 2), tongueWidth);
                break;
            case "U":
                ctx.fillRect(tongueX - Math.trunc(tongueWidth / 2), game.y[0] - tongueLength, tongueWidth, tongueLength);
                ctx.fillRect(tongueX - tongueWidth * 2, game.y[0] - tongueLength - Math.trunc(tongueLength / 2), tongueWidth, Math.trunc(tongueLength / 2));
                ctx.fillRect(tongueX + tongueWidth, game.y[0] - tongueLength - Math.trunc(tongueLength / 2), tongueWidth, Math.trunc(tongueLength / 2));
                break;
            case "D":
                ctx.fillRect(tongueX - Math.trunc(tongueWidth / 2), game.y[0] + UNIT, tongueWidth, tongueLength);
                ctx.fillRect(tongueX - tongueWidth * 2, game.y[0] + UNIT + tongueLength, tongueWidth, Math.trunc(tongueLength / 2));
                ctx.fillRect(tongueX + tongueWidth, game.y[0] + UNIT + tongueLength, tongueWidth, Math.trunc(tongueLength / 2));
                break;
        }
    }

    function drawScore() {
        const panelX = 15, panelY = 15, panelW = 150, panelH = 60;
        fillRR(panelX, panelY, panelW, panelH, 10, rgba([0, 0, 0]), JA(130));

        const border = MAP_BORDER[game.selectedMap] || MAP_BORDER.classic;
        strokeRR(panelX, panelY, panelW, panelH, 10, rgba(border.color), 1.5, JA(border.alpha));

        drawText("SCORE", panelX + 10, panelY + 25, { size: 16, weight: "bold", color: [255, 255, 255] });
        drawText(String(game.applesEaten), panelX + panelW - 10, panelY + 25, { size: 16, weight: "bold", color: [255, 255, 255], align: "right" });

        drawText("BEST", panelX + 10, panelY + 45, { size: 16, weight: "bold", color: [255, 215, 0] });
        if (game.applesEaten > game.bestScore) {
            const alpha = Math.abs(Math.sin(performance.now() * 0.005)) * 0.7 + 0.3;
            drawText(String(game.applesEaten), panelX + panelW - 10, panelY + 45, { size: 16, weight: "bold", color: [255, 100, 100], align: "right", alpha });
        } else {
            drawText(String(game.bestScore), panelX + panelW - 10, panelY + 45, { size: 16, weight: "bold", color: [255, 215, 0], align: "right" });
        }

        const mapName = game.selectedMap.toUpperCase();
        const mapW = measure(mapName, 10, "bold");
        const badgeX = SCREEN_W - mapW - 25;
        fillRR(badgeX - 5, 15, mapW + 10, 18, 6, rgba([0, 0, 0]), JA(120));
        strokeRR(badgeX - 5, 15, mapW + 10, 18, 6, rgba(border.color), 1.5, JA(border.alpha));
        drawText(mapName, badgeX, 28, { size: 10, weight: "bold", color: [255, 255, 255], alpha: JA(200) });

        const hintText = "WASD/Arrow: Gerak  |  ESC: Pause";
        drawText(hintText, SCREEN_W / 2, SCREEN_H - 12, { size: 11, color: [255, 255, 255], alpha: JA(120), align: "center" });
    }

    function drawCountdown() {
        ctx.fillStyle = rgba([0, 0, 0], JA(150));
        ctx.fillRect(0, 0, SCREEN_W, SCREEN_H);

        const text = game.countdown > 0 ? String(game.countdown) : "GO!";
        const size = Math.trunc(80 * (1 + Math.sin(performance.now() * 0.005) * 0.1));

        drawText(text, SCREEN_W / 2 + 3, SCREEN_H / 2 + 3, { size, weight: "bold", color: [0, 0, 0], alpha: JA(150), align: "center" });
        drawText(text, SCREEN_W / 2, Math.trunc(SCREEN_H / 2), {
            size,
            weight: "bold",
            color: game.countdown > 0 ? [255, 255, 100] : [100, 255, 100],
            align: "center",
        });
        drawText("Bersiap...", SCREEN_W / 2, Math.trunc(SCREEN_H / 2) + 50, { size: 16, color: [200, 200, 200], align: "center" });
    }

    function drawPauseButton(text, rect, bgColor, isSelected) {
        const [x, y, w, h] = rect;
        if (isSelected) {
            ctx.fillStyle = rgba([255, 255, 200]);
            ctx.globalAlpha = 0.3;
            for (let i = 6; i > 0; i--) {
                roundRectPath(ctx, x - i, y - i, w + i * 2, h + i * 2, 7.5);
                ctx.fill();
            }
            ctx.globalAlpha = 1;
        }
        const bg = isSelected ? brighter(bgColor) : bgColor;
        const grad = ctx.createLinearGradient(x, y, x, y + h);
        grad.addColorStop(0, rgba(bg));
        grad.addColorStop(1, rgba(darker(bg)));
        ctx.fillStyle = grad;
        roundRectPath(ctx, x, y, w, h, 7.5);
        ctx.fill();
        strokeRR(x, y, w, h, 15, rgba(isSelected ? [255, 255, 255] : darker(darker(bg))), isSelected ? 3 : 2);

        if (isSelected) {
            const pulse = Math.sin(performance.now() * 0.005) * 3;
            const arrowX = x - 20 + pulse;
            ctx.fillStyle = rgba([255, 255, 100]);
            ctx.beginPath();
            ctx.moveTo(arrowX, y + h / 2);
            ctx.lineTo(arrowX - 10, y + h / 2 - 7);
            ctx.lineTo(arrowX - 10, y + h / 2 + 7);
            ctx.fill();
        }

        drawText(text, x + w / 2, y + h / 2, { size: 20, weight: "bold", color: [255, 255, 255], align: "center", baseline: "middle" });
    }

    function drawPauseScreen() {
        ctx.fillStyle = rgba([0, 0, 0], JA(150));
        ctx.fillRect(0, 0, SCREEN_W, SCREEN_H);

        drawText("PAUSED", SCREEN_W / 2, Math.trunc(SCREEN_H / 3), { size: 40, weight: "bold", color: [255, 255, 255], align: "center" });

        const btnW = 200, btnH = 50;
        const centerX = Math.trunc(SCREEN_W / 2 - btnW / 2);
        const resumeRect = [centerX, Math.trunc(SCREEN_H / 2) - 60, btnW, btnH];
        const menuRect = [centerX, Math.trunc(SCREEN_H / 2) + 10, btnW, btnH];
        drawPauseButton("RESUME", resumeRect, [0, 150, 0], game.pauseSelectedIndex === 0);
        drawPauseButton("MAIN MENU", menuRect, [150, 0, 0], game.pauseSelectedIndex === 1);

        const controlText = "[W/S] Navigasi  |  [ENTER] Pilih  |  [ESC] Menu Utama";
        drawText(controlText, SCREEN_W / 2, Math.trunc(SCREEN_H / 2) + 100, { size: 13, weight: "bold", color: [200, 200, 200], align: "center" });
    }

    function drawGame() {
        ctx.save();
        if (game.shakeMagnitude > 0) ctx.translate(game.shakeX, game.shakeY);
        drawGrid();
        if (game.running) {
            drawFood();
            for (const p of game.foodParticles) {
                fillOval(Math.trunc(p.x), Math.trunc(p.y), Math.trunc(p.size), Math.trunc(p.size), rgba(p.color), Math.max(0, p.alpha));
            }
            drawSnake();
            for (const p of game.scorePopups) {
                drawText(p.text, Math.trunc(p.x), Math.trunc(p.y), { size: 18, weight: "bold", color: [255, 255, 100], alpha: Math.max(0, p.alpha) });
            }
            drawScore();
            if (game.countdownActive) drawCountdown();
            if (game.paused && !game.countdownActive) drawPauseScreen();
        }
        ctx.restore();
    }

    function showGameOver() {
        state = "gameover";
        over.score = game.applesEaten;
        over.bestScore = game.bestScore;
        over.fadeIn = 0;
        over.textPulse = 0;
        over.selectedOption = 0;
        over.message = GAME_OVER_MESSAGES[Math.floor(Math.random() * GAME_OVER_MESSAGES.length)];
        over.titleShake = 15;
        over.particles = [];
        for (let i = 0; i < 40; i++) over.particles.push(makeDeathParticle());
    }

    function resetGameAndStart() {
        game.bodyParts = 6;
        game.applesEaten = 0;
        game.direction = "R";
        game.paused = false;
        game.foodParticles = [];
        game.scorePopups = [];
        game.shakeMagnitude = 0;
        game.shakeX = 0;
        game.shakeY = 0;
        game.queuedDirection = "";
        game.directionChangedThisTick = false;
        game.tickAcc = 0;
        gradientCache.clear();

        const startX = Math.trunc(SCREEN_W / UNIT) / 2 * UNIT;
        const startY = Math.trunc(SCREEN_H / UNIT) / 2 * UNIT;
        for (let i = 0; i < game.bodyParts; i++) {
            game.x[i] = startX - i * UNIT;
            game.y[i] = startY;
            game.displayX[i] = game.x[i];
            game.displayY[i] = game.y[i];
            game.prevX[i] = game.x[i];
            game.prevY[i] = game.y[i];
        }
        newApple();
        state = "game";
        startCountdown();
    }

    function returnToMainMenu() {
        stopCountdown();
        if (game.applesEaten > game.bestScore) {
            game.bestScore = game.applesEaten;
            saveBestScore();
        }
        state = "menu";
        resetMenu();
    }

    // ===== Game over =====
    function updateOver() {
        if (over.fadeIn < 1) over.fadeIn = Math.min(1, over.fadeIn + 0.03);
        over.titleShake *= 0.95;
        over.textPulse += 0.05;
        for (const p of over.particles) {
            p.x += p.vx;
            p.y += p.vy;
            p.alpha -= 0.001;
            if (p.y > SCREEN_H + 10 || p.alpha <= 0) resetDeathParticle(p);
        }
    }

    function drawOptionButton(text, x, y, w, h, baseColor, isSelected) {
        if (isSelected) {
            ctx.fillStyle = rgba([255, 255, 200]);
            ctx.globalAlpha = 0.25 * over.fadeIn;
            for (let i = 5; i > 0; i--) {
                roundRectPath(ctx, x - i, y - i, w + i * 2, h + i * 2, 6);
                ctx.fill();
            }
            ctx.globalAlpha = 1;
        }
        const bg = isSelected ? brighter(baseColor) : baseColor;
        const grad = ctx.createLinearGradient(x, y, x, y + h);
        grad.addColorStop(0, rgba(bg));
        grad.addColorStop(1, rgba(darker(bg)));
        ctx.globalAlpha = over.fadeIn;
        ctx.fillStyle = grad;
        roundRectPath(ctx, x, y, w, h, 5);
        ctx.fill();
        ctx.strokeStyle = rgba(isSelected ? [255, 255, 255] : darker(darker(bg)));
        ctx.lineWidth = isSelected ? 2.5 : 1.5;
        roundRectPath(ctx, x, y, w, h, 5);
        ctx.stroke();
        ctx.globalAlpha = 1;
        drawText(text, x + w / 2, y + h / 2, { size: 16, weight: "bold", color: [255, 255, 255], align: "center", baseline: "middle", alpha: over.fadeIn });
    }

    function drawGameOver() {
        if (img.bg3) ctx.drawImage(img.bg3, 0, 0, SCREEN_W, SCREEN_H);
        for (const p of over.particles) {
            fillOval(Math.trunc(p.x), Math.trunc(p.y), Math.trunc(p.size), Math.trunc(p.size), rgba(p.color), Math.max(0, p.alpha));
        }

        const overlayAlpha = Math.min(200, Math.trunc(200 * over.fadeIn));
        ctx.fillStyle = rgba([0, 0, 0], JA(overlayAlpha));
        ctx.fillRect(0, 0, SCREEN_W, SCREEN_H);

        const shakeOffset = Math.sin(performance.now() * 0.02) * over.titleShake;

        drawText("GAME OVER", SCREEN_W / 2 + 3 + shakeOffset, Math.trunc(SCREEN_H / 3) + 3, { size: 42, weight: "bold", color: [100, 0, 0], alpha: JA(150) * over.fadeIn, align: "center" });
        const grad = ctx.createLinearGradient(0, Math.trunc(SCREEN_H / 3) - 30, SCREEN_W, Math.trunc(SCREEN_H / 3) - 30);
        grad.addColorStop(0, rgba([255, 50, 50]));
        grad.addColorStop(1, rgba([255, 150, 0]));
        setFont(42, "bold");
        ctx.globalAlpha = over.fadeIn;
        ctx.fillStyle = grad;
        ctx.textAlign = "center";
        ctx.fillText("GAME OVER", SCREEN_W / 2 + shakeOffset, Math.trunc(SCREEN_H / 3));
        ctx.globalAlpha = 1;
        ctx.textAlign = "left";

        drawText(over.message, SCREEN_W / 2, Math.trunc(SCREEN_H / 2) - 50, { size: 22, weight: "bold", color: [255, 255, 0], align: "center", alpha: over.fadeIn });

        const scoreText = "SKOR AKHIR: " + over.score;
        // Java menggabungkan alpha warna (150*fadeIn) dengan composite fadeIn
        drawText(scoreText, SCREEN_W / 2 + 2, Math.trunc(SCREEN_H / 2) + 2, { size: 22, weight: "bold", color: [0, 0, 0], alpha: JA(150) * over.fadeIn * over.fadeIn, align: "center" });
        drawText(scoreText, SCREEN_W / 2, Math.trunc(SCREEN_H / 2), { size: 22, weight: "bold", color: [255, 255, 255], align: "center", alpha: over.fadeIn });

        if (over.score >= over.bestScore && over.score > 0) {
            const pulse = Math.sin(over.textPulse) * 0.3 + 0.7;
            drawText("★ REKOR BARU! ★", SCREEN_W / 2, Math.trunc(SCREEN_H / 2) + 30, { size: 18, weight: "bold", color: [255, 215, 0], align: "center", alpha: pulse * over.fadeIn });
        }

        const btnW = 200, btnH = 40;
        const btnX = Math.trunc((SCREEN_W - btnW) / 2);
        const btn1Y = Math.trunc((SCREEN_H * 2) / 3) - 25;
        const btn2Y = Math.trunc((SCREEN_H * 2) / 3) + 25;
        drawOptionButton("▶ COBA LAGI", btnX, btn1Y, btnW, btnH, [0, 180, 0], over.selectedOption === 0);
        drawOptionButton("▶ MENU UTAMA", btnX, btn2Y, btnW, btnH, [180, 0, 0], over.selectedOption === 1);

        const hintText = "[W/S] Navigasi  |  [ENTER/SPACE] Pilih  |  [ESC] Menu Utama";
        const hw = measure(hintText, 12, "bold");
        fillRR((SCREEN_W - hw) / 2 - 10, SCREEN_H - 40, hw + 20, 20, 8, rgba([0, 0, 0]), JA(120) * over.fadeIn);
        drawText(hintText, (SCREEN_W - hw) / 2, SCREEN_H - 26, { size: 12, weight: "bold", color: [200, 200, 200], alpha: over.fadeIn });
    }

    // ===== Posisi kursor di ruang koordinat canvas (aman terhadap transform CSS) =====
    function canvasPos(e) {
        const rect = gameCanvas.getBoundingClientRect();
        return {
            x: ((e.clientX - rect.left) * SCREEN_W) / rect.width,
            y: ((e.clientY - rect.top) * SCREEN_H) / rect.height,
        };
    }

    function inRect(p, rect) {
        return p.x >= rect[0] && p.x <= rect[0] + rect[2] && p.y >= rect[1] && p.y <= rect[1] + rect[3];
    }

    // ===== Mouse =====
    gameCanvas.addEventListener("mousemove", (e) => {
        if (state !== "menu" || menu.customActive) return;
        const p = canvasPos(e);
        if (inRect(p, [BUTTON_X, START_BUTTON_Y, BUTTON_W, BUTTON_H])) menu.menuSelectedIndex = 0;
        else if (inRect(p, [BUTTON_X, CUSTOM_BUTTON_Y, BUTTON_W, BUTTON_H])) menu.menuSelectedIndex = 1;
        else if (inRect(p, [BUTTON_X, EXIT_BUTTON_Y, BUTTON_W, BUTTON_H])) menu.menuSelectedIndex = 2;
        for (let i = 0; i < 3; i++) menu.hoverTarget[i] = i === menu.menuSelectedIndex ? 1.08 : 1.0;
    });

    function exitApp() {
        window.close();
        setTimeout(() => {
            if (!window.closed) exitEl.hidden = false;
        }, 250);
    }

    exitEl.addEventListener("click", () => location.reload());

    gameCanvas.addEventListener("click", (e) => {
        const p = canvasPos(e);
        if (state === "menu") {
            if (!menu.customActive) {
                if (inRect(p, [BUTTON_X, START_BUTTON_Y, BUTTON_W, BUTTON_H])) startGameWithTransition();
                else if (inRect(p, [BUTTON_X, CUSTOM_BUTTON_Y, BUTTON_W, BUTTON_H])) {
                    menu.customActive = true;
                    menu.customCategory = 0;
                } else if (inRect(p, [BUTTON_X, EXIT_BUTTON_Y, BUTTON_W, BUTTON_H])) exitApp();
            } else if (inRect(p, [0, 0, BACK_W, BACK_H])) {
                menu.customActive = false;
            } else {
                for (const key of COLOR_KEYS) {
                    if (inRect(p, COLOR_RECTS[key])) {
                        menu.selectedColor = key;
                        menu.customCategory = 0;
                        return;
                    }
                }
                for (const key of FOOD_KEYS) {
                    if (inRect(p, FOOD_RECTS[key])) {
                        menu.selectedFood = key;
                        menu.customCategory = 1;
                        return;
                    }
                }
                for (const key of MAP_KEYS) {
                    if (inRect(p, MAP_RECTS[key])) {
                        menu.selectedMap = key;
                        menu.customCategory = 2;
                        return;
                    }
                }
            }
        } else if (state === "game") {
            if (game.paused && !game.countdownActive) {
                const btnW = 200, btnH = 50;
                const centerX = Math.trunc(SCREEN_W / 2 - btnW / 2);
                if (inRect(p, [centerX, Math.trunc(SCREEN_H / 2) - 60, btnW, btnH])) {
                    game.paused = false;
                } else if (inRect(p, [centerX, Math.trunc(SCREEN_H / 2) + 10, btnW, btnH])) {
                    returnToMainMenu();
                }
            }
        } else if (state === "gameover") {
            const btnW = 200, btnH = 40;
            const btnX = Math.trunc((SCREEN_W - btnW) / 2);
            const btn1Y = Math.trunc((SCREEN_H * 2) / 3) - 25;
            const btn2Y = Math.trunc((SCREEN_H * 2) / 3) + 25;
            if (inRect(p, [btnX, btn1Y, btnW, btnH])) {
                over.selectedOption = 0;
                resetGameAndStart();
            } else if (inRect(p, [btnX, btn2Y, btnW, btnH])) {
                returnToMainMenu();
            }
        }
    });

    // ===== Keyboard =====
    function cyclePauseSelection(delta) {
        game.pauseSelectedIndex = ((game.pauseSelectedIndex + delta) % 2 + 2) % 2;
    }

    window.addEventListener("keydown", (e) => {
        if (state === "loading") return;
        const code = e.code;
        const isUp = code === "KeyW" || code === "ArrowUp";
        const isDown = code === "KeyS" || code === "ArrowDown";
        const isLeft = code === "KeyA" || code === "ArrowLeft";
        const isRight = code === "KeyD" || code === "ArrowRight";
        if (isUp || isDown || isLeft || isRight || code === "Space") e.preventDefault();

        if (state === "menu") {
            if (code === "Escape") {
                if (menu.customActive) menu.customActive = false;
                else exitApp();
                return;
            }
            if (menu.customActive) {
                const cat = menu.customCategory;
                const keys = cat === 0 ? COLOR_KEYS : cat === 1 ? FOOD_KEYS : MAP_KEYS;
                const prop = cat === 0 ? "selectedColor" : cat === 1 ? "selectedFood" : "selectedMap";
                if (isLeft || isRight) {
                    let idx = keys.indexOf(menu[prop]);
                    idx = isLeft ? (idx - 1 + keys.length) % keys.length : (idx + 1) % keys.length;
                    menu[prop] = keys[idx];
                } else if (isUp) {
                    menu.customCategory = (cat + 2) % 3;
                } else if (isDown) {
                    menu.customCategory = (cat + 1) % 3;
                } else if (code === "Enter") {
                    menu.customActive = false;
                }
            } else {
                if (isUp) {
                    menu.menuSelectedIndex = (menu.menuSelectedIndex + 2) % 3;
                } else if (isDown) {
                    menu.menuSelectedIndex = (menu.menuSelectedIndex + 1) % 3;
                } else if (code === "Enter" || code === "Space") {
                    if (menu.menuSelectedIndex === 0) startGameWithTransition();
                    else if (menu.menuSelectedIndex === 1) {
                        menu.customActive = true;
                        menu.customCategory = 0;
                    } else exitApp();
                }
                for (let i = 0; i < 3; i++) menu.hoverTarget[i] = i === menu.menuSelectedIndex ? 1.08 : 1.0;
            }
        } else if (state === "game") {
            if (game.countdownActive) return;
            if (game.running) {
                if (game.paused) {
                    if (code === "Escape") returnToMainMenu();
                    else if (code === "Space" || code === "Enter") {
                        if (game.pauseSelectedIndex === 0) game.paused = false;
                        else returnToMainMenu();
                    } else if (isUp) cyclePauseSelection(-1);
                    else if (isDown) cyclePauseSelection(1);
                } else {
                    if (isLeft) {
                        if (game.direction !== "R") {
                            if (!game.directionChangedThisTick) { game.direction = "L"; game.directionChangedThisTick = true; }
                            else game.queuedDirection = "L";
                        }
                    } else if (isRight) {
                        if (game.direction !== "L") {
                            if (!game.directionChangedThisTick) { game.direction = "R"; game.directionChangedThisTick = true; }
                            else game.queuedDirection = "R";
                        }
                    } else if (isUp) {
                        if (game.direction !== "D") {
                            if (!game.directionChangedThisTick) { game.direction = "U"; game.directionChangedThisTick = true; }
                            else game.queuedDirection = "U";
                        }
                    } else if (isDown) {
                        if (game.direction !== "U") {
                            if (!game.directionChangedThisTick) { game.direction = "D"; game.directionChangedThisTick = true; }
                            else game.queuedDirection = "D";
                        }
                    } else if (code === "Escape" || code === "Space") {
                        game.pauseSelectedIndex = 0;
                        game.paused = true;
                    }
                }
            }
        } else if (state === "gameover") {
            if (code === "Space" || code === "Enter") {
                if (over.selectedOption === 0) resetGameAndStart();
                else returnToMainMenu();
            } else if (code === "Escape") {
                returnToMainMenu();
            } else if (isUp || isDown) {
                over.selectedOption = (over.selectedOption + 1) % 2;
            }
        }
    });

    // ===== Skala responsif (game 713x613 diskalakan proporsional) =====
    function fitScale() {
        const heading = document.querySelector("h2");
        const instructions = document.querySelector(".instructions");
        const usedH = heading.offsetHeight + 10 + instructions.offsetHeight + 15 + 24;
        const availW = Math.min(window.innerWidth - 16, SCREEN_W);
        const availH = window.innerHeight - usedH;
        const s = Math.max(0.2, Math.min(1, availW / SCREEN_W, availH / SCREEN_H));
        container.style.transform = `scale(${s})`;
        scaleWrap.style.width = `${SCREEN_W * s}px`;
        scaleWrap.style.height = `${SCREEN_H * s}px`;
    }
    window.addEventListener("resize", fitScale);

    // ===== Loop utama =====
    let lastTime = performance.now();
    function frame(now) {
        // Satu frame yang gagal tidak boleh mematikan loop selamanya
        try {
            const dt = Math.min(100, now - lastTime);
            lastTime = now;

            if (state === "menu") {
                updateMenu();
                ctx = mainCtx;
                drawMenuBase();
                ctx = fxCtx;
                fxCtx.clearRect(0, 0, SCREEN_W, SCREEN_H);
                drawMenuFx();
            } else if (state === "game") {
                ctx = mainCtx;
                updateGame(dt);
                drawGame();
                fxCtx.clearRect(0, 0, SCREEN_W, SCREEN_H);
                setGifVisibility(false, false, false);
            } else if (state === "gameover") {
                ctx = mainCtx;
                updateOver();
                drawGameOver();
                fxCtx.clearRect(0, 0, SCREEN_W, SCREEN_H);
                setGifVisibility(false, false, false);
            }

            if (state === "menu") fadeEl.style.opacity = menu.transitionAlpha;
            else if (state === "game") fadeEl.style.opacity = game.transitionAlpha;
            else fadeEl.style.opacity = 0;
        } catch (err) {
            console.error("Frame error:", err);
            if (Array.isArray(window.__errs)) window.__errs.push("frame: " + String(err));
        }
        ctx = mainCtx;
        requestAnimationFrame(frame);
    }

    // Handle debug/pengujian ringan
    window.__snakeGame = { game, menu, over, get state() { return state; } };

    loadAssets()
        .then(() => {
            loadingEl.style.display = "none";
            state = "menu";
            resetMenu();
            fitScale();
            requestAnimationFrame(frame);
        })
        .catch((failedSrc) => {
            loadingEl.style.display = "none";
            errorEl.hidden = false;
            console.error("Gagal memuat gambar:", failedSrc);
        });
})();
