package com.example.planehunter.ui.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.example.planehunter.R;

import java.util.concurrent.ThreadLocalRandom;

public class CaptureGameView extends View {

    // --- Config ---
    private static final float TARGET_SIZE_DP = 100f;
    private static final float PLANE_SIZE_DP  = 44f;

    // Plane speed in px/sec
    private float speedPxPerSec = 650f;

    // --- State ---
    private boolean running = false;
    private long lastFrameMs = 0;

    private float planeX;
    private float planeY;

    private float velX;
    private float velY;
    private float planeAngleDeg = 0f; // Rotation angle in degrees (0 = pointing right)

    private final RectF targetRect = new RectF();

    // --- Paints ---
    private final Paint paintBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintTarget = new Paint(Paint.ANTI_ALIAS_FLAG);

    // --- Plane bitmap ---
    private Bitmap planeBmpOriginal;
    private Bitmap planeBmpScaled;

    public CaptureGameView(Context context) { super(context); init(); }
    public CaptureGameView(Context context, @Nullable AttributeSet attrs) { super(context, attrs); init(); }
    public CaptureGameView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    private void init() {

        // Background
        paintBg.setStyle(Paint.Style.FILL);
        paintBg.setColor(Color.rgb(5, 10, 20));

        // Target box
        paintTarget.setStyle(Paint.Style.STROKE);
        paintTarget.setStrokeWidth(dp(3));
        paintTarget.setColor(Color.argb(220, 255, 255, 255));

        // Load plane PNG from drawable (plane.png -> R.drawable.plane)
        planeBmpOriginal = BitmapFactory.decodeResource(getResources(), R.drawable.air_plan);
        planeBmpScaled = scaleToDp(planeBmpOriginal, PLANE_SIZE_DP);
    }

    // ---------- Public API ----------

    public void startGame() {
        running = true;
        lastFrameMs = 0;
        resetRandomPassThroughTarget();
        postInvalidateOnAnimation();
    }

    public void stopGame() {
        running = false;
    }

    // Returns true if the plane is inside the target at this moment
    public boolean tryCapture() {
        return RectF.intersects(targetRect, getPlaneRect());
    }

    // ---------- Drawing + Update ----------

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawRect(0, 0, getWidth(), getHeight(), paintBg);

        if (running) updateMotionWrap();

        // Draw target
        canvas.drawRoundRect(targetRect, dp(10), dp(10), paintTarget);

        // Draw plane bitmap
        drawPlane(canvas);

