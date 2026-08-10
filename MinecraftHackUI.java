import com.formdev.flatlaf.FlatDarkLaf;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;

/**
 * Minecraft 风格 ClickGUI —— 纯透明背景水玻璃
 * 窗口本身全透明，背后桌面自然透出，仅叠加轻薄水色玻璃层
 */
public class MinecraftHackUI {

    // ==================== 字体 ====================
    static float fontScale = 1.0f;
    static Font uiFont(float size) {
        String[] candidates = {"Microsoft YaHei", "SimHei", "PingFang SC", "Dialog"};
        for (String name : candidates) {
            Font f = new Font(name, Font.PLAIN, (int)(size * fontScale));
            if (f.canDisplay('中')) return f;
        }
        return new Font("Dialog", Font.PLAIN, (int)(size * fontScale));
    }
    static Font uiFont(int style, float size) {
        String[] candidates = {"Microsoft YaHei", "SimHei", "PingFang SC", "Dialog"};
        for (String name : candidates) {
            Font f = new Font(name, style, (int)(size * fontScale));
            if (f.canDisplay('中')) return f;
        }
        return new Font("Dialog", style, (int)(size * fontScale));
    }

    // ==================== 音效 ====================
    static void playClick() { playTone(800, 80, 60); }
    static void playToggle() { playTone(600, 60, 50); }
    static void playTone(int freq, int len, int vol) {
        new Thread(() -> {
            try {
                javax.sound.sampled.AudioFormat fmt = new javax.sound.sampled.AudioFormat(8000, 8, 1, true, false);
                javax.sound.sampled.SourceDataLine line = javax.sound.sampled.AudioSystem.getSourceDataLine(fmt);
                line.open(fmt); line.start();
                byte[] buf = new byte[len];
                for (int i = 0; i < buf.length; i++) {
                    double t = (double)i / 8000;
                    double env = Math.exp(-t * 60);
                    buf[i] = (byte)(Math.sin(2 * Math.PI * freq * t) * env * vol);
                }
                line.write(buf, 0, buf.length);
                line.drain(); line.close();
            } catch (Exception ignored) {}
        }).start();
    }

    // ==================== 缓动与动画 ====================
    static class Easing {
        static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
        static float easeOutCubic(float t) {
            float x = clamp01(t);
            return 1f - (float)Math.pow(1f - x, 3);
        }
        static float easeOutBack(float t) {
            float x = clamp01(t);
            float c1 = 1.70158f, c3 = c1 + 1f;
            return 1f + c3 * (float)Math.pow(x - 1f, 3) + c1 * (float)Math.pow(x - 1f, 2);
        }
    }

    static class AnimFloat {
        private float value, target, startValue;
        private long startTime;
        private int durationMs;
        private final List<Runnable> listeners = new ArrayList<>();
        private javax.swing.Timer timer;
        AnimFloat(float initial) { this(initial, 220); }
        AnimFloat(float initial, int durationMs) {
            this.value = initial; this.target = initial; this.durationMs = durationMs;
        }
        void setTarget(float t) {
            if (Math.abs(target - t) < 0.001f && timer != null && timer.isRunning()) return;
            startValue = value; target = t; startTime = System.currentTimeMillis();
            ensureRunning();
        }
        void snapTo(float t) { value = t; target = t; notifyListeners(); }
        float get() { return value; }
        float getTarget() { return target; }
        void addListener(Runnable r) { listeners.add(r); }
        private void ensureRunning() {
            if (timer != null && timer.isRunning()) return;
            timer = new javax.swing.Timer(8, e -> {
                long elapsed = System.currentTimeMillis() - startTime;
                float t = Math.min(1f, (float)elapsed / durationMs);
                value = startValue + (target - startValue) * Easing.easeOutCubic(t);
                notifyListeners();
                if (t >= 1f) { value = target; notifyListeners(); ((javax.swing.Timer)e.getSource()).stop(); }
            });
            timer.start();
        }
        private void notifyListeners() { for (Runnable r : listeners) r.run(); }
    }

    // ==================== 主题 ====================
    static class Theme {
        // 红色强调
        static Color accent       = new Color(0xFF3333);
        static Color accentDim    = new Color(0xCC2222);
        static Color glowA        = new Color(0xFF3333);
        static Color glowB        = new Color(0xFF8888);

        // 文字色 —— 浅色用于透明背景上
        static Color textPrimary   = new Color(0xF0F4F8);
        static Color textSecondary = new Color(0xB0BEC8);
        static Color textDim       = new Color(0x708090);

        private static final List<Consumer<Color>> listeners = new ArrayList<>();
        static void updateFromAccent(Color c) {
            accent = c;
            float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
            accentDim = Color.getHSBColor(hsb[0], hsb[1], Math.max(0f, hsb[2] - 0.2f));
            glowA = c;
            glowB = Color.getHSBColor((hsb[0] + 0.03f) % 1f,
                    Math.max(0f, hsb[1] - 0.05f), Math.min(1f, hsb[2] + 0.08f));
            for (Consumer<Color> l : listeners) l.accept(c);
        }
        static void addListener(Consumer<Color> l) { listeners.add(l); }
    }

    // ==================== 样式 ====================
    static class Style {
        static int panelRounding = 18;
        static int glassAlpha = 70;           // 面板玻璃透明度 (0-255)
        static int cardAlpha = 40;            // 卡片透明度
        static int borderAlpha = 50;          // 边框透明度
        static int highlightAlpha = 35;       // 高光透明度
        static int hoverAlpha = 30;
        static int shadowAlpha = 50;          // 底部阴影透明度

        private static final List<Runnable> listeners = new ArrayList<>();
        static void addListener(Runnable r) { listeners.add(r); }
        static void notifyUpdate() { for (Runnable r : listeners) r.run(); }
    }

