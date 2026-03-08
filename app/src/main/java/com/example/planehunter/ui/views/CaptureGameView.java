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


    private static final float TARGET_SIZE_DP = 100f;
    private static final float PLANE_SIZE_DP  = 44f;

    private float speedPxPerSec = 650f;

    private boolean running = false;
    private long lastFrameMs = 0;

    private float planeX;
    private float planeY;

    private float velX;
    private float velY;
    private float planeAngleDeg = 0f; // Rotation angle in degrees (0 = pointing right)

    private final RectF targetRect = new RectF();

    //paints
    private final Paint paintBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintTarget = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Bitmap planeBmpOriginal;
    private Bitmap planeBmpScaled;


    public CaptureGameView(Context context) { super(context); init(); }
    public CaptureGameView(Context context, @Nullable AttributeSet attrs) { super(context, attrs); init(); }
    public CaptureGameView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    private void init() {

        //background
        paintBg.setStyle(Paint.Style.FILL);
        paintBg.setColor(Color.rgb(5, 10, 20));

        //target
        paintTarget.setStyle(Paint.Style.STROKE);
        paintTarget.setStrokeWidth(3);
        paintTarget.setColor(Color.argb(220, 255, 255, 255));

        //laod plane image
        planeBmpOriginal = BitmapFactory.decodeResource(getResources(), R.drawable.air_plan);
        planeBmpScaled = scaleToSize(planeBmpOriginal, PLANE_SIZE_DP);
    }

    public void startGame() {
        running = true;
        lastFrameMs = 0;

        //needed so the game wont start unless there is a size for the view
        if (getWidth() <= 0 || getHeight() <= 0) {
            post(this::startGame);
            return;
        }

        resetRandomPassThroughTarget();
        postInvalidateOnAnimation();
    }

    public void stopGame() {
        running = false;
    }

    //checks if the plane is in the target
    public boolean tryCapture() {
        return RectF.intersects(targetRect, getPlaneRect());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawRect(0, 0, getWidth(), getHeight(), paintBg);

        if (running) updateMotionWrap();

        //draw target
        canvas.drawRoundRect(targetRect, 10,10, paintTarget);

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

        //start from the right side after it is out of bounds
        if (planeX + half < 0) {
            resetRandomPassThroughTarget();
        }
    }

    private void drawPlane(Canvas canvas) {

        Bitmap bmp = planeBmpScaled;

        if (bmp == null)
            return;

        float halfW = bmp.getWidth() / 2f;
        float halfH = bmp.getHeight() / 2f;

        canvas.save();

        canvas.translate(planeX, planeY); //moves to the center of the plane
        canvas.rotate(planeAngleDeg);
        canvas.drawBitmap(bmp, -halfW, -halfH, null);

        canvas.restore();
    }

    private RectF getPlaneRect() {

        Bitmap bmp = planeBmpScaled;
        if (bmp == null) {
            float half = PLANE_SIZE_DP * 0.5f;
            return new RectF(planeX - half, planeY - half, planeX + half, planeY + half);
        }

        float halfW = bmp.getWidth() / 2f;
        float halfH = bmp.getHeight() / 2f;
        return new RectF(planeX - halfW, planeY - halfH, planeX + halfW, planeY + halfH);
    }

    private float getPlaneHalfSizePx() {

        Bitmap bmp = planeBmpScaled;
        if (bmp == null) return PLANE_SIZE_DP * 0.5f;

        // Use max dimension for safe "fully offscreen" checks
        return Math.max(bmp.getWidth(), bmp.getHeight()) / 2f;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        rebuildTargetRect(w, h);

        // Rescale bitmap in case density/size changes and restart a clean pass
        //planeBmpScaled = scaleToDp(planeBmpOriginal, PLANE_SIZE_DP);
        //resetRandomPassThroughTarget();
    }

    private void rebuildTargetRect(int w, int h) {

        float size = TARGET_SIZE_DP;
        float cx = w / 2f;
        float cy = h / 2f;

        targetRect.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f);
    }


    private void resetRandomPassThroughTarget() {

        float half = getPlaneHalfSizePx();

        float targetX = targetRect.centerX();
        float targetY = targetRect.centerY();

        //start at random plance on the right side
        float startX = getWidth() + half; //out of screen
        float startY = randFloat(0f, getHeight());

        // End somewhere near the left edge (random X band)
        float endX = randFloat(-half - 120, -half);
        float endY;

        //the plane should go through the center of the screen
        //line equation: y = startY + t*(endY-startY), where t = (tx-startX)/(endX-startX).
        //we solve for endY: endY = startY + (ty-startY)/t
        float denom = (endX - startX);

        float t = (targetX - startX) / denom;

        endY = startY + (targetY - startY) / t;

        //ensure that plane doesn't get out of the screen
        endY = clamp(endY, half, getHeight() - half);

        //set start cords
        planeX = startX;
        planeY = startY;

        //calc distance for each axis
        float dx = endX - startX;
        float dy = endY - startY;

        float len = (float) Math.sqrt(Math.pow(dx,2) + Math.pow(dy,2));

        velX = (dx / len) * speedPxPerSec;
        velY = (dy / len) * speedPxPerSec;

        //calc angle in degrees
        planeAngleDeg = (float) Math.toDegrees(Math.atan2(dy, dx));
    }


    private Bitmap scaleToSize(Bitmap src, float targetDp) {
        if (src == null) return null;
        int targetPx = Math.max(16, Math.round(targetDp));
        return Bitmap.createScaledBitmap(src, targetPx, targetPx, true);
    }

    private float randFloat(float a, float b) {
        //https://stackoverflow.com/questions/40431966/what-is-the-best-way-to-generate-a-random-float-value-included-into-a-specified
        return (float) (ThreadLocalRandom.current().nextDouble(a, b));
    }

    private float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

}