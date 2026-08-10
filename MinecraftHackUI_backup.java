import com.formdev.flatlaf.FlatDarkLaf;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.event.ChangeListener;

/**
 * Minecraft 外挂 ClickGUI — 透明玻璃主题 + 氛围光带 + 可调颜色
 * 纯 Java Swing，零依赖，javac 编译即可运行
 */
public class MinecraftHackUI {

    // 获取系统中文安全字体
    static Font chineseFont(float size) {
        return chineseFont(Font.PLAIN, size * fontScale);
    }
    static Font chineseFont(int style, float size) {
        String[] candidates = {"Microsoft YaHei", "SimHei", "SimSun", "Dialog"};
        for (String name : candidates) {
            Font f = new Font(name, style, (int)(size * fontScale));
            if (f.canDisplay('中')) return f;
        }
        return new Font("Dialog", style, (int)(size * fontScale));
    }
    static Font monoFont(float size) {
        return monoFont(Font.PLAIN, size * fontScale);
    }
    static Font monoFont(int style, float size) {
        return new Font("Consolas", style, (int)(size * fontScale));
    }

    // 音效（Solstice 风格 — 短促清脆的点击声）
    static void playClickSound() {
        new Thread(() -> {
            try {
                javax.sound.sampled.AudioFormat fmt = new javax.sound.sampled.AudioFormat(8000, 8, 1, true, false);
                javax.sound.sampled.SourceDataLine line = javax.sound.sampled.AudioSystem.getSourceDataLine(fmt);
                line.open(fmt); line.start();
                byte[] buf = new byte[80];
                for (int i = 0; i < buf.length; i++) {
                    double t = (double)i / 8000;
                    double env = Math.exp(-t * 60);
                    buf[i] = (byte)(Math.sin(2 * Math.PI * 800 * t) * env * 80);
                }
                line.write(buf, 0, buf.length);
                line.drain(); line.close();
            } catch (Exception ignored) {}
        }).start();
    }
    static void playToggleSound() {
        new Thread(() -> {
            try {
                javax.sound.sampled.AudioFormat fmt = new javax.sound.sampled.AudioFormat(8000, 8, 1, true, false);
                javax.sound.sampled.SourceDataLine line = javax.sound.sampled.AudioSystem.getSourceDataLine(fmt);
                line.open(fmt); line.start();
                byte[] buf = new byte[60];
                for (int i = 0; i < buf.length; i++) {
                    double t = (double)i / 8000;
                    double env = Math.exp(-t * 50);
                    buf[i] = (byte)(Math.sin(2 * Math.PI * 600 * t) * env * 60);
                }
                line.write(buf, 0, buf.length);
                line.drain(); line.close();
            } catch (Exception ignored) {}
        }).start();
    }

    // 可配置参数
    static int guiRounding = 20;       // 圆角半径
    static float hudOpacity = 1.0f;    // HUD 不透明度
    static boolean simpleMode = false;  // 简易模式
    static float fontScale = 1.0f;     // 字体缩放
    static ModuleHUD hudRef;           // 右侧HUD引用

    // ==================== 动画与缓动工具 ====================
    static class Easing {
        static float easeOutCubic(float t) {
            float x = clamp01(t);
            return 1f - (float) Math.pow(1f - x, 3);
        }
        static float easeInOutCubic(float t) {
            float x = clamp01(t);
            return x < 0.5f ? 4 * x * x * x : 1f - (float) Math.pow(-2f * x + 2f, 3) / 2f;
        }
        static float easeOutBack(float t) {
            float x = clamp01(t);
            float c1 = 1.70158f;
            float c3 = c1 + 1f;
            return 1f + c3 * (float) Math.pow(x - 1f, 3) + c1 * (float) Math.pow(x - 1f, 2);
        }
        static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
    }

    /** 基于时间的平滑动画：帧率无关，始终流畅 */
    static class AnimFloat {
        private float value, target, startValue;
        private final List<Runnable> listeners = new ArrayList<>();
        private Timer timer;
        private long startTime;
        private int durationMs;

        AnimFloat(float initial) { this(initial, 200); }
        AnimFloat(float initial, float speedOrDuration) {
            this.value = initial; this.target = initial;
            this.durationMs = speedOrDuration < 0.5f ? 200 : (int) speedOrDuration;
        }

        void setTarget(float t) {
            if (Math.abs(target - t) < 0.0001f) return;
            startValue = value;
            target = t;
            startTime = System.currentTimeMillis();
            ensureRunning();
        }
        void snapTo(float t) {
            this.value = t; this.target = t;
            notifyListeners();
        }
        float get() { return value; }
        float getTarget() { return target; }
        void addListener(Runnable r) { listeners.add(r); }

        private void ensureRunning() {
            if (timer != null && timer.isRunning()) return;
            timer = new Timer(8, e -> {
                long elapsed = System.currentTimeMillis() - startTime;
                float t = Math.min(1f, (float) elapsed / durationMs);
                float eased = Easing.easeOutCubic(t);
                value = startValue + (target - startValue) * eased;
                notifyListeners();
                if (t >= 1f) {
                    value = target;
                    notifyListeners();
                    ((Timer) e.getSource()).stop();
                }
            });
            timer.start();
        }

        private void notifyListeners() {
            for (Runnable r : listeners) r.run();
        }
    }

    // ==================== 主题 ====================
    static class Theme {
        static Color accent       = new Color(0x00D4FF);  // 亮青主色
        static Color accentDim    = new Color(0x0088AA);
        static Color glowA        = new Color(0x00D4FF);  // 光带A
        static Color glowB        = new Color(0x00FFEA);  // 光带B

        static Color bgDark       = new Color(0x0A0A0A);
        static Color bgPanel      = new Color(20, 20, 28, 130);
        static Color bgCard       = new Color(28, 28, 36, 110);
        static Color bgHover      = new Color(40, 40, 52, 140);
        static Color green        = new Color(0x4CAF50);
        static Color red          = new Color(0xF44336);
        static Color textPrimary  = new Color(0xF0F0F0);
        static Color textSecondary= new Color(0xB0B0B0);
        static Color textDim      = new Color(0x707070);
        static Color borderGlow   = new Color(0x6080E0);

        private static final List<Consumer<Color>> listeners = new ArrayList<>();

        /** 统一更新主题色，并通知所有监听组件 */
        static void updateFromAccent(Color c) {
            accent = c;
            float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
            accentDim = Color.getHSBColor(hsb[0], hsb[1], Math.max(0f, hsb[2] - 0.2f));
            glowA = c;
            glowB = Color.getHSBColor((hsb[0] + 0.04f) % 1f,
                    Math.max(0f, hsb[1] - 0.05f), Math.min(1f, hsb[2] + 0.08f));
            borderGlow = c;
            for (Consumer<Color> l : listeners) l.accept(c);
        }

        static void addListener(Consumer<Color> l) { listeners.add(l); }
        static void removeListener(Consumer<Color> l) { listeners.remove(l); }
    }

    // ==================== 数据模型 ====================
    enum Category {
        COMBAT  ("[战] 战斗"),
        MOVE    ("[移] 移动"),
        RENDER  ("[视] 渲染"),
        PLAYER  ("[身] 玩家"),
        WORLD   ("[界] 世界"),
        MISC    ("[杂] 杂项"),
        SETTINGS("[设] 设置");

        final String label;
        Category(String s) { label = s; }
    }

    static class Setting {
        enum Type { BOOL, SLIDER, MODE }
        String name;
        Type type;
        Object value;       // Boolean / Integer / String
        int min, max;       // slider 用
        String[] options;   // mode 用

        // bool
        Setting(String name, boolean def) {
            this.name = name; this.type = Type.BOOL; this.value = def;
        }
        // slider
        Setting(String name, int def, int min, int max) {
            this.name = name; this.type = Type.SLIDER; this.value = def; this.min = min; this.max = max;
        }
        // mode
        Setting(String name, String def, String... options) {
            this.name = name; this.type = Type.MODE; this.value = def; this.options = options;
        }
    }

    static class Module {
        String name, desc;
        Category category;
        boolean enabled;
        String keybind;
        List<Setting> settings = new ArrayList<>();

        Module(String name, String desc, Category cat) {
            this(name, desc, cat, false, "");
        }
        Module(String name, String desc, Category cat, boolean enabled, String keybind) {
            this.name = name; this.desc = desc;
            this.category = cat; this.enabled = enabled; this.keybind = keybind;
        }
        Module s(Setting... ss) { Collections.addAll(settings, ss); return this; }
    }

