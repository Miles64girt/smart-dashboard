package ru.home.dashboard;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Вечернее окно «Список вещей, которые нужно взять с собой»:
 * размытый фон, центральная карточка, квадраты-отметки, добавление своих пунктов,
 * анимация успеха (зелёная галочка + салют).
 */
public class PackingOverlayView extends View {

    public interface Listener {
        void onClosed();
        void onAddCustomRequested();
        void onAllChecked();
    }

    private static final int CARD_BG = 0xFF13233B;
    private static final int CARD_STROKE = 0x22FFFFFF;
    private static final int WHITE = 0xFFF3F8FF;
    private static final int MUTED = 0xFF93A9C9;
    private static final int DIM = 0xFF6C82A6;
    private static final int GREEN = 0xFF34D399;
    private static final int SKY = 0xFF7DD3FC;
    private static final int WARN = 0xFFFFB86B;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF card = new RectF();
    private final RectF tmp = new RectF();
    private final Path path = new Path();

    private final Typeface tfLight = Typeface.create("sans-serif-light", Typeface.NORMAL);
    private final Typeface tfRegular = Typeface.create("sans-serif", Typeface.NORMAL);
    private final Typeface tfMedium = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    private Bitmap background;
    private PackingListModel model;
    private Listener listener;
    private boolean flipped = false;

    private int scrollY = 0;
    private int contentHeight = 0;
    private int visibleHeight = 0;

    private boolean success = false;
    private long successStart = 0L;
    private boolean allCheckedNotified = false;
    private final List<Particle> particles = new ArrayList<Particle>();
    private final Random random = new Random();

    private final List<RectF> rowRects = new ArrayList<RectF>();
    private final List<RectF> customRemoveRects = new ArrayList<RectF>();
    private final RectF addRect = new RectF();
    private final RectF closeRect = new RectF();

    private float downY = 0f;
    private long downTime = 0L;
    private boolean dragging = false;
    private boolean downOutside = false;

