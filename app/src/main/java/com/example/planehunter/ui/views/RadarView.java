package com.example.planehunter.ui.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.planehunter.model.Plane;
import com.example.planehunter.R;
import com.example.planehunter.util.UtilMath;
import com.example.planehunter.model.AircraftCategory;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom View that displays a radar-like representation of nearby aircraft.
 * Shows aircraft as icons (blips) relative to the user's location at the center.
 */
public class RadarView extends View {

    /**
     * Interface for listening to aircraft selection events on the radar.
     */
    public interface OnPlaneClickListener {
        /**
         * Called when a single plane is clicked.
         * @param plane The clicked plane.
         */
        void onPlaneClicked(Plane plane);

        /**
         * Called when multiple planes are clicked (overlapping area).
         * @param planes The list of planes in the clicked area.
         */
        void onMultiplePlanesClicked(ArrayList<Plane> planes);
    }

    /**
     * Internal class representing a plane's projected position and metadata on the canvas.
     */
    private static class Blip {
        /** The plane object associated with this blip. */
        Plane plane;
        /** Projected X coordinate on the canvas. */
        float x;
        /** Projected Y coordinate on the canvas. */
        float y;
        /** Interaction radius for detecting taps. */
        float hitR;
        /** Rotation angle in degrees for the aircraft icon. */
        float rotDeg;
    }

    /** Offset to correct the orientation of plane icons (defaulting to North). */
    private static final float ICON_HEADING_OFFSET = -90f;

    /** Paint for drawing radar rings and grid lines. */
    private final Paint paintGrid = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Paint for drawing the user's location indicator. */
    private final Paint paintMe = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Paint for drawing selection pulses. */
    private final Paint paintPulse = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Paint for drawing planes in their normal state. */
    private final Paint paintPlaneNormal = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Paint for drawing planes that are in capture cooldown. */
    private final Paint paintPlaneCooldown = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** Lock object for thread-safe access to plane data. */
    private final Object lock = new Object();

    /** Set of ICAO24 addresses for planes currently in cooldown. */
    private final Set<String> cooldownIcaos = new HashSet<>();
    /** List of blips currently displayed on the radar. */
    private final List<Blip> blips = new ArrayList<>();
    /** Cached list of last known planes for merging updates. */
    private ArrayList<Plane> lastPlanes = new ArrayList<>();

    /** Current user latitude. */
    private double userLat = 0.0;
    /** Current user longitude. */
    private double userLon = 0.0;

    /** The scale of the radar view in meters from center to edge. */
    private double radarRangeMeters = 30000.0;
    /** Radius of the user's indicator in pixels. */
    private float meRadiusPx = 14f;

    /** Map of scaled bitmaps for each aircraft category. */
    private final Map<Long, Bitmap> categoryBitmaps = new HashMap<>();
    /** Default bitmap used when a category is unknown. */
    private Bitmap fallbackBitmap;

    /** Size in pixels for aircraft icons. */
    private int planeIconSizePx = 100;

    /** Listener for plane click events. */
    private OnPlaneClickListener listener;

    /** Currently selected plane. */
    private Plane selectedPlane = null;
    /** Start time of the selection pulse animation in milliseconds. */
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

    /**
     * Initializes paints and loads aircraft category bitmaps.
     */
    private void init() {
        paintGrid.setStyle(Paint.Style.STROKE);
        paintGrid.setStrokeWidth(3f);
        paintGrid.setARGB(255, 0, 180, 0);

        paintMe.setStyle(Paint.Style.FILL);
        paintMe.setARGB(255, 0, 255, 0);


        paintPulse.setStyle(Paint.Style.STROKE);
        paintPulse.setStrokeWidth(4f);
        paintPulse.setARGB(255, 255, 255, 0);

        ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(0f);

        paintPlaneCooldown.setColorFilter(new ColorMatrixColorFilter(matrix));
        paintPlaneCooldown.setAlpha(120);

        loadCategoryBitmap();
    }

