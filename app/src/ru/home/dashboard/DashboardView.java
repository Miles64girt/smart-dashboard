package ru.home.dashboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Вся панель рисуется одним View на Canvas: две вертикальные половины 50/50,
 * без XML-разметки, картинок и анимаций — минимум памяти и CPU для 1 ГБ ОЗУ.
 */
public class DashboardView extends View {

    private static final int BG_TOP = 0xFF081222;
    private static final int BG_BOTTOM = 0xFF112039;
    private static final int WHITE = 0xFFF3F8FF;
    private static final int MUTED = 0xFF9AAFCB;
    private static final int DIM = 0xFF6B82A6;
    private static final int ACCENT = 0xFF7DD3FC;
    private static final int SUN_COLOR = 0xFFFFD166;
    private static final int CLOUD_COLOR = 0xFFC9D6EA;
    private static final int CARD_BG = 0xCC14243D;
    private static final int CARD_STROKE = 0x1AFFFFFF;
    private static final int WARN_COLOR = 0xFFFFB86B;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private final Typeface tfLight = Typeface.create("sans-serif-light", Typeface.NORMAL);
    private final Typeface tfRegular = Typeface.create("sans-serif", Typeface.NORMAL);
    private final Typeface tfMedium = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    private final Locale RU = new Locale("ru", "RU");
    private final SimpleDateFormat dateFmt = new SimpleDateFormat("d MMMM, EEEE", RU);
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", RU);

    private LinearGradient gradient;
    private int lastW = -1, lastH = -1;

    private WeatherData weather;
    private List<EventItem> events = new ArrayList<EventItem>();
    private boolean calendarLoaded = false;
    private boolean night = false;
    private boolean offline = false;
    private boolean flipped = false;

    public interface Listener {
        void onListRequested();
        void onPowerRequested();
    }

    private Listener listener;
    private final RectF listBtn = new RectF();
    private final RectF powerBtn = new RectF();
    private String city = "";
    private long now = System.currentTimeMillis();
    private long weatherTime = 0L;

    public DashboardView(Context context) {
        super(context);
        dateFmt.setTimeZone(TimeUtil.ZONE);
        timeFmt.setTimeZone(TimeUtil.ZONE);
        setBackgroundColor(BG_TOP);
        setClickable(true);
    }

    public void setWeather(WeatherData w, long when) {
        this.weather = w;
        this.weatherTime = when > 0 ? when : (w != null ? w.fetchedAt : 0L);
        this.offline = false;
        invalidate();
    }

    public void setOffline(boolean v) {
        this.offline = v;
        invalidate();
    }

    public void setEvents(List<EventItem> list, boolean loaded) {
        this.events = list != null ? list : new ArrayList<EventItem>();
        this.calendarLoaded = loaded;
        android.util.Log.i("SmartDashboard", "setEvents count=" + this.events.size() + " loaded=" + loaded);
        invalidate();
    }

    public void setNight(boolean v) {
        this.night = v;
        invalidate();
    }

    /** Переворот отрисовки на 180° (планшет закреплён вверх ногами). */
    public void setFlipped(boolean v) {
        this.flipped = v;
        invalidate();
    }