    public PackingOverlayView(Context context) {
        super(context);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);
    }

    public void setModel(PackingListModel model) {
        this.model = model;
        this.scrollY = 0;
        this.success = false;
        this.allCheckedNotified = false;
        this.particles.clear();
        invalidate();
    }

    public void setBackgroundBitmap(Bitmap bitmap) {
        this.background = bitmap;
        invalidate();
    }

    public void setFlipped(boolean value) {
        this.flipped = value;
        invalidate();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void showSuccess() {
        if (model == null) return;
        success = true;
        successStart = SystemClock.elapsedRealtime();
        allCheckedNotified = false;
        spawnParticles();
        invalidate();
    }

    // ------------------------------------------------------------------ draw

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        float s = Math.min(w / DeviceConfig.DESIGN_WIDTH, h / DeviceConfig.DESIGN_HEIGHT);

        c.save();
        if (flipped) c.rotate(180f, w / 2f, h / 2f);

        // фон: размытый снимок панели + затемнение
        if (background != null) {
            tmp.set(0f, 0f, (float) w, (float) h);
            c.drawBitmap(background, null, tmp, fill);
        } else {
            fill.setColor(0xFF0B1220);
            c.drawRect(0f, 0f, (float) w, (float) h, fill);
        }
        fill.setColor(0x86000000);
        c.drawRect(0f, 0f, (float) w, (float) h, fill);

        card.set(w * 0.055f, h * 0.06f, w * 0.945f, h * 0.94f);
        float pad = 24f * s;

        // тень + карточка
        fill.setColor(0x66000000);
        tmp.set(card.left + 4f * s, card.top + 8f * s, card.right + 4f * s, card.bottom + 8f * s);
        c.drawRoundRect(tmp, 26f * s, 26f * s, fill);
        fill.setColor(CARD_BG);
        c.drawRoundRect(card, 26f * s, 26f * s, fill);
        stroke.setColor(CARD_STROKE);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(1f * s);
        c.drawRoundRect(card, 26f * s, 26f * s, stroke);
        stroke.setStyle(Paint.Style.FILL);

        // кнопка закрытия
        float cx = card.right - pad - 14f * s;
        float cy = card.top + pad + 10f * s;
        closeRect.set(cx - 18f * s, cy - 18f * s, cx + 18f * s, cy + 18f * s);
        txt.setTypeface(tfRegular);
        txt.setTextSize(20f * s);
        txt.setColor(MUTED);
        txt.setTextAlign(Paint.Align.CENTER);
        c.drawText("×", cx, cy + 7f * s, txt);

        // заголовок
        txt.setTypeface(tfMedium);
        txt.setTextSize(16.5f * s);
        txt.setColor(WHITE);
        txt.setTextAlign(Paint.Align.LEFT);
        String title = "Список вещей, которые нужно взять с собой:";
        float titleWidth = card.width() - pad * 2f - 34f * s;
        String[] titleLines = wrap(title, txt, titleWidth, 3);
        float titleLineH = 21f * s;
        float y = card.top + pad + 6f * s + titleLineH;
        for (String line : titleLines) {
            c.drawText(line, card.left + pad, y, txt);
            y += titleLineH;
        }
        float listTop = y + 6f * s;
        float addRowHeight = 42f * s;
        float listBottom = card.bottom - pad - addRowHeight - 10f * s;

        // содержимое списка
        c.save();
        c.clipRect(card.left + 6f * s, listTop, card.right - 6f * s, listBottom);
        float yy = listTop - scrollY;
        rowRects.clear();
        customRemoveRects.clear();
        addRect.set(0f, 0f, 0f, 0f);

        if (model != null) {
            List<PackingItem> items = model.items();
            float listX = card.left + pad;
            float listWidth = card.width() - pad * 2f;
            float box = 22f * s;
            float gap = 13f * s;

            for (PackingItem item : items) {
                txt.setTypeface(item.checked ? tfRegular : tfMedium);
                txt.setTextSize(14f * s);
                float textX = listX + box + gap;
                float textWidth = listWidth - box - gap - 4f * s;
                String[] lines = wrap(item.label, txt, textWidth, 3);
                float rowH = Math.max(46f * s, lines.length * 18f * s + 18f * s);
                RectF rowRect = new RectF(listX, yy, listX + listWidth, yy + rowH - 6f * s);
                rowRects.add(rowRect);

                int color = item.checked ? 0x55FFFFFF : WHITE;
                // квадрат
                float by = yy + (rowH - box) / 2f - 3f * s;
                tmp.set(listX, by, listX + box, by + box);
                if (item.checked) {
                    fill.setColor(GREEN);
                    c.drawRoundRect(tmp, 6f * s, 6f * s, fill);
                    stroke.setColor(0xFFFFFFFF);
                    stroke.setStyle(Paint.Style.STROKE);
                    stroke.setStrokeWidth(2.2f * s);
                    Path tick = new Path();
                    tick.moveTo(listX + box * 0.26f, by + box * 0.54f);
                    tick.lineTo(listX + box * 0.44f, by + box * 0.72f);
                    tick.lineTo(listX + box * 0.76f, by + box * 0.32f);
                    c.drawPath(tick, stroke);
                    stroke.setStyle(Paint.Style.FILL);
                } else {
                    fill.setColor(0x22FFFFFF);
                    c.drawRoundRect(tmp, 6f * s, 6f * s, fill);
                    stroke.setColor(0x66FFFFFF);
                    stroke.setStyle(Paint.Style.STROKE);
                    stroke.setStrokeWidth(1.6f * s);
                    c.drawRoundRect(tmp, 6f * s, 6f * s, stroke);
                    stroke.setStyle(Paint.Style.FILL);
                }

                txt.setColor(color);
                if (item.checked) {
                    txt.setFlags(Paint.STRIKE_THRU_TEXT_FLAG | Paint.ANTI_ALIAS_FLAG);
                } else {
                    txt.setFlags(Paint.ANTI_ALIAS_FLAG);
                }
                float lineY = yy + (rowH - lines.length * 18f * s) / 2f + 13f * s;
                for (String line : lines) {
                    c.drawText(line, textX, lineY, txt);
                    lineY += 18f * s;
                }
                txt.setFlags(Paint.ANTI_ALIAS_FLAG);
                yy += rowH;
            }

            // Пустой список: подсказываем, что вещи добавляет сам пользователь.
            if (items.isEmpty()) {
                float ecx = card.centerX();
                float emptyY = listTop + (listBottom - listTop) * 0.34f;
                drawText(c, "Список пока пуст", ecx, emptyY,
                        17f * s, WHITE, tfMedium, Paint.Align.CENTER);
                drawText(c, "Нажмите «+ Добавить свой пункт» ниже", ecx, emptyY + 27f * s,
                        12.5f * s, MUTED, tfRegular, Paint.Align.CENTER);
            }
        }
        contentHeight = (int) (yy - (listTop - scrollY));
        visibleHeight = (int) (listBottom - listTop);
        c.restore();

        // фиксированная строка «добавить свой пункт» внизу окна
        if (model != null) {
            float listX = card.left + pad;
            float listWidth = card.width() - pad * 2f;
            addRect.set(listX, card.bottom - pad - addRowHeight, listX + listWidth, card.bottom - pad);
            fill.setColor(0x10FFFFFF);
            c.drawRoundRect(addRect, 12f * s, 12f * s, fill);
            txt.setTypeface(tfRegular);
            txt.setTextSize(13.5f * s);
            txt.setColor(0x88FFFFFF);
            txt.setTextAlign(Paint.Align.LEFT);
            c.drawText("+  Добавить свой пункт", listX + 14f * s, addRect.top + 27f * s, txt);
        }

        if (success) {
            drawSuccess(c, s);
            long elapsed = SystemClock.elapsedRealtime() - successStart;
            if (elapsed > 5600 && !allCheckedNotified) {
                allCheckedNotified = true;
                if (listener != null) listener.onAllChecked();
            }
            if (elapsed < 6500) postInvalidateOnAnimation();
        }

        c.restore();
    }

    private void drawSuccess(Canvas c, float s) {
        long elapsed = SystemClock.elapsedRealtime() - successStart;

        // затемняем карточку
        fill.setColor(0xE613233B);
        c.drawRoundRect(card, 26f * s, 26f * s, fill);

        float cx = card.centerX();
        float cy = card.top + card.height() * 0.36f;
        float r = 46f * s;

        // круг
        float circleProgress = Math.min(1f, elapsed / 450f);
        stroke.setColor(GREEN);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(5f * s);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        tmp.set(cx - r, cy - r, cx + r, cy + r);
        c.drawArc(tmp, -90f, 360f * circleProgress, false, stroke);

        // галочка
        float checkProgress = Math.min(1f, Math.max(0f, (elapsed - 250f) / 400f));
        if (checkProgress > 0f) {
            Path check = new Path();
            float x1 = cx - r * 0.42f, y1 = cy + 0.02f * r;
            float x2 = cx - r * 0.10f, y2 = cy + r * 0.34f;
            float x3 = cx + r * 0.46f, y3 = cy - r * 0.32f;
            check.moveTo(x1, y1);
            check.lineTo(x1 + (x2 - x1) * Math.min(1f, checkProgress * 2f), y1 + (y2 - y1) * Math.min(1f, checkProgress * 2f));
            if (checkProgress > 0.5f) {
                float p2 = (checkProgress - 0.5f) * 2f;
                check.moveTo(x2, y2);
                check.lineTo(x2 + (x3 - x2) * p2, y2 + (y3 - y2) * p2);
            }
            stroke.setColor(WHITE);
            stroke.setStrokeWidth(5f * s);
            c.drawPath(check, stroke);
        }
        stroke.setStyle(Paint.Style.FILL);
        stroke.setStrokeCap(Paint.Cap.BUTT);

        // текст
        float textAlpha = Math.min(1f, Math.max(0f, (elapsed - 500f) / 400f));
        int alpha = (int) (255 * textAlpha);
        txt.setTypeface(tfMedium);
        txt.setTextSize(19f * s);
        txt.setColor((alpha << 24) | 0x00F3F8FF);
        txt.setTextAlign(Paint.Align.CENTER);
        c.drawText("Всё собрано!", cx, cy + r + 42f * s, txt);

        txt.setTypeface(tfRegular);
        txt.setTextSize(12.5f * s);
        txt.setColor((alpha << 24) | 0x0093A9C9);
        c.drawText("Можно ложиться спать", cx, cy + r + 64f * s, txt);

        // салют
        drawParticles(c);
    }

    private void drawParticles(Canvas c) {
        long elapsed = SystemClock.elapsedRealtime() - successStart;
        if (elapsed > 6000) return;
        fill.setStyle(Paint.Style.FILL);
        for (Particle p : particles) {
            float t = Math.max(0f, elapsed - p.startMs) / 1000f;
            if (t <= 0f || t > p.life) continue;
            float x = p.x + p.vx * t;
            float y = p.y + p.vy * t + 0.5f * 420f * t * t;
            float alpha = 1f - t / p.life;
            int a = (int) (alpha * 255 * p.alpha);
            fill.setColor((a << 24) | (p.color & 0x00FFFFFF));
            c.drawCircle(x, y, p.size, fill);
        }
    }

    private static final class Particle {
        float x, y, vx, vy, size, life, alpha, startMs;
        int color;
    }

    private void spawnParticles() {
        int[] colors = { 0xFF34D399, 0xFF7DD3FC, 0xFFFFD166, 0xFFF472B6, 0xFFF3F8FF };
        long now = 0L;
        for (int burst = 0; burst < 4; burst++) {
            float bx = card.centerX() + (random.nextFloat() - 0.5f) * card.width() * 0.6f;
            float by = card.centerY() + (random.nextFloat() - 0.5f) * card.height() * 0.4f;
            for (int i = 0; i < 34; i++) {
                Particle p = new Particle();
                double angle = random.nextDouble() * Math.PI * 2.0;
                float speed = 60f + random.nextFloat() * 190f;
                p.x = bx;
                p.y = by;
                p.vx = (float) Math.cos(angle) * speed;
                p.vy = (float) Math.sin(angle) * speed;
                p.size = 1.6f + random.nextFloat() * 2.2f;
                p.life = 1.1f + random.nextFloat() * 0.9f;
                p.alpha = 0.75f + random.nextFloat() * 0.25f;
                p.startMs = 250f * burst;
                p.color = colors[random.nextInt(colors.length)];
                particles.add(p);
            }
        }
    }

    // ----------------------------------------------------------------- touch

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int w = getWidth();
        int h = getHeight();
        float x = e.getX();
        float y = e.getY();
        if (flipped) {
            x = w - x;
            y = h - y;
        }

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downY = y;
                downTime = SystemClock.elapsedRealtime();
                dragging = false;
                downOutside = !card.contains(x, y);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (downOutside) return true;
                if (Math.abs(y - downY) > 10f) dragging = true;
                if (dragging) {
                    int maxScroll = Math.max(0, contentHeight - visibleHeight);
                    if (maxScroll > 0) {
                        scrollY -= (int) ((y - downY));
                        if (scrollY < 0) scrollY = 0;
                        if (scrollY > maxScroll) scrollY = maxScroll;
                        downY = y;
                        invalidate();
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (success) {
                    // во время анимации успеха закрываем по тапу
                    if (listener != null) listener.onClosed();
                    return true;
                }
                if (downOutside) {
                    if (!card.contains(x, y) && listener != null) listener.onClosed();
                    return true;
                }
                if (dragging) return true;
                handleTap(x, y);
                return true;
            case MotionEvent.ACTION_CANCEL:
                return true;
            default:
                return super.onTouchEvent(e);
        }
    }

    private void handleTap(float x, float y) {
        if (closeRect.contains(x, y)) {
            if (listener != null) listener.onClosed();
            return;
        }
        long pressMs = SystemClock.elapsedRealtime() - downTime;
        if (model != null) {
            List<PackingItem> items = model.items();
            for (int i = 0; i < rowRects.size() && i < items.size(); i++) {
                if (rowRects.get(i).contains(x, y)) {
                    PackingItem item = items.get(i);
                    if (pressMs > 650 && item.custom) {
                        model.removeCustom(item.label);
                        android.widget.Toast.makeText(getContext(),
                                "Удалено: " + item.label, android.widget.Toast.LENGTH_SHORT).show();
                        invalidate();
                        return;
                    }
                    boolean all = model.toggle(item.id);
                    invalidate();
                    if (all) showSuccess();
                    return;
                }
            }
        }
        if (addRect.contains(x, y)) {
            if (listener != null) listener.onAddCustomRequested();
        }
    }

    // ---------------------------------------------------------------- helpers

    private void drawText(Canvas c, String str, float x, float y, float size, int color,
                          Typeface tf, Paint.Align align) {
        txt.setTypeface(tf);
        txt.setTextSize(size);
        txt.setColor(color);
        txt.setTextAlign(align);
        c.drawText(str, x, y, txt);
    }

    private String[] wrap(String text, Paint paint, float maxW, int maxLines) {
        if (text == null || text.length() == 0) return new String[] { "" };
        String[] words = text.split(" ");
        List<String> lines = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        for (String word : words) {
            if (word.length() == 0) continue;
            String candidate = cur.length() == 0 ? word : cur.toString() + " " + word;
            if (paint.measureText(candidate) <= maxW) {
                cur.setLength(0);
                cur.append(candidate);
            } else {
                if (cur.length() > 0) lines.add(cur.toString());
                if (lines.size() >= maxLines) {
                    lines.set(lines.size() - 1, ellipsize(lines.get(lines.size() - 1), paint, maxW));
                    return lines.toArray(new String[0]);
                }
                cur.setLength(0);
                cur.append(word);
            }
        }
        if (cur.length() > 0) lines.add(cur.toString());
        if (lines.isEmpty()) lines.add("");
        return lines.toArray(new String[0]);
    }

    private String ellipsize(String s, Paint paint, float maxW) {
        if (paint.measureText(s) <= maxW) return s;
        String ell = "…";
        int lo = 0;
        int hi = s.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (paint.measureText(s.substring(0, mid)) + paint.measureText(ell) <= maxW) lo = mid;
            else hi = mid - 1;
        }
        return s.substring(0, lo) + ell;
    }
}