    /**
     * Loads and scales bitmaps for all aircraft categories.
     */
    private void loadCategoryBitmap() {
        categoryBitmaps.clear();

        categoryBitmaps.put(AircraftCategory.AIRLINER, loadScaledBitmap(R.drawable.plane_airliner));
        categoryBitmaps.put(AircraftCategory.CARGO, loadScaledBitmap(R.drawable.plane_cargo));
        categoryBitmaps.put(AircraftCategory.BUSINESS_JET, loadScaledBitmap(R.drawable.plane_business_jet));
        categoryBitmaps.put(AircraftCategory.GENERAL_AVIATION, loadScaledBitmap(R.drawable.plane_turboprop));
        categoryBitmaps.put(AircraftCategory.TURBOPROP_REGIONAL, loadScaledBitmap(R.drawable.plane_turboprop));
        categoryBitmaps.put(AircraftCategory.HELICOPTER, loadScaledBitmap(R.drawable.plane_helicopter));
        categoryBitmaps.put(AircraftCategory.MILITARY_GOVERNMENT, loadScaledBitmap(R.drawable.plane_military));
        categoryBitmaps.put(AircraftCategory.UNKNOWN, loadScaledBitmap(R.drawable.plane_turboprop));

        fallbackBitmap = categoryBitmaps.get(AircraftCategory.UNKNOWN);
    }

    private Bitmap loadScaledBitmap(int drawableResId) {
        Bitmap originalBitmap = BitmapFactory.decodeResource(getResources(), drawableResId);
        return scalePlaneBitmap(originalBitmap, planeIconSizePx);
    }

    private Bitmap getBitmapForPlane(Plane plane){
        if(plane == null) return fallbackBitmap;

        Bitmap bitmap = categoryBitmaps.get(plane.getCategory());
        if(bitmap != null) return bitmap;
        return fallbackBitmap;
    }

    /**
     * Sets the listener for plane click events.
     * @param l The listener.
     */
    public void setOnPlaneClickListener(OnPlaneClickListener l) {
        this.listener = l;
    }

    /**
     * Sets the user's current GPS location and updates the radar display.
     * @param lat Latitude.
     * @param lon Longitude.
     */
    public void setUserLocation(double lat, double lon) {
        this.userLat = lat;
        this.userLon = lon;
        rebuildBlips();
        invalidate();
    }

    /**
     * Updates the list of planes to be displayed, merging metadata from previously known planes.
     * @param planes The new list of planes.
     */
    public void setPlanes(ArrayList<Plane> planes) {
        if (planes == null) {
            planes = new ArrayList<>();
        }

        ArrayList<Plane> mergedPlanes = new ArrayList<>();

        for (Plane newPlane : planes) {
            if (newPlane == null) continue;

            Plane oldPlane = findPlaneByIcao24(lastPlanes, newPlane.getIcao24());

            if (oldPlane != null) {
                if (isBlank(newPlane.getRegistration()) && !isBlank(oldPlane.getRegistration())) {
                    newPlane.setRegistration(oldPlane.getRegistration());
                }

                if (isBlank(newPlane.getTypeName()) && !isBlank(oldPlane.getTypeName())) {
                    newPlane.setTypeName(oldPlane.getTypeName());
                }
            }

            mergedPlanes.add(newPlane);
        }

        lastPlanes = new ArrayList<>(mergedPlanes);
        rebuildBlips(mergedPlanes);
        invalidate();
    }

