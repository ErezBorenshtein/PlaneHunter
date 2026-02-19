package com.example.planehunter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class RadarView extends View {

    public interface OnPlaneClickListener {
        void onPlaneClicked(Plane plane);
    }

    private static class Blip {
        Plane plane;
        float x;
        float y;
        float hitR;     // hit radius in px
        float rotDeg;   // rotation degrees for drawing
    }

    private static final float ICON_HEADING_OFFSET_DEG = -90f; //the default heading is right(-90 degs)

    private final Paint paintGrid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintMe = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Object lock = new Object();
    private final List<Blip> blips = new ArrayList<>();
    private ArrayList<Plane> lastPlanes = new ArrayList<>();

    private double userLat = 0.0;
    private double userLon = 0.0;

    private double radarRangeMeters = 4000.0; // default 4km
    private float meRadiusPx = 14f;

    private Bitmap planeBmpOriginal;
    private Bitmap planeBmpScaled;
    private int planeIconSizePx = 56;

    private OnPlaneClickListener listener;

    public RadarView(Context context) { super(context); init(); }
    public RadarView(Context context, @Nullable AttributeSet attrs) { super(context, attrs); init(); }
    public RadarView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    private void init() {
        paintGrid.setStyle(Paint.Style.STROKE);
        paintGrid.setStrokeWidth(3f);
        paintGrid.setARGB(255, 0, 180, 0);

        paintMe.setStyle(Paint.Style.FILL);
        paintMe.setARGB(255, 0, 255, 0);

        planeBmpOriginal = BitmapFactory.decodeResource(getResources(), R.drawable.air_plan);
        planeBmpScaled = scalePlaneBitmap(planeBmpOriginal, planeIconSizePx);
    }

    // -------- Public API --------

    public void setOnPlaneClickListener(OnPlaneClickListener l) {
        this.listener = l;
    }

    public void setUserLocation(double lat, double lon) {
        this.userLat = lat;
        this.userLon = lon;
        rebuildBlips();
        invalidate();
    }

    public void setPlanes(ArrayList<Plane> planes) {
        if (planes == null) planes = new ArrayList<>();
        lastPlanes = new ArrayList<>(planes);
        rebuildBlips(planes);
        invalidate();
    }

    public void setRadarRangeMeters(double meters) {
        this.radarRangeMeters = Math.max(100.0, meters);
        rebuildBlips();
        invalidate();
    }

    public void setPlaneIconSizePx(int px) {
        this.planeIconSizePx = Math.max(16, px);
        planeBmpScaled = scalePlaneBitmap(planeBmpOriginal, planeIconSizePx);
        rebuildBlips();
        invalidate();
    }

    // -------- Drawing --------

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;
        float radius = Math.min(w, h) * 0.45f;

        // Radar rings
        canvas.drawCircle(cx, cy, radius, paintGrid);
        canvas.drawCircle(cx, cy, radius * 0.66f, paintGrid);
        canvas.drawCircle(cx, cy, radius * 0.33f, paintGrid);

        // Cross
        canvas.drawLine(cx - radius, cy, cx + radius, cy, paintGrid);
        canvas.drawLine(cx, cy - radius, cx, cy + radius, paintGrid);

        // Me in center
        canvas.drawCircle(cx, cy, meRadiusPx, paintMe);

        Bitmap bmp = planeBmpScaled;
        if (bmp == null) return;

        float half = bmp.getWidth() / 2f;

        synchronized (lock) {
            for (Blip b : blips) {
                canvas.save();
                canvas.translate(b.x, b.y);
                canvas.rotate(b.rotDeg);
                canvas.drawBitmap(bmp, -half, -half, null);
                canvas.restore();
            }
        }
    }

    // -------- Touch --------

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_DOWN) return true;

        float tx = event.getX();
        float ty = event.getY();

        Plane hit = findHitPlane(tx, ty);
        if (hit != null && listener != null) {
            listener.onPlaneClicked(hit);
        }
        return true;
    }

    private Plane findHitPlane(float x, float y) {
        synchronized (lock) {
            for (Blip b : blips) {
                float dx = x - b.x;
                float dy = y - b.y;
                float dist2 = dx * dx + dy * dy;
                float r = b.hitR;
                if (dist2 <= r * r) return b.plane;
            }
        }
        return null;
    }

    // -------- Blip building --------

    private void rebuildBlips() {
        rebuildBlips(lastPlanes);
    }

    private void rebuildBlips(ArrayList<Plane> planes) {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        float cx = w / 2f;
        float cy = h / 2f;
        float radiusPx = Math.min(w, h) * 0.45f;

        // Can't position planes without user location
        if (userLat == 0.0 && userLon == 0.0) {
            synchronized (lock) { blips.clear(); }
            return;
        }

        ArrayList<Blip> newBlips = new ArrayList<>();

        for (Plane p : planes) {
            if (p == null) continue;

            double distM = RadarMath.haversineMeters(userLat, userLon, p.getLat(), p.getLon());
            if (distM > radarRangeMeters) continue;

            double bearing = RadarMath.bearingDeg(userLat, userLon, p.getLat(), p.getLon());

            // bearing (0=N) -> canvas angle (0=+X): bearing - 90
            double ang = Math.toRadians(bearing - 90.0);

            float r = (float) (distM / radarRangeMeters * radiusPx);
            float x = cx + (float) (Math.cos(ang) * r);
            float y = cy + (float) (Math.sin(ang) * r);

            Blip b = new Blip();
            b.plane = p;
            b.x = x;
            b.y = y;

            // rotation by trackDeg (0=N) mapped to canvas, plus PNG heading offset
            double track = p.getTrackDeg();
            if (!Double.isNaN(track)) {
                b.rotDeg = (float) track + ICON_HEADING_OFFSET_DEG;
            } else {
                b.rotDeg = 0f; // unknown
            }

            int iconSize = (planeBmpScaled != null) ? planeBmpScaled.getWidth() : planeIconSizePx;
            b.hitR = Math.max(18f, iconSize * 0.65f);

            newBlips.add(b);
        }

        synchronized (lock) {
            blips.clear();
            blips.addAll(newBlips);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rebuildBlips();
    }

    // -------- Bitmap utils --------

    private Bitmap scalePlaneBitmap(Bitmap src, int targetPx) {
        if (src == null) return null;
        int t = Math.max(16, targetPx);
        return Bitmap.createScaledBitmap(src, t, t, true);
    }
}
