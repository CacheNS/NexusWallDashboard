package com.cachens.nexusdashboard;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

final class RadioControl extends FrameLayout {
    private final ImageButton button;
    private final ProgressBar progress;

    RadioControl(Context context, OnClickListener listener) {
        super(context);
        button = new ImageButton(context);
        button.setPadding(dp(12), dp(12), dp(12), dp(12));
        button.setColorFilter(Color.WHITE);
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[] {android.R.attr.state_pressed}, circle(Color.argb(150, 80, 80, 80)));
        background.addState(new int[] {android.R.attr.state_focused}, circle(Color.argb(150, 80, 80, 80)));
        background.addState(new int[0], circle(Color.argb(105, 0, 0, 0)));
        button.setBackground(background);
        button.setOnClickListener(listener);
        button.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                Toast.makeText(getContext(), button.getContentDescription(), Toast.LENGTH_SHORT).show();
                return true;
            }
        });
        addView(button, new LayoutParams(dp(48), dp(48), Gravity.CENTER));
        progress = new ProgressBar(context, null, android.R.attr.progressBarStyleSmall);
        progress.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        progress.setClickable(false);
        addView(progress, new LayoutParams(dp(40), dp(40), Gravity.CENTER));
        setState(RadioController.State.STOPPED);
    }

    void setState(RadioController.State state) {
        boolean stopped = state == RadioController.State.STOPPED;
        if (stopped) {
            button.setImageResource(android.R.drawable.ic_media_play);
        } else {
            GradientDrawable stop = new GradientDrawable();
            stop.setColor(Color.WHITE);
            stop.setSize(dp(16), dp(16));
            button.setImageDrawable(stop);
        }
        button.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        button.setContentDescription(AppText.get(getContext(), stopped ? "radio_play"
                : state == RadioController.State.BUFFERING ? "radio_cancel" : "radio_stop"));
        progress.setVisibility(state == RadioController.State.BUFFERING ? View.VISIBLE : View.GONE);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable circle(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }
}