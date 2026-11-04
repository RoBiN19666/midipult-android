package com.midipult.app;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Обновление страницы жестом «потянуть вниз», без внешних библиотек. */
final class PullRefresh extends FrameLayout {
    interface Listener {
        void onRefresh();
    }

    private static final long TIMEOUT_MS = 20000L;

    private final float density;
    private final int trigger;
    private final int maxPull;
    private final int arrowSize;
    private final float slop;
    private final TextView arrow;
    private final ProgressBar spinner;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timeout = this::finishRefresh;

    private Listener listener;
    private float downY;
    private float offset;
    private boolean pulling;
    private boolean refreshing;

    PullRefresh(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        trigger = Math.round(64 * density);
        maxPull = Math.round(120 * density);
        arrowSize = Math.round(42 * density);
        slop = ViewConfiguration.get(context).getScaledTouchSlop();

        arrow = new TextView(context);
        arrow.setText("↓");
        arrow.setTextSize(18);
        arrow.setGravity(Gravity.CENTER);
        arrow.setTextColor(0xFFFFFFFF);
        arrow.setBackground(circle(0xFF7C5CFF));
        arrow.setAlpha(0f);
        LayoutParams arrowParams = new LayoutParams(arrowSize, arrowSize,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        arrowParams.topMargin = Math.round(14 * density);
        addView(arrow, arrowParams);
        arrow.setTranslationY(-arrowSize);

        spinner = new ProgressBar(context);
        spinner.setIndeterminate(true);
        spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(0xFF7C5CFF));
        spinner.setVisibility(View.GONE);
        int spinnerSize = Math.round(34 * density);
        LayoutParams spinnerParams = new LayoutParams(spinnerSize, spinnerSize,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        spinnerParams.topMargin = Math.round(18 * density);
        addView(spinner, spinnerParams);
    }

    private GradientDrawable circle(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        drawable.setStroke(Math.round(2 * density), 0x55FFFFFF);
        return drawable;
    }

    /** Единственный ребёнок — прокручиваемый контент (WebView). */
    void content(View view) {
        addView(view, 0, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    boolean isRefreshing() {
        return refreshing;
    }

    void finishRefresh() {
        handler.removeCallbacks(timeout);
        if (!refreshing) return;
        refreshing = false;
        spinner.setVisibility(View.GONE);
        apply(0f);
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        // WebView просит не перехватывать жесты — иначе не работает «потянуть вниз».
        super.requestDisallowInterceptTouchEvent(false);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (refreshing) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downY = event.getY();
                pulling = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (!pulling && event.getPointerCount() == 1
                        && !canScrollUp() && event.getY() - downY > slop) {
                    pulling = true;
                    apply(0f);
                    return true;
                }
                break;
            default:
                break;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (refreshing) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                return true;
            case MotionEvent.ACTION_MOVE:
                if (pulling) {
                    float raw = event.getY() - downY;
                    apply(Math.max(0f, Math.min(maxPull, raw * 0.6f)));
                    return true;
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                if (pulling) {
                    pulling = false;
                    apply(0f);
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (pulling) {
                    pulling = false;
                    if (offset >= trigger) {
                        startRefresh();
                    } else {
                        apply(0f);
                    }
                    return true;
                }
                break;
            default:
                break;
        }
        return pulling;
    }

    private void apply(float value) {
        offset = value;
        float progress = trigger <= 0 ? 0f : Math.min(1f, value / trigger);
        arrow.setAlpha(progress);
        arrow.setRotation(progress * 180f);
        arrow.setTranslationY(value - arrowSize);
        if (!refreshing && progress <= 0f) {
            arrow.setRotation(0f);
        }
    }

    private void startRefresh() {
        refreshing = true;
        arrow.setAlpha(0f);
        arrow.setRotation(0f);
        spinner.setVisibility(View.VISIBLE);
        handler.removeCallbacks(timeout);
        handler.postDelayed(timeout, TIMEOUT_MS);
        if (listener != null) listener.onRefresh();
    }

    private boolean canScrollUp() {
        return getChildCount() > 0 && getChildAt(0).canScrollVertically(-1);
    }
}
