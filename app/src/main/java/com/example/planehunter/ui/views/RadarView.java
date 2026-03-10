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
import com.example.planehunter.util.UtilMath;

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
        float hitR; //hit radius in px
        float rotDeg; //rotation degrees for drawing
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

    private double radarRangeMeters = 30000.0; //default 30km
    private float meRadiusPx = 14f;

    private Bitmap planeOriginalBitmap;
    private Bitmap planeScaledBitmap;
    private int planeIconSizePx = 56;

    private OnPlaneClickListener listener;

    private Plane selectedPlane = null;
    private long pulseStartTime = 0;

    public RadarView(Context context) {
        super(context);
        init();
    }
    public RadarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    public RadarView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paintGrid.setStyle(Paint.Style.STROKE);
        paintGrid.setStrokeWidth(3f);
        paintGrid.setARGB(255, 0, 180, 0); //green

        paintMe.setStyle(Paint.Style.FILL);
        paintMe.setARGB(255, 0, 255, 0); //green


        paintPulse.setStyle(Paint.Style.STROKE);
        paintPulse.setStrokeWidth(4f);
        paintPulse.setARGB(255, 255, 255, 0); //yellow with alpha changing later

        planeOriginalBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.air_plan);
        planeScaledBitmap = scalePlaneBitmap(planeOriginalBitmap, planeIconSizePx);
    }

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
        if (planes == null){
            planes = new ArrayList<>();
        }

        lastPlanes = new ArrayList<>(planes);
        rebuildBlips(planes);
        invalidate();
    }

    public void setRadarRangeMeters(double meters) {
        this.radarRangeMeters = meters;
        rebuildBlips();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();
        float centerX = width / 2f;
        float centerY = height / 2f;
        float radius = Math.min(width, height) * 0.45f; //45% of the small side

        //radar rings
        canvas.drawCircle(centerX, centerY, radius, paintGrid);
        canvas.drawCircle(centerX, centerY, radius * 0.66f, paintGrid);
        canvas.drawCircle(centerX, centerY, radius * 0.33f, paintGrid);

        //cross
        canvas.drawLine(centerX - radius, centerY, centerX + radius, centerY, paintGrid);
        canvas.drawLine(centerX, centerY - radius, centerX, centerY + radius, paintGrid);

        //me in center
        canvas.drawCircle(centerX, centerY, meRadiusPx, paintMe);

        Bitmap bitmap = planeScaledBitmap;
        if (bitmap == null) return;

        float half = bitmap.getWidth() / 2f;

        synchronized (lock) {
            for (Blip blip : blips) {
                canvas.save();
                canvas.translate(blip.x, blip.y); //moves canvas center to plane center
                canvas.rotate(blip.rotDeg);
                canvas.drawBitmap(bitmap, -half, -half, null);
                canvas.restore();
            }
            drawSelectedPlanePulse(canvas);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_DOWN) return true;

        float touchX = event.getX();
        float touchY = event.getY();

        Plane hit = findHitPlane(touchX, touchY);

        if (hit == null) {
            setSelectedPlane(null); // clear selection
            return true;
        }

        setSelectedPlane(hit);

        if (listener != null) {
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

    public void setSelectedPlane(@Nullable Plane plane) {
        selectedPlane = plane;
        pulseStartTime = System.currentTimeMillis();
        invalidate();
    }
    

    private void rebuildBlips() {
        //when user location, view size or radar radius changes
        rebuildBlips(lastPlanes);
    }

    private void rebuildBlips(ArrayList<Plane> planes) {//when there are new planes

        float width = getWidth();
        float height = getHeight();
        if (width <= 0 || height <= 0) return; //if view wasn't initialized

        float cx = width / 2f;
        float cy = height / 2f;
        float radiusPx = Math.min(width, height) * 0.45f;

        //cant position planes without user location
        if (userLat == 0.0 && userLon == 0.0) {
            synchronized (lock) { blips.clear(); }
            return;
        }

        ArrayList<Blip> newBlips = new ArrayList<>();

        //doing it in because I need to write the blimps in synchronization
        for (Plane p : planes) {
            if (p == null) continue;

            double distM = UtilMath.haversineMeters(userLat, userLon, p.getLat(), p.getLon());
            if (distM > radarRangeMeters) continue;//if outside of the radar radius

            double bearing = UtilMath.bearingDeg(userLat, userLon, p.getLat(), p.getLon());

            //bearing (0=N) -> canvas angle (0=+X): bearing - 90
            double ang = Math.toRadians(bearing - 90.0); //in canvas 0->right so i do -90

            //convert real dist to racial dist for the radar
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

            int iconSize = (planeScaledBitmap != null) ? planeScaledBitmap.getWidth() : planeIconSizePx;
            b.hitR = Math.max(18f, iconSize * 0.65f); //so icon wont be too small

            newBlips.add(b);
        }

        synchronized (lock) { //move new blips to the blips list
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

        //find the selected plane's current screen position from blips list
        Blip selectedBlip = null;
        synchronized (lock) {
            for (Blip b : blips) {
                if (b == null || b.plane == null) continue;

                if (b.plane == selectedPlane) {
                    selectedBlip = b;
                    break;
                }
            }
        }

        if (selectedBlip == null) return;

        long now = System.currentTimeMillis();
        long t = now - pulseStartTime;

        final float periodMs = 1200f; //pulse period in ms
        float phase = (t % (long) periodMs) / periodMs;

        // Animate radius and alpha: ring expands and fades out.
        float baseR = Math.max(18f, selectedBlip.hitR);
        float radius = baseR + phase * (baseR * 1.2f);
        int alpha = (int) (255 * (1f - phase));

        paintPulse.setAlpha(alpha);

        canvas.drawCircle(selectedBlip.x, selectedBlip.y, radius, paintPulse);

        //keep the animation running while plane is selected
        postInvalidateOnAnimation();
    }

    private Bitmap scalePlaneBitmap(Bitmap src, int targetPx) {
        if (src == null) return null;
        int t = Math.max(16, targetPx);
        return Bitmap.createScaledBitmap(src, t, t, true);
    }
}