        if (running) postInvalidateOnAnimation();
    }

    private void updateMotionWrap() {

        long now = SystemClock.uptimeMillis();
        if (lastFrameMs == 0) {
            lastFrameMs = now;
            return;
        }

        float dt = (now - lastFrameMs) / 1000f;
        lastFrameMs = now;

        planeX += velX * dt;
        planeY += velY * dt;

        float half = getPlaneHalfSizePx();

        // When fully out of bounds, start a new random pass from the right side
        if (planeX + half < 0) {
            resetRandomPassThroughTarget();
        }
    }

    private void drawPlane(Canvas canvas) {

        Bitmap bmp = planeBmpScaled;
        if (bmp == null) return;

        float halfW = bmp.getWidth() / 2f;
        float halfH = bmp.getHeight() / 2f;

        canvas.save();

        // Move to plane center, rotate, then draw bitmap centered
        canvas.translate(planeX, planeY);
        canvas.rotate(planeAngleDeg);
        canvas.drawBitmap(bmp, -halfW, -halfH, null);

        canvas.restore();
    }

    private RectF getPlaneRect() {

        Bitmap bmp = planeBmpScaled;
        if (bmp == null) {
            float half = dp(PLANE_SIZE_DP) * 0.5f;
            return new RectF(planeX - half, planeY - half, planeX + half, planeY + half);
        }

        float halfW = bmp.getWidth() / 2f;
        float halfH = bmp.getHeight() / 2f;
        return new RectF(planeX - halfW, planeY - halfH, planeX + halfW, planeY + halfH);
    }

    private float getPlaneHalfSizePx() {

        Bitmap bmp = planeBmpScaled;
        if (bmp == null) return dp(PLANE_SIZE_DP) * 0.5f;

        // Use max dimension for safe "fully offscreen" checks
        return Math.max(bmp.getWidth(), bmp.getHeight()) / 2f;
    }

    // ---------- Setup ----------

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        rebuildTargetRect(w, h);

        // Rescale bitmap in case density/size changes and restart a clean pass
        planeBmpScaled = scaleToDp(planeBmpOriginal, PLANE_SIZE_DP);
        resetRandomPassThroughTarget();
    }

    private void rebuildTargetRect(int w, int h) {

        float size = dp(TARGET_SIZE_DP);
        float cx = w / 2f;
        float cy = h / 2f;

        targetRect.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f);
    }

    /**
     * Starts a new pass from the right side.
     * The Y is chosen so the plane will definitely pass through the target box.
     */
    private void resetRandomPassThroughTarget() {

        if (getWidth() <= 0 || getHeight() <= 0) return;

        float half = getPlaneHalfSizePx();

        float tx = targetRect.centerX();
        float ty = targetRect.centerY();

        // Start somewhere near the right edge (random X band) + random Y band (full height)
        float startX = getWidth() + half;
        float startY = randFloat(half, getHeight() - half);

        // End somewhere near the left edge (random X band)
        float endX = randFloat(-half - dp(120), -half);
        float endY;

        // Pick endY so that the line segment from start->end crosses the target center Y at X=tx.
        // Line equation: y = startY + t*(endY-startY), where t = (tx-startX)/(endX-startX).
        // We solve for endY: endY = startY + (ty-startY)/t
        float denom = (endX - startX);

        // Avoid degenerate case (should not happen, but be safe)
        if (Math.abs(denom) < 0.001f) {
            endY = ty;
        } else {
            float t = (tx - startX) / denom;

            // Ensure t is between 0..1 (target X is between start and end)
            // If not (rare due to randomness), force a simple valid configuration
            if (t <= 0.05f || t >= 0.95f) {
                startX = getWidth() + half + dp(60);
                endX = -half - dp(60);
                t = (tx - startX) / (endX - startX);
            }

            endY = startY + (ty - startY) / t;
        }

        // Clamp endY to screen bounds. If clamp breaks the guarantee too much,
        // regenerate with a better startY.
        endY = clamp(endY, half, getHeight() - half);

        // Apply start as the current plane position
        planeX = startX;
        planeY = startY;

        // Build velocity toward end with a constant speed
        float dx = endX - startX;
        float dy = endY - startY;

        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1f) len = 1f;

        velX = (dx / len) * speedPxPerSec;
        velY = (dy / len) * speedPxPerSec;

        // Angle in degrees: bitmap points right at 0 degrees, so atan2(dy, dx) is perfect.
        planeAngleDeg = (float) Math.toDegrees(Math.atan2(dy, dx));
    }

    /**
     * Picks a Y so the plane's bounding box will intersect the target box during the pass.
     * We choose within [targetTop+half .. targetBottom-half].
     */
    private float pickYThatCrossesTarget(float planeHalf) {

        float top = targetRect.top + planeHalf;
        float bottom = targetRect.bottom - planeHalf;

        // Safety clamp in case the target is too large or the screen is too small
        top = clamp(top, planeHalf, getHeight() - planeHalf);
        bottom = clamp(bottom, planeHalf, getHeight() - planeHalf);

        if (bottom <= top) {
            return getHeight() / 2f;
        }

        return randFloat(top, bottom);
    }

    // ---------- Utils ----------

    private Bitmap scaleToDp(Bitmap src, float targetDp) {
        if (src == null) return null;
        int targetPx = Math.max(16, Math.round(dp(targetDp)));
        return Bitmap.createScaledBitmap(src, targetPx, targetPx, true);
    }

    private float randFloat(float a, float b) {
        return (float) (ThreadLocalRandom.current().nextDouble(a, b));
    }

    private float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}