    static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, alpha)));
    }
    static Color lerp(Color a, Color b, float t) {
        return new Color(
            (int)(a.getRed()   + (b.getRed()   - a.getRed())   * t),
            (int)(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
            (int)(a.getBlue()  + (b.getBlue()  - a.getBlue())  * t));
    }

    // ==================== 全局管理 ====================
    static final List<JFrame> allWindows = new ArrayList<>();
    static final Map<String, Module> moduleMap = new HashMap<>();
    static final Map<String, JComponent> quickButtonMap = new HashMap<>();
    static final List<ModuleCard> allCards = new ArrayList<>();
    static ModuleHUD hudRef;

    static void registerWindow(JFrame f) { allWindows.add(f); }
    static void closeAll() {
        for (JFrame f : allWindows) f.dispose();
        System.exit(0);
    }
    static void refreshAll() {
        for (JFrame f : allWindows) f.repaint();
        for (ModuleCard c : allCards) c.refresh();
    }
    static void onModuleToggle(Module m) {
        refreshAll();
        if (hudRef != null) hudRef.syncRefresh();
    }

    // ==================== 可拖动透明窗口基类 ====================
    static class FloatingWindow extends JFrame {
        Point dragOffset;
        FloatingWindow(String title, int x, int y, int w, int h) {
            super(title);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setUndecorated(true);
            // 关键：背景设为全透明，桌面可以透过来
            setBackground(new Color(0, 0, 0, 0));
            setSize(w, h);
            setLocation(x, y);
            setAlwaysOnTop(true);
            registerWindow(this);
        }
        void makeDraggable(Component c) {
            MouseAdapter ma = new MouseAdapter() {
                public void mousePressed(MouseEvent e) { dragOffset = e.getPoint(); }
                public void mouseDragged(MouseEvent e) {
                    Point p = e.getLocationOnScreen();
                    setLocation(p.x - dragOffset.x, p.y - dragOffset.y);
                }
            };
            c.addMouseListener(ma);
            c.addMouseMotionListener(ma);
        }
    }

    // ==================== 数据模型 ====================
    enum Category {
        COMBAT("Combat"), MOVEMENT("Movement"), PLAYER("Player"),
        VISUAL("Visual"), WORLD("World"), MISC("Misc");
        final String label;
        Category(String s) { label = s; }
    }

    static class Setting {
        enum Type { BOOL, SLIDER, MODE }
        String name; Type type; Object value; int min, max; String[] options;
        Setting(String name, boolean def) { this.name=name; this.type=Type.BOOL; this.value=def; }
        Setting(String name, int def, int min, int max) { this.name=name; this.type=Type.SLIDER; this.value=def; this.min=min; this.max=max; }
        Setting(String name, String def, String... options) { this.name=name; this.type=Type.MODE; this.value=def; this.options=options; }
    }

    static class Module {
        String name; Category category; boolean enabled; boolean highlighted;
        String keyBind; // 快捷键
        List<Setting> settings = new ArrayList<>();
        Module(String n, Category c) { this(n, c, false, false); }
        Module(String n, Category c, boolean en) { this(n, c, en, false); }
        Module(String n, Category c, boolean en, boolean hi) { name=n; category=c; enabled=en; highlighted=hi; }
        Module s(Setting... ss) { Collections.addAll(settings, ss); return this; }
    }

    static List<Module> buildModules() {
        List<Module> list = new ArrayList<>();
        list.add(new Module("KillAura", Category.COMBAT, true));
        list.add(new Module("Backtrack", Category.COMBAT));
        list.add(new Module("Aimbot", Category.COMBAT));
        list.add(new Module("AutoClicker", Category.COMBAT));
        list.add(new Module("AutoSnowball", Category.COMBAT));
        list.add(new Module("FireAura", Category.COMBAT));
        list.add(new Module("Criticals", Category.COMBAT, false, true).s(
            new Setting("Hit Boxes", false), new Setting("InfiniteAura", false),
            new Setting("TPAura", false), new Setting("PvPTurbo", false),
            new Setting("RememberAura", false), new Setting("LegitFightBot", false),
            new Setting("RiptideAura", false), new Setting("SpinAttack", false)
        ));

        list.add(new Module("AirJump", Category.MOVEMENT));
        list.add(new Module("AirStuck", Category.MOVEMENT));
        list.add(new Module("BlinkFly", Category.MOVEMENT));
        list.add(new Module("FastStop", Category.MOVEMENT));
        list.add(new Module("Fly", Category.MOVEMENT));
        list.add(new Module("ThemisFly", Category.MOVEMENT));
        list.add(new Module("ForceSprint", Category.MOVEMENT, false, true));
        list.add(new Module("JumpReset", Category.MOVEMENT));
        list.add(new Module("MotionFly", Category.MOVEMENT));
        list.add(new Module("NoSlowdown", Category.MOVEMENT, true, true));
        list.add(new Module("SafeWalk", Category.MOVEMENT));
        list.add(new Module("Speed", Category.MOVEMENT));
        list.add(new Module("Step", Category.MOVEMENT, false, true));
        list.add(new Module("TargetStrafe", Category.MOVEMENT, false, true));
        list.add(new Module("Velocity", Category.MOVEMENT, true, true));
        list.add(new Module("Scaffold", Category.MOVEMENT));
        list.add(new Module("BoatFly", Category.MOVEMENT));

        list.add(new Module("ECGodMode", Category.PLAYER));
        list.add(new Module("AutoL", Category.PLAYER, false, true));
        list.add(new Module("ChestAura", Category.PLAYER));
        list.add(new Module("ChestStealer", Category.PLAYER));
        list.add(new Module("InvManager", Category.PLAYER));
        list.add(new Module("Grapple", Category.PLAYER));
        list.add(new Module("AntiVoid", Category.PLAYER));
        list.add(new Module("AutoTool", Category.PLAYER, false, true));
        list.add(new Module("HotbarDebug", Category.PLAYER));
        list.add(new Module("Derp", Category.PLAYER));
        list.add(new Module("Freecam", Category.PLAYER));
        list.add(new Module("NoDebuff", Category.PLAYER));
        list.add(new Module("NoFall", Category.PLAYER, true));
        list.add(new Module("NoRotate", Category.PLAYER, true));
        list.add(new Module("ClickTeleport", Category.PLAYER));
        list.add(new Module("Respawn", Category.PLAYER));
        list.add(new Module("SummonVehicle", Category.PLAYER));

        list.add(new Module("Shortcut Scale", Category.VISUAL).s(new Setting("", 125, 50, 200)));
        list.add(new Module("Theme", Category.VISUAL).s(new Setting("", "Fixed", "Fixed", "Rainbow", "Gradient")));
        list.add(new Module("Saturation", Category.VISUAL).s(new Setting("", 100, 0, 200)));
        list.add(new Module("HUD Primary", Category.VISUAL, false, true).s(
            new Setting("Pick Color", false),
            new Setting("R", 77, 0, 255), new Setting("G", 166, 0, 255), new Setting("B", 255, 0, 255)
        ));
        list.add(new Module("HUD Secondary", Category.VISUAL, false, true).s(
            new Setting("Pick Color", false),
            new Setting("R", 255, 0, 255), new Setting("G", 110, 0, 255), new Setting("B", 199, 0, 255)
        ));
        list.add(new Module("HUD Speed", Category.VISUAL).s(new Setting("", 15, 5, 30)));
        list.add(new Module("HUD Font Size", Category.VISUAL).s(new Setting("", 12, 8, 20)));
        list.add(new Module("Accent Color", Category.VISUAL, false, true).s(
            new Setting("Pick", false)
        ));
        list.add(new Module("Panel Glow", Category.VISUAL, false, true).s(
            new Setting("Pick Color", false),
            new Setting("Pick Sec Col", false),
            new Setting("Alpha", 80, 0, 100)
        ));
        list.add(new Module("Panel Opacity", Category.VISUAL).s(new Setting("", 15, 0, 100)));
        list.add(new Module("HUD Opacity", Category.VISUAL).s(new Setting("", 80, 0, 100)));
        list.add(new Module("Preset", Category.VISUAL).s(new Setting("", "Ocean", "Ocean", "Sunset", "Forest", "Berry", "Neon")));
        list.add(new Module("ClickUI Blur", Category.VISUAL, true).s(
            new Setting("Blur Radius", 9, 0, 20),
            new Setting("Blur Downscale", 1, 1, 4),
            new Setting("Blur Alpha", 100, 0, 100)
        ));
        list.add(new Module("Glass Alpha", Category.VISUAL).s(new Setting("", 37, 0, 100)));
        list.add(new Module("Shadow", Category.VISUAL, true).s(
            new Setting("Shadow Alpha", 53, 0, 100),
            new Setting("Shadow Strength", 52, 0, 100),
            new Setting("Shadow Radius", 22, 0, 50)
        ));
        list.add(new Module("Panel Rounding", Category.VISUAL).s(new Setting("", 15, 0, 30)));
        list.add(new Module("Card Alpha", Category.VISUAL).s(new Setting("", 25, 0, 100)));
        list.add(new Module("Border Alpha", Category.VISUAL).s(new Setting("", 0, 0, 100)));
        list.add(new Module("Hover Alpha", Category.VISUAL).s(new Setting("", 100, 0, 100)));
        list.add(new Module("Block Bloom", Category.VISUAL, true).s(new Setting("Bloom Alpha", 65, 0, 100)));

        list.add(new Module("InvManager", Category.WORLD));
        list.add(new Module("FastBuild", Category.WORLD));
        list.add(new Module("PlacementRange", Category.WORLD).s(new Setting("", 5, 1, 10)));
        list.add(new Module("AutoTrap", Category.WORLD));
        list.add(new Module("FastDig", Category.WORLD));
        list.add(new Module("FastBreak", Category.WORLD));
        list.add(new Module("Nuker", Category.WORLD));
        list.add(new Module("WorldTimer", Category.WORLD));
        list.add(new Module("Timer", Category.WORLD).s(new Setting("", 1, 1, 10)));

        list.add(new Module("Configuration", Category.MISC, false, true).s(
            new Setting("Show shortcut", true), new Setting("Config File", false),
            new Setting("Refresh Configs", false), new Setting("Save Config", false),
            new Setting("Load Config", false), new Setting("Reset HUD Layout", false)
        ));
        list.add(new Module("AntiBot", Category.MISC, false, true).s(
            new Setting("Teams", false), new Setting("AutoHeypixel", false),
            new Setting("Disabler", false), new Setting("Spammer", false)
        ));
        list.add(new Module("CookieLogin", Category.MISC, false, true).s(
            new Setting("CookieExport", false), new Setting("CookieLogin", false),
            new Setting("NoPacket", false), new Setting("RepeatPacket", false),
            new Setting("EmptyTransferTest", false), new Setting("PlayerKick", false),
            new Setting("RemoteShop", false), new Setting("DebugBlockInfo", false)
        ));

        for (Module m : list) moduleMap.put(m.name, m);
        return list;
    }

    // ==================== 水玻璃绘制（纯透明背景 + 轻薄水色叠加） ====================

    /**
     * 面板级水玻璃：
     * 窗口背景已经是全透明的，桌面自然透出。
     * 我们只叠加轻薄的水色玻璃层 + 高光 + 暗部 + 边框。
     * 就像一层水膜贴在透明玻璃上。
     */
    static void drawWaterPanel(Graphics2D g2, int w, int h, int r) {
        if (w <= 0 || h <= 0) return;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int m = TranslucentPanel.GLOW_MARGIN;
        int gw = w - m*2, gh = h - m*2;

        // ---- 1. 边缘光晕（16层，动态渐变：相位摆动 + 上下渐变） ----
        for (int l = 16; l >= 1; l--) {
            int pad = l;
            float ratio = (float)l / 16;
            int a = (int)(panelGlowAlpha * 55 * Math.exp(-ratio * 3) * 0.8f);
            if (a > 0) {
                float fa = Math.min(1f, a / 255f);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, fa));
                // 静态上下渐变：顶部主色、底部副色
                GradientPaint vertGrad = new GradientPaint(0, 0, panelGlow, 0, h, panelGlowSec);
                g2.setPaint(vertGrad);
                g2.fillRoundRect(m - pad, m - pad, gw + pad*2, gh + pad*2, r + pad, r + pad);
            }
        }
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));

        // ---- 2. 磨砂玻璃（6层半透模拟毛玻璃） ----
        int bgA = (int)(panelBgAlpha * 255);
        if (bgA > 0) {
            g2.setColor(new Color(10, 14, 22, (int)(bgA * 0.55f)));
            g2.fillRoundRect(m, m, gw, gh, r, r);
            g2.setColor(new Color(240, 245, 252, (int)(bgA * 0.15f)));
            g2.fillRoundRect(m + 1, m + 1, gw - 2, gh - 2, r - 1, r - 1);
            g2.setColor(new Color(255, 255, 255, (int)(bgA * 0.08f)));
            g2.fillRoundRect(m + 2, m + 2, gw - 4, gh - 4, r - 2, r - 2);
            GradientPaint topFade = new GradientPaint(m, m, new Color(255,255,255,(int)(bgA*0.18f)), m, m+gh*0.3f, new Color(255,255,255,0));
            g2.setPaint(topFade);
            g2.fillRoundRect(m + 1, m + 1, gw - 2, (int)(gh * 0.35), r - 1, r - 1);
            GradientPaint botFade = new GradientPaint(m, m+gh, new Color(0,0,0,(int)(bgA*0.25f)), m, m+gh*0.6f, new Color(0,0,0,0));
            g2.setPaint(botFade);
            g2.fillRoundRect(m + 1, (int)(m + gh * 0.55), gw - 2, (int)(gh * 0.45), r - 1, r - 1);
            g2.setColor(new Color(255, 255, 255, (int)(bgA * 0.03f)));
            for (int nx = m + 4; nx < m + gw - 4; nx += 3)
                for (int ny = m + 4; ny < m + gh - 4; ny += 3)
                    if ((nx * 7 + ny * 13) % 5 == 0) g2.fillRect(nx, ny, 1, 1);
        }

        // ---- 3. 边缘细边框 ----
        g2.setStroke(new BasicStroke(1.2f));
        g2.setColor(new Color(255, 255, 255, 35));
        g2.drawRoundRect(m, m, gw - 1, gh - 1, r, r);

    }

    /**
     * 卡片级水玻璃 —— 更轻薄透明
     */
    static void drawWaterCard(Graphics2D g2, int w, int h, int r,
                               boolean highlighted, boolean enabled, float hover) {
        if (w <= 0 || h <= 0) return;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (enabled) {
            // ---- 激活态：水蓝色玻璃 ----
            g2.setColor(withAlpha(Theme.accent, 75));
            g2.fillRoundRect(0, 0, w, h, r, r);

            g2.setColor(new Color(8, 12, 20, 35));
            g2.fillRoundRect(0, 0, w, h, r, r);

            GradientPaint gp = new GradientPaint(
                0, 0, new Color(255, 255, 255, 40),
                0, h * 0.35f, new Color(255, 255, 255, 0)
            );
            g2.setPaint(gp);
            g2.fillRoundRect(1, 1, w - 2, (int)(h * 0.35), r - 1, r - 1);

            g2.setColor(withAlpha(Theme.accent, 120));
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(0, 0, w - 1, h - 1, r, r);

            g2.setColor(new Color(255, 255, 255, 30));
            g2.drawRoundRect(1, 1, w - 3, h - 3, r - 1, r - 1);

        } else {
            // ---- 普通态：统一外观，无悬停变色 ----
            g2.setColor(new Color(26, 30, 38, 60));
            g2.fillRoundRect(0, 0, w, h, r, r);

            // 边框
            g2.setColor(new Color(255, 255, 255, 20));
            g2.setStroke(new BasicStroke(0.6f));
            g2.drawRoundRect(0, 0, w - 1, h - 1, r, r);
        }
    }

    // ==================== 自定义开关 ====================
    static class CustomToggle extends JComponent {
        boolean on;
        AnimFloat anim;
        int w = 26, h = 14;
        CustomToggle(boolean initial) {
            this.on = initial;
            anim = new AnimFloat(on ? 1f : 0f, 150);
            anim.addListener(this::repaint);
            setPreferredSize(new Dimension(w, h));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    on = !on; playToggle();
                    anim.setTarget(on ? 1f : 0f);
                    fireActionEvent();
                }
            });
        }
        void setOn(boolean v) { on = v; anim.setTarget(on ? 1f : 0f); }
        private final List<ActionListener> actionListeners = new ArrayList<>();
        void addActionListener(ActionListener l) { actionListeners.add(l); }
        void fireActionEvent() {
            ActionEvent e = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "toggle");
            for (ActionListener l : actionListeners) l.actionPerformed(e);
        }
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            float t = anim.get();
            int x = 0, y = (getHeight() - h) / 2;

            // 关闭态：半透深灰玻璃，不黑不白
            Color bgOff = new Color(55, 60, 70, 50);
            Color bgOn = Theme.accent;
            Color bg = lerp(bgOff, bgOn, t);
            g2.setColor(bg);
            g2.fillRoundRect(x, y, w, h, h, h);

            g2.setColor(new Color(255, 255, 255, 30));
            g2.setStroke(new BasicStroke(0.6f));
            g2.drawRoundRect(x, y, w, h, h, h);

            float pos = Easing.easeOutBack(t);
            int knobR = h - 4;
            int knobX = (int)(x + 2 + (w - h - 2) * Math.max(0f, Math.min(1f, pos)));

            g2.setColor(new Color(0, 0, 0, 25));
            g2.fillOval(knobX + 1, y + 3, knobR, knobR);

            g2.setColor(new Color(240, 245, 250, 200));
            g2.fillOval(knobX, y + 2, knobR, knobR);

            g2.setColor(new Color(255, 255, 255, 120));
            g2.fillOval(knobX + 2, y + 3, knobR / 2, knobR / 3);

            g2.dispose();
        }
    }

    // ==================== 自定义滑条 ====================
    static class CustomSlider extends JPanel {
        int min, max, value;
        AnimFloat fillAnim;
        List<ChangeListener> listeners = new ArrayList<>();
        boolean dragging = false;
        CustomSlider(int min, int max, int value) {
            this.min = min; this.max = max; this.value = value;
            fillAnim = new AnimFloat((float)(value - min) / (max - min), 120);
            fillAnim.addListener(this::repaint);
            setOpaque(false);
            setPreferredSize(new Dimension(60, 14));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            MouseAdapter ma = new MouseAdapter() {
                public void mousePressed(MouseEvent e) { dragging = true; updateValue(e); }
                public void mouseDragged(MouseEvent e) { if (dragging) updateValue(e); }
                public void mouseReleased(MouseEvent e) { dragging = false; }
            };
            addMouseListener(ma); addMouseMotionListener(ma);
        }
        void updateValue(MouseEvent e) {
            float ratio = Math.max(0f, Math.min(1f, (float)e.getX() / getWidth()));
            value = min + Math.round((max - min) * ratio);
            fillAnim.snapTo(ratio);  // 拖动时直接到位，不走动画
            for (ChangeListener l : listeners) l.stateChanged(new ChangeEvent(this));
            paintImmediately(0, 0, getWidth(), getHeight());  // 即时渲染
        }
        void addChangeListener(ChangeListener l) { listeners.add(l); }
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            int cy = h / 2, barH = 4;
            float fillRatio = fillAnim.get();
            int fillW = Math.max(barH, (int)(w * fillRatio));

            // 轨道底色（胶囊形）
            int trackHalf = barH / 2;
            g2.setColor(new Color(40, 46, 56, 100));
            g2.fillOval(0, cy - trackHalf, barH, barH);
            g2.fillOval(w - barH, cy - trackHalf, barH, barH);
            g2.fillRect(trackHalf, cy - trackHalf, w - barH, barH);

            // 已填充部分的光晕（胶囊形扩散）
            for (int l = 3; l >= 1; l--) {
                int fade = 8 + l * 6;
                int halfH = trackHalf + l;
                int top = cy - halfH;
                g2.setColor(new Color(Theme.accent.getRed(), Theme.accent.getGreen(), Theme.accent.getBlue(), fade));
                g2.fillOval(-l, top, halfH*2, halfH*2);
                g2.fillOval(fillW + l - halfH*2, top, halfH*2, halfH*2);
                g2.fillRect(-l + halfH, top, fillW + l*2 - halfH*2, halfH*2);
            }

            // 已填充部分（胶囊形）
            int fillHalf = trackHalf;
            g2.setColor(Theme.accent);
            if (fillW >= barH) {
                g2.fillOval(0, cy - fillHalf, barH, barH);
                g2.fillOval(fillW - barH, cy - fillHalf, barH, barH);
                g2.fillRect(fillHalf, cy - fillHalf, fillW - barH, barH);
            } else {
                g2.fillRoundRect(0, cy - fillHalf, fillW, barH, barH, barH);
            }

            // 填充高光
            g2.setColor(new Color(255, 255, 255, 30));
            g2.fillRoundRect(2, cy - barH/2, fillW - 4, barH/2, barH/2, barH/2);

            // 滑块光晕
            int knobR = 7;
            int knobX = fillW - knobR / 2;
            int knobY = cy - knobR / 2;
            for (int l = 3; l >= 1; l--) {
                g2.setColor(new Color(Theme.accent.getRed(), Theme.accent.getGreen(), Theme.accent.getBlue(), 20 + l * 10));
                g2.fillOval(knobX - l, knobY - l, knobR + l*2, knobR + l*2);
            }

            // 滑块阴影
            g2.setColor(new Color(0, 0, 0, 35));
            g2.fillOval(knobX + 1, knobY + 1, knobR, knobR);

            // 滑块主体
            g2.setColor(new Color(250, 252, 255));
            g2.fillOval(knobX, knobY, knobR, knobR);

            // 滑块高光
            g2.setColor(new Color(255, 255, 255, 160));
            g2.fillOval(knobX + 2, knobY + 1, knobR - 3, knobR / 2 - 1);

            g2.dispose();
        }
    }

    // ==================== 模块卡片 ====================
    static class ModuleCard extends JPanel {
        Module module;
        CustomToggle toggle;
        AnimFloat hoverAnim, expandAnim;
        boolean expanded = false;
        JPanel settingsPanel;
        JLabel nameLabel;
        ModuleCard(Module m) {
            this.module = m;
            setOpaque(false);
            setLayout(new BorderLayout());
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
            setBorder(BorderFactory.createEmptyBorder(1, 6, 1, 6));
            allCards.add(this);

            hoverAnim = new AnimFloat(0f, 120);
            hoverAnim.addListener(this::repaint);
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hoverAnim.setTarget(1f); }
                public void mouseExited(MouseEvent e) { hoverAnim.setTarget(0f); }
                public void mousePressed(MouseEvent e) {
                    if (e.getX() < getWidth() - 50) toggleExpanded();
                }
            });

            JPanel row = new JPanel(new BorderLayout(4, 0));
            row.setOpaque(false);
            nameLabel = new JLabel(module.name + (module.keyBind != null ? " [" + module.keyBind + "]" : ""));
            nameLabel.setFont(uiFont(module.highlighted ? Font.BOLD : Font.PLAIN, 10));
            nameLabel.setForeground(module.enabled ? Theme.textPrimary : Theme.textSecondary);
            nameLabel.addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) { toggleExpanded(); }
            });
            row.add(nameLabel, BorderLayout.CENTER);

            toggle = new CustomToggle(module.enabled);
            toggle.addActionListener(e -> {
                module.enabled = toggle.on;
                nameLabel.setForeground(module.enabled ? Theme.textPrimary : Theme.textSecondary);
                onModuleToggle(module);
            });
            row.add(toggle, BorderLayout.EAST);
            add(row, BorderLayout.NORTH);

            settingsPanel = createSettingsPanel();
            settingsPanel.setVisible(false);
            add(settingsPanel, BorderLayout.CENTER);

            expandAnim = new AnimFloat(0f, 160);
            expandAnim.addListener(() -> {
                int target = (module.settings.size() + 1) * 22 + 2; // +1 是 Bind Key 行
                int h = (int)(target * Easing.easeOutCubic(expandAnim.get()));
                settingsPanel.setPreferredSize(new Dimension(0, h));
                settingsPanel.setVisible(h > 0 || expandAnim.getTarget() > 0.01f);
                revalidate(); repaint();
                if (getParent() != null) { getParent().revalidate(); getParent().repaint(); }
            });
        }
        void toggleExpanded() {
            expanded = !expanded; playClick();
            expandAnim.setTarget(expanded ? 1f : 0f);
        }
        void refresh() {
            toggle.setOn(module.enabled);
            nameLabel.setForeground(module.enabled ? Theme.textPrimary : Theme.textSecondary);
            repaint();
        }
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            drawWaterCard(g2, getWidth(), getHeight(), 6, module.highlighted, module.enabled, hoverAnim.get());
            g2.dispose();
        }
        JPanel createSettingsPanel() {
            JPanel p = new JPanel();
            p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
            p.setOpaque(false);
            p.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 2));
            for (Setting s : module.settings) {
                JPanel row = new JPanel(new BorderLayout(3, 0));
                row.setOpaque(false);
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
                JLabel name = new JLabel(s.name);
                name.setFont(uiFont(8));
                name.setForeground(Theme.textSecondary);
                row.add(name, BorderLayout.WEST);
                switch (s.type) {
                    case BOOL -> {
                        CustomToggle sw = new CustomToggle((Boolean)s.value);
                        sw.setPreferredSize(new Dimension(20, 11));
                        sw.w = 20; sw.h = 11;
                        sw.addActionListener(e -> {
                            if (s.name.equals("Pick Color") || s.name.equals("Pick")) {
                                s.value = false; sw.setOn(false);
                                lastPickWasSec = false;
                                openColorPicker(module.name);
                            } else if (s.name.equals("Pick Sec Col")) {
                                s.value = false; sw.setOn(false);
                                lastPickWasSec = true;
                                openColorPicker(module.name);
                            } else {
                                s.value = sw.on;
                                applyVisualBool(module.name, s.name, sw.on);
                            }
                        });
                        row.add(sw, BorderLayout.EAST);
                    }
                    case SLIDER -> {
                        JLabel valLbl = new JLabel(String.valueOf(s.value));
                        valLbl.setFont(uiFont(8));
                        valLbl.setForeground(Theme.accent);
                        valLbl.setPreferredSize(new Dimension(22, 14));
                        valLbl.setHorizontalAlignment(SwingConstants.RIGHT);
                        CustomSlider slider = new CustomSlider(s.min, s.max, (Integer)s.value);
                        slider.addChangeListener(e -> {
                            s.value = slider.value;
                            valLbl.setText(String.valueOf(slider.value));
                            applyVisualSetting(module.name, s.name, slider.value);
                        });
                        JPanel wrap = new JPanel(new BorderLayout(2, 0));
                        wrap.setOpaque(false);
                        wrap.add(slider, BorderLayout.CENTER);
                        wrap.add(valLbl, BorderLayout.EAST);
                        row.add(wrap, BorderLayout.CENTER);
                    }
                    case MODE -> {
                        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 1, 0));
                        btns.setOpaque(false);
                        for (String opt : s.options) {
                            JButton b = new JButton(opt);
                            b.setFont(uiFont(7));
                            b.setFocusPainted(false);
                            b.setBorderPainted(false);
                            b.setContentAreaFilled(false);
                            b.setForeground(opt.equals(s.value) ? Theme.accent : Theme.textDim);
                            b.addActionListener(e -> {
                                s.value = opt;
                                for (Component c : btns.getComponents()) {
                                    if (c instanceof JButton) c.setForeground(((JButton)c).getText().equals(s.value) ? Theme.accent : Theme.textDim);
                                }
                                applyVisualMode(module.name, s.name, opt);
                            });
                            btns.add(b);
                        }
                        row.add(btns, BorderLayout.EAST);
                    }
                }
                p.add(row);
            }
            // 桌面快捷按钮行
            JPanel scRow = new JPanel(new BorderLayout(3, 0));
            scRow.setOpaque(false);
            scRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
            JLabel scLabel = new JLabel("Shortcut");
            scLabel.setFont(uiFont(8));
            scLabel.setForeground(Theme.textDim);
            scRow.add(scLabel, BorderLayout.WEST);
            JButton scBtn = new JButton(module.keyBind != null ? "●" : "+");
            scBtn.setFont(uiFont(Font.BOLD, 9));
            scBtn.setForeground(module.keyBind != null ? Theme.accent : Theme.textDim);
            scBtn.setFocusPainted(false);
            scBtn.setBorderPainted(false);
            scBtn.setContentAreaFilled(false);
            scBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            scBtn.addActionListener(ev -> {
                if (module.keyBind != null) {
                    // 移除快捷按钮
                    JComponent old = quickButtonMap.remove(module.name);
                    if (old != null) {
                        JFrame sf = (JFrame) old.getClientProperty("shortcutFrame");
                        if (sf != null) sf.dispose();
                    }
                    module.keyBind = null;
                    scBtn.setText("+"); scBtn.setForeground(Theme.textDim);
                } else {
                    createModuleShortcut(module);
                    module.keyBind = "●";
                    scBtn.setText("●"); scBtn.setForeground(Theme.accent);
                }
                nameLabel.setText(module.name + (module.keyBind != null ? " ●" : ""));
                scBtn.repaint();
            });
            scRow.add(scBtn, BorderLayout.EAST);
            p.add(scRow);
            return p;
        }
    }

    // ==================== 取色器 ====================
    static void openColorPicker(String moduleName) {
        JFrame owner = null;
        for (JFrame f : allWindows) {
            if (f.isVisible()) { owner = f; break; }
        }
        if (owner == null) return;

        // "Panel Glow" 的 Pick Color 和 Pick Sec Col 走不同目标色
        boolean isSec = moduleName.equals("Panel Glow") && lastPickWasSec;
        Color original = switch (moduleName) {
            case "HUD Primary"   -> hudPrimary;
            case "HUD Secondary" -> hudSecondary;
            case "Accent Color"  -> Theme.accent;
            case "Panel Glow"    -> isSec ? panelGlowSec : panelGlow;
            default -> Theme.accent;
        };

        Consumer<Color> onChange = switch (moduleName) {
            case "HUD Primary"   -> c -> { hudPrimary = c; if (hudRef != null) hudRef.hudPanel.repaint(); };
            case "HUD Secondary" -> c -> { hudSecondary = c; if (hudRef != null) hudRef.hudPanel.repaint(); };
            case "Accent Color"  -> Theme::updateFromAccent;
            case "Panel Glow"    -> (isSec ? (Consumer<Color>)(c -> panelGlowSec = c) : (c -> panelGlow = c));
            default              -> Theme::updateFromAccent;
        };

        ColorPickerDialog dlg = new ColorPickerDialog(owner, original, onChange);
        dlg.setVisible(true);

        if (dlg.result != null) {
            switch (moduleName) {
                case "HUD Primary"   -> hudPrimary = dlg.result;
                case "HUD Secondary" -> hudSecondary = dlg.result;
                case "Accent Color"  -> Theme.updateFromAccent(dlg.result);
                case "Panel Glow"    -> { if (isSec) panelGlowSec = dlg.result; else panelGlow = dlg.result; }
            }
            refreshAll();
        } else {
            onChange.accept(original);
        }
    }
    static boolean lastPickWasSec = false;

    /** 创建一个桌面浮动快捷按钮 */
    static void createModuleShortcut(Module mod) {
        // 找右下角位置
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int sx = screen.width - 200;
        int sy = 60 + quickButtonMap.size() * 30;
        JFrame f = new JFrame();
        f.setUndecorated(true);
        f.setBackground(new Color(0,0,0,0));
        f.setAlwaysOnTop(true);
        int fw = Math.max(100, Toolkit.getDefaultToolkit().getFontMetrics(uiFont(Font.BOLD,9)).stringWidth(mod.name)+50);
        f.setSize(fw, 28);
        f.setLocation(sx, sy);
        // 不加入 allWindows，ESC 只关面板不关快捷按钮
        JComponent btn = new JComponent() {
            AnimFloat hov = new AnimFloat(0f, 150);
            { hov.addListener(this::repaint);
              setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
              addMouseListener(new MouseAdapter() {
                  public void mouseEntered(MouseEvent e) { hov.setTarget(1f); }
                  public void mouseExited(MouseEvent e)  { hov.setTarget(0f); }
                  public void mousePressed(MouseEvent e) {
                      mod.enabled = !mod.enabled;
                      onModuleToggle(mod);
                      repaint();
                  }
              });
            }
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w=getWidth(),h=getHeight(); boolean on=mod.enabled; float hv=hov.get();
                if (on) {
                    g2.setColor(withAlpha(Theme.accent, 160));
                    g2.fillRoundRect(0,0,w,h,12,12);
                    GradientPaint gp = new GradientPaint(0,0,new Color(255,255,255,40),0,h,new Color(0,0,0,0));
                    g2.setPaint(gp);
                    g2.fillRoundRect(0,0,w,h/2,12,12);
                } else {
                    g2.setColor(new Color(30,36,48,(int)(50+30*hv)));
                    g2.fillRoundRect(0,0,w,h,12,12);
                }
                g2.setColor(on ? withAlpha(Theme.accent,160) : new Color(255,255,255,(int)(20+15*hv)));
                g2.setStroke(new BasicStroke(0.8f));
                g2.drawRoundRect(0,0,w-1,h-1,12,12);
                g2.setFont(uiFont(Font.BOLD, 9));
                g2.setColor(on ? Color.WHITE : Theme.textSecondary);
                FontMetrics fm = g2.getFontMetrics();
                String txt = mod.name.length()>10 ? mod.name.substring(0,9)+"~" : mod.name;
                g2.drawString(txt, (w-fm.stringWidth(txt))/2, (h+fm.getAscent()-fm.getDescent())/2);
                g2.dispose();
            }
        };
        quickButtonMap.put(mod.name, btn);
        // 存储 frame 引用以便清理
        btn.putClientProperty("shortcutFrame", f);
        f.add(btn);
        MouseAdapter drag = new MouseAdapter() {
            Point off;
            public void mousePressed(MouseEvent e) { off = e.getPoint(); }
            public void mouseDragged(MouseEvent e) {
                Point p = e.getLocationOnScreen();
                f.setLocation(p.x-off.x, p.y-off.y);
            }
        };
        btn.addMouseListener(drag);
        btn.addMouseMotionListener(drag);
        f.setVisible(true);
    }

    // ==================== Visual 设置联动 ====================
    static void applyVisualSetting(String moduleName, String settingName, int value) {
        switch (moduleName) {
            case "Panel Rounding" -> Style.panelRounding = value;
            case "Glass Alpha" -> Style.glassAlpha = (int)(150 * value / 100f);
            case "Card Alpha" -> Style.cardAlpha = (int)(100 * value / 100f);
            case "Border Alpha" -> Style.borderAlpha = (int)(100 * value / 100f);
            case "Hover Alpha" -> Style.hoverAlpha = (int)(80 * value / 100f);
            case "Shortcut Scale" -> fontScale = 0.8f + value / 200f;
            case "Saturation" -> {
                float sat = value / 100f;
                Theme.updateFromAccent(Color.getHSBColor(
                    Color.RGBtoHSB(Theme.accent.getRed(), Theme.accent.getGreen(), Theme.accent.getBlue(), null)[0],
                    sat, 1f));
            }
            case "HUD Speed" -> hudColorSpeed = value / 10f;
            case "HUD Font Size" -> hudFontSize = value;
            case "Panel Glow"    -> panelGlowAlpha = value / 100f;
            case "Panel Opacity" -> panelBgAlpha = value / 100f;
            case "HUD Opacity"   -> hudOpacity = value / 100f;
            // RGB 滑块实时更新颜色
            case "HUD Primary" -> {
                hudPrimary = new Color(
                    clampRGB(getSettingVal(moduleName, "R")),
                    clampRGB(getSettingVal(moduleName, "G")),
                    clampRGB(getSettingVal(moduleName, "B")));
            }
            case "HUD Secondary" -> {
                hudSecondary = new Color(
                    clampRGB(getSettingVal(moduleName, "R")),
                    clampRGB(getSettingVal(moduleName, "G")),
                    clampRGB(getSettingVal(moduleName, "B")));
            }
        }
        switch (settingName) {
            case "Shadow Radius" -> {}
            case "Blur Radius" -> Style.glassAlpha = 30 + value * 3;
            case "Shadow Alpha", "Blur Alpha" -> Style.glassAlpha = (int)(150 * value / 100f);
            case "Bloom Alpha" -> Style.highlightAlpha = (int)(80 * value / 100f);
        }
        Style.notifyUpdate();
    }

    static int clampRGB(int v) { return Math.max(0, Math.min(255, v)); }

    /** 根据模块名和设置名查找当前值 */
    static int getSettingVal(String moduleName, String settingName) {
        Module m = moduleMap.get(moduleName);
        if (m != null) {
            for (Setting s : m.settings) {
                if (s.name.equals(settingName) && s.type == Setting.Type.SLIDER) {
                    return (Integer) s.value;
                }
            }
        }
        return 128;
    }

    static void applyVisualBool(String moduleName, String settingName, boolean value) {
        if (moduleName.equals("ClickUI Blur") && settingName.equals("")) {
            Style.glassAlpha = value ? 70 : 20;
        }
        if (moduleName.equals("Block Bloom") && settingName.equals("")) {
            Style.highlightAlpha = value ? 40 : 15;
        }
        Style.notifyUpdate();
    }

    static void applyVisualMode(String moduleName, String settingName, String mode) {
        if (moduleName.equals("Theme")) {
            switch (mode) {
                case "Fixed"    -> { useGradient = false; }
                case "Rainbow"  -> { useGradient = true; gradientSpeed = 1.2f; }
                case "Gradient" -> { useGradient = true; gradientSpeed = 0.4f; }
            }
        }
        if (moduleName.equals("Preset")) {
            switch (mode) {
                case "Ocean"  -> { hudPrimary = new Color(0x4DA6FF); hudSecondary = new Color(0x00D4FF); }
                case "Sunset" -> { hudPrimary = new Color(0xFF6B35); hudSecondary = new Color(0xFF2E97); }
                case "Forest" -> { hudPrimary = new Color(0x00E676); hudSecondary = new Color(0x1DE9B6); }
                case "Berry"  -> { hudPrimary = new Color(0xE040FB); hudSecondary = new Color(0xFF6EC7); }
                case "Neon"   -> { hudPrimary = new Color(0xFFFF00); hudSecondary = new Color(0x00FF00); }
            }
            refreshAll();
        }
    }

    // ==================== 内容面板：纯透明，不画任何背景 ====================
    static class TranslucentPanel extends JPanel {
        JComponent content;
        static final int GLOW_MARGIN = 18;

        TranslucentPanel(JComponent content) {
            this.content = content;
            setOpaque(false);
            setLayout(new BorderLayout());
            setBorder(BorderFactory.createEmptyBorder(GLOW_MARGIN, GLOW_MARGIN, GLOW_MARGIN, GLOW_MARGIN));
            add(content, BorderLayout.CENTER);
            Style.addListener(this::repaint);
            Theme.addListener(c -> repaint());
        }
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            drawWaterPanel(g2, getWidth(), getHeight(), Style.panelRounding);
            g2.dispose();
        }
    }

    // ==================== 分类窗口 ====================
    static class CategoryWindow extends FloatingWindow {
        boolean collapsed = false;
        JPanel cardsPanel;
        AnimFloat collapseAnim;
        int fullCardsHeight;

        CategoryWindow(Category cat, List<Module> modules, int x, int y) {
            super(cat.label, x, y, 180, 400);
            JPanel root = new JPanel();
            root.setOpaque(false);
            root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
            root.setBorder(BorderFactory.createEmptyBorder(10, 8, 8, 8));

            // 居中对齐标题，可点击折叠
            JLabel title = new JLabel(cat.label, SwingConstants.CENTER);
            title.setFont(uiFont(Font.BOLD, 12));
            title.setForeground(Theme.textPrimary);
            title.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
            title.setAlignmentX(CENTER_ALIGNMENT);
            title.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            root.add(title);
            root.add(Box.createVerticalStrut(4));

            // 模块卡片容器
            cardsPanel = new JPanel();
            cardsPanel.setOpaque(false);
            cardsPanel.setLayout(new BoxLayout(cardsPanel, BoxLayout.Y_AXIS));
            int count = 0;
            for (Module m : modules) {
                if (m.category == cat) {
                    cardsPanel.add(new ModuleCard(m));
                    cardsPanel.add(Box.createVerticalStrut(2));
                    count++;
                }
            }
            fullCardsHeight = count * 26 + 10;
            root.add(cardsPanel);
            root.add(Box.createVerticalGlue());

            int baseH = 42;  // 仅标题高度
            collapseAnim = new AnimFloat(1f, 280);
            collapseAnim.addListener(() -> {
                float a = Easing.easeOutCubic(collapseAnim.get());
                int ch = (int)(fullCardsHeight * a);
                cardsPanel.setPreferredSize(new Dimension(180, ch));
                cardsPanel.setVisible(ch > 2);
                // 整个窗口同步缩放
                int newH = baseH + ch + TranslucentPanel.GLOW_MARGIN*2;
                if (Math.abs(getHeight() - newH) > 3) {
                    setSize(getWidth(), newH);
                }
                cardsPanel.revalidate();
                repaint();
            });

            setSize(180 + TranslucentPanel.GLOW_MARGIN*2, baseH + fullCardsHeight + TranslucentPanel.GLOW_MARGIN*2);
            setContentPane(new TranslucentPanel(root));
            makeDraggable(getContentPane());
            makeDraggable(title);

            // 点击标题折叠/展开
            title.addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    collapsed = !collapsed; playClick();
                    collapseAnim.setTarget(collapsed ? 0f : 1f);
                }
            });
        }
    }

    // ==================== 渐变色彩系统 ====================
    static long hueCycleStart = System.currentTimeMillis();
    static boolean useGradient = true;
    static float gradientSpeed = 1.0f;

    static Color getGradientColor(int index, int total) {
        if (!useGradient) return Theme.accent;
        long elapsed = System.currentTimeMillis() - hueCycleStart;
        float hueOffset = (elapsed * 0.00003f * gradientSpeed) % 1.0f;
        float hue = (hueOffset + (float)index / Math.max(1, total)) % 1.0f;
        return Color.getHSBColor(hue, 0.8f, 1.0f);
    }

    // ==================== 可调光晕参数 ====================
    static Color hudPrimary = new Color(0xFF3333);
    static Color hudSecondary = new Color(0xFFAAAA);
    static float hudColorSpeed = 1.5f;
    static int hudFontSize = 12;
    static Color panelGlow = new Color(0xFF3333);       // 面板光晕主色（红）
    static Color panelGlowSec = new Color(0xFFFFFF);     // 面板光晕副色（白）
    static float panelGlowAlpha = 0.8f;                  // 面板光晕强度
    static float panelBgAlpha = 0.15f;                   // 面板背景透明度（0=全透明）
    static float hudOpacity = 0.8f;                      // HUD 背景透明度（0=全透明）

    // ==================== 右侧模块 HUD ====================
    static class ModuleHUD {
        JFrame hudFrame;
        JPanel hudPanel;
        List<Module> modules;
        float glowPhase = 0f;
        Point dragOffset;
        int hudX = 0, hudY = 5;
        int fixedW = 160, lastEntryCount = -1, lastVis;

        // 手动动画值（独立线程插值，无额外 Timer）
        static class HUDEntry {
            String name;
            float animVal, animTarget, animFrom;
            long animStart;
            boolean active;
            HUDEntry(String n) { this.name = n; }
        }
        List<HUDEntry> entries = new ArrayList<>();
        Font modFont;
        int lineH, panelH;

        // 预计算 glow alpha（避免每帧重复 Math.exp）
        static final float[] GLOW_ALPHAS = new float[16];
        static {
            for (int l = 0; l < 16; l++) {
                float ratio = (float)(l+1) / 16f;
                GLOW_ALPHAS[l] = (float)(55 * Math.exp(-ratio * 3));
            }
        }

        ModuleHUD(List<Module> modules) {
            this.modules = modules;
            modFont = uiFont(Font.BOLD, hudFontSize);

            FontMetrics probeFm = Toolkit.getDefaultToolkit().getFontMetrics(modFont);
            int maxNameW = 80;
            for (Module m : modules) {
                int w = probeFm.stringWidth(m.name);
                if (w > maxNameW) maxNameW = w;
            }
            fixedW = maxNameW + 70;

            hudFrame = new JFrame();
            hudFrame.setUndecorated(true);
            hudFrame.setBackground(new Color(0, 0, 0, 0));
            hudFrame.setAlwaysOnTop(true);
            registerWindow(hudFrame);

            hudPanel = new JPanel() {
                private Font lastFont;
                private FontMetrics lastFm;
                private int lastUniW, lastLabelH;

                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                    if (entries.isEmpty()) { g2.dispose(); return; }

                    // 缓存 font metrics + 字体变化时重算宽度
                    if (lastFont == null || lastFont.getSize() != hudFontSize) {
                        lastFont = uiFont(Font.BOLD, hudFontSize);
                        lastFm = g2.getFontMetrics(lastFont);
                        lineH = lastFm.getHeight() + 6;
                        int maxW = 80;
                        for (Module m : modules) {
                            int w = lastFm.stringWidth(m.name);
                            if (w > maxW) maxW = w;
                        }
                        fixedW = maxW + 70;
                        lastUniW = fixedW - 50;
                        lastLabelH = lineH;
                        lastEntryCount = -1;
                    }
                    modFont = lastFont;

                    int uniW = lastUniW, labelH = lastLabelH, y = 18;
                    Composite origComp = g2.getComposite();
                    Composite defComp = g2.getComposite();

                    for (int i = 0; i < entries.size(); i++) {
                        HUDEntry e = entries.get(i);
                        float raw = e.animVal;
                        if (raw < 0.002f && e.animTarget < 0.001f && !e.active) continue;
                        float sv = e.active ? Easing.easeOutBack(raw) : raw;
                        if (sv < 0.001f) continue;

                        float wave = (float) Math.sin(glowPhase * hudColorSpeed + i * 0.25f);
                        float t = wave * 0.5f + 0.5f;
                        Color c = lerp(hudPrimary, hudSecondary, t);

                        float glowAlpha = Math.min(1f, sv);
                        int arcR = labelH / 2;

                        // 居中缩放 transform（真正的缩放动画）
                        AffineTransform saveAt = g2.getTransform();
                        g2.translate(18 + uniW/2f, y + labelH/2f);
                        g2.scale(sv, sv);
                        g2.translate(-(18 + uniW/2f), -(y + labelH/2f));

                        // Glow（16层填充光晕）
                        for (int l = 15; l >= 0; l--) {
                            int a = (int)(glowAlpha * GLOW_ALPHAS[l] * hudOpacity);
                            if (a <= 0) continue;
                            int pad = l + 1;
                            g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.min(255, a)));
                            g2.fillRoundRect(18 - pad, y - pad, uniW + pad*2, labelH + pad*2, arcR+pad, arcR+pad);
                        }
                        // 深色标签背景
                        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, glowAlpha * hudOpacity));
                        g2.setColor(new Color(10, 10, 16, 210));
                        g2.fillRoundRect(18, y, uniW, labelH, arcR, arcR);
                        g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), (int)(150 * hudOpacity)));
                        g2.setStroke(new BasicStroke(1f));
                        g2.drawRoundRect(18, y, uniW - 1, labelH, arcR, arcR);
                        g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), (int)(255 * hudOpacity)));
                        g2.fillRoundRect(22, y + 5, 3, labelH - 10, 2, 2);
                        g2.setComposite(origComp);
                        // 文字
                        g2.setFont(lastFont);
                        g2.setColor(c);
                        g2.drawString(e.name, 32, y + lastFm.getAscent() + 2);

                        g2.setTransform(saveAt);

                        y += labelH + 3;
                    }
                    g2.dispose();
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

            // 独立线程驱动 + paintImmediately（优化：合并循环、缓存 Runnable）
            final boolean[] running = {true};
            final int[] lastSz = {hudFontSize};
            final Runnable paintTask = () -> {
                resizeIfNeeded(lastVis);
                if (hudPanel.isShowing()) hudPanel.paintImmediately(0, 0, hudPanel.getWidth(), hudPanel.getHeight());
            };
            Thread animThread = new Thread(() -> {
                while (running[0]) {
                    long t0 = System.currentTimeMillis();
                    glowPhase += 0.02f; if (glowPhase > 5f) glowPhase -= 5f;
                    if (hudFontSize != lastSz[0]) { lastSz[0] = hudFontSize; lastEntryCount = -1; }
                    int vis = 0;
                    Iterator<HUDEntry> it = entries.iterator();
                    while (it.hasNext()) {
                        HUDEntry en = it.next();
                        if (Math.abs(en.animVal - en.animTarget) > 0.001f) {
                            float f = (t0 - en.animStart) / 300f;
                            en.animVal = f >= 1f ? en.animTarget : en.animFrom + (en.animTarget - en.animFrom) * Easing.easeOutCubic(f);
                        }
                        if (!en.active && en.animVal < 0.002f && en.animTarget < 0.001f) { it.remove(); continue; }
                        if (en.animVal > 0.01f || en.animTarget > 0.01f) vis++;
                    }
                    lastVis = vis;
                    SwingUtilities.invokeLater(paintTask);
                    long dt = System.currentTimeMillis() - t0;
                    if (dt < 10) { try { Thread.sleep(10 - dt); } catch (InterruptedException ex) { running[0] = false; } }
                }
            });
            animThread.setDaemon(true);

            modFont = uiFont(Font.BOLD, hudFontSize);
            FontMetrics initFm = Toolkit.getDefaultToolkit().getFontMetrics(modFont);
            lineH = initFm.getHeight() + 6;
            panelH = 36;
            hudPanel.setPreferredSize(new Dimension(fixedW, panelH));
            hudFrame.add(hudPanel);
            hudFrame.pack();
            if (hudX == 0) { Dimension screen = Toolkit.getDefaultToolkit().getScreenSize(); hudX = screen.width - fixedW - 20; }
            reposition();

            syncRefresh();
            hudFrame.setVisible(true);
            animThread.start();
        }

        void resizeIfNeeded(int entryCount) {
            if (entryCount == lastEntryCount) return;
            lastEntryCount = entryCount;
            int newH = Math.max(24, entryCount * (lineH + 3) + 36);
            if (newH != panelH) {
                panelH = newH;
                hudPanel.setPreferredSize(new Dimension(fixedW, panelH));
                hudFrame.pack();
            }
        }

        void reposition() { hudFrame.setLocation(hudX, hudY); }

        void syncRefresh() {
            Set<String> nowActive = new LinkedHashSet<>();
            for (Module m : this.modules) if (m.enabled) nowActive.add(m.name);
            long now = System.currentTimeMillis();
            for (HUDEntry e : entries) {
                if (!nowActive.contains(e.name) && e.active) {
                    e.active = false;
                    e.animFrom = e.animVal;
                    e.animTarget = 0f; e.animStart = now;
                }
            }
            for (String name : nowActive) {
                HUDEntry found = null;
                for (HUDEntry e : entries) if (e.name.equals(name)) { found = e; break; }
                if (found != null) {
                    if (!found.active) {
                        found.active = true;
                        found.animFrom = found.animVal; found.animTarget = 1f; found.animStart = now;
                    }
                } else {
                    HUDEntry ne = new HUDEntry(name);
                    ne.active = true;
                    ne.animFrom = 0f; ne.animTarget = 1f; ne.animStart = now;
                    entries.add(ne);
                }
            }
            hudPanel.repaint();
        }
    }

    // ==================== 色盘选择器 ====================
    static class HSBPalette extends JPanel {
        float hue = 0.55f, sat = 1f, bri = 1f;
        Point selectedPt;
        BufferedImage cache;
        int cacheW, cacheH;
        float cacheHue;
        Consumer<Color> onColorChange;  // 回调而非直接改全局
        HSBPalette(Color startColor, Consumer<Color> onChange) {
            this.onColorChange = onChange;
            setOpaque(false);
            setPreferredSize(new Dimension(160, 160));
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            float[] hsb = Color.RGBtoHSB(startColor.getRed(), startColor.getGreen(), startColor.getBlue(), null);
            hue = hsb[0]; sat = hsb[1]; bri = hsb[2];
            MouseAdapter ma = new MouseAdapter() {
                public void mousePressed(MouseEvent e) { updateFromMouse(e); }
                public void mouseDragged(MouseEvent e) { updateFromMouse(e); }
            };
            addMouseListener(ma); addMouseMotionListener(ma);
        }
        void updateFromMouse(MouseEvent e) {
            int w = getWidth(), h = getHeight();
            sat = Math.max(0, Math.min(1, (float)e.getX() / w));
            bri = Math.max(0, Math.min(1, 1f - (float)e.getY() / h));
            selectedPt = e.getPoint();
            onColorChange.accept(Color.getHSBColor(hue, sat, bri));
            repaint();
        }
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            int w = getWidth(), h = getHeight();
            if (cache == null || cacheW != w || cacheH != h || cacheHue != hue) {
                cache = new BufferedImage(Math.max(1,w), Math.max(1,h), BufferedImage.TYPE_INT_RGB);
                Graphics2D cg = cache.createGraphics();
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        cg.setColor(Color.getHSBColor(hue, (float)x/w, 1f - (float)y/h));
                        cg.fillRect(x, y, 1, 1);
                    }
                }
                cg.dispose();
                cacheW = w; cacheH = h; cacheHue = hue;
            }
            g2.drawImage(cache, 0, 0, null);
            if (selectedPt == null) selectedPt = new Point((int)(sat*w), (int)((1f-bri)*h));
            g2.setColor(Color.WHITE); g2.setStroke(new BasicStroke(2f));
            g2.drawOval(selectedPt.x - 5, selectedPt.y - 5, 10, 10);
            g2.setColor(Color.BLACK); g2.drawOval(selectedPt.x - 6, selectedPt.y - 6, 12, 12);
            g2.dispose();
        }
    }

    static class HueBar extends JPanel {
        HSBPalette palette;
        HueBar(HSBPalette p) {
            this.palette = p; setOpaque(false); setPreferredSize(new Dimension(160, 14));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            MouseAdapter ma = new MouseAdapter() {
                public void mousePressed(MouseEvent e) { updateHue(e); }
                public void mouseDragged(MouseEvent e) { updateHue(e); }
            };
            addMouseListener(ma); addMouseMotionListener(ma);
        }
        void updateHue(MouseEvent e) {
            palette.hue = Math.max(0, Math.min(1, (float)e.getX() / getWidth()));
            palette.onColorChange.accept(Color.getHSBColor(palette.hue, palette.sat, palette.bri));
            palette.repaint(); repaint();
        }
        protected void paintComponent(Graphics g) {
            int w = getWidth(), h = getHeight();
            for (int x = 0; x < w; x++) {
                g.setColor(Color.getHSBColor((float)x/w, 1f, 1f));
                g.fillRect(x, 0, 1, h);
            }
            int cx = (int)(palette.hue * w);
            g.setColor(Color.WHITE); g.fillRect(cx - 1, 0, 3, h);
            g.setColor(Color.BLACK); g.drawRect(cx - 1, 0, 2, h - 1);
        }
    }

    static class ColorPickerDialog extends JDialog {
        Color result;
        final Color[] liveColor = new Color[1];  // 实时取色结果
        Color originalColor;
        Consumer<Color> onChange;

        ColorPickerDialog(Frame owner, Color startColor, Consumer<Color> onChange) {
            super(owner, "Color Picker", true);
            this.onChange = onChange;
            this.originalColor = startColor;
            liveColor[0] = startColor;
            setUndecorated(true);
            setSize(210, 290);
            setLocationRelativeTo(owner);
            setBackground(new Color(0,0,0,0));

            JPanel main = new JPanel();
            main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));
            main.setOpaque(false);
            main.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

            JLabel title = new JLabel("Pick Color");
            title.setFont(uiFont(Font.BOLD, 14));
            title.setForeground(Color.WHITE);
            title.setAlignmentX(LEFT_ALIGNMENT);
            main.add(title);
            main.add(Box.createVerticalStrut(10));

            HSBPalette palette = new HSBPalette(startColor, c -> {
                liveColor[0] = c;
                onChange.accept(c);  // 实时预览
            });
            palette.setAlignmentX(LEFT_ALIGNMENT);
            main.add(palette);
            main.add(Box.createVerticalStrut(4));

            HueBar hueBar = new HueBar(palette);
            hueBar.setAlignmentX(LEFT_ALIGNMENT);
            main.add(hueBar);
            main.add(Box.createVerticalStrut(12));

            JPanel btns = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
            btns.setOpaque(false);
            btns.setAlignmentX(LEFT_ALIGNMENT);
            JButton ok = new JButton("OK");
            ok.setFont(uiFont(Font.BOLD, 12));
            ok.setBackground(startColor);
            ok.setForeground(Color.WHITE);
            ok.setFocusPainted(false);
            ok.setBorder(BorderFactory.createEmptyBorder(6, 20, 6, 20));
            ok.addActionListener(e -> { result = liveColor[0]; dispose(); });
            JButton cancel = new JButton("Cancel");
            cancel.setFont(uiFont(11));
            cancel.setBackground(withAlpha(Color.WHITE, 25));
            cancel.setForeground(Theme.textSecondary);
            cancel.setFocusPainted(false);
            cancel.setBorder(BorderFactory.createEmptyBorder(6, 16, 6, 16));
            cancel.addActionListener(e -> { onChange.accept(originalColor); dispose(); });
            btns.add(ok); btns.add(cancel);
            main.add(btns);

            JPanel glassWrap = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    drawWaterPanel(g2, getWidth(), getHeight(), 16);
                    g2.dispose();
                }
            };
            glassWrap.setOpaque(false);
            glassWrap.setLayout(new BorderLayout());
            glassWrap.add(main, BorderLayout.CENTER);
            add(glassWrap);
        }
    }

    // ==================== 入口 ====================
    public static void main(String[] args) {
        FlatDarkLaf.setup();
        UIManager.put("Button.arc", 999);
        UIManager.put("Component.arc", 8);

        SwingUtilities.invokeLater(() -> {
            List<Module> modules = buildModules();

            // 左侧三列分类面板（宽度 216px + 间距）
            new CategoryWindow(Category.COMBAT, modules, 20, 20).setVisible(true);
            new CategoryWindow(Category.MOVEMENT, modules, 250, 20).setVisible(true);
            new CategoryWindow(Category.PLAYER, modules, 480, 20).setVisible(true);
            new CategoryWindow(Category.VISUAL, modules, 20, 280).setVisible(true);
            new CategoryWindow(Category.WORLD, modules, 250, 280).setVisible(true);
            new CategoryWindow(Category.MISC, modules, 480, 280).setVisible(true);

            // 右侧活动模块 HUD（16层霓虹光晕 + 缩放动画）
            int screenW = Toolkit.getDefaultToolkit().getScreenSize().width;
            ModuleHUD hud = new ModuleHUD(modules);
            hud.hudX = screenW - 180;
            hud.hudY = 20;
            hud.reposition();
            hudRef = hud;

            Toolkit.getDefaultToolkit().addAWTEventListener(e -> {
                if (e instanceof KeyEvent ke && ke.getID() == KeyEvent.KEY_PRESSED && ke.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    closeAll();
                }
            }, AWTEvent.KEY_EVENT_MASK);
        });
    }
}
