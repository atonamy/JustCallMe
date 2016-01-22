package com.atonamy.justcallme;

import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

/**
 * Created by archie (Arseniy Kucherenko) on 11/1/16.
 */
public class ZoomViewAnimation {

    private Animation zoominAnimation, zoomoutAnimation;
    private View currentView;
    private boolean animationFinished;

    public ZoomViewAnimation(final View view) {
        currentView = view;
        if (currentView != null)
            initAnimation();
    }

    public void performAnimation() {
        if (currentView != null && animationFinished) {
            animationFinished = false;
            currentView.startAnimation(zoominAnimation);
        }
    }

    private void initAnimation() {
        animationFinished = true;
        zoominAnimation = AnimationUtils.loadAnimation(currentView.getContext(), R.anim.zoomin);
        zoomoutAnimation = AnimationUtils.loadAnimation(currentView.getContext(), R.anim.zoomout);
        zoominAnimation.setAnimationListener(ZoomInListener);
        zoomoutAnimation.setAnimationListener(ZoomOutListener);
    }


    private Animation.AnimationListener ZoomInListener = new Animation.AnimationListener() {

        @Override
        public void onAnimationStart(Animation animation) {

        }

        @Override
        public void onAnimationEnd(Animation animation) {
            currentView.startAnimation(zoomoutAnimation);
        }

        @Override
        public void onAnimationRepeat(Animation animation) {

        }
    };

    private Animation.AnimationListener ZoomOutListener = new Animation.AnimationListener() {

        @Override
        public void onAnimationStart(Animation animation) {

        }

        @Override
        public void onAnimationEnd(Animation animation) {
            animationFinished = true;
        }

        @Override
        public void onAnimationRepeat(Animation animation) {

        }


    };
}