    // ==================== 预设模块数据 ====================
    static List<Module> buildModules() {
        List<Module> list = new ArrayList<>();

        list.add(new Module("KillAura",    "自动瞄准并攻击附近敌人", Category.COMBAT, true, "R").s(
            new Setting("范围", 4, 1, 8),
            new Setting("仅玩家", true),
            new Setting("优先", "距离", "距离", "血量", "护甲")));
        list.add(new Module("Reach",       "增加攻击距离",          Category.COMBAT, false, "").s(
            new Setting("距离", 4, 3, 7)));
        list.add(new Module("Velocity",    "减少击退效果",          Category.COMBAT, true, "").s(
            new Setting("水平", 0, 0, 100),
            new Setting("垂直", 0, 0, 100)));
        list.add(new Module("AutoClicker", "自动点击左键",          Category.COMBAT, false, "G").s(
            new Setting("CPS", 12, 8, 20),
            new Setting("随机化", true)));
        list.add(new Module("Criticals",   "每次攻击打出暴击",      Category.COMBAT, false, ""));

        list.add(new Module("Speed",       "移动速度加成",          Category.MOVE, false, "V").s(
            new Setting("模式", "Hypixel", "Hypixel", "Vanilla", "NCP"),
            new Setting("速度", 1, 0, 10)));
        list.add(new Module("Fly",         "允许玩家飞行",          Category.MOVE, false, "F").s(
            new Setting("模式", "Vanilla", "Vanilla", "Motion", "Packet"),
            new Setting("速度", 2, 1, 10)));
        list.add(new Module("NoFall",      "免疫摔落伤害",          Category.MOVE, true, ""));
        list.add(new Module("Sprint",      "自动疾跑",              Category.MOVE, true, ""));
        list.add(new Module("Step",        "自动跨越方块",          Category.MOVE, false, "").s(
            new Setting("高度", 1, 1, 5)));

        list.add(new Module("ESP",         "实体透视显示",          Category.RENDER, true, "P").s(
            new Setting("玩家", true),
            new Setting("怪物", false),
            new Setting("箱子", true)));
        list.add(new Module("Tracers",     "指向实体的线条",        Category.RENDER, false, ""));
        list.add(new Module("Nametags",    "放大玩家名称标签",      Category.RENDER, true, ""));
        list.add(new Module("ChestESP",    "箱子透视高亮",          Category.RENDER, true, ""));
        list.add(new Module("Fullbright",  "全亮度",                Category.RENDER, true, ""));

        list.add(new Module("AutoArmor",   "自动穿戴最佳装备",      Category.PLAYER, true, ""));
        list.add(new Module("ChestStealer","自动偷取箱子物品",      Category.PLAYER, false, "M"));
        list.add(new Module("InvCleaner",  "自动清理背包垃圾",      Category.PLAYER, false, ""));
        list.add(new Module("NoRotate",    "防止被强制转向",        Category.PLAYER, true, ""));

        list.add(new Module("X-Ray",       "透视矿物",              Category.WORLD, false, "X").s(
            new Setting("钻石", true),
            new Setting("绿宝石", true),
            new Setting("金", true),
            new Setting("铁", false)));
        list.add(new Module("Timer",       "游戏速度调整",          Category.WORLD, false, "").s(
            new Setting("倍速", 1, 1, 10)));
        list.add(new Module("NoWeather",   "移除天气效果",          Category.WORLD, true, ""));

        list.add(new Module("AutoRespawn", "自动重生",              Category.MISC, true, ""));
        list.add(new Module("AntiAFK",     "防止挂机踢出",          Category.MISC, false, ""));
        list.add(new Module("AutoTool",    "自动切换最佳工具",      Category.MISC, true, ""));
        list.add(new Module("DiscordRPC",  "Discord 状态显示",      Category.MISC, true, ""));

        // 设置分类
        list.add(new Module("HUD显示",     "右上角模块列表显示",    Category.SETTINGS, true, ""));
        list.add(new Module("简易模式",     "简化界面效果",          Category.SETTINGS, false, ""));
        list.add(new Module("HUD透明度",   "模块列表透明度",        Category.SETTINGS, false, "").s(
            new Setting("透明度", 100, 50, 100)));
        list.add(new Module("圆角大小",     "窗口圆角半径",          Category.SETTINGS, false, "").s(
            new Setting("圆角", 20, 0, 30)));
        list.add(new Module("字体大小",     "全局字体缩放",          Category.SETTINGS, false, "").s(
            new Setting("大小", 12, 10, 16)));
        list.add(new Module("HUD样式",      "右侧HUD配色/速度/大小",  Category.SETTINGS, false, "").s(
            new Setting("调色盘", false),
            new Setting("配色", "青粉", "青粉", "绿青", "金橙", "紫靛", "青翠"),
            new Setting("色相", 200, 0, 360),
            new Setting("速度", 5, 1, 15),
            new Setting("字号", 12, 10, 16),
            new Setting("背景透明", 80, 0, 100)));
        return list;
    }

