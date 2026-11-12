package com.rtngodot;

import org.godotengine.godot.input.GodotInputHandler;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

public class RTNGodotView extends SurfaceView implements SurfaceHolder.Callback2 {
	private static final String TAG = "RTNGodotView";

	private String windowName = "";
	private GodotInputHandler mInputHandler;
	private ReactContext mReactContext;

	// 双指手势状态
	private boolean mTwoFingerActive = false;
	private float mPrevDist = 0;
	private float mPrevCenterX = 0;
	private float mPrevCenterY = 0;

	public RTNGodotView(Context context) {
		super(context);
		if (context instanceof ReactContext) {
			mReactContext = (ReactContext) context;
		}
		this.configureComponent();
	}

	public RTNGodotView(Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
		if (context instanceof ReactContext) {
			mReactContext = (ReactContext) context;
		}
		this.configureComponent();
	}

	public RTNGodotView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		if (context instanceof ReactContext) {
			mReactContext = (ReactContext) context;
		}
		this.configureComponent();
	}

	private void configureComponent() {
		mInputHandler = RTNLibGodot.getInstance().getInputHandler();
		setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		getHolder().addCallback(this);
	}

	public void setWindowName(String newWindowName) {
		windowName = newWindowName;
	}

	public String getWindowName() {
		return windowName;
	}

	@Override
	public void surfaceRedrawNeeded(@NonNull SurfaceHolder surfaceHolder) {
		Log.i(TAG, String.format("surfaceRedrawNeeded: %s %s", windowName, surfaceHolder.getSurface().toString()));
	}

	@Override
	public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {
		Log.i(TAG, String.format("surfaceCreated: %s %s", windowName, surfaceHolder.getSurface().toString()));
	}

	@Override
	public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int format, int width, int height) {
		Log.i(TAG, String.format("surfaceChanged: %s %s %d %d %d", windowName, surfaceHolder.getSurface().toString(), format, width, height));
		RTNLibGodot.getInstance().updateWindow(windowName, getSurfaceControl(), surfaceHolder, format, width, height);
	}

	@Override
	public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {
		Log.i(TAG, String.format("surfaceRemoved: %s %s", windowName, surfaceHolder.getSurface().toString()));
		RTNLibGodot.getInstance().removeWindow(windowName);
	}

	// ===== 双指手势辅助方法 =====

	private float getTwoFingerDist(MotionEvent event) {
		float dx = event.getX(0) - event.getX(1);
		float dy = event.getY(0) - event.getY(1);
		return (float) Math.sqrt(dx * dx + dy * dy);
	}

	private float getTwoFingerCenterX(MotionEvent event) {
		return (event.getX(0) + event.getX(1)) / 2f;
	}

	private float getTwoFingerCenterY(MotionEvent event) {
		return (event.getY(0) + event.getY(1)) / 2f;
	}

	private void sendEvent(String eventName, WritableMap params) {
		if (mReactContext != null && mReactContext.hasActiveReactInstance()) {
			mReactContext
				.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
				.emit(eventName, params);
		}
	}

	private boolean handleTwoFingerGesture(MotionEvent event) {
		int action = event.getActionMasked();

		if (event.getPointerCount() >= 2) {
			if (!mTwoFingerActive) {
				// 双指开始
				mTwoFingerActive = true;
				mPrevDist = getTwoFingerDist(event);
				mPrevCenterX = getTwoFingerCenterX(event);
				mPrevCenterY = getTwoFingerCenterY(event);
				return true;
			}

			if (action == MotionEvent.ACTION_MOVE) {
				float dist = getTwoFingerDist(event);
				float centerX = getTwoFingerCenterX(event);
				float centerY = getTwoFingerCenterY(event);

				// 缩放
				if (mPrevDist > 0) {
					float factor = dist / mPrevDist;
					if (Math.abs(factor - 1.0f) > 0.005f) {
						WritableMap params = Arguments.createMap();
						params.putDouble("factor", factor);
						sendEvent("godotPinch", params);
					}
				}

				// 平移
				float dx = centerX - mPrevCenterX;
				float dy = centerY - mPrevCenterY;
				if (Math.abs(dx) > 0.5f || Math.abs(dy) > 0.5f) {
					WritableMap params = Arguments.createMap();
					params.putDouble("dx", dx);
					params.putDouble("dy", dy);
					sendEvent("godotPan", params);
				}

				mPrevDist = dist;
				mPrevCenterX = centerX;
				mPrevCenterY = centerY;
				return true;
			}
		}

		// 手指抬起时重置
		if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL
				|| action == MotionEvent.ACTION_POINTER_UP) {
			if (event.getPointerCount() <= 2) {
				mTwoFingerActive = false;
				mPrevDist = 0;
			}
		}

		return mTwoFingerActive;
	}

	// ===== 触摸事件 =====

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		super.onTouchEvent(event);
		if (mInputHandler == null) {
			mInputHandler = RTNLibGodot.getInstance().getInputHandler();
			if (mInputHandler == null) return false;
		}

		// 双指时自己处理，不传给 Godot 的 GestureHandler
		if (event.getPointerCount() >= 2 || mTwoFingerActive) {
			return handleTwoFingerGesture(event);
		}

		// 单指传给 Godot
		return mInputHandler.onTouchEvent(event);
	}

	@Override
	public boolean onGenericMotionEvent(MotionEvent event) {
		if (mInputHandler == null) {
			mInputHandler = RTNLibGodot.getInstance().getInputHandler();
			if (mInputHandler == null) return false;
		}
		return mInputHandler.onGenericMotionEvent(event);
	}

	@Override
	public boolean onCapturedPointerEvent(MotionEvent event) {
		if (mInputHandler == null) {
			mInputHandler = RTNLibGodot.getInstance().getInputHandler();
			if (mInputHandler == null) return false;
		}
		return mInputHandler.onGenericMotionEvent(event);
	}

	private boolean canCapturePointer() {
		return true;
	}

	@Override
	public void requestPointerCapture() {
		if (canCapturePointer()) {
			super.requestPointerCapture();
			if (mInputHandler != null) {
				if (mInputHandler != null) mInputHandler.onPointerCaptureChange(true);
			}
		}
	}

	@Override
	public void releasePointerCapture() {
		super.releasePointerCapture();
		if (mInputHandler != null) {
			if (mInputHandler != null) mInputHandler.onPointerCaptureChange(false);
		}
	}

	@Override
	public void onPointerCaptureChange(boolean hasCapture) {
		super.onPointerCaptureChange(hasCapture);
		if (mInputHandler != null) {
			if (mInputHandler != null) mInputHandler.onPointerCaptureChange(hasCapture);
		}
	}
}