    public boolean isFlipped() {
        return flipped;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Снимок панели без переворота (для размытого фона вечернего окна). */
    public void drawSnapshot(Canvas canvas) {
        boolean old = flipped;
        flipped = false;
        super.draw(canvas);
        flipped = old;
    }

    public void setCity(String c) {
        this.city = c;
        invalidate();
    }

    public void setNow(long t) {
        this.now = t;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        lastW = w;
        lastH = h;
        gradient = new LinearGradient(0f, 0f, 0f, (float) h, BG_TOP, BG_BOTTOM, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        float s = Math.min(w / DeviceConfig.DESIGN_WIDTH, h / DeviceConfig.DESIGN_HEIGHT);

        if (gradient == null || lastW != w || lastH != h) {
            gradient = new LinearGradient(0f, 0f, 0f, (float) h, BG_TOP, BG_BOTTOM, Shader.TileMode.CLAMP);
            lastW = w;
            lastH = h;
        }

        if (flipped) {
            c.save();
            c.rotate(180f, w / 2f, h / 2f);
        }

        if (night) {
            fill.setShader(null);
            fill.setColor(0xFF05070C);
            c.drawRect(0f, 0f, (float) w, (float) h, fill);
        } else {
            fill.setShader(gradient);
            c.drawRect(0f, 0f, (float) w, (float) h, fill);
            fill.setShader(null);
        }

        drawDivider(c, s, w, h);
        drawLeft(c, s, w, h);
        drawRight(c, s, w, h);
        if (!night) drawCornerButtons(c, s, w, h);

        if (night) {
            fill.setShader(null);
            fill.setColor(0xE805070C);
            c.drawRect(0f, 0f, (float) w, (float) h, fill);
            drawText(c, "касание — включить экран", w / 2f, h - 26f * s, 11f * s, 0x30FFFFFF, tfRegular, Paint.Align.CENTER);
        }

        if (flipped) {
            c.restore();
        }
    }

    private void drawDivider(Canvas c, float s, int w, int h) {
        line.setColor(0x14FFFFFF);
        line.setStrokeWidth(1f * s);
        float x = w / 2f;
        c.drawLine(x, 46f * s, x, h - 46f * s, line);
    }

    // ------------------------------------------------------------------ LEFT

    private void drawLeft(Canvas c, float s, int w, int h) {
        float px = 24f * s;
        float panelRight = w / 2f - 24f * s;
        float half = (panelRight - px) / 2f;

        drawText(c, dateFmt.format(new Date(now)), px, 60f * s, 15f * s, MUTED, tfRegular, Paint.Align.LEFT);
        drawText(c, city, panelRight, 60f * s, 12f * s, DIM, tfRegular, Paint.Align.RIGHT);

        if (weather == null) {
            drawText(c, "нет данных", px - 2f * s, 196f * s, 40f * s, WHITE, tfLight, Paint.Align.LEFT);
            drawText(c, "Ожидание ответа wttr.in", px, 238f * s, 15f * s, DIM, tfRegular, Paint.Align.LEFT);
            drawFooter(c, s, px, panelRight, h);
            return;
        }

        drawText(c, fmtTemp(weather.temp), px - 4f * s, 202f * s, 104f * s, WHITE, tfLight, Paint.Align.LEFT);
        drawText(c, "ощущается " + fmtTemp(weather.feels), px, 240f * s, 15f * s, MUTED, tfRegular, Paint.Align.LEFT);
        drawText(c, weather.condition(), px, 286f * s, 20f * s, WHITE, tfMedium, Paint.Align.LEFT);
        drawText(c, "мин " + fmtTemp(weather.tmin) + "   макс " + fmtTemp(weather.tmax),
                px, 318f * s, 13.5f * s, DIM, tfRegular, Paint.Align.LEFT);

        drawWeatherIcon(c, s, weather, w / 2f - 72f * s, 148f * s, 42f * s);

        // Детали погоды
        float y1 = 372f * s;
        float y2 = 416f * s;
        float y3 = 460f * s;
        detail(c, s, "ВЕТЕР", windText(weather.windMs), px, y1);
        detail(c, s, "ВЛАЖНОСТЬ", Math.round(weather.humidity) + " %", px + half, y1);
        detail(c, s, "ОСАДКИ", Math.round(weather.precipProb) + " %", px, y2);
        detail(c, s, "ВОСХОД", weather.sunrise, px + half, y2);
        detail(c, s, "ЗАКАТ", weather.sunset, px, y3);
        detail(c, s, "СВЕТЛОЙ ЧАСТИ", dayLength(weather), px + half, y3);

        drawClothing(c, s, px, panelRight);
        drawFooter(c, s, px, panelRight, h);
    }

    private void detail(Canvas c, float s, String label, String value, float x, float y) {
        drawText(c, label, x, y - 13f * s, 9.5f * s, DIM, tfMedium, Paint.Align.LEFT);
        drawText(c, value, x, y + 3f * s, 15f * s, WHITE, tfRegular, Paint.Align.LEFT);
    }

    private void drawClothing(Canvas c, float s, float px, float panelRight) {
        float top = 500f * s;
        float bottom = 906f * s;
        rect.set(px, top, panelRight, bottom);
        fill.setShader(null);
        fill.setColor(CARD_BG);
        c.drawRoundRect(rect, 16f * s, 16f * s, fill);
        line.setColor(CARD_STROKE);
        line.setStrokeWidth(1f * s);
        line.setStyle(Paint.Style.STROKE);
        c.drawRoundRect(rect, 16f * s, 16f * s, line);
        line.setStyle(Paint.Style.FILL);

        drawText(c, "ЧТО НАДЕТЬ СЕГОДНЯ", px + 18f * s, top + 30f * s, 10.5f * s, MUTED, tfMedium, Paint.Align.LEFT);

        String[] values = weather != null ? weather.clothing()
                : new String[] { "—", "—", "—", "—", "—" };
        String[] labels = { "ОДЕЖДА", "ВЕРХ", "ЗОНТ", "ОБУВЬ", "АКСЕССУАРЫ" };

        float rowY = top + 56f * s;
        float step = 68f * s;
        float textW = panelRight - px - 36f * s;
        for (int i = 0; i < labels.length; i++) {
            drawText(c, labels[i], px + 18f * s, rowY, 9f * s, DIM, tfMedium, Paint.Align.LEFT);
            txt.setTypeface(tfRegular);
            txt.setTextSize(12.5f * s);
            txt.setColor(WHITE);
            txt.setTextAlign(Paint.Align.LEFT);
            String[] lines = wrap(values[i], txt, textW, 2);
            for (int k = 0; k < lines.length; k++) {
                c.drawText(lines[k], px + 18f * s, rowY + (16f + k * 15f) * s, txt);
            }
            rowY += step;
        }
    }

    private void drawFooter(Canvas c, float s, float px, float panelRight, int h) {
        String footer;
        int color;
        if (offline && weatherTime > 0) {
            footer = "Нет сети · данные " + timeFmt.format(new Date(weatherTime));
            color = WARN_COLOR;
        } else if (weather == null) {
            footer = "wttr.in · ожидание";
            color = DIM;
        } else {
            long t = weatherTime > 0 ? weatherTime : now;
            footer = "wttr.in · обновлено " + timeFmt.format(new Date(t));
            color = DIM;
        }
        drawText(c, footer, px, h - 30f * s, 9.5f * s, color, tfRegular, Paint.Align.LEFT);
        findTextWidth(footer, 9.5f * s);
    }

    // ----------------------------------------------------------------- RIGHT

    private void drawRight(Canvas c, float s, int w, int h) {
        if (!calendarLoaded || events.isEmpty()) return;

        float x0 = w / 2f + 24f * s;
        float x1 = (float) w - 24f * s;
        drawText(c, "СЕГОДНЯ", x0, 60f * s, 10.5f * s, MUTED, tfMedium, Paint.Align.LEFT);

        float top = 78f * s;
        float bottom = h - 24f * s;
        int n = events.size();
        float gap = 8f * s;
        float avail = bottom - top;
        float cardH = (avail - gap * (n - 1)) / n;
        float maxCard = (n <= 3 ? 230f : 190f) * s;
        if (cardH > maxCard) cardH = maxCard;

        float fs = cardH / (120f * s);
        if (fs > 1.0f) fs = 1.0f;
        if (fs < 0.66f) fs = 0.66f;

        for (int i = 0; i < n; i++) {
            EventItem e = events.get(i);
            float y = top + i * (cardH + gap);
            drawEventCard(c, s, e, fs, x0, y, x1, y + cardH);
        }
    }

    private void drawEventCard(Canvas c, float s, EventItem e, float fs,
                               float left, float top, float right, float bottom) {
        float cardH = bottom - top;

        rect.set(left, top, right, bottom);
        fill.setShader(null);
        fill.setColor(CARD_BG);
        c.drawRoundRect(rect, 12f * s, 12f * s, fill);
        line.setColor(CARD_STROKE);
        line.setStrokeWidth(1f * s);
        line.setStyle(Paint.Style.STROKE);
        c.drawRoundRect(rect, 12f * s, 12f * s, line);
        line.setStyle(Paint.Style.FILL);

        // Акцентная полоса слева
        fill.setColor(ACCENT);
        rect.set(left + 9f * s, top + 12f * s, left + 12f * s, bottom - 12f * s);
        c.drawRoundRect(rect, 2f * s, 2f * s, fill);

        float cy = top + cardH / 2f;
        float timeX = left + 22f * s;

        if (e.allDay) {
            txt.setTypeface(tfMedium);
            txt.setTextSize(9f * fs * s);
            txt.setColor(MUTED);
            txt.setTextAlign(Paint.Align.LEFT);
            c.drawText("ВЕСЬ", timeX, cy - 2f * s, txt);
            c.drawText("ДЕНЬ", timeX, cy + 11f * fs * s, txt);
        } else {
            txt.setTypeface(tfMedium);
            txt.setTextSize(14f * fs * s);
            txt.setColor(WHITE);
            txt.setTextAlign(Paint.Align.LEFT);
            c.drawText(timeFmt.format(new Date(e.begin)), timeX, cy - 1f * s, txt);

            txt.setTypeface(tfRegular);
            txt.setTextSize(10f * fs * s);
            txt.setColor(DIM);
            c.drawText("– " + timeFmt.format(new Date(e.end)), timeX, cy + 15f * fs * s, txt);
        }

        float titleX = left + 86f * s;
        float titleW = right - 14f * s - titleX;
        txt.setTypeface(tfMedium);
        txt.setTextSize(16f * fs * s);
        txt.setColor(WHITE);
        txt.setTextAlign(Paint.Align.LEFT);

        int maxLines = cardH > 104f * s ? 2 : 1;
        String[] titleLines = wrap(e.title, txt, titleW, maxLines);
        float lineH = 19f * fs * s;

        boolean showDesc = cardH >= 96f * s && e.description != null && e.description.length() > 0;
        float blockH = titleLines.length * lineH + (showDesc ? 16f * fs * s : 0f);
        float contentTop = cy - blockH / 2f + 13f * fs * s;

        for (int i = 0; i < titleLines.length; i++) {
            c.drawText(titleLines[i], titleX, contentTop + i * lineH, txt);
        }

        if (showDesc) {
            txt.setTypeface(tfRegular);
            txt.setTextSize(10.5f * fs * s);
            txt.setColor(MUTED);
            String d = ellipsize(e.description, txt, titleW);
            c.drawText(d, titleX, contentTop + titleLines.length * lineH + 4f * fs * s, txt);
        }
    }

    // ----------------------------------------------------------------- ICON

    // ------------------------------------------------- кнопки в правом нижнем углу

    private void drawCornerButtons(Canvas c, float s, int w, int h) {
        float r = 25f * s;
        float margin = 20f * s;
        float powerX = w - margin - r;
        float powerY = h - margin - r;
        float listX = powerX - 2f * r - 12f * s;
        float listY = powerY;
        powerBtn.set(powerX - r, powerY - r, powerX + r, powerY + r);
        listBtn.set(listX - r, listY - r, listX + r, listY + r);
        drawCircleButton(c, s, listX, listY, r, false);
        drawCircleButton(c, s, powerX, powerY, r, true);
    }

    private void drawCircleButton(Canvas c, float s, float cx, float cy, float r, boolean power) {
        fill.setShader(null);
        fill.setColor(0x1FFFFFFF);
        c.drawCircle(cx, cy, r, fill);
        stroke.setColor(0x40FFFFFF);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(1.2f * s);
        c.drawCircle(cx, cy, r, stroke);
        stroke.setStyle(Paint.Style.FILL);

        stroke.setColor(0xFF9AAFCB);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2.2f * s);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        if (power) {
            RectF arc = new RectF(cx - r * 0.42f, cy - r * 0.42f, cx + r * 0.42f, cy + r * 0.42f);
            c.drawArc(arc, -215f, 250f, false, stroke);
            c.drawLine(cx, cy - r * 0.55f, cx, cy - r * 0.10f, stroke);
        } else {
            for (int i = -1; i <= 1; i++) {
                float y = cy + i * r * 0.22f;
                c.drawLine(cx - r * 0.38f, y, cx + r * 0.38f, y, stroke);
            }
        }
        stroke.setStrokeCap(Paint.Cap.BUTT);
        stroke.setStyle(Paint.Style.FILL);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (listener == null) return super.onTouchEvent(event);
        int w = getWidth();
        int h = getHeight();
        float x = event.getX();
        float y = event.getY();
        if (flipped) {
            x = w - x;
            y = h - y;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            if (listBtn.contains(x, y)) {
                listener.onListRequested();
                return true;
            }
            if (powerBtn.contains(x, y)) {
                listener.onPowerRequested();
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    private void drawWeatherIcon(Canvas c, float s, WeatherData w, float cx, float cy, float r) {
        int code = w.code;
        if (code == 0 || code == 1) {
            drawSun(c, cx, cy, r, w.isDay ? SUN_COLOR : 0xFFB9C7DE);
        } else if (code == 2) {
            drawSun(c, cx - r * 0.34f, cy - r * 0.24f, r * 0.62f, SUN_COLOR);
            drawCloud(c, cx + r * 0.10f, cy + r * 0.16f, r * 0.92f, CLOUD_COLOR);
        } else if (code == 3) {
            drawCloud(c, cx, cy, r, CLOUD_COLOR);
        } else if (code == 45 || code == 48) {
            drawCloud(c, cx, cy - r * 0.10f, r * 0.86f, CLOUD_COLOR);
            drawFog(c, cx, cy + r * 0.34f, r);
        } else if (w.isSnow()) {
            drawCloud(c, cx, cy - r * 0.12f, r * 0.90f, CLOUD_COLOR);
            drawSnow(c, cx, cy + r * 0.36f, r);
        } else if (code == 95 || code == 96 || code == 99) {
            drawCloud(c, cx, cy - r * 0.12f, r * 0.90f, CLOUD_COLOR);
            drawBolt(c, cx, cy + r * 0.34f, r);
        } else {
            drawCloud(c, cx, cy - r * 0.12f, r * 0.90f, CLOUD_COLOR);
            drawDrops(c, cx, cy + r * 0.34f, r);
        }
    }

    private void drawSun(Canvas c, float cx, float cy, float r, int color) {
        fill.setShader(null);
        fill.setColor(color);
        c.drawCircle(cx, cy, r * 0.42f, fill);
        line.setColor(color);
        line.setStrokeWidth(r * 0.075f);
        line.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * i / 4.0;
            float x1 = cx + (float) Math.cos(a) * r * 0.58f;
            float y1 = cy + (float) Math.sin(a) * r * 0.58f;
            float x2 = cx + (float) Math.cos(a) * r * 0.78f;
            float y2 = cy + (float) Math.sin(a) * r * 0.78f;
            c.drawLine(x1, y1, x2, y2, line);
        }
        line.setStrokeCap(Paint.Cap.BUTT);
    }

    private void drawCloud(Canvas c, float cx, float cy, float r, int color) {
        fill.setShader(null);
        fill.setColor(color);
        c.drawCircle(cx - r * 0.34f, cy, r * 0.30f, fill);
        c.drawCircle(cx + r * 0.02f, cy - r * 0.16f, r * 0.36f, fill);
        c.drawCircle(cx + r * 0.36f, cy, r * 0.28f, fill);
        rect.set(cx - r * 0.36f, cy, cx + r * 0.36f, cy + r * 0.26f);
        c.drawRoundRect(rect, r * 0.13f, r * 0.13f, fill);
    }

    private void drawDrops(Canvas c, float cx, float cy, float r) {
        line.setColor(0xFF7DD3FC);
        line.setStrokeWidth(r * 0.07f);
        line.setStrokeCap(Paint.Cap.ROUND);
        for (int i = -1; i <= 1; i++) {
            float x = cx + i * r * 0.26f;
            c.drawLine(x, cy, x - r * 0.04f, cy + r * 0.22f, line);
        }
        line.setStrokeCap(Paint.Cap.BUTT);
    }

    private void drawSnow(Canvas c, float cx, float cy, float r) {
        line.setColor(0xFFE8F1FF);
        line.setStrokeWidth(r * 0.06f);
        line.setStrokeCap(Paint.Cap.ROUND);
        for (int i = -1; i <= 1; i++) {
            float x = cx + i * r * 0.26f;
            c.drawLine(x - r * 0.05f, cy + r * 0.08f, x + r * 0.05f, cy + r * 0.08f, line);
            c.drawLine(x, cy + r * 0.03f, x, cy + r * 0.13f, line);
            c.drawLine(x - r * 0.04f, cy + r * 0.04f, x + r * 0.04f, cy + r * 0.12f, line);
            c.drawLine(x + r * 0.04f, cy + r * 0.04f, x - r * 0.04f, cy + r * 0.12f, line);
        }
        line.setStrokeCap(Paint.Cap.BUTT);
    }

    private void drawFog(Canvas c, float cx, float cy, float r) {
        line.setColor(0x99C9D6EA);
        line.setStrokeWidth(r * 0.055f);
        line.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 3; i++) {
            float y = cy + i * r * 0.13f;
            c.drawLine(cx - r * 0.34f, y, cx + r * 0.34f, y, line);
        }
        line.setStrokeCap(Paint.Cap.BUTT);
    }

    private void drawBolt(Canvas c, float cx, float cy, float r) {
        Path p = new Path();
        p.moveTo(cx + r * 0.04f, cy - r * 0.02f);
        p.lineTo(cx - r * 0.12f, cy + r * 0.20f);
        p.lineTo(cx + r * 0.02f, cy + r * 0.20f);
        p.lineTo(cx - r * 0.06f, cy + r * 0.40f);
        p.lineTo(cx + r * 0.16f, cy + r * 0.12f);
        p.lineTo(cx + r * 0.02f, cy + r * 0.12f);
        p.close();
        fill.setShader(null);
        fill.setColor(SUN_COLOR);
        c.drawPath(p, fill);
    }

    // --------------------------------------------------------------- helpers

    private void drawText(Canvas c, String str, float x, float y, float size, int color,
                          Typeface tf, Paint.Align align) {
        txt.setShader(null);
        txt.setTypeface(tf);
        txt.setTextSize(size);
        txt.setColor(color);
        txt.setTextAlign(align);
        c.drawText(str, x, y, txt);
    }

    private void findTextWidth(String s, float size) {
        // no-op: сохраняет единый стиль вызовов
    }

    private static String fmtTemp(double v) {
        long r = Math.round(v);
        return r + "°";
    }

    private static String windText(double ms) {
        if (ms < 0.05) return "штиль";
        return String.format(new Locale("ru", "RU"), "%.1f м/с", ms);
    }

    private static String dayLength(WeatherData w) {
        int sr = minutes(w.sunrise);
        int ss = minutes(w.sunset);
        if (sr < 0 || ss < 0) return "—";
        int diff = ss - sr;
        if (diff < 0) diff += 24 * 60;
        return (diff / 60) + " ч " + (diff % 60) + " мин";
    }

    private static int minutes(String hhmm) {
        try {
            int idx = hhmm.indexOf(':');
            if (idx <= 0) return -1;
            int h = Integer.parseInt(hhmm.substring(0, idx));
            int m = Integer.parseInt(hhmm.substring(idx + 1, Math.min(idx + 3, hhmm.length())));
            return h * 60 + m;
        } catch (Exception e) {
            return -1;
        }
    }

    private String ellipsize(String s, Paint paint, float maxW) {
        if (s == null) return "";
        if (paint.measureText(s) <= maxW) return s;
        String ell = "…";
        float ew = paint.measureText(ell);
        int lo = 0;
        int hi = s.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (paint.measureText(s.substring(0, mid)) + ew <= maxW) lo = mid;
            else hi = mid - 1;
        }
        return s.substring(0, lo) + ell;
    }

    /** Разбивает текст на строки по ширине; при переполнении обрезает последнюю строку «…». */
    private String[] wrap(String text, Paint paint, float maxW, int maxLines) {
        if (text == null || text.length() == 0) return new String[] { "" };
        String[] words = text.split(" ");
        List<String> lines = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            String wd = words[i];
            if (wd.length() == 0) continue;
            String candidate = cur.length() == 0 ? wd : cur.toString() + " " + wd;
            if (paint.measureText(candidate) <= maxW) {
                cur.setLength(0);
                cur.append(candidate);
            } else {
                if (cur.length() > 0) {
                    lines.add(cur.toString());
                    cur.setLength(0);
                }
                if (lines.size() >= maxLines) {
                    // переполнение: обрезаем последнюю строку
                    String last = lines.get(lines.size() - 1);
                    String rest = joinFrom(words, i);
                    lines.set(lines.size() - 1, ellipsize(last + " " + rest, paint, maxW));
                    cur.setLength(0);
                    break;
                }
                cur.append(wd);
            }
        }
        if (cur.length() > 0 && lines.size() < maxLines) {
            lines.add(cur.toString());
        } else if (cur.length() > 0 && lines.size() == maxLines) {
            String last = lines.get(lines.size() - 1);
            lines.set(lines.size() - 1, ellipsize(last + " " + cur, paint, maxW));
        }
        if (lines.isEmpty()) lines.add("");
        return lines.toArray(new String[0]);
    }

    private static String joinFrom(String[] words, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < words.length; i++) {
            if (words[i].length() == 0) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(words[i]);
        }
        return sb.toString();
    }
}
