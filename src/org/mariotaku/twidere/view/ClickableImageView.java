package org.mariotaku.twidere.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.ImageView;
import org.mariotaku.twidere.util.Utils;

public class ClickableImageView extends ImageView {

	private final int mHightlightColor;
	private final Rect mRect;
	private boolean mIsDown;

	public ClickableImageView(final Context context) {
		this(context, null);
	}

	public ClickableImageView(final Context context, final AttributeSet attrs) {
		this(context, attrs, 0);
	}
	
	public ClickableImageView(final Context context, final AttributeSet attrs, final int defStyle) {
		super(context, attrs, defStyle);
		final int color = Utils.getThemeColor(context);
		mHightlightColor = Color.argb(0x80, Color.red(color), Color.green(color), Color.blue(color));
		mRect = new Rect();
	}
	
	@Override
	public boolean onTouchEvent(final MotionEvent e) {
		switch (e.getAction()) {
			case MotionEvent.ACTION_DOWN:
				mRect.set(getLeft(), getTop(), getRight(), getBottom());
				mIsDown = true;
				invalidate();
				break;
			case MotionEvent.ACTION_CANCEL:
			case MotionEvent.ACTION_UP:
				mIsDown = false;
				invalidate();
				break;
			case MotionEvent.ACTION_MOVE:
				if (mRect.contains(getLeft() + (int) e.getX(), getTop() + (int) e.getY())) break;
				if (mIsDown) {
					mIsDown = false;
					invalidate();
				}
				break;
		}
		return super.onTouchEvent(e);		
	}
	
	@Override
	protected void onDraw(final Canvas canvas) {
		super.onDraw(canvas);
		if (mIsDown && isClickable() && isEnabled()) {
			canvas.drawColor(mHightlightColor);
		}
	}

}