    private Plane findPlaneByIcao24(ArrayList<Plane> planes, String icao24) {
        if (planes == null || icao24 == null) return null;

        for (Plane p : planes) {
            if (p == null || p.getIcao24() == null) continue;

            if (icao24.equalsIgnoreCase(p.getIcao24())) {
                return p;
            }
        }

        return null;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * Sets the visible range of the radar.
     * @param meters The range in meters from the center.
     */
    public void setRadarRangeMeters(double meters) {
        this.radarRangeMeters = meters;
        rebuildBlips();
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();
        float centerX = width / 2f;
        float centerY = height / 2f;
        float radius = Math.min(width, height) * 0.45f;

        canvas.drawCircle(centerX, centerY, radius, paintGrid);
        canvas.drawCircle(centerX, centerY, radius * 0.66f, paintGrid);
        canvas.drawCircle(centerX, centerY, radius * 0.33f, paintGrid);

        canvas.drawLine(centerX - radius, centerY, centerX + radius, centerY, paintGrid);
        canvas.drawLine(centerX, centerY - radius, centerX, centerY + radius, paintGrid);

        canvas.drawCircle(centerX, centerY, meRadiusPx, paintMe);


        synchronized (lock) {

            for (Blip blip : blips) {

                Bitmap bitmap = getBitmapForPlane(blip.plane);
                float half = bitmap.getWidth() / 2f;

                canvas.save();
                canvas.translate(blip.x, blip.y);
                canvas.rotate(blip.rotDeg);

                Paint planePaint = isPlaneInCooldown(blip.plane)
                        ? paintPlaneCooldown
                        : paintPlaneNormal;

                canvas.drawBitmap(bitmap, -half, -half, planePaint);

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

        ArrayList<Plane> hits = findPlanesNearTap(touchX, touchY);

        if (hits.isEmpty()) {
            setSelectedPlane("");
            return true;
        }

        if (hits.size() == 1) {
            listener.onPlaneClicked(hits.get(0));
        } else {
            listener.onMultiplePlanesClicked(hits);
        }


        return true;
    }

    private ArrayList<Plane> findPlanesNearTap(float tapX, float tapY) {
        ArrayList<Plane> hits = new ArrayList<>();

        synchronized (lock){
            for (Blip blip : blips) {
                float dx = tapX - blip.x;
                float dy = tapY - blip.y;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);

                if (distance <= blip.hitR) {
                    hits.add(blip.plane);
                }
            }
        }

        return hits;
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

    /**
     * Marks the plane with the specified ICAO24 as selected and triggers the pulse animation.
     * @param icao24 The ICAO24 identifier.
     */
    public void setSelectedPlane(String icao24) {
        String normalizedTarget = normalizeIcao(icao24);
        if (normalizedTarget == null) {
            setSelectedPlane((Plane) null);
            return;
        }

        for (Plane plane : lastPlanes) {
            if (plane == null) continue;

            String normalizedPlaneIcao = normalizeIcao(plane.getIcao24());
            if (normalizedTarget.equals(normalizedPlaneIcao)) {
                setSelectedPlane(plane);
                return;
            }
        }
    }

    /**
     * Marks the specified plane as selected and starts the selection animation.
     * @param plane The plane to select.
     */
    public void setSelectedPlane(@Nullable Plane plane) {
        selectedPlane = plane;
        pulseStartTime = System.currentTimeMillis();
        invalidate();
    }

    @Nullable
    private String normalizeIcao(@Nullable String icao24) {
        if (icao24 == null) return null;

        String trimmed = icao24.trim();
        if (trimmed.isEmpty()) return null;

        return trimmed.toUpperCase();
    }

    private boolean isPlaneInCooldown(@Nullable Plane plane) {
        if (plane == null) return false;

        String normalized = normalizeIcao(plane.getIcao24());
        if (normalized == null) return false;

        synchronized (lock) {
            return cooldownIcaos.contains(normalized);
        }
    }
    

    private void rebuildBlips() {
        rebuildBlips(lastPlanes);
    }

    /**
     * Projects GPS coordinates into canvas coordinates for all provided planes.
     * @param planes The planes to project.
     */
    private void rebuildBlips(ArrayList<Plane> planes) {

        float width = getWidth();
        float height = getHeight();
        if (width <= 0 || height <= 0) return;

        float cx = width / 2f;
        float cy = height / 2f;
        float radiusPx = Math.min(width, height) * 0.45f;

        if (userLat == 0.0 && userLon == 0.0) {
            synchronized (lock) { blips.clear(); }
            return;
        }

        ArrayList<Blip> newBlips = new ArrayList<>();

        for (Plane p : planes) {
            if (p == null) continue;

            double distM = UtilMath.haversineMeters(userLat, userLon, p.getLat(), p.getLon());
            if (distM > radarRangeMeters) continue;

            double bearing = UtilMath.bearingDeg(userLat, userLon, p.getLat(), p.getLon());

            double ang = Math.toRadians(bearing - 90.0);

            float r = (float) (distM / radarRangeMeters * radiusPx);
            float x = cx + (float) (Math.cos(ang) * r);
            float y = cy + (float) (Math.sin(ang) * r);


            Blip b = new Blip();
            b.plane = p;
            b.x = x;
            b.y = y;

            double track = p.getTrackDeg();
            if (!Double.isNaN(track)) {
                b.rotDeg = (float) track + ICON_HEADING_OFFSET;
            } else {
                b.rotDeg = 0f;
            }

            Bitmap bitmap = getBitmapForPlane(p);
            int iconSize =bitmap.getWidth();
            b.hitR = Math.max(18f,iconSize*0.65f);

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

    /**
     * Draws the selection pulse animation around the selected blip.
     * @param canvas The canvas to draw on.
     */
    private void drawSelectedPlanePulse(Canvas canvas) {
        if (selectedPlane == null) return;

        String selectedIcao = normalizeIcao(selectedPlane.getIcao24());
        if (selectedIcao == null) return;

        Blip selectedBlip = null;

        synchronized (lock) {
            for (Blip b : blips) {
                if (b == null || b.plane == null) continue;

                String blipIcao = normalizeIcao(b.plane.getIcao24());
                if (selectedIcao.equals(blipIcao)) {
                    selectedBlip = b;
                    break;
                }
            }
        }

        if (selectedBlip == null) return;

        long now = System.currentTimeMillis();
        long t = now - pulseStartTime;

        final float periodMs = 1200f;
        float phase = (t % (long) periodMs) / periodMs;

        float baseR = Math.max(18f, selectedBlip.hitR);
        float radius = baseR + phase * (baseR * 1.2f);
        int alpha = (int) (255 * (1f - phase));

        paintPulse.setAlpha(alpha);
        canvas.drawCircle(selectedBlip.x, selectedBlip.y, radius, paintPulse);

        postInvalidateOnAnimation();
    }

    /**
     * Sets the set of aircraft that are currently in capture cooldown.
     * @param icaos Set of ICAO24 addresses.
     */
    public void setCooldownIcaos(Set<String> icaos) {
        synchronized (lock) {
            cooldownIcaos.clear();

            if (icaos == null) {
                invalidate();
                return;
            }

            for (String icao : icaos) {
                String normalized = normalizeIcao(icao);
                if (normalized != null) {
                    cooldownIcaos.add(normalized);
                }
            }
        }

        invalidate();
    }

    /**
     * Adds a specific ICAO24 to the cooldown set.
     * @param icao24 The identifier to add.
     */
    public void addCooldownIcao(String icao24) {
        String normalized = normalizeIcao(icao24);
        if (normalized == null) return;

        synchronized (lock) {
            cooldownIcaos.add(normalized);
        }

        invalidate();
    }

    private Bitmap scalePlaneBitmap(Bitmap src, int targetPx) {
        if (src == null) return null;

        int target = Math.max(16, targetPx);

        int srcWidth = src.getWidth();
        int srcHeight = src.getHeight();

        if (srcWidth <= 0 || srcHeight <= 0) {
            return src;
        }

        float scale = Math.min(
                (float) target / srcWidth,
                (float) target / srcHeight
        );

        int scaledWidth = Math.max(1, Math.round(srcWidth * scale));
        int scaledHeight = Math.max(1, Math.round(srcHeight * scale));

        return Bitmap.createScaledBitmap(src, scaledWidth, scaledHeight, true);
    }

    /**
     * Finds a Plane object by its ICAO24 address in the last known plane list.
     * @param icao24 The identifier.
     * @return The Plane or null.
     */
    public Plane findPlaneByIcao24(String icao24) {
        String normalized = normalizeIcao(icao24);
        if (normalized == null) return null;

        for (Plane p : lastPlanes) {
            if (p == null) continue;

            String pIcao = normalizeIcao(p.getIcao24());
            if (normalized.equals(pIcao)) {
                return p;
            }
        }

        return null;
    }
    public Set<String> getCooldownIcaosCopy() {
        synchronized (lock) {
            return new HashSet<>(cooldownIcaos);
        }
    }
}