    // ==================== 工具方法 ====================
    static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }
    static Color lerp(Color a, Color b, float t) {
        return new Color(
            (int)(a.getRed()   + (b.getRed()   - a.getRed())   * t),
            (int)(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
            (int)(a.getBlue()  + (b.getBlue()  - a.getBlue())  * t));
    }

    // ==================== 氛围光带面板 ====================
    static class GlowStrip extends JPanel {
        boolean top;
        float phase = 0f;
        Timer anim;

        GlowStrip(boolean top) {
            this.top = top;
            setOpaque(false);
            setPreferredSize(new Dimension(0, 3));
            anim = new Timer(8, e -> {
                phase += 0.004f;
                if (phase > 1f) phase -= 1f;
                repaint();
            });
            anim.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();

            // 多段渐变光带
            for (int x = 0; x < w; x++) {
                float f = (float) x / w;
                float sine = (float) (Math.sin((f + phase) * Math.PI * 3) * 0.5 + 0.5);
                Color c = lerp(Theme.glowA, Theme.glowB, sine);
                float alpha = (top ? (float)(h - 1) / h : (float)1 / h);
                g2.setColor(withAlpha(c, (int)(180 * (0.3 + 0.7 * sine))));
                g2.fillRect(x, top ? 0 : h - 2, 1, 2);
            }
            g2.dispose();
        }
    }

    // ==================== 自定义开关按钮 ====================
    static class ToggleSwitch extends JComponent {
        boolean on;
        AnimFloat animPos;
        int w = 36, h = 20;

        ToggleSwitch(boolean initial) {
            this.on = initial;
            this.animPos = new AnimFloat(on ? 1f : 0f, 0.28f);
            this.animPos.addListener(this::repaint);
            setPreferredSize(new Dimension(w, h));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) { toggle(); }
            });
        }

        void toggle() {
            on = !on;
            playToggleSound();
            animPos.setTarget(on ? 1f : 0f);
        }

        void setOn(boolean v, boolean animate) {
            on = v;
            if (animate) animPos.setTarget(on ? 1f : 0f);
            else animPos.snapTo(on ? 1f : 0f);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int x = 0, y = (getHeight() - h) / 2;
            float raw = animPos.get();
            float pos = Easing.easeOutBack(raw);

            // 开启时光晕
            if (raw > 0.001f) {
                g2.setColor(withAlpha(Theme.green, (int) (90 * raw)));
                g2.fillRoundRect(x - 2, y - 2, w + 4, h + 4, h + 4, h + 4);
            }

            // 背景凹槽阴影
            g2.setColor(new Color(0, 0, 0, 60));
            g2.fillRoundRect(x, y + 1, w, h, h, h);
            // 背景色（渐变过渡）
            Color offColor = new Color(0x3A3A3A);
            Color onColor = Theme.green;
            Color bg = lerp(offColor, onColor, raw);
            g2.setColor(new Color(bg.getRed(), bg.getGreen(), bg.getBlue(), 200));
            g2.fillRoundRect(x, y, w, h, h, h);
            // 内部刻痕
            g2.setColor(new Color(0, 0, 0, 30));
            g2.drawRoundRect(x + 1, y + 1, w - 2, h - 2, h - 2, h - 2);

            // 滑块 + 阴影（带弹性过冲）
            int knobR = h - 4;
            int knobX = (int) (x + 2 + (w - h) * Math.max(0f, Math.min(1f, pos)));
            g2.setColor(new Color(0, 0, 0, 40));
            g2.fillOval(knobX + 1, y + 3, knobR, knobR);
            // 滑块主体（微渐变）
            GradientPaint knobGrad = new GradientPaint(knobX, y + 2, new Color(250, 250, 250), knobX, y + knobR, new Color(220, 220, 220));
            g2.setPaint(knobGrad);
            g2.fillOval(knobX, y + 2, knobR, knobR);
            // 高光
            g2.setColor(new Color(255, 255, 255, 80));
            g2.fillOval(knobX + 2, y + 4, knobR / 2, knobR / 3);

            g2.dispose();
        }
    }

    // ==================== HSB 色盘面板 ====================
    static class HSBPalette extends JPanel {
        float hue = 0.77f, sat = 1f, bri = 1f;
        Point selectedPt;
        BufferedImage cache;
        int cacheW, cacheH;
        float cacheHue;

        HSBPalette() {
            setOpaque(false);
            setPreferredSize(new Dimension(180, 180));
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            // 从当前 accent 提取 HSB
            float[] hsb = Color.RGBtoHSB(Theme.accent.getRed(), Theme.accent.getGreen(), Theme.accent.getBlue(), null);
            hue = hsb[0]; sat = hsb[1]; bri = hsb[2];

            MouseAdapter ma = new MouseAdapter() {
                public void mousePressed(MouseEvent e)  { updateFromMouse(e); }
                public void mouseDragged(MouseEvent e)  { updateFromMouse(e); }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        void updateFromMouse(MouseEvent e) {
            int w = getWidth(), h = getHeight();
            float sx = Math.max(0, Math.min(1, (float)e.getX() / w));
            float sy = Math.max(0, Math.min(1, (float)e.getY() / h));
            sat = sx;
            bri = 1f - sy;
            selectedPt = e.getPoint();
            updateTheme();
            repaint();
        }

        void updateTheme() {
            Color c = Color.getHSBColor(hue, sat, bri);
            Theme.updateFromAccent(c);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            int w = getWidth(), h = getHeight();

            // 缓存色盘，避免每帧重算
            if (cache == null || cacheW != w || cacheH != h || cacheHue != hue) {
                cache = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_RGB);
                Graphics2D cg = cache.createGraphics();
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        float sx = (float) x / w;
                        float sy = 1f - (float) y / h;
                        cg.setColor(Color.getHSBColor(hue, sx, sy));
                        cg.fillRect(x, y, 1, 1);
                    }
                }
                cg.dispose();
                cacheW = w; cacheH = h; cacheHue = hue;
            }
            g2.drawImage(cache, 0, 0, null);

            // 十字准心
            if (selectedPt == null) {
                selectedPt = new Point((int)(sat * w), (int)((1f - bri) * h));
            }
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval(selectedPt.x - 5, selectedPt.y - 5, 10, 10);
            g2.setColor(Color.BLACK);
            g2.drawOval(selectedPt.x - 6, selectedPt.y - 6, 12, 12);
            g2.dispose();
        }
    }

    // ==================== 色相条 ====================
    static class HueBar extends JPanel {
        HSBPalette palette;
        HueBar(HSBPalette p) { this.palette = p; setOpaque(false); setPreferredSize(new Dimension(180, 16)); setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            MouseAdapter ma = new MouseAdapter() {
                public void mousePressed(MouseEvent e) { updateHue(e); }
                public void mouseDragged(MouseEvent e) { updateHue(e); }
            };
            addMouseListener(ma); addMouseMotionListener(ma);
        }
        void updateHue(MouseEvent e) {
            palette.hue = Math.max(0, Math.min(1, (float)e.getX() / getWidth()));
            palette.updateTheme();
            palette.repaint(); repaint();
        }
        protected void paintComponent(Graphics g) {
            int w = getWidth(), h = getHeight();
            for (int x = 0; x < w; x++) {
                g.setColor(Color.getHSBColor((float)x/w, 1f, 1f));
                g.fillRect(x, 0, 1, h);
            }
            // 指示器
            int cx = (int)(palette.hue * w);
            g.setColor(Color.WHITE); g.fillRect(cx-1, 0, 3, h);
            g.setColor(Color.BLACK); g.drawRect(cx-1, 0, 2, h-1);
        }
    }

    // ==================== 预设色块 ====================
    static class PresetSwatches extends JPanel {
        HSBPalette palette;
        static final Color[][] PRESETS = {
            {new Color(0xE040FB), new Color(0x2196F3), new Color(0x00BCD4), new Color(0x4CAF50),
             new Color(0xFFEB3B), new Color(0xFF9800), new Color(0xF44336), new Color(0xE91E63)},
            {new Color(0x9C27B0), new Color(0x3F51B5), new Color(0x03A9F4), new Color(0x009688),
             new Color(0x8BC34A), new Color(0xFFC107), new Color(0xFF5722), new Color(0x795548)},
            {new Color(0x607D8B), new Color(0x37474F), new Color(0xFFFFFF), new Color(0x90A4AE),
             new Color(0x263238), new Color(0xCFD8DC), new Color(0x000000), new Color(0xB0BEC5)}
        };

        PresetSwatches(HSBPalette p) { this.palette = p; setOpaque(false); setPreferredSize(new Dimension(180, 70));
            addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    int cw = Math.max(1, getWidth() / 8);
                    int ch = Math.max(1, getHeight() / 3);
                    int col = Math.min(7, e.getX() / cw);
                    int row = Math.min(2, e.getY() / ch);
                    Color c = PRESETS[row][col];
                    float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
                    palette.hue = hsb[0]; palette.sat = hsb[1]; palette.bri = hsb[2];
                    palette.selectedPt = new Point((int)(palette.sat * palette.getWidth()), (int)((1f-palette.bri) * palette.getHeight()));
                    palette.updateTheme(); palette.repaint(); repaint();
                }
            });
        }
        protected void paintComponent(Graphics g) {
            int cw = Math.max(1, getWidth() / 8);
            int ch = Math.max(1, getHeight() / 3);
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 8; c++) {
                    g.setColor(PRESETS[r][c]);
                    g.fillRoundRect(c*cw+1, r*ch+1, cw-2, ch-2, 6, 6);
                }
            }
        }
    }

    // ==================== 颜色选择器对话框 ====================
    static class ColorPickerDialog extends JDialog {
        Color result;
        Color originalAccent;

        ColorPickerDialog(Frame owner) {
            super(owner, "颜色选择器", true);
            originalAccent = Theme.accent;
            setUndecorated(true);
            setSize(220, 340);
            setLocationRelativeTo(owner);
            getContentPane().setBackground(Theme.bgDark);

            JPanel main = new JPanel();
            main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));
            main.setBackground(Theme.bgDark);
            main.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

            // 标题
            JLabel title = new JLabel("🎨 选择强调色");
            title.setFont(chineseFont(Font.BOLD, 14));
            title.setForeground(Color.WHITE);
            title.setAlignmentX(LEFT_ALIGNMENT);
            main.add(title);
            main.add(Box.createVerticalStrut(10));

            // 色盘
            HSBPalette palette = new HSBPalette();
            palette.setAlignmentX(LEFT_ALIGNMENT);
            main.add(palette);
            main.add(Box.createVerticalStrut(4));

            // 色相条
            HueBar hueBar = new HueBar(palette);
            hueBar.setAlignmentX(LEFT_ALIGNMENT);
            main.add(hueBar);
            main.add(Box.createVerticalStrut(8));

            // 预设
            PresetSwatches swatches = new PresetSwatches(palette);
            swatches.setAlignmentX(LEFT_ALIGNMENT);
            main.add(swatches);
            main.add(Box.createVerticalStrut(12));

            // 按钮
            JPanel btns = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
            btns.setBackground(Theme.bgDark);
            btns.setAlignmentX(LEFT_ALIGNMENT);
            JButton ok = new JButton("确定");
            ok.setFont(chineseFont(Font.BOLD, 13));
            ok.setBackground(Theme.accent);
            ok.setForeground(Color.WHITE);
            ok.setFocusPainted(false);
            ok.setBorder(BorderFactory.createEmptyBorder(6, 24, 6, 24));
            ok.addActionListener(e -> { result = Theme.accent; dispose(); });
            JButton cancel = new JButton("取消");
            cancel.setFont(chineseFont(12));
            cancel.setBackground(withAlpha(Color.WHITE, 25));
            cancel.setForeground(Theme.textSecondary);
            cancel.setFocusPainted(false);
            cancel.setBorder(BorderFactory.createEmptyBorder(6, 18, 6, 18));
            cancel.addActionListener(e -> { Theme.updateFromAccent(originalAccent); dispose(); });
            cancel.addActionListener(e -> dispose());
            btns.add(ok); btns.add(cancel);
            main.add(btns);

            add(main);
        }
    }

    // ==================== 分类侧边栏 ====================
    static class CategorySidebar extends JPanel {
        int selectedIdx = 0;
        List<JLabel> items = new ArrayList<>();
        Map<JLabel, AnimFloat> hoverMap = new HashMap<>();
        Runnable onSelect;

        CategorySidebar(Runnable onSelect) {
            this.onSelect = onSelect;
            setOpaque(false);
            setPreferredSize(new Dimension(120, 0));
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 4));

            for (Category cat : Category.values()) {
                AnimFloat hover = new AnimFloat(0f, 0.22f);
                JLabel lbl = new JLabel("  " + cat.label) {
                    {
                        hover.addListener(this::repaint);
                    }
                    protected void paintComponent(Graphics g) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        int selIdx = selectedIdx;
                        int myIdx = items.indexOf(this);
                        boolean selected = myIdx == selIdx;
                        float h = hover.get();

                        // 悬停背景
                        if (!selected && h > 0.001f) {
                            g2.setColor(withAlpha(Theme.accent, (int) (45 * h)));
                            g2.fillRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 10, 10);
                        }

                        // 选中态背景
                        if (selected) {
                            g2.setColor(withAlpha(Theme.accent, 60));
                            g2.fillRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 10, 10);
                            // 左侧指示条 + 光晕
                            g2.setColor(withAlpha(Theme.accent, 80));
                            g2.fillRoundRect(3, 5, 5, getHeight() - 10, 3, 3);
                            g2.setColor(Theme.accent);
                            g2.fillRoundRect(4, 7, 3, getHeight() - 14, 3, 3);
                        }
                        g2.setColor(selected || h > 0.5f ? Color.WHITE : Theme.textSecondary);
                        g2.setFont(chineseFont(13));
                        FontMetrics fm = g2.getFontMetrics();
                        g2.drawString(cat.label, 18, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                        g2.dispose();
                    }
                };
                hoverMap.put(lbl, hover);
                lbl.setPreferredSize(new Dimension(110, 36));
                lbl.setMaximumSize(new Dimension(110, 36));
                lbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                int idx = items.size();
                lbl.addMouseListener(new MouseAdapter() {
                    public void mouseClicked(MouseEvent e) {
                        selectedIdx = idx;
                        items.forEach(i -> i.repaint());
                        onSelect.run();
                    }
                    public void mouseEntered(MouseEvent e) { hover.setTarget(1f); }
                    public void mouseExited(MouseEvent e)  { hover.setTarget(0f); }
                });
                items.add(lbl);
                add(lbl);
                if (cat != Category.MISC) add(Box.createVerticalStrut(2));
            }
        }

        Category getSelected() { return Category.values()[selectedIdx]; }
    }

    // ==================== 模块条目组件 ====================
    static class ModuleItem extends JPanel {
        Module module;
        ToggleSwitch toggle;
        boolean expanded = false;
        int expandHeight = 0;
        AnimFloat expandAnim;
        AnimFloat hoverAnim;
        AnimFloat appearAnim;
        AnimFloat pulseAnim;
        JPanel settingsPanel;
        JLabel nameLabel, keyLabel;
        ColorPickerDialog colorDialog;
        Runnable onColorChange;
        Consumer<String> onToggleNotify;
        List<Runnable> colorRefreshers = new ArrayList<>();

        ModuleItem(Module m, Runnable onColorChange, Runnable onToggle, Consumer<String> onToggleNotify, int appearDelayMs) {
            this.onToggleNotify = onToggleNotify;
            this.module = m;
            this.onColorChange = onColorChange;
            setOpaque(false);
            setLayout(new BorderLayout());
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            // 悬停动画
            hoverAnim = new AnimFloat(0f, 0.18f);
            hoverAnim.addListener(this::repaint);
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hoverAnim.setTarget(1f); }
                public void mouseExited(MouseEvent e)  { hoverAnim.setTarget(0f); }
            });

            // 进入动画（淡入 + 上滑），支持延迟
            appearAnim = new AnimFloat(0f, 0.14f);
            appearAnim.addListener(this::repaint);
            if (appearDelayMs > 0) {
                javax.swing.Timer delayTimer = new javax.swing.Timer(appearDelayMs, e -> {
                    appearAnim.setTarget(1f);
                    ((javax.swing.Timer) e.getSource()).stop();
                });
                delayTimer.setRepeats(false);
                delayTimer.start();
            } else {
                SwingUtilities.invokeLater(() -> appearAnim.setTarget(1f));
            }

            // 启用脉冲动画
            pulseAnim = new AnimFloat(0f, 0.18f);
            pulseAnim.addListener(this::repaint);

            // 主行
            JPanel row = new JPanel(new BorderLayout(15, 0));
            row.setOpaque(false);
            row.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 10));

            // 左侧：名称 + 描述
            JPanel info = new JPanel(new GridLayout(2, 1, 0, 0));
            info.setOpaque(false);
            // Solstice +/- 指示器
            String prefix = m.settings.isEmpty() ? "  " : (expanded ? "[-] " : "[+] ");
            nameLabel = new JLabel(prefix + m.name);
            nameLabel.setFont(chineseFont(Font.BOLD, 13));
            nameLabel.setForeground(m.enabled ? Color.WHITE : Theme.textSecondary);
            JLabel descLabel = new JLabel(m.desc);
            descLabel.setFont(chineseFont(10));
            descLabel.setForeground(Theme.textDim);
            info.add(nameLabel); info.add(descLabel);
            row.add(info, BorderLayout.CENTER);

            // 右侧：键位 + 开关
            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 1));
            right.setOpaque(false);
            keyLabel = new JLabel(m.keybind.isEmpty() ? "..." : m.keybind);
            keyLabel.setFont(new Font("Consolas", Font.BOLD, 11));
            keyLabel.setForeground(Theme.textDim);
            keyLabel.setPreferredSize(new Dimension(30, 18));
            keyLabel.setHorizontalAlignment(SwingConstants.CENTER);
            keyLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            keyLabel.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    keyLabel.setText("...");
                    keyLabel.setForeground(Color.YELLOW);
                    new Thread(() -> {
                        try { Thread.sleep(2000); } catch (Exception ex) {}
                        SwingUtilities.invokeLater(() -> {
                            keyLabel.setText(module.keybind.isEmpty() ? "..." : module.keybind);
                            keyLabel.setForeground(Theme.textDim);
                        });
                    }).start();
                }
            });
            toggle = new ToggleSwitch(m.enabled);
            toggle.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    module.enabled = toggle.on;
                    nameLabel.setForeground(toggle.on ? Color.WHITE : Theme.textSecondary);
                    if (toggle.on) { pulseAnim.snapTo(0f); pulseAnim.setTarget(1f); }
                    if (onToggle != null) onToggle.run();
                    if (onToggleNotify != null) onToggleNotify.accept((module.enabled ? "+" : "-") + module.name);
                }
            });
            right.add(keyLabel);
            right.add(toggle);
            row.add(right, BorderLayout.EAST);

            add(row, BorderLayout.NORTH);

            // 设置面板
            settingsPanel = createSettingsPanel();
            settingsPanel.setVisible(false);
            add(settingsPanel, BorderLayout.CENTER);

            // 展开动画（带缓动）
            expandAnim = new AnimFloat(0f, 0.22f);
            expandAnim.addListener(() -> {
                int target = getSettingsPreferredHeight();
                expandHeight = (int) (target * Easing.easeOutCubic(expandAnim.get()));
                settingsPanel.setPreferredSize(new Dimension(0, expandHeight));
                settingsPanel.setVisible(expandHeight > 0 || expandAnim.getTarget() > 0);
                revalidate(); repaint();
                if (getParent() != null) {
                    getParent().revalidate();
                    getParent().repaint();
                }
            });

            // 点击展开
            row.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    if (e.getX() > getWidth() - 120) return; // 避免误触开关区域
                    toggleExpanded();
                }
            });
            nameLabel.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) { toggleExpanded(); }
            });
        }

        void toggleExpanded() {
            expanded = !expanded;
            // 更新 +/- 指示器
            String prefix = module.settings.isEmpty() ? "  " : (expanded ? "[-] " : "[+] ");
            nameLabel.setText(prefix + module.name);
            // 点击音效
            playClickSound();
            expandAnim.setTarget(expanded ? 1f : 0f);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            float hover = hoverAnim.get();
            int w = getWidth(), h = getHeight();
            int r = 14;

            // 独立卡片阴影
            g2.setColor(new Color(0, 0, 0, 25 + (int)(20 * hover)));
            g2.fillRoundRect(1, 3, w - 2, h - 1, r, r);

            // 卡片背景
            float bgAlpha = module.enabled ? 0.08f : 0f;
            g2.setColor(withAlpha(Theme.accent, (int)(255 * (bgAlpha + hover * 0.06f))));
            g2.fillRoundRect(1, 1, w - 2, h - 2, r, r);

            // 卡片边框
            g2.setColor(withAlpha(Theme.accent, 40 + (int)(80 * hover)));
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(1, 1, w - 3, h - 3, r, r);

            // 启用指示条
            if (module.enabled) {
                g2.setColor(Theme.accent);
                g2.fillRoundRect(6, 10, 3, h - 20, 2, 2);
            }

            // 启用脉冲
            float pulse = Easing.easeOutCubic(pulseAnim.get());
            if (pulse > 0.001f) {
                int cy = h / 2;
                int pr = (int)(8 + pulse * 60);
                g2.setColor(withAlpha(Theme.accent, (int)(80 * (1 - pulse))));
                g2.setStroke(new BasicStroke(2f));
                g2.drawOval(8 - pr, cy - pr, pr * 2, pr * 2);
            }

            g2.dispose();
        }

        @Override
        public void paint(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            float appear = appearAnim.get();
            if (appear < 0.999f) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, appear));
                g2.translate(0, (1f - appear) * 8);
            }
            super.paint(g2);
            g2.dispose();
        }

        int getSettingsPreferredHeight() {
            return Math.min(module.settings.size() * 30 + 10, 160);
        }

        void applySetting(String name, int value) {
            switch (module.name) {
                case "HUD透明度" -> hudOpacity = value / 100f;
                case "圆角大小" -> guiRounding = value;
                case "字体大小" -> fontScale = value / 12f;
                case "HUD样式" -> {
                    if (name.equals("速度")) hudRef.hudColorSpeed = value;
                    if (name.equals("字号")) { hudRef.hudFontSize = value; hudRef.modFont = chineseFont(Font.BOLD, value); hudRef.hudPanel.repaint(); }
                    if (name.equals("色相")) { float hue = value/360f; hudRef.hudPrimary = Color.getHSBColor(hue,1f,1f); hudRef.hudSecondary = Color.getHSBColor((hue+0.5f)%1f,1f,1f); hudRef.hudPanel.repaint(); }
                    if (name.equals("背景透明")) { hudRef.hudBgAlpha = value/100f; hudRef.hudPanel.repaint(); }
                }
            }
        }

        /** 淡出动画 */
        void fadeOut() { appearAnim.setTarget(0f); }

        /** 主题色变化时刷新本条目中控件的颜色 */
        void refreshColors() {
            nameLabel.setForeground(module.enabled ? Color.WHITE : Theme.textSecondary);
            for (Runnable r : colorRefreshers) r.run();
            repaint();
        }

        JPanel createSettingsPanel() {
            JPanel p = new JPanel();
            p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
            p.setOpaque(false);
            p.setBorder(BorderFactory.createEmptyBorder(0, 20, 6, 20));

            for (Setting s : module.settings) {
                JPanel row = new JPanel(new BorderLayout(10, 0));
                row.setOpaque(false);
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

                JLabel nameLbl = new JLabel("  " + s.name);
                nameLbl.setFont(chineseFont(11));
                nameLbl.setForeground(Theme.textSecondary);
                row.add(nameLbl, BorderLayout.WEST);

                switch (s.type) {
                    case BOOL -> {
                        ToggleSwitch sw = new ToggleSwitch((Boolean) s.value);
                        sw.setPreferredSize(new Dimension(28, 15));
                        sw.w = 28; sw.h = 15;
                        sw.addMouseListener(new MouseAdapter() {
                            public void mouseClicked(MouseEvent e) {
                                s.value = sw.on;
                                if (module.name.equals("HUD样式") && s.name.equals("调色盘") && sw.on && hudRef != null) {
                                    sw.on = false; s.value = false;
                                    ColorPickerDialog dlg = new ColorPickerDialog((Frame) SwingUtilities.getWindowAncestor(sw));
                                    dlg.setVisible(true);
                                    if (dlg.result != null) {
                                        hudRef.hudPrimary = dlg.result;
                                        float[] hsb = Color.RGBtoHSB(dlg.result.getRed(), dlg.result.getGreen(), dlg.result.getBlue(), null);
                                        hudRef.hudSecondary = Color.getHSBColor((hsb[0] + 0.5f) % 1f, 1f, 1f);
                                        hudRef.hudPanel.repaint();
                                    }
                                }
                            }
                        });
                        row.add(sw, BorderLayout.EAST);
                    }
                    case SLIDER -> {
                        int val = (Integer) s.value;
                        JSlider slider = new JSlider(s.min, s.max, val);
                        slider.setPreferredSize(new Dimension(100, 24));
                        slider.setFont(chineseFont(9));
                        JLabel valLbl = new JLabel(String.valueOf(val));
                        valLbl.setFont(chineseFont(10));
                        valLbl.setForeground(Theme.accent);
                        valLbl.setPreferredSize(new Dimension(22, 20));
                        slider.addChangeListener(e -> {
                            s.value = slider.getValue();
                            valLbl.setText(String.valueOf(slider.getValue()));
                            applySetting(s.name, slider.getValue());
                        });
                        colorRefreshers.add(() -> {});
                        JPanel wrap = new JPanel(new BorderLayout(4, 0));
                        wrap.setOpaque(false);
                        wrap.add(slider, BorderLayout.CENTER);
                        wrap.add(valLbl, BorderLayout.EAST);
                        row.add(wrap, BorderLayout.CENTER);
                    }
                    case MODE -> {
                        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
                        btns.setOpaque(false);
                        for (String opt : s.options) {
                            JButton btn = new JButton(opt);
                            btn.setFont(chineseFont(10));
                            btn.setFocusPainted(false);
                            btn.addActionListener(e -> {
                                s.value = opt;
                                if (module.name.equals("HUD样式") && s.name.equals("配色") && hudRef != null) {
                                    Color[][] pairs = {{new Color(0x00D4FF),new Color(0xFF6EC7)},{new Color(0x00FF88),new Color(0x00D4FF)},{new Color(0xFFB800),new Color(0xFF3D00)},{new Color(0xA855F7),new Color(0x6366F1)},{new Color(0x06B6D4),new Color(0x10B981)}};
                                    int idx = java.util.Arrays.asList(s.options).indexOf(opt);
                                    if (idx>=0 && idx<pairs.length) { hudRef.hudPrimary=pairs[idx][0]; hudRef.hudSecondary=pairs[idx][1]; hudRef.hudPanel.repaint(); }
                                }
                            });
                            colorRefreshers.add(() -> {});
                            btns.add(btn);
                        }
                        row.add(btns, BorderLayout.EAST);
                    }
                }
                p.add(row);
                if (s != module.settings.get(module.settings.size() - 1))
                    p.add(Box.createVerticalStrut(2));
            }
            return p;
        }
    }

    // ==================== 模块列表面板 ====================
    static class ModuleListPanel extends JPanel {
        List<Module> allModules;
        List<ModuleItem> items = new ArrayList<>();
        Runnable onColorChange;
        Consumer<String> onToggleNotify;
        Category currentCat = Category.COMBAT;
        String searchText = "";

        ModuleListPanel(List<Module> modules, Runnable onColorChange, Runnable onToggle, Consumer<String> onToggleNotify) {
            this.allModules = modules;
            this.onColorChange = onColorChange;
            this.onToggle = onToggle;
            this.onToggleNotify = onToggleNotify;
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));

            // 搜索栏
            JTextField searchField = new JTextField() {
                AnimFloat focusAnim = new AnimFloat(0f, 0.2f);
                boolean hoverClear = false;
                { setOpaque(false); setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 24));
                  setFont(chineseFont(11)); setForeground(Theme.textSecondary);
                  setCaretColor(Theme.accent);
                  focusAnim.addListener(this::repaint);
                  addFocusListener(new FocusAdapter() {
                      public void focusGained(FocusEvent e) { focusAnim.setTarget(1f); }
                      public void focusLost(FocusEvent e)   { focusAnim.setTarget(0f); }
                  });
                  MouseAdapter clearHandler = new MouseAdapter() {
                      void updateHover(MouseEvent e) {
                          boolean was = hoverClear;
                          hoverClear = e.getX() > getWidth() - 24 && e.getX() < getWidth() - 4
                                  && e.getY() > 2 && e.getY() < getHeight() - 2 && !getText().isEmpty();
                          if (was != hoverClear) repaint();
                          setCursor(hoverClear ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                                  : Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
                      }
                      public void mouseMoved(MouseEvent e) { updateHover(e); }
                      public void mouseDragged(MouseEvent e) { updateHover(e); }
                      public void mouseClicked(MouseEvent e) { if (hoverClear) setText(""); }
                  };
                  addMouseListener(clearHandler);
                  addMouseMotionListener(clearHandler);
                }
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    float f = focusAnim.get();
                    // 背景
                    g2.setColor(new Color(255,255,255,25));
                    g2.fillRoundRect(0,0,getWidth(),getHeight(),10,10);
                    // 边框：聚焦时发光
                    if (f > 0.001f) {
                        g2.setColor(withAlpha(Theme.accent, (int) (40 * f)));
                        g2.setStroke(new BasicStroke(3f));
                        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                        g2.setColor(withAlpha(Theme.accent, (int) (180 * f)));
                        g2.setStroke(new BasicStroke(1.5f));
                        g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 10, 10);
                    } else {
                        g2.setColor(new Color(255,255,255,40));
                        g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,10,10);
                    }
                    // 清空按钮
                    if (!getText().isEmpty()) {
                        int cx = getWidth() - 14;
                        int cy = getHeight() / 2;
                        g2.setColor(hoverClear ? Color.WHITE : Theme.textDim);
                        g2.setStroke(new BasicStroke(1.5f));
                        g2.drawLine(cx - 3, cy - 3, cx + 3, cy + 3);
                        g2.drawLine(cx + 3, cy - 3, cx - 3, cy + 3);
                    }
                    if (getText().isEmpty()) {
                        g2.setColor(Theme.textDim);
                        g2.drawString("搜索模块...", 10, getHeight()-6);
                    }
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            searchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
            searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void changedUpdate(javax.swing.event.DocumentEvent e) { doSearch(); }
                public void insertUpdate(javax.swing.event.DocumentEvent e) { doSearch(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { doSearch(); }
                void doSearch() {
                    searchText = searchField.getText().toLowerCase().trim();
                    refreshList();
                }
            });
            add(searchField);
            add(Box.createVerticalStrut(6));

            // 主题色变化时刷新当前所有模块条目的控件颜色
            Theme.addListener(c -> {
                for (ModuleItem item : items) item.refreshColors();
                repaint();
            });
        }

        Runnable onToggle;

        void showCategory(Category cat) {
            currentCat = cat;
            refreshList();
        }

        void refreshList() {
            // 先让旧条目淡出
            if (!items.isEmpty()) {
                List<ModuleItem> oldItems = new ArrayList<>(items);
                for (ModuleItem it : oldItems) it.fadeOut();
                javax.swing.Timer rebuildTimer = new javax.swing.Timer(160, e -> {
                    ((javax.swing.Timer) e.getSource()).stop();
                    doRefreshList();
                });
                rebuildTimer.setRepeats(false);
                rebuildTimer.start();
            } else {
                doRefreshList();
            }
        }

        void doRefreshList() {
            int keep = getComponentCount() > 2 ? 2 : 0;
            while (getComponentCount() > keep) remove(keep);
            items.clear();
            Category lastCat = null;
            int itemIdx = 0;
            for (Module m : allModules) {
                boolean matchCat = m.category == currentCat;
                boolean matchSearch = searchText.isEmpty() ||
                    m.name.toLowerCase().contains(searchText) ||
                    m.desc.toLowerCase().contains(searchText);
                if (matchCat && matchSearch) {
                    // 搜索为空时显示分类分隔线
                    if (searchText.isEmpty() && lastCat != null && m.category != lastCat) {
                        JPanel divider = new JPanel() {
                            protected void paintComponent(Graphics g) {
                                Graphics2D g2d = (Graphics2D) g.create();
                                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                                g2d.setColor(withAlpha(Theme.accent, 30));
                                g2d.fillRoundRect(20, 4, getWidth()-40, 2, 2, 2);
                                g2d.dispose();
                            }
                        };
                        divider.setOpaque(false);
                        divider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 9));
                        add(divider);
                    }
                    lastCat = m.category;
                    ModuleItem item = new ModuleItem(m, onColorChange, onToggle, onToggleNotify, itemIdx * 25);
                    itemIdx++;
                    items.add(item);
                    add(item);
                    add(Box.createVerticalStrut(6));
                }
            }
            add(Box.createVerticalGlue());
            revalidate();
            repaint();
        }
    }

    // ==================== 主 GUI 面板 ====================
    static class ClickGUIPanel extends JPanel {
        JFrame frame;
        CategorySidebar sidebar;
        ModuleListPanel moduleList;
        JPanel topGlow, bottomGlow;
        Point dragOffset;
        List<Module> modules;

        ClickGUIPanel(JFrame frame, List<Module> modules) {
            this.frame = frame;
            this.modules = modules;
            setOpaque(false);
            setLayout(new BorderLayout());
            setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

            // 顶部光带
            topGlow = new GlowStrip(true);
            bottomGlow = new GlowStrip(false);

            JPanel glowPanel = new JPanel(new BorderLayout());
            glowPanel.setOpaque(false);
            glowPanel.add(topGlow, BorderLayout.NORTH);
            glowPanel.add(bottomGlow, BorderLayout.SOUTH);

            // 主内容
            JPanel content = new JPanel(new BorderLayout());
            content.setOpaque(false);

            moduleList = new ModuleListPanel(modules, () -> refreshColors(), () -> fireToggle(),
                name -> {});
            // 顶部分类胶囊标签栏
            JPanel catTabs = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
            catTabs.setOpaque(false);
            catTabs.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
            for (Category cat : Category.values()) {
                JComponent tab = new JComponent() {
                    { setFont(chineseFont(Font.BOLD, 11)); setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                      setPreferredSize(new Dimension(68, 26));
                    }
                    protected void paintComponent(Graphics g) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        boolean sel = cat.ordinal() == sidebar.selectedIdx;
                        int w = getWidth(), h = getHeight();
                        if (sel) {
                            g2.setColor(Theme.accent);
                            g2.fillRoundRect(0, 0, w, h, 13, 13);
                            g2.setColor(Color.WHITE);
                        } else {
                            g2.setColor(withAlpha(Theme.accent, 30));
                            g2.fillRoundRect(0, 0, w, h, 13, 13);
                            g2.setColor(Theme.textSecondary);
                        }
                        g2.setFont(getFont());
                        FontMetrics fm = g2.getFontMetrics();
                        String t = cat.label;
                        g2.drawString(t, (w-fm.stringWidth(t))/2, (h+fm.getAscent()-fm.getDescent())/2);
                        g2.dispose();
                    }
                };
                tab.addMouseListener(new MouseAdapter() {
                    public void mouseClicked(MouseEvent e) {
                        moduleList.showCategory(cat);
                        sidebar.selectedIdx = cat.ordinal();
                        for (Component c : catTabs.getComponents()) c.repaint();
                    }
                });
                catTabs.add(tab);
            }
            sidebar = new CategorySidebar(() -> {});
            sidebar.selectedIdx = Category.COMBAT.ordinal();

            JPanel centerArea = new JPanel(new BorderLayout());
            centerArea.setOpaque(false);
            centerArea.add(catTabs, BorderLayout.NORTH);
            JScrollPane scrollPane = new JScrollPane(moduleList);
            scrollPane.setOpaque(false);
            scrollPane.getViewport().setOpaque(false);
            scrollPane.setBorder(null);
            scrollPane.getVerticalScrollBar().setUnitIncrement(16);
            centerArea.add(scrollPane, BorderLayout.CENTER);

            content.add(centerArea, BorderLayout.CENTER);

            // 内边距
            JPanel padded = new JPanel(new BorderLayout());
            padded.setOpaque(false);
            padded.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
            padded.add(content, BorderLayout.CENTER);

            add(glowPanel, BorderLayout.NORTH);
            add(padded, BorderLayout.CENTER);

            // 默认显示战斗分类
            moduleList.showCategory(Category.COMBAT);

            // ===== 窗口拖动 =====
            MouseAdapter dragHandler = new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    dragOffset = e.getPoint();
                }
                public void mouseDragged(MouseEvent e) {
                    Point p = e.getLocationOnScreen();
                    frame.setLocation(p.x - dragOffset.x, p.y - dragOffset.y);
                }
            };
            addMouseListener(dragHandler);
            addMouseMotionListener(dragHandler);

            // 主题色变化时重绘整体
            Theme.addListener(c -> refreshColors());

            // 模块变更回调（联动 HUD）
            onModuleToggle = () -> { if (hud != null) hud.syncRefresh(); };
        }

        ModuleHUD hud;
        Runnable onModuleToggle;
        void setHUD(ModuleHUD h) { this.hud = h; }
        void fireToggle() {
            if (onModuleToggle != null) onModuleToggle.run();
            // 设置模块联动
            for (Module m : modules) {
                switch (m.name) {
                    case "HUD显示" -> { if (hud != null && hud.isVisible() != m.enabled) hud.toggleVisible(); }
                    case "简易模式" -> simpleMode = m.enabled;
                }
            }
        }

        // 设置行：标签 + 开关
        JPanel settingRow(String label, boolean def, java.util.function.Consumer<Boolean> cb) {
            JPanel p = new JPanel(new BorderLayout(4, 0));
            p.setOpaque(false);
            p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
            JLabel lbl = new JLabel(label);
            lbl.setFont(chineseFont(10));
            lbl.setForeground(Theme.textSecondary);
            p.add(lbl, BorderLayout.CENTER);
            ToggleSwitch sw = new ToggleSwitch(def);
            sw.setPreferredSize(new Dimension(26, 14));
            sw.w = 26; sw.h = 14;
            sw.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) { cb.accept(sw.on); }
            });
            p.add(sw, BorderLayout.EAST);
            return p;
        }

        // 设置行：标签 + 滑块
        JPanel settingSliderRow(String label, int min, int max, int def, java.util.function.IntConsumer cb) {
            JPanel p = new JPanel(new BorderLayout(2, 0));
            p.setOpaque(false);
            p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
            JLabel lbl = new JLabel(label);
            lbl.setFont(chineseFont(10));
            lbl.setForeground(Theme.textSecondary);
            lbl.setPreferredSize(new Dimension(55, 18));
            p.add(lbl, BorderLayout.WEST);
            JSlider sl = new JSlider(min, max, def);
            sl.setOpaque(false);
            sl.setPreferredSize(new Dimension(50, 16));
            sl.setFont(monoFont(8));
            sl.setForeground(Theme.accent);
            sl.addChangeListener(e -> cb.accept(sl.getValue()));
            p.add(sl, BorderLayout.CENTER);
            return p;
        }

        /** 刷新所有颜色相关的组件 — 切换主题色后调用 */
        void refreshColors() {
            frame.repaint();                 // 玻璃背景
            topGlow.repaint();               // 顶部光带
            bottomGlow.repaint();            // 底部光带
            sidebar.repaint();               // 分类侧边栏
            moduleList.repaint();            // 模块列表
            repaint();                       // 整体
            // 子组件已通过 Theme 监听器自动刷新颜色，无需重建整个列表
        }

    }

    // ==================== 屏幕 HUD（带滑入滑出动画） ====================
    static class ModuleHUD {
        JFrame hudFrame;
        JPanel hudPanel;
        List<Module> modules;
        float glowPhase = 0f;
        Point dragOffset;
        int hudX = 0, hudY = 5;
        Color hudPrimary = new Color(0x00D4FF);
        Color hudSecondary = new Color(0xFF6EC7);
        float hudColorSpeed = 1.5f;
        int hudFontSize = 12;
        float hudBgAlpha = 0.8f;

        // 动画条目列表
        static class HUDEntry {
            String name, info;
            AnimFloat anim = new AnimFloat(0f);
            boolean active;
            HUDEntry(String n, String i) { name = n; info = i; anim = new AnimFloat(0f, 150); anim.snapTo(0f); }
        }
        List<HUDEntry> entries = new ArrayList<>();
        Font modFont, smallFont;
        int lineH, panelW, panelH;

        ModuleHUD(List<Module> modules) {
            this.modules = modules;
            modFont = chineseFont(Font.BOLD, 12);
            smallFont = chineseFont(Font.BOLD, 9);

            hudFrame = new JFrame();
            hudFrame.setUndecorated(true);
            hudFrame.setBackground(new Color(0, 0, 0, 0));
            hudFrame.setAlwaysOnTop(true);

            hudPanel = new JPanel() {
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                    if (entries.isEmpty()) return;
                    FontMetrics fm = g2.getFontMetrics(modFont);

                    // 统一宽度：取最宽标签
                    int uniW = 100;
                    for (HUDEntry e : entries) {
                        String lbl = e.info.isEmpty() ? e.name : e.name + "  " + e.info;
                        int w = fm.stringWidth(lbl) + 20;
                        if (w > uniW) uniW = w;
                    }
                    int labelH = lineH;

                    int y = 18;
                    for (int i = 0; i < entries.size(); i++) {
                        HUDEntry e = entries.get(i);
                        float anim = Easing.easeOutCubic(e.anim.get());
                        if (anim < 0.001f && !e.active) continue;
                        float alpha = Math.max(0f, anim);
                        float shiftX = (1f - anim) * 40f;
                        float ga = alpha * hudOpacity;

                        // 平滑振荡渐变，无跳变
                        float wave = (float) Math.sin(glowPhase * hudColorSpeed + i * 0.25f);
                        float t = wave * 0.5f + 0.5f;
                        Color c = lerp(hudPrimary, hudSecondary, t);

                        int arcR = labelH / 2;

                        // 霓虹光晕：16层高斯扩散，最外层透明
                        for (int l = 16; l >= 1; l--) {
                            int pad = l;
                            float ratio = (float)l / 16;
                            int a = (int)(ga * 55 * Math.exp(-ratio * 3) * hudBgAlpha);
                            if (a > 0) {
                                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1f, a/255f)));
                                g2.setColor(c);
                                g2.fillRoundRect(18 + (int)shiftX - pad, y - pad,
                                        uniW + pad*2, labelH + pad*2, arcR+pad, arcR+pad);
                            }
                        }

                        // 标签背景
                        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, ga));
                        int bgA = (int)(230 * hudBgAlpha);
                        if (bgA > 0) {
                            g2.setColor(new Color(10, 10, 16, bgA));
                            g2.fillRoundRect(18 + (int)shiftX, y, uniW, labelH, arcR, arcR);
                        }

                        // 彩色边框
                        g2.setColor(withAlpha(c, (int)(150 * hudBgAlpha)));
                        g2.setStroke(new BasicStroke(1f));
                        g2.drawRoundRect(18 + (int)shiftX, y, uniW - 1, labelH, arcR, arcR);

                        // 左侧彩色竖条
                        g2.setColor(withAlpha(c, (int)(255 * hudBgAlpha)));
                        g2.fillRoundRect(18 + (int)shiftX + 4, y + 5, 3, labelH - 10, 2, 2);

                        // 文字（流动渐变色）
                        String label = e.info.isEmpty() ? e.name : e.name + "  " + e.info;
                        g2.setFont(modFont);
                        g2.setColor(c);
                        g2.drawString(label, 18 + (int)shiftX + 14, y + fm.getAscent() + 2);

                        y += labelH + 3;
                    }

                }
            };
            hudPanel.setOpaque(false);
            hudPanel.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));

            MouseAdapter ma = new MouseAdapter() {
                public void mousePressed(MouseEvent e)  { dragOffset = e.getPoint(); }
                public void mouseDragged(MouseEvent e)  {
                    Point p = e.getLocationOnScreen();
                    hudX = p.x - dragOffset.x;
                    hudY = p.y - dragOffset.y;
                    reposition();
                }
            };
            hudPanel.addMouseListener(ma);
            hudPanel.addMouseMotionListener(ma);

            hudPanel.addMouseListener(new MouseAdapter() {});

            javax.swing.Timer loop = new javax.swing.Timer(8, e -> {
                glowPhase += 0.015f;
                if (glowPhase > 5f) glowPhase -= 5f;
                entries.removeIf(en -> !en.active && en.anim.get() < 0.002f);
                updateSize();
                hudPanel.repaint();
            });
            loop.start();

            hudFrame.add(hudPanel);
            updateSize();
            syncRefresh();
            reposition();
            hudFrame.setVisible(true);
        }

        void updateSize() {
            FontMetrics fm = Toolkit.getDefaultToolkit().getFontMetrics(modFont);
            lineH = fm.getHeight() + 6;
            int uniW = 100;
            int visible = 0;
            for (HUDEntry e : entries) {
                if (!e.active && e.anim.get() < 0.002f) continue;
                visible++;
                String label = e.info.isEmpty() ? e.name : e.name + "  " + e.info;
                int w = fm.stringWidth(label) + 20;
                if (w > uniW) uniW = w;
            }
            int newW = uniW + 54;
            int newH = Math.max(20, visible * (lineH + 3) + 36);
            if (newW != panelW || newH != panelH) {
                panelW = newW; panelH = newH;
                hudPanel.setPreferredSize(new Dimension(panelW, panelH));
                hudFrame.pack();
                if (hudX == 0) { Dimension screen = Toolkit.getDefaultToolkit().getScreenSize(); hudX = screen.width - panelW - 20; }
                reposition();
            }
        }

        void reposition() { hudFrame.setLocation(hudX, hudY); }

        /** 模块切换时触发动画 */
        void syncRefresh() {
            // 收集当前启用的模块
            Map<Category, List<Module>> grouped = new LinkedHashMap<>();
            for (Category cat : Category.values()) grouped.put(cat, new ArrayList<>());
            for (Module m : this.modules) if (m.enabled) grouped.get(m.category).add(m);

            List<String[]> lines = new ArrayList<>();
            for (Map.Entry<Category, List<Module>> e : grouped.entrySet()) {
                List<Module> mods = e.getValue();
                if (mods.isEmpty()) continue;
                mods.sort(Comparator.comparing(m -> m.name));
                for (Module m : mods) {
                    String info = "";
                    for (Setting s : m.settings) {
                        if (s.type == Setting.Type.SLIDER && (s.name.equals("距离") || s.name.equals("范围"))) {
                            info = "[" + s.value + "]"; break;
                        } else if (s.type == Setting.Type.MODE && s.name.equals("模式")) {
                            info = "[" + s.value + "]"; break;
                        }
                    }
                    lines.add(new String[]{m.name, info});
                }
            }

            // 找出新启用的模块
            Set<String> currentNames = new HashSet<>();
            for (String[] l : lines) currentNames.add(l[0]);

            // 标记要移除的（不在当前列表中的旧条目）
            for (HUDEntry e : entries) {
                if (!currentNames.contains(e.name) && e.active) {
                    e.active = false;
                    e.anim.setTarget(0f);
                }
            }

            // 添加新条目或重新激活现有条目
            for (String[] line : lines) {
                HUDEntry found = null;
                for (HUDEntry e : entries) {
                    if (e.name.equals(line[0])) { found = e; break; }
                }
                if (found != null) {
                    if (!found.active) {
                        found.active = true;
                        found.anim.setTarget(1f);
                    }
                    found.info = line[1];
                } else {
                    HUDEntry ne = new HUDEntry(line[0], line[1]);
                    ne.active = true;
                    ne.anim.setTarget(1f);
                    entries.add(ne);
                }
            }

            hudPanel.repaint();
        }

        void toggleVisible() {
            hudFrame.setVisible(!hudFrame.isVisible());
            syncRefresh();
        }
        boolean isVisible() { return hudFrame.isVisible(); }
    }

    public static void main(String[] args) {
        // FlatLaf 现代暗色主题
        FlatDarkLaf.setup();
        UIManager.put("Button.arc", 999);
        UIManager.put("Component.arc", 12);
        UIManager.put("Slider.thumbSize", 12);

        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Minecraft Cheat Client");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setUndecorated(true);
            frame.setBackground(new Color(0, 0, 0, 0)); // 全透明

            // 主面板 — 绘制玻璃背景
            JPanel glassBg = new JPanel() {
                float gridPhase = 0f;
                {
                    javax.swing.Timer t = new javax.swing.Timer(8, e -> {
                        gridPhase += 0.0025f;
                        if (gridPhase > 1f) gridPhase -= 1f;
                        repaint();
                    });
                    t.start();
                }
                void drawTechGrid(Graphics2D g2, int w, int h) {
                    int spacing = 44;
                    g2.setColor(withAlpha(Theme.accent, 12));
                    for (int x = 0; x < w; x += spacing) g2.drawLine(x, 0, x, h);
                    for (int y = 0; y < h; y += spacing) g2.drawLine(0, y, w, y);
                    // 流动节点
                    for (int x = 0; x < w; x += spacing) {
                        for (int y = 0; y < h; y += spacing) {
                            float dist = (float) Math.hypot(x - w / 2f, y - h / 2f);
                            float p = (dist / 180f + gridPhase) % 1f;
                            if (p < 0.15f) {
                                int a = (int) (180 * (1 - p / 0.15f));
                                g2.setColor(withAlpha(Theme.accent, a / 5));
                                g2.fillOval(x - 2, y - 2, 4, 4);
                            }
                        }
                    }
                }
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int w = getWidth(), h = getHeight();
                    int r = guiRounding;

                    // 1. 科技网格背景
                    drawTechGrid(g2, w, h);

                    // 2. 外层投影（10层，模仿 Discord 窗口阴影）
                    for (int i = 9; i >= 0; i--) {
                        g2.setColor(new Color(0, 0, 0, 5 + i * 5));
                        g2.fillRoundRect(i - 2, i + 2, w - i * 2 + 4, h - i * 2 + 3, r + i, r + i);
                    }

                    // 3. 深色玻璃底
                    g2.setColor(new Color(8, 8, 14, 190));
                    g2.fillRoundRect(0, 0, w, h, r, r);

                    // 4. 磨砂层
                    g2.setColor(new Color(40, 40, 55, 60));
                    g2.fillRoundRect(1, 1, w - 2, h - 2, r - 1, r - 1);

                    // 5. 顶部镜面高光
                    GradientPaint glare = new GradientPaint(0, 0, new Color(255, 255, 255, 25),
                            0, h * 0.35f, new Color(255, 255, 255, 0));
                    g2.setPaint(glare);
                    g2.fillRoundRect(2, 2, w - 4, (int) (h * 0.35), r - 2, r - 2);

                    // 6. 底部 accent 柔光带
                    GradientPaint bottomGlow = new GradientPaint(0, h, withAlpha(Theme.accent, 15),
                            0, (int) (h * 0.85f), new Color(0, 0, 0, 0));
                    g2.setPaint(bottomGlow);
                    g2.fillRoundRect(2, (int) (h * 0.8f), w - 4, h / 5, r - 2, r - 2);

                    // 7. 内边框（微白）
                    g2.setColor(withAlpha(Color.WHITE, 12));
                    g2.setStroke(new BasicStroke(1f));
                    g2.drawRoundRect(2, 2, w - 5, h - 5, r - 2, r - 2);

                    // 8. 外边框
                    g2.setColor(withAlpha(Theme.accent, 70));
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawRoundRect(0, 0, w - 1, h - 1, r, r);

                    g2.dispose();
                }
            };
            glassBg.setOpaque(false);
            glassBg.setLayout(new BorderLayout());

            // 内容（先创建，以便 colorDot 的回调能引用）
            List<Module> modules = buildModules();
            ClickGUIPanel gui = new ClickGUIPanel(frame, modules);

            // 标题栏
            final float[] titlePhase = {0f};
            JPanel titleBar = new JPanel(new BorderLayout()) {
                {
                    javax.swing.Timer t = new javax.swing.Timer(8, e -> {
                        titlePhase[0] += 0.006f;
                        if (titlePhase[0] > 2f) titlePhase[0] -= 2f;
                        repaint();
                    });
                    t.start();
                }
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    int w = getWidth(), h = getHeight();

                    // 标题渐变
                    GradientPaint gp = new GradientPaint(0, 0, Theme.glowA, w, 0, Theme.glowB);
                    g2.setPaint(gp);
                    g2.setFont(monoFont(Font.BOLD, 13));
                    g2.drawString(">  MYTHIC CLIENT", 18, 22);

                    // 底部扫描线光效
                    float sx = (float) (Math.sin(titlePhase[0] * Math.PI) * 0.5 + 0.5);
                    g2.setColor(withAlpha(Theme.accent, (int) (15 * sx)));
                    g2.fillRect(0, h - 2, w, 2);

                    g2.dispose();
                }
            };
            titleBar.setOpaque(false);
            titleBar.setPreferredSize(new Dimension(0, 36));
            titleBar.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));

            // 右上角：功能统计 + 颜色指示器 + 关闭按钮
            JPanel rightGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
            rightGroup.setOpaque(false);

            // 渐变模块名轮播标签（自定义绘制）
            final float[] gradPhase = {0f};
            final String[] displayText = {""};
            List<Module> allMods = modules;
            final int[] scrollIdx = {0};

            JPanel modLabel = new JPanel() {
                {
                    setOpaque(false);
                    setPreferredSize(new Dimension(150, 22));
                }
                Runnable updater = () -> {
                    List<Module> enabled = new ArrayList<>();
                    for (Module m : allMods) if (m.enabled) enabled.add(m);
                    if (enabled.isEmpty()) {
                        displayText[0] = "-- 无启用模块 --";
                    } else {
                        scrollIdx[0] = scrollIdx[0] % enabled.size();
                        displayText[0] = "[" + enabled.size() + "] " + enabled.get(scrollIdx[0]).name;
                        scrollIdx[0]++;
                    }
                    repaint();
                };
                {
                    updater.run();
                    // 每 2.5 秒轮播
                    javax.swing.Timer t = new javax.swing.Timer(2500, e -> updater.run());
                    t.start();
                    // 渐变流动
                    javax.swing.Timer t2 = new javax.swing.Timer(8, e -> {
                        gradPhase[0] += 0.005f;
                        if (gradPhase[0] > 2f) gradPhase[0] -= 2f;
                        repaint();
                    });
                    t2.start();
                }
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    Font font = chineseFont(11);
                    g2.setFont(font);
                    FontMetrics fm = g2.getFontMetrics();
                    String text = displayText[0];
                    int textW = fm.stringWidth(text);
                    int x = 0, y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;

                    // 逐字符渐变绘制
                    float charW = (float) textW / Math.max(1, text.length());
                    for (int i = 0; i < text.length(); i++) {
                        float phase = gradPhase[0] + (float) i / text.length() * 2f;
                        float t = (float)(Math.sin(phase * Math.PI) * 0.5 + 0.5);
                        Color c = lerp(Theme.glowA, Theme.glowB, t);
                        g2.setColor(c);
                        String ch = String.valueOf(text.charAt(i));
                        int cx = x + Math.round(i * charW);
                        g2.drawString(ch, cx, y);
                    }
                    g2.dispose();
                }
            };
            rightGroup.add(modLabel);

            // 分隔小竖线
            JPanel sepLine = new JPanel() {
                protected void paintComponent(Graphics g) {
                    Graphics2D g2d = (Graphics2D) g.create();
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2d.setColor(withAlpha(Theme.accent, 40));
                    g2d.fillRoundRect(0, 0, 1, 14, 2, 2);
                    g2d.dispose();
                }
            };
            sepLine.setPreferredSize(new Dimension(1, 14));
            sepLine.setOpaque(false);
            rightGroup.add(sepLine);

            // 颜色指示圆点
            JPanel colorDot = new JPanel() {
                final float[] pulsePhase = {0f};
                {
                    javax.swing.Timer t = new javax.swing.Timer(8, e -> {
                        pulsePhase[0] += 0.016f;
                        repaint();
                    });
                    t.start();
                }
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    float p = (float) (Math.sin(pulsePhase[0]) * 0.5 + 0.5);
                    int glow = (int) (3 + 2 * p);
                    // 呼吸光晕
                    g2.setColor(withAlpha(Theme.accent, (int) (70 * (0.4f + 0.6f * p))));
                    g2.fillOval(2 - glow, 2 - glow, 14 + glow * 2, 14 + glow * 2);
                    g2.setColor(Theme.accent);
                    g2.fillOval(2, 2, 14, 14);
                    g2.setStroke(new BasicStroke(1.2f));
                    g2.setColor(withAlpha(Color.WHITE, 100));
                    g2.drawOval(2, 2, 14, 14);
                    g2.dispose();
                }
            };
            colorDot.setPreferredSize(new Dimension(18, 18));
            colorDot.setOpaque(false);
            colorDot.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            colorDot.setToolTipText("点击更换颜色");
            colorDot.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    ColorPickerDialog dlg = new ColorPickerDialog(frame);
                    dlg.setVisible(true);
                    if (dlg.result != null) {
                        colorDot.repaint();
                        frame.repaint();
                        gui.refreshColors();
                    }
                }
            });
            rightGroup.add(colorDot);

            // 关闭按钮（带动画和光晕）
            AnimFloat closeHoverAni = new AnimFloat(0f, 0.25f);
            JButton closeBtn = new JButton("✕") {
                {
                    closeHoverAni.addListener(this::repaint);
                    setOpaque(false);
                    setContentAreaFilled(false);
                    setBorderPainted(false);
                    setFocusPainted(false);
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    setPreferredSize(new Dimension(28, 24));
                }
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    float h = closeHoverAni.get();
                    int w = getWidth(), hh = getHeight();
                    if (h > 0.01f) {
                        g2.setColor(withAlpha(Color.RED, (int) (40 * h)));
                        g2.fillOval(w / 2 - 10, hh / 2 - 10, 20, 20);
                    }
                    Font f = getFont().deriveFont(Font.BOLD, 15f + 3f * h);
                    g2.setFont(f);
                    g2.setColor(h > 0.5f ? Color.RED : Theme.textSecondary);
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString("X", (w - fm.stringWidth("X")) / 2,
                            (hh + fm.getAscent() - fm.getDescent()) / 2);
                    g2.dispose();
                }
            };
            closeBtn.addActionListener(e -> System.exit(0));
            closeBtn.addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { closeHoverAni.setTarget(1f); }
                public void mouseExited(MouseEvent e)  { closeHoverAni.setTarget(0f); }
            });
            rightGroup.add(closeBtn);

            titleBar.add(rightGroup, BorderLayout.EAST);

            // 拖动标题栏移动窗口
            MouseAdapter titleDrag = new MouseAdapter() {
                Point offset;
                public void mousePressed(MouseEvent e) {
                    offset = e.getPoint();
                }
                public void mouseDragged(MouseEvent e) {
                    Point p = e.getLocationOnScreen();
                    frame.setLocation(p.x - offset.x, p.y - offset.y);
                }
            };
            titleBar.addMouseListener(titleDrag);
            titleBar.addMouseMotionListener(titleDrag);

            glassBg.add(titleBar, BorderLayout.NORTH);
            glassBg.add(gui, BorderLayout.CENTER);

            frame.setContentPane(glassBg);
            frame.setSize(800, 530);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            // 启动屏幕右上角 HUD，与 ClickGUI 联动
            ModuleHUD hud = new ModuleHUD(modules);
            hudRef = hud;
            gui.setHUD(hud);

        });
    }
}
