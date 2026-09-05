package com.violetbank.app;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BankView extends View {
    static final int VIEW_ID = 0x5A11;
    private enum Screen { LOGIN, HOME, HISTORY, PAYMENTS, PROFILE, TRANSFER, CARD }

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Map<String, RectF> taps = new LinkedHashMap<>();
    private final DemoStore store;
    private final float d;
    private Screen screen = Screen.LOGIN;
    private Screen previousScreen = Screen.LOGIN;
    private boolean transitioning = false;
    private boolean collectTaps = true;
    private long transitionStart;
    private long screenEnteredAt = System.currentTimeMillis();
    private String pin = "";
    private String phone = "+7 999 123-45-67";
    private String transferAmount = "1 000";
    private boolean balanceHidden = false;
    private boolean cardRevealed = false;
    private boolean cardFrozen = false;
    private int selectedPayment = -1;
    private RectF pressedRect;
    private String pressedKey;
    private float pressX;
    private float pressY;
    private long pressStart;
    private boolean pressReleased;
    private long shimmerStart;
    private double balanceFrom;
    private double balanceTo;
    private double displayedBalance;
    private long balanceAnimStart;
    private boolean successActive;
    private String successTitle = "";
    private String successAmount = "";
    private long successStart;

    private static final int BG = Color.rgb(247, 245, 250);
    private static final int WHITE = Color.WHITE;
    private static final int INK = Color.rgb(28, 22, 38);
    private static final int MUTED = Color.rgb(117, 106, 133);
    private static final int PURPLE = Color.rgb(124, 58, 237);
    private static final int PURPLE_DARK = Color.rgb(88, 28, 135);
    private static final int PURPLE_PALE = Color.rgb(237, 233, 254);
    private static final int GREEN = Color.rgb(16, 185, 129);
    private static final int RED = Color.rgb(239, 68, 68);
    private static final int LINE = Color.rgb(232, 227, 238);

    public BankView(Context context) {
        super(context);
        setId(VIEW_ID);
        d = getResources().getDisplayMetrics().density;
        store = new DemoStore(context);
        displayedBalance = store.getBalance();
        balanceFrom = displayedBalance;
        balanceTo = displayedBalance;
        p.setTypeface(Typeface.create("sans", Typeface.NORMAL));
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        taps.clear();
        c.drawColor(BG);
        long now = System.currentTimeMillis();
        if (transitioning) {
            float raw = Math.min(1f, (now - transitionStart) / 330f);
            float eased = easeOutCubic(raw);
            collectTaps = false;
            drawScreenLayer(c, previousScreen, 1f - raw, -widthDp() * .09f * eased);
            collectTaps = true;
            drawScreenLayer(c, screen, eased, widthDp() * .12f * (1f - eased));
            if (raw >= 1f) transitioning = false;
            else postInvalidateOnAnimation();
        } else {
            collectTaps = true;
            drawScreen(c, screen);
        }
        drawPressEffect(c, now);
        if (successActive) drawSuccessOverlay(c, now);
    }

    private void drawScreenLayer(Canvas c, Screen target, float alpha, float offsetX) {
        c.save();
        c.translate(dp(offsetX), 0);
        c.saveLayerAlpha(0, 0, getWidth(), getHeight(), Math.max(0, Math.min(255, (int) (alpha * 255))));
        drawScreen(c, target);
        c.restore();
        c.restore();
    }

    private void drawScreen(Canvas c, Screen target) {
        c.drawColor(BG);
        switch (target) {
            case LOGIN: drawLogin(c); break;
            case HOME: drawHome(c); break;
            case HISTORY: drawHistory(c); break;
            case PAYMENTS: drawPayments(c); break;
            case PROFILE: drawProfile(c); break;
            case TRANSFER: drawTransfer(c); break;
            case CARD: drawCardScreen(c); break;
        }
    }

    private float easeOutCubic(float t) {
        float v = 1f - t;
        return 1f - v * v * v;
    }

    private float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float v = t - 1f;
        return 1f + c3 * v * v * v + c1 * v * v;
    }

    private float dp(float n) { return n * d; }

    private void fill(int color) { p.setStyle(Paint.Style.FILL); p.setColor(color); p.setShader(null); }
    private void stroke(int color, float width) { p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(width)); p.setColor(color); p.setShader(null); }

    private void text(Canvas c, String s, float x, float y, float size, int color, boolean bold) {
        fill(color);
        p.setTextSize(dp(size));
        p.setTypeface(Typeface.create("sans", bold ? Typeface.BOLD : Typeface.NORMAL));
        p.setTextAlign(Paint.Align.LEFT);
        c.drawText(s, dp(x), dp(y), p);
    }

    private void centered(Canvas c, String s, float x, float y, float size, int color, boolean bold) {
        fill(color);
        p.setTextSize(dp(size));
        p.setTypeface(Typeface.create("sans", bold ? Typeface.BOLD : Typeface.NORMAL));
        p.setTextAlign(Paint.Align.CENTER);
        c.drawText(s, dp(x), dp(y), p);
    }

    private void card(Canvas c, float l, float t, float r, float b, float radius, int color) {
        fill(color);
        p.setShadowLayer(dp(10), 0, dp(3), 0x15000000);
        c.drawRoundRect(new RectF(dp(l), dp(t), dp(r), dp(b)), dp(radius), dp(radius), p);
        p.clearShadowLayer();
    }

    private void flatCard(Canvas c, float l, float t, float r, float b, float radius, int color) {
        fill(color);
        c.drawRoundRect(new RectF(dp(l), dp(t), dp(r), dp(b)), dp(radius), dp(radius), p);
    }

    private void tap(String key, float l, float t, float r, float b) {
        if (collectTaps) taps.put(key, new RectF(dp(l), dp(t), dp(r), dp(b)));
    }

    private float widthDp() { return getWidth() / d; }
    private float heightDp() { return getHeight() / d; }

    private void drawLogin(Canvas c) {
        float w = widthDp(), h = heightDp();
        long now = System.currentTimeMillis();
        float driftA = (float) Math.sin(now / 820.0) * 8f;
        float driftB = (float) Math.cos(now / 1050.0) * 10f;
        fill(PURPLE_DARK);
        c.drawRect(0, 0, getWidth(), getHeight(), p);

        fill(0xFF6D28D9);
        c.drawCircle(dp(w * .86f + driftA), dp(80 + driftB), dp(115), p);
        fill(0x257C3AED);
        c.drawCircle(dp(20 + driftB), dp(h * .7f + driftA), dp(150), p);
        fill(0x16FFFFFF);
        c.drawCircle(dp(w * .12f - driftA), dp(170 + driftA), dp(28), p);

        float logoPop = easeOutBack(Math.min(1f, (now - screenEnteredAt) / 650f));
        c.save();
        c.scale(logoPop, logoPop, dp(w / 2), dp(83));
        flatCard(c, w / 2 - 33, 50, w / 2 + 33, 116, 21, WHITE);
        centered(c, "V", w / 2, 96, 38, PURPLE, true);
        c.restore();
        centered(c, "Violet Bank", w / 2, 148, 25, WHITE, true);
        centered(c, "Демо-банк нового поколения", w / 2, 174, 14, 0xFFD8CCF1, false);

        centered(c, "Введите код", w / 2, 232, 21, WHITE, true);
        centered(c, "Подойдёт любая комбинация из 4 цифр", w / 2, 256, 12, 0xFFCCBEEA, false);

        for (int i = 0; i < 4; i++) {
            float x = w / 2 - 42 + i * 28;
            fill(i < pin.length() ? WHITE : 0x557C6C9C);
            c.drawCircle(dp(x), dp(292), dp(6), p);
        }

        float keyTop = Math.max(325, h - 365);
        int[][] nums = {{1,2,3},{4,5,6},{7,8,9},{-1,0,-2}};
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 3; col++) {
                int n = nums[row][col];
                float x = w / 2 - 92 + col * 92;
                float y = keyTop + row * 74;
                if (n >= 0) {
                    fill(0x24FFFFFF);
                    c.drawCircle(dp(x), dp(y), dp(28), p);
                    centered(c, String.valueOf(n), x, y + 7, 22, WHITE, false);
                    tap("pin:" + n, x - 34, y - 34, x + 34, y + 34);
                } else if (n == -2) {
                    centered(c, "⌫", x, y + 7, 24, WHITE, false);
                    tap("backspace", x - 34, y - 34, x + 34, y + 34);
                } else {
                    centered(c, "ID", x, y + 5, 12, 0xFFD8CCF1, true);
                }
            }
        }
        centered(c, "Face ID", w / 2, h - 26, 13, 0xFFD8CCF1, false);
        postInvalidateOnAnimation();
    }

    private void drawTop(Canvas c, String title, boolean back) {
        float w = widthDp();
        if (back) {
            flatCard(c, 16, 15, 56, 55, 13, WHITE);
            text(c, "‹", 29, 47, 34, INK, false);
            tap("back", 10, 9, 62, 61);
            text(c, title, 70, 44, 23, INK, true);
        } else {
            text(c, title, 20, 45, 25, INK, true);
        }
        fill(LINE);
        c.drawRect(0, dp(70), dp(w), dp(71), p);
    }

    private void drawHome(Canvas c) {
        float w = widthDp(), h = heightDp();
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0, 0, dp(w), dp(245), PURPLE_DARK, 0xFF7C3AED, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, getWidth(), dp(245), p);
        p.setShader(null);
        text(c, "Доброе утро, Артём", 20, 38, 20, WHITE, true);
        flatCard(c, w - 58, 14, w - 18, 54, 20, 0xFF8B5CF6);
        centered(c, "А", w - 38, 41, 17, WHITE, true);

        text(c, "Общий баланс", 20, 82, 13, 0xFFD8CCF1, false);
        text(c, balanceHidden ? "•••••• ₽" : DemoStore.money(animatedBalance()), 20, 119, 29, WHITE, true);
        centered(c, balanceHidden ? "○" : "●", w - 37, 110, 14, 0xFFE9DFFC, false);
        tap("toggle_balance", w - 66, 82, w - 10, 132);

        // Main bank card
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(dp(16), dp(143), dp(w - 16), dp(268), 0xFF8B5CF6, 0xFF5B21B6, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(dp(16), dp(143), dp(w - 16), dp(268)), dp(22), dp(22), p);
        p.setShader(null);
        fill(0x33FFFFFF);
        c.drawCircle(dp(w - 40), dp(178), dp(76), p);
        c.drawCircle(dp(w - 115), dp(250), dp(58), p);
        text(c, "VIOLET", 34, 174, 14, WHITE, true);
        text(c, "••••  4821", 34, 218, 22, WHITE, true);
        text(c, "VISA", w - 76, 247, 17, WHITE, true);
        drawCardShimmer(c, w);
        tap("card", 16, 143, w - 16, 268);

        float quickY = 288;
        String[] qLabels = {"Перевести", "Оплатить", "Пополнить"};
        String[] qIcons = {"→", "✓", "+"};
        String[] qKeys = {"transfer", "payments", "topup"};
        float cellW = (w - 52) / 3f;
        for (int i = 0; i < 3; i++) {
            float l = 16 + i * (cellW + 10);
            card(c, l, quickY, l + cellW, quickY + 92, 18, WHITE);
            flatCard(c, l + cellW / 2 - 19, quickY + 12, l + cellW / 2 + 19, quickY + 50, 13, PURPLE_PALE);
            centered(c, qIcons[i], l + cellW / 2, quickY + 39, 22, PURPLE, true);
            centered(c, qLabels[i], l + cellW / 2, quickY + 74, 12, INK, true);
            tap(qKeys[i], l, quickY, l + cellW, quickY + 92);
        }

        float listY = 409;
        text(c, "Последние операции", 20, listY, 20, INK, true);
        text(c, "Все", w - 52, listY, 13, PURPLE, true);
        tap("history", w - 74, listY - 28, w - 12, listY + 12);
        List<DemoStore.Operation> ops = store.getOperations();
        int count = Math.min(3, ops.size());
        for (int i = 0; i < count; i++) drawOperation(c, ops.get(i), 20, listY + 30 + i * 65, w - 20);
        drawBottom(c, h, 0);
    }

    private double animatedBalance() {
        if (balanceAnimStart == 0) return displayedBalance;
        float raw = Math.min(1f, (System.currentTimeMillis() - balanceAnimStart) / 760f);
        float eased = easeOutCubic(raw);
        displayedBalance = balanceFrom + (balanceTo - balanceFrom) * eased;
        if (raw < 1f) postInvalidateOnAnimation();
        else {
            displayedBalance = balanceTo;
            balanceAnimStart = 0;
        }
        return displayedBalance;
    }

    private void animateBalance(double from, double to) {
        balanceFrom = from;
        balanceTo = to;
        displayedBalance = from;
        balanceAnimStart = System.currentTimeMillis();
        postInvalidateOnAnimation();
    }

    private void drawCardShimmer(Canvas c, float w) {
        if (shimmerStart == 0) return;
        float raw = (System.currentTimeMillis() - shimmerStart) / 1050f;
        if (raw >= 1f) {
            shimmerStart = 0;
            return;
        }
        float x = -110 + (w + 220) * easeOutCubic(raw);
        Path clip = new Path();
        clip.addRoundRect(new RectF(dp(16), dp(143), dp(w - 16), dp(268)), dp(22), dp(22), Path.Direction.CW);
        c.save();
        c.clipPath(clip);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(dp(x), 0, dp(x + 95), 0,
            new int[]{0x00FFFFFF, 0x55FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        c.drawRect(dp(x - 20), dp(143), dp(x + 120), dp(268), p);
        p.setShader(null);
        c.restore();
        postInvalidateOnAnimation();
    }

    private void drawOperation(Canvas c, DemoStore.Operation op, float l, float y, float r) {
        flatCard(c, l, y - 18, l + 43, y + 25, 14, op.income ? 0xFFE4F8F0 : PURPLE_PALE);
        centered(c, op.income ? "+" : categoryLetter(op.title), l + 21.5f, y + 10, 16, op.income ? GREEN : PURPLE, true);
        text(c, op.title, l + 56, y, 14, INK, true);
        text(c, op.subtitle, l + 56, y + 20, 11, MUTED, false);
        String amt = (op.income ? "+" : "−") + DemoStore.money(op.amount);
        p.setTextSize(dp(13));
        p.setTypeface(Typeface.create("sans", Typeface.BOLD));
        float tw = p.measureText(amt) / d;
        text(c, amt, r - tw, y + 7, 13, op.income ? GREEN : INK, true);
    }

    private String categoryLetter(String title) {
        if (title.contains("Яндекс")) return "Я";
        if (title.contains("Ozon")) return "O";
        if (title.contains("Коф")) return "К";
        return "₽";
    }

    private void drawBottom(Canvas c, float h, int active) {
        float w = widthDp();
        float top = h - 78;
        fill(WHITE);
        p.setShadowLayer(dp(15), 0, -dp(2), 0x16000000);
        c.drawRect(0, dp(top), getWidth(), getHeight(), p);
        p.clearShadowLayer();
        String[] labels = {"Главная", "История", "Платежи", "Профиль"};
        String[] icons = {"⌂", "≡", "₽", "●"};
        String[] keys = {"home", "history", "payments", "profile"};
        for (int i = 0; i < 4; i++) {
            float x = w * (i + .5f) / 4f;
            if (i == active) flatCard(c, x - 25, top + 8, x + 25, top + 38, 15, PURPLE_PALE);
            centered(c, icons[i], x, top + 31, 20, i == active ? PURPLE : MUTED, i == active);
            centered(c, labels[i], x, top + 58, 10, i == active ? PURPLE : MUTED, i == active);
            tap(keys[i], w * i / 4, top, w * (i + 1) / 4, h);
        }
    }

    private void drawHistory(Canvas c) {
        float w = widthDp(), h = heightDp();
        drawTop(c, "История", false);
        String[] chips = {"Все", "Расходы", "Пополнения"};
        float x = 20;
        for (int i = 0; i < chips.length; i++) {
            float cw = i == 2 ? 110 : 82;
            flatCard(c, x, 90, x + cw, 127, 18, i == 0 ? PURPLE : WHITE);
            centered(c, chips[i], x + cw / 2, 114, 12, i == 0 ? WHITE : MUTED, true);
            x += cw + 9;
        }
        text(c, "Сентябрь", 20, 164, 16, INK, true);
        List<DemoStore.Operation> ops = store.getOperations();
        int count = Math.min(6, ops.size());
        for (int i = 0; i < count; i++) drawOperation(c, ops.get(i), 20, 196 + i * 67, w - 20);
        drawBottom(c, h, 1);
    }

    private void drawPayments(Canvas c) {
        float w = widthDp(), h = heightDp();
        drawTop(c, "Платежи", false);
        card(c, 20, 88, w - 20, 140, 16, WHITE);
        text(c, "⌕", 37, 122, 24, MUTED, false);
        text(c, "Найти услугу", 72, 120, 14, MUTED, false);

        text(c, "Популярное", 20, 180, 19, INK, true);
        String[] names = {"Мобильная\nсвязь", "Интернет", "ЖКХ", "Транспорт", "Штрафы", "Образование"};
        String[] signs = {"☎", "@", "⌂", "→", "!", "A"};
        for (int i = 0; i < 6; i++) {
            int col = i % 3, row = i / 3;
            float cellW = (w - 52) / 3f;
            float l = 16 + col * (cellW + 10);
            float t = 201 + row * 123;
            card(c, l, t, l + cellW, t + 106, 18, WHITE);
            flatCard(c, l + 12, t + 12, l + 50, t + 50, 12, PURPLE_PALE);
            centered(c, signs[i], l + 31, t + 39, 18, PURPLE, true);
            String[] lines = names[i].split("\\n");
            text(c, lines[0], l + 12, t + 76, 12, INK, true);
            if (lines.length > 1) text(c, lines[1], l + 12, t + 92, 12, INK, true);
            tap("pay:" + i, l, t, l + cellW, t + 106);
        }
        text(c, "Шаблоны и автоплатежи", 20, 474, 19, INK, true);
        card(c, 20, 492, w - 20, 558, 18, WHITE);
        flatCard(c, 34, 506, 72, 544, 12, 0xFFE4F8F0);
        centered(c, "+", 53, 532, 22, GREEN, true);
        text(c, "Создать автоплатёж", 87, 522, 14, INK, true);
        text(c, "Не забывайте об оплате", 87, 541, 11, MUTED, false);
        drawBottom(c, h, 2);
    }

    private void drawProfile(Canvas c) {
        float w = widthDp(), h = heightDp();
        drawTop(c, "Профиль", false);
        flatCard(c, 20, 91, 82, 153, 31, PURPLE);
        centered(c, "А", 51, 133, 25, WHITE, true);
        text(c, "Артём", 99, 117, 20, INK, true);
        text(c, "+7 ••• •••-45-67", 99, 140, 13, MUTED, false);

        String[] rows = {"Личные данные", "Безопасность", "Уведомления", "Настройки приложения", "Помощь и поддержка"};
        String[] signs = {"●", "◆", "◉", "⚙", "?"};
        float y = 186;
        for (int i = 0; i < rows.length; i++) {
            card(c, 20, y, w - 20, y + 60, 17, WHITE);
            flatCard(c, 34, y + 11, 72, y + 49, 12, PURPLE_PALE);
            centered(c, signs[i], 53, y + 37, 16, PURPLE, true);
            text(c, rows[i], 87, y + 37, 14, INK, true);
            text(c, "›", w - 44, y + 39, 24, MUTED, false);
            y += 71;
        }
        flatCard(c, 20, y + 3, w - 20, y + 53, 16, 0xFFFFE8E8);
        centered(c, "Выйти из демо-профиля", w / 2, y + 35, 14, RED, true);
        tap("logout", 20, y + 3, w - 20, y + 53);
        drawBottom(c, h, 3);
    }

    private void drawTransfer(Canvas c) {
        float w = widthDp(), h = heightDp();
        drawTop(c, "Перевод", true);
        text(c, "С карты", 20, 102, 13, MUTED, false);
        card(c, 20, 116, w - 20, 185, 18, WHITE);
        flatCard(c, 34, 131, 82, 170, 12, PURPLE);
        centered(c, "V", 58, 158, 18, WHITE, true);
        text(c, "Violet •• 4821", 98, 147, 14, INK, true);
        text(c, balanceHidden ? "•••••• ₽" : DemoStore.money(store.getBalance()), 98, 169, 12, MUTED, false);

        text(c, "Получатель", 20, 221, 13, MUTED, false);
        card(c, 20, 235, w - 20, 304, 18, WHITE);
        flatCard(c, 34, 250, 73, 289, 19, PURPLE_PALE);
        centered(c, "А", 53.5f, 277, 16, PURPLE, true);
        text(c, phone, 88, 265, 14, INK, true);
        text(c, "По номеру телефона", 88, 285, 11, MUTED, false);
        tap("edit_phone", 20, 235, w - 20, 304);

        text(c, "Сумма", 20, 340, 13, MUTED, false);
        card(c, 20, 354, w - 20, 429, 18, WHITE);
        text(c, transferAmount + " ₽", 38, 400, 27, INK, true);
        tap("edit_amount", 20, 354, w - 20, 429);

        flatCard(c, 20, 451, w - 20, 498, 15, PURPLE_PALE);
        text(c, "Комиссия", 36, 480, 13, MUTED, false);
        p.setTextSize(dp(13)); p.setTypeface(Typeface.DEFAULT_BOLD);
        String free = "0 ₽";
        float ftw = p.measureText(free) / d;
        text(c, free, w - 36 - ftw, 480, 13, GREEN, true);

        flatCard(c, 20, Math.min(540, h - 132), w - 20, Math.min(596, h - 76), 18, PURPLE);
        centered(c, "Продолжить", w / 2, Math.min(576, h - 96), 16, WHITE, true);
        tap("confirm_transfer", 20, Math.min(540, h - 132), w - 20, Math.min(596, h - 76));
        centered(c, "Все операции в приложении демонстрационные", w / 2, Math.min(630, h - 42), 10, MUTED, false);
    }

    private void drawCardScreen(Canvas c) {
        float w = widthDp(), h = heightDp();
        drawTop(c, "Моя карта", true);
        float floatY = cardFrozen ? 0 : (float) Math.sin((System.currentTimeMillis() - screenEnteredAt) / 720.0) * 2.2f;
        c.save();
        c.translate(0, dp(floatY));
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(dp(20), dp(95), dp(w - 20), dp(286),
            cardFrozen ? 0xFF77717E : 0xFF8B5CF6,
            cardFrozen ? 0xFF4B4651 : 0xFF4C1D95,
            Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(dp(20), dp(95), dp(w - 20), dp(286)), dp(24), dp(24), p);
        p.setShader(null);
        fill(0x22FFFFFF);
        c.drawCircle(dp(w - 60), dp(140), dp(100), p);
        c.drawCircle(dp(65), dp(285), dp(82), p);
        text(c, "VIOLET", 42, 133, 16, WHITE, true);
        text(c, cardRevealed ? "2200 1515 7842 4821" : "••••  ••••  ••••  4821", 42, 194, 20, WHITE, true);
        text(c, cardRevealed ? "09/30" : "••/••", 42, 235, 13, 0xFFD8CCF1, false);
        text(c, cardRevealed ? "482" : "•••", 116, 235, 13, 0xFFD8CCF1, false);
        text(c, "VISA", w - 92, 257, 19, WHITE, true);
        if (cardFrozen) centered(c, "КАРТА ЗАМОРОЖЕНА", w / 2, 273, 10, WHITE, true);
        c.restore();
        if (!cardFrozen) postInvalidateOnAnimation();

        flatCard(c, 20, 307, w - 20, 359, 17, PURPLE_PALE);
        centered(c, cardRevealed ? "Скрыть реквизиты" : "Показать реквизиты", w / 2, 340, 14, PURPLE, true);
        tap("reveal_card", 20, 307, w - 20, 359);

        text(c, "Управление картой", 20, 401, 19, INK, true);
        String[] rows = {cardFrozen ? "Разморозить карту" : "Заморозить карту", "Изменить ПИН-код", "Лимиты по карте"};
        String[] signs = {"◆", "#", "≡"};
        float y = 420;
        for (int i = 0; i < rows.length; i++) {
            card(c, 20, y, w - 20, y + 62, 17, WHITE);
            flatCard(c, 34, y + 12, 72, y + 50, 12, PURPLE_PALE);
            centered(c, signs[i], 53, y + 38, 15, PURPLE, true);
            text(c, rows[i], 87, y + 38, 14, INK, true);
            text(c, "›", w - 44, y + 40, 24, MUTED, false);
            tap(i == 0 ? "freeze" : "card_option", 20, y, w - 20, y + 62);
            y += 73;
        }
        centered(c, "Виртуальная демо-карта • настоящие платежи отключены", w / 2, Math.min(y + 30, h - 24), 10, MUTED, false);
    }

    private void drawPressEffect(Canvas c, long now) {
        if (pressedRect == null) return;
        float elapsed = now - pressStart;
        float progress = Math.min(1f, elapsed / 360f);
        float fade = pressReleased ? Math.max(0f, 1f - Math.max(0f, elapsed - 90f) / 250f) : 1f;
        float radius = Math.max(pressedRect.width(), pressedRect.height()) * (.15f + .9f * easeOutCubic(progress));
        c.save();
        Path clip = new Path();
        clip.addRoundRect(pressedRect, dp(18), dp(18), Path.Direction.CW);
        c.clipPath(clip);
        fill((((int) (42 * fade)) << 24) | (PURPLE & 0x00FFFFFF));
        c.drawCircle(pressX, pressY, radius, p);
        c.restore();
        if (pressReleased && fade <= 0f) pressedRect = null;
        else postInvalidateOnAnimation();
    }

    private void drawSuccessOverlay(Canvas c, long now) {
        float w = widthDp(), h = heightDp();
        float elapsed = now - successStart;
        float fade = Math.min(1f, elapsed / 220f);
        fill((((int) (150 * fade)) << 24) | 0x000B0712);
        c.drawRect(0, 0, getWidth(), getHeight(), p);

        float cardL = 27, cardR = w - 27;
        float cardT = h / 2f - 170, cardB = h / 2f + 150;
        float pop = easeOutBack(Math.min(1f, elapsed / 480f));
        c.save();
        c.scale(pop, pop, dp(w / 2), dp(h / 2 - 8));
        card(c, cardL, cardT, cardR, cardB, 28, WHITE);

        float iconY = cardT + 82;
        float pulse = .5f + .5f * (float) Math.sin(elapsed / 115.0);
        fill((((int) (28 * (1f - Math.min(1f, elapsed / 900f)) + 10 * pulse)) << 24) | (GREEN & 0x00FFFFFF));
        c.drawCircle(dp(w / 2), dp(iconY), dp(49 + 8 * pulse), p);
        fill(GREEN);
        c.drawCircle(dp(w / 2), dp(iconY), dp(39), p);

        float checkP = Math.min(1f, Math.max(0f, (elapsed - 240f) / 340f));
        stroke(WHITE, 4.5f);
        p.setStrokeCap(Paint.Cap.ROUND);
        Path check = new Path();
        check.moveTo(dp(w / 2 - 17), dp(iconY));
        if (checkP < .45f) {
            float q = checkP / .45f;
            check.lineTo(dp(w / 2 - 17 + 13 * q), dp(iconY + 13 * q));
        } else {
            check.lineTo(dp(w / 2 - 4), dp(iconY + 13));
            float q = (checkP - .45f) / .55f;
            check.lineTo(dp(w / 2 - 4 + 24 * q), dp(iconY + 13 - 30 * q));
        }
        c.drawPath(check, p);
        p.setStrokeCap(Paint.Cap.BUTT);

        drawConfetti(c, w / 2, iconY, elapsed);
        centered(c, successTitle, w / 2, cardT + 153, 21, INK, true);
        centered(c, successAmount, w / 2, cardT + 187, 17, PURPLE, true);
        flatCard(c, cardL + 22, cardB - 69, cardR - 22, cardB - 17, 17, PURPLE);
        centered(c, "Готово", w / 2, cardB - 36, 15, WHITE, true);
        c.restore();
        centered(c, "Нажмите, чтобы закрыть", w / 2, cardB + 29, 11, 0xFFE1D8F0, false);
        if (elapsed < 1300) postInvalidateOnAnimation();
    }

    private void drawConfetti(Canvas c, float cx, float cy, float elapsed) {
        float t = Math.min(1f, Math.max(0f, (elapsed - 180f) / 720f));
        if (t <= 0f) return;
        int[] colors = {PURPLE, 0xFFF59E0B, GREEN, 0xFFEC4899, 0xFF38BDF8};
        for (int i = 0; i < 14; i++) {
            double angle = i * Math.PI * 2 / 14.0 - Math.PI / 2;
            float distance = 45 + 58 * easeOutCubic(t) + (i % 3) * 7;
            float x = cx + (float) Math.cos(angle) * distance;
            float y = cy + (float) Math.sin(angle) * distance + 22 * t * t;
            fill((((int) (255 * (1f - .68f * t))) << 24) | (colors[i % colors.length] & 0x00FFFFFF));
            c.save();
            c.rotate(i * 31 + t * 160, dp(x), dp(y));
            c.drawRoundRect(new RectF(dp(x - 2), dp(y - 6), dp(x + 2), dp(y + 6)), dp(2), dp(2), p);
            c.restore();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX(), y = event.getY();
        if (successActive) {
            if (event.getAction() == MotionEvent.ACTION_UP && System.currentTimeMillis() - successStart > 450) {
                successActive = false;
                invalidate();
            }
            return true;
        }
        if (transitioning) return true;
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            for (Map.Entry<String, RectF> e : taps.entrySet()) {
                if (e.getValue().contains(x, y)) {
                    pressedKey = e.getKey();
                    pressedRect = new RectF(e.getValue());
                    pressX = x;
                    pressY = y;
                    pressStart = System.currentTimeMillis();
                    pressReleased = false;
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    postInvalidateOnAnimation();
                    break;
                }
            }
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
            pressReleased = true;
            String action = pressedKey;
            RectF target = pressedRect;
            pressedKey = null;
            if (action != null && target != null && target.contains(x, y)) {
                handle(action);
                performClick();
            }
            postInvalidateOnAnimation();
            return true;
        }
        return true;
    }

    @Override
    public boolean performClick() { super.performClick(); return true; }

    private void handle(String key) {
        if (key.startsWith("pin:")) {
            if (pin.length() < 4) pin += key.substring(4);
            if (pin.length() == 4) {
                postDelayed(() -> {
                    pin = "";
                    animateBalance(0, store.getBalance());
                    shimmerStart = System.currentTimeMillis() + 120;
                    navigate(Screen.HOME);
                }, 160);
            }
            invalidate();
            return;
        }
        if (key.equals("backspace")) {
            if (!pin.isEmpty()) pin = pin.substring(0, pin.length() - 1);
            invalidate(); return;
        }
        if (key.equals("home")) navigate(Screen.HOME);
        else if (key.equals("history")) navigate(Screen.HISTORY);
        else if (key.equals("payments")) navigate(Screen.PAYMENTS);
        else if (key.equals("profile")) navigate(Screen.PROFILE);
        else if (key.equals("transfer")) navigate(Screen.TRANSFER);
        else if (key.equals("card")) navigate(Screen.CARD);
        else if (key.equals("back")) navigate(Screen.HOME);
        else if (key.equals("toggle_balance")) balanceHidden = !balanceHidden;
        else if (key.equals("topup")) askAmount("Пополнить демо-счёт", true, -1);
        else if (key.equals("edit_phone")) askPhone();
        else if (key.equals("edit_amount")) askTransferAmount();
        else if (key.equals("confirm_transfer")) confirmTransfer();
        else if (key.startsWith("pay:")) {
            selectedPayment = Integer.parseInt(key.substring(4));
            askAmount("Оплата услуги", false, selectedPayment);
        }
        else if (key.equals("reveal_card")) cardRevealed = !cardRevealed;
        else if (key.equals("freeze")) {
            cardFrozen = !cardFrozen;
            toast(cardFrozen ? "Демо-карта заморожена" : "Демо-карта снова активна");
        }
        else if (key.equals("card_option")) toast("Доступно в следующей версии");
        else if (key.equals("logout")) {
            pin = "";
            navigate(Screen.LOGIN);
            toast("Вы вышли из демо-профиля");
        }
        invalidate();
    }

    private void navigate(Screen target) {
        if (target == screen) return;
        previousScreen = screen;
        screen = target;
        transitionStart = System.currentTimeMillis();
        screenEnteredAt = transitionStart;
        transitioning = true;
        if (target == Screen.HOME) shimmerStart = transitionStart + 90;
        postInvalidateOnAnimation();
    }

    public boolean goBack() {
        if (screen == Screen.LOGIN) return false;
        if (screen == Screen.HOME) { navigate(Screen.LOGIN); return true; }
        navigate(Screen.HOME);
        return true;
    }

    private EditText input(String hint, int type) {
        EditText edit = new EditText(getContext());
        edit.setHint(hint);
        edit.setTextSize(18);
        edit.setTextColor(INK);
        edit.setHintTextColor(MUTED);
        edit.setSingleLine(true);
        edit.setInputType(type);
        edit.setPadding((int) dp(16), (int) dp(8), (int) dp(16), (int) dp(8));
        return edit;
    }

    private void askPhone() {
        final EditText edit = input("+7 999 123-45-67", InputType.TYPE_CLASS_PHONE);
        edit.setText(phone);
        showInputDialog("Номер получателя", edit, "Сохранить", () -> {
            String value = edit.getText().toString().trim();
            if (!value.isEmpty()) phone = value;
            invalidate();
        });
    }

    private void askTransferAmount() {
        final EditText edit = input("1000", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        edit.setText(transferAmount.replace(" ", ""));
        showInputDialog("Сумма перевода", edit, "Сохранить", () -> {
            double amount = parse(edit.getText().toString());
            if (amount > 0) transferAmount = prettyAmount(amount);
            else toast("Введите сумму больше нуля");
            invalidate();
        });
    }

    private void askAmount(String title, boolean topUp, int payment) {
        final EditText edit = input("1000", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        showInputDialog(title, edit, topUp ? "Пополнить" : "Оплатить", () -> {
            double amount = parse(edit.getText().toString());
            if (amount <= 0) { toast("Введите сумму больше нуля"); return; }
            double before = store.getBalance();
            if (topUp) {
                store.addMoney(amount);
                animateBalance(before, store.getBalance());
                success("Счёт пополнен", "+" + DemoStore.money(amount));
            } else {
                String[] services = {"Мобильная связь", "Интернет", "ЖКХ", "Транспорт", "Штрафы", "Образование"};
                String service = payment >= 0 && payment < services.length ? services[payment] : "Оплата";
                if (store.spend(service, amount)) {
                    animateBalance(before, store.getBalance());
                    success("Оплата выполнена", DemoStore.money(amount));
                }
                else toast("Недостаточно средств на демо-счёте");
            }
            invalidate();
        });
    }

    private void confirmTransfer() {
        double amount = parse(transferAmount);
        if (amount <= 0) { toast("Введите сумму перевода"); return; }
        new AlertDialog.Builder(getContext())
            .setTitle("Подтвердить перевод?")
            .setMessage("Получатель: " + phone + "\nСумма: " + DemoStore.money(amount) + "\nКомиссия: 0 ₽")
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Перевести", (dialog, which) -> {
                double before = store.getBalance();
                if (store.spend("Перевод по телефону", amount)) {
                    animateBalance(before, store.getBalance());
                    navigate(Screen.HOME);
                    success("Перевод выполнен", DemoStore.money(amount));
                } else toast("Недостаточно средств на демо-счёте");
                invalidate();
            }).show();
    }

    private void showInputDialog(String title, EditText edit, String positive, Runnable action) {
        LinearLayout wrap = new LinearLayout(getContext());
        wrap.setPadding((int) dp(20), 0, (int) dp(20), 0);
        wrap.addView(edit, new LinearLayout.LayoutParams(-1, (int) dp(58)));
        AlertDialog dialog = new AlertDialog.Builder(getContext())
            .setTitle(title)
            .setView(wrap)
            .setNegativeButton("Отмена", null)
            .setPositiveButton(positive, (d, which) -> action.run())
            .create();
        dialog.setOnShowListener(d -> {
            edit.requestFocus();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(PURPLE);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(MUTED);
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        });
        dialog.show();
    }

    private void success(String title, String amount) {
        successTitle = title;
        successAmount = amount;
        successStart = System.currentTimeMillis();
        successActive = true;
        performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        postInvalidateOnAnimation();
    }

    private double parse(String s) {
        try { return Double.parseDouble(s.replace(" ", "").replace(',', '.').replace("₽", "").trim()); }
        catch (Exception ignored) { return 0; }
    }

    private String prettyAmount(double amount) {
        long whole = Math.round(amount);
        String raw = String.valueOf(whole);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            if (i > 0 && (raw.length() - i) % 3 == 0) b.append(' ');
            b.append(raw.charAt(i));
        }
        return b.toString();
    }

    private void toast(String message) { Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show(); }
}
