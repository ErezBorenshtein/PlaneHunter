package com.example.planehunter.ui.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.example.planehunter.model.Plane;
import com.example.planehunter.R;
import com.example.planehunter.util.RadarMath;

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
    private final Paint paintPulse = new Paint(Paint.ANTI_ALIAS_FLAG);

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

    private Plane selectedPlane = null;
    private long pulseStartTime = 0;

    public RadarView(Context context) { super(context); init(); }
    public RadarView(Context context, @Nullable AttributeSet attrs) { super(context, attrs); init(); }
    public RadarView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    private void init() {
        paintGrid.setStyle(Paint.Style.STROKE);
        paintGrid.setStrokeWidth(3f);
        paintGrid.setARGB(255, 0, 180, 0);

        paintMe.setStyle(Paint.Style.FILL);
        paintMe.setARGB(255, 0, 255, 0);

        // Selected-plane pulse ring (stroke only)
        paintPulse.setStyle(Paint.Style.STROKE);
        paintPulse.setStrokeWidth(4f);
        paintPulse.setARGB(255, 255, 255, 0); // Yellow, alpha will be animated

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
            drawSelectedPlanePulse(canvas);
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

        if (hit == null) {
            setSelectedPlane(null); // clear selection
            return true;
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

    public void setSelectedPlane(@Nullable Plane plane) {
        selectedPlane = plane;
        pulseStartTime = System.currentTimeMillis();
        invalidate();
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

    private void drawSelectedPlanePulse(Canvas canvas) {

        if (selectedPlane == null) return;

        // Find the selected plane's current screen position from the built blips list.
        // We do NOT recompute geometry here; we reuse the x/y already calculated in rebuildBlips().
        Blip sb = null;
        synchronized (lock) {
            for (Blip b : blips) {
                if (b == null || b.plane == null) continue;

                // Reference match is usually enough because planes are from the same list object.
                // Fallback: match by ICAO24 in case you rebuild Plane objects elsewhere.
                if (b.plane == selectedPlane) {
                    sb = b;
                    break;
                }
                if (b.plane.getIcao24() != null && selectedPlane.getIcao24() != null
                        && b.plane.getIcao24().equals(selectedPlane.getIcao24())) {
                    sb = b;
                    break;
                }
            }
        }

        if (sb == null) return;

        long now = System.currentTimeMillis();
        long t = now - pulseStartTime;

        // Pulse period in ms (1.2 seconds). Phase goes 0..1.
        final float periodMs = 1200f;
        float phase = (t % (long) periodMs) / periodMs;

        // Animate radius and alpha: ring expands and fades out.
        float baseR = Math.max(18f, sb.hitR);
        float radius = baseR + phase * (baseR * 1.2f);   // grows to ~2.2x
        int alpha = (int) (255 * (1f - phase));

        paintPulse.setAlpha(alpha);

        canvas.drawCircle(sb.x, sb.y, radius, paintPulse);

        // Keep the animation running while a plane is selected.
        postInvalidateOnAnimation();
    }

    // -------- Bitmap utils --------

    private Bitmap scalePlaneBitmap(Bitmap src, int targetPx) {
        if (src == null) return null;
        int t = Math.max(16, targetPx);
        return Bitmap.createScaledBitmap(src, t, t, true);
    }
}
