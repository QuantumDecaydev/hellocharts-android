package lecho.lib.hellocharts;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import lecho.lib.hellocharts.anim.ChartAnimator;
import lecho.lib.hellocharts.anim.ChartAnimatorV11;
import lecho.lib.hellocharts.anim.ChartAnimatorV8;
import lecho.lib.hellocharts.model.AnimatedValue;
import lecho.lib.hellocharts.model.Axis;
import lecho.lib.hellocharts.model.ChartData;
import lecho.lib.hellocharts.model.InternalLineChartData;
import lecho.lib.hellocharts.model.InternalSeries;
import lecho.lib.hellocharts.utils.Config;
import lecho.lib.hellocharts.utils.Utils;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Cap;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

public class LineChart extends View {
	private static final String TAG = "LineChart";
	private static final float LINE_SMOOTHNES = 0.16f;
	private static final int DEFAULT_LINE_WIDTH_DP = 2;
	private static final int DEFAULT_POINT_RADIUS_DP = 6;
	private static final int DEFAULT_POINT_TOUCH_RADIUS_DP = 12;
	private static final int DEFAULT_POINT_PRESSED_RADIUS = DEFAULT_POINT_RADIUS_DP + 4;
	private static final int DEFAULT_TEXT_SIZE_DP = 14;
	private static final int DEFAULT_TEXT_COLOR = Color.WHITE;
	private static final int DEFAULT_AXIS_COLOR = Color.LTGRAY;
	private static final int DEFAULT_AREA_TRANSPARENCY = 64;
	private int mCommonMargin = 0;
	private Path mLinePath = new Path();
	private Paint mLinePaint = new Paint();
	private Paint mTextPaint = new Paint();
	private InternalLineChartData mData;
	private float mLineWidth;
	private int mPointRadius;
	private int mPointPressedRadius;
	private float mTouchRadius;
	private float mPixelPerXValue;
	private float mPixelPerYValue;
	private int mYAxisMargin = 0;
	private int mXAxisMargin = 0;
	private boolean mLinesOn = true;
	private boolean mInterpolationOn = true;
	private boolean mPointsOn = true;
	private boolean mPopupsOn = false;
	private boolean mAxesOn = true;
	private ChartAnimator mAnimator;
	private int mSelectedSeriesIndex = Integer.MIN_VALUE;
	private int mSelectedValueIndex = Integer.MIN_VALUE;
	private OnPointClickListener mOnPointClickListener = new DummyOnPointListener();
	public float mZoomLevel = 0.0f;

	/**
	 * The current area (in pixels) for chart data, including mCoomonMargin. Labels are drawn outside this area.
	 */
	private Rect mContentArea = new Rect();
	/**
	 * This rectangle represents the currently visible chart values ranges. The currently visible chart X values are
	 * from this rectangle's left to its right. The currently visible chart Y values are from this rectangle's top to
	 * its bottom.
	 * <p>
	 * Note that this rectangle's top is actually the smaller Y value, and its bottom is the larger Y value. Since the
	 * chart is drawn onscreen in such a way that chart Y values increase towards the top of the screen (decreasing
	 * pixel Y positions), this rectangle's "top" is drawn above this rectangle's "bottom" value.
	 * 
	 */
	private RectF mCurrentViewport = new RectF();
	private ScaleGestureDetector mScaleGestureDetector = new ScaleGestureDetector(getContext(),
			new ChartScaleGestureListener());

	public LineChart(Context context) {
		super(context);
		initAttributes();
		initPaints();
		initAnimatiors();
	}

	public LineChart(Context context, AttributeSet attrs) {
		super(context, attrs);
		initAttributes();
		initPaints();
		initAnimatiors();
	}

	public LineChart(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		initAttributes();
		initPaints();
		initAnimatiors();
	}

	private void initAttributes() {
		mLineWidth = Utils.dp2px(getContext(), DEFAULT_LINE_WIDTH_DP);
		mPointRadius = Utils.dp2px(getContext(), DEFAULT_POINT_RADIUS_DP);
		mPointPressedRadius = Utils.dp2px(getContext(), DEFAULT_POINT_PRESSED_RADIUS);
		mTouchRadius = Utils.dp2px(getContext(), DEFAULT_POINT_TOUCH_RADIUS_DP);
		mCommonMargin = Utils.dp2px(getContext(), DEFAULT_POINT_PRESSED_RADIUS + 4);
	}

	private void initPaints() {
		mLinePaint.setAntiAlias(true);
		mLinePaint.setStyle(Paint.Style.STROKE);
		mLinePaint.setStrokeCap(Cap.ROUND);

		mTextPaint.setAntiAlias(true);
		mTextPaint.setStyle(Paint.Style.FILL);
		mTextPaint.setStrokeWidth(1);
		mTextPaint.setTextSize(Utils.dp2px(getContext(), DEFAULT_TEXT_SIZE_DP));

	}

	private void initAnimatiors() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
			mAnimator = new ChartAnimatorV8(this, Config.DEFAULT_ANIMATION_DURATION);
		} else {
			mAnimator = new ChartAnimatorV11(this, Config.DEFAULT_ANIMATION_DURATION);
		}
	}

	@Override
	protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
		super.onSizeChanged(width, height, oldWidth, oldHeight);
		// TODO mPointRadus can change, recalculate in setter
		calculateContentArea();
		// TODO max-min can chage - recalculate in setter
		calculatePixelsPerValue();
	}

	/**
	 * Calculates available width and height. Should be called when chart dimensions or chart data change.
	 */
	private void calculateContentArea() {
		mContentArea.set(getPaddingLeft() + mYAxisMargin, getPaddingTop(), getWidth() - getPaddingRight(), getHeight()
				- getPaddingBottom() - mXAxisMargin);
	}

	/**
	 * Calculates multipliers used to translate values into pixels. Should be called when chart dimensions or chart data
	 * change.
	 */
	private void calculatePixelsPerValue() {
		mCurrentViewport.set(mData.getMinXValue(), mData.getMinYValue(), mData.getMaxXValue(), mData.getMaxYValue());
		mPixelPerXValue = (mContentArea.width() - 2 * mCommonMargin) / mCurrentViewport.width();
		mPixelPerYValue = (mContentArea.height() - 2 * mCommonMargin) / mCurrentViewport.height();
	}

	private void calculateYAxisMargin() {
		if (mAxesOn) {
			final Rect textBounds = new Rect();
			final String text;
			// TODO: check if axis has at least one element.
			final int axisSize = mData.getYAxis().getValues().size();
			final Axis yAxis = mData.getYAxis();
			if (Math.abs(yAxis.getValues().get(0)) > Math.abs(yAxis.getValues().get(axisSize - 1))) {
				text = getAxisValueToDraw(yAxis, yAxis.getValues().get(0), 0);
			} else {
				text = getAxisValueToDraw(yAxis, yAxis.getValues().get(axisSize - 1), axisSize - 1);
			}
			mTextPaint.getTextBounds(text, 0, text.length(), textBounds);
			mYAxisMargin = textBounds.width();
		} else {
			mYAxisMargin = 0;
		}
	}

	private void calculateXAxisMargin() {
		if (mAxesOn) {
			final Rect textBounds = new Rect();
			// Hard coded only for text height calculation.
			mTextPaint.getTextBounds("X", 0, 1, textBounds);
			mXAxisMargin = textBounds.height();
		} else {
			mYAxisMargin = 0;
		}
	}

	// Automatically calculates Y axis values.
	private Axis calculateYAxis(int numberOfSteps) {
		if (numberOfSteps < 2) {
			throw new IllegalArgumentException("Number or steps have to be grater or equal 2");
		}
		List<Float> values = new ArrayList<Float>();
		final float range = mData.getMaxYValue() - mData.getMinYValue();
		final float tickRange = range / (numberOfSteps - 1);
		final float x = (float) Math.ceil(Math.log10(tickRange) - 1);
		final float pow10x = (float) Math.pow(10, x);
		final float roundedTickRange = (float) Math.ceil(tickRange / pow10x) * pow10x;
		float value = mData.getMinYValue();
		while (value <= mData.getMaxYValue()) {
			values.add(value);
			value += roundedTickRange;
		}
		Axis yAxis = new Axis();
		yAxis.setValues(values);
		return yAxis;
	}

	@Override
	protected void onDraw(Canvas canvas) {
		long time = System.nanoTime();
		if (mAxesOn) {
			drawXAxis(canvas);
			drawYAxis(canvas);
		}
		if (mLinesOn) {
			drawLines(canvas);
		}
		if (mPointsOn) {
			drawPoints(canvas);
		}

		Log.v(TAG, "onDraw [ms]: " + (System.nanoTime() - time) / 1000000f);
	}

	private void drawXAxis(Canvas canvas) {
		mLinePaint.setStrokeWidth(1);
		mLinePaint.setColor(DEFAULT_AXIS_COLOR);
		mTextPaint.setColor(DEFAULT_AXIS_COLOR);
		mTextPaint.setTextAlign(Align.CENTER);
		final int xAxisBaseline = mContentArea.bottom + mXAxisMargin;
		canvas.drawLine(mContentArea.left, mContentArea.bottom - mCommonMargin, mContentArea.right, mContentArea.bottom
				- mCommonMargin, mLinePaint);
		Axis xAxis = mData.getXAxis();
		int index = 0;
		for (float x : xAxis.getValues()) {
			final String text = getAxisValueToDraw(xAxis, x, index);
			// TODO: check if raw x > contentArea.left
			canvas.drawText(text, calculatePixelX(x), xAxisBaseline, mTextPaint);
			++index;
		}
	}

	private void drawYAxis(Canvas canvas) {
		mLinePaint.setStrokeWidth(1);
		mLinePaint.setColor(DEFAULT_AXIS_COLOR);
		mTextPaint.setColor(DEFAULT_AXIS_COLOR);
		mTextPaint.setTextAlign(Align.RIGHT);
		final int yAxisRightAlign = mContentArea.left;
		Axis yAxis = mData.getYAxis();
		int index = 0;
		for (float y : yAxis.getValues()) {
			// Draw only if y is in chart range
			if (y >= mData.getMinYValue() && y <= mData.getMaxYValue()) {
				final String text = getAxisValueToDraw(yAxis, y, index);
				float rawY = calculatePixelY(y);
				canvas.drawLine(mContentArea.left, rawY, mContentArea.right, rawY, mLinePaint);
				canvas.drawText(text, yAxisRightAlign, rawY, mTextPaint);
			}
			++index;
		}
	}

	private String getAxisValueToDraw(Axis axis, Float value, int index) {
		if (axis.isStringAxis()) {
			return axis.getStringValues().get(index);
		} else {
			return String.format(axis.getValueFormatter(), value);
		}
	}

	private void drawLines(Canvas canvas) {
		mLinePaint.setStrokeWidth(mLineWidth);
		for (InternalSeries internalSeries : mData.getInternalsSeries()) {
			if (mInterpolationOn) {
				drawSmoothPath(canvas, internalSeries);
			} else {
				drawPath(canvas, internalSeries);
			}
			mLinePath.reset();
		}
	}

	// TODO Drawing points can be done in the same loop as drawing lines but it may cause problems in the future. Reuse
	// calculated X/Y;
	private void drawPoints(Canvas canvas) {
		for (InternalSeries internalSeries : mData.getInternalsSeries()) {
			mTextPaint.setColor(internalSeries.getColor());
			int valueIndex = 0;
			for (float valueX : mData.getDomain()) {
				final float rawValueX = calculatePixelX(valueX);
				final float valueY = internalSeries.getValues().get(valueIndex).getPosition();
				final float rawValueY = calculatePixelY(valueY);
				canvas.drawCircle(rawValueX, rawValueY, mPointRadius, mTextPaint);
				if (mPopupsOn) {
					drawValuePopup(canvas, mPointRadius, valueY, rawValueX, rawValueY);
				}
				++valueIndex;
			}
		}
		if (mSelectedSeriesIndex >= 0 && mSelectedValueIndex >= 0) {
			final float valueX = mData.getDomain().get(mSelectedValueIndex);
			final float rawValueX = calculatePixelX(valueX);
			final float valueY = mData.getInternalsSeries().get(mSelectedSeriesIndex).getValues()
					.get(mSelectedValueIndex).getPosition();
			final float rawValueY = calculatePixelY(valueY);
			mTextPaint.setColor(mData.getInternalsSeries().get(mSelectedSeriesIndex).getColor());
			canvas.drawCircle(rawValueX, rawValueY, mPointPressedRadius, mTextPaint);
			if (mPopupsOn) {
				drawValuePopup(canvas, mPointRadius, valueY, rawValueX, rawValueY);
			}
		}
	}

	private void drawValuePopup(Canvas canvas, float offset, float valueY, float rawValueX, float rawValueY) {
		mTextPaint.setTextAlign(Align.LEFT);
		final String text = String.format(Locale.ENGLISH, Config.DEFAULT_VALUE_FORMAT, valueY);
		final Rect textBounds = new Rect();
		mTextPaint.getTextBounds(text, 0, text.length(), textBounds);
		float left = rawValueX + offset;
		float right = rawValueX + offset + textBounds.width() + mCommonMargin * 2;
		float top = rawValueY - offset - textBounds.height() - mCommonMargin * 2;
		float bottom = rawValueY - offset;
		if (top < getPaddingTop() + mPointPressedRadius) {
			top = rawValueY + offset;
			bottom = rawValueY + offset + textBounds.height() + mCommonMargin * 2;
		}
		if (right > getWidth() - getPaddingRight() - mPointPressedRadius) {
			left = rawValueX - offset - textBounds.width() - mCommonMargin * 2;
			right = rawValueX - offset;
		}
		final RectF popup = new RectF(left, top, right, bottom);
		canvas.drawRoundRect(popup, mCommonMargin, mCommonMargin, mTextPaint);
		final int color = mTextPaint.getColor();
		mTextPaint.setColor(DEFAULT_TEXT_COLOR);
		canvas.drawText(text, left + mCommonMargin, bottom - mCommonMargin, mTextPaint);
		mTextPaint.setColor(color);
	}

	private void drawPath(Canvas canvas, final InternalSeries internalSeries) {
		int valueIndex = 0;
		for (float valueX : mData.getDomain()) {
			final float rawValueX = calculatePixelX(valueX);
			final float rawValueY = calculatePixelY(internalSeries.getValues().get(valueIndex).getPosition());
			if (valueIndex == 0) {
				mLinePath.moveTo(rawValueX, rawValueY);
			} else {
				mLinePath.lineTo(rawValueX, rawValueY);
			}
			++valueIndex;
		}
		mLinePaint.setColor(internalSeries.getColor());
		canvas.drawPath(mLinePath, mLinePaint);
		drawArea(canvas);
	}

	private void drawSmoothPath(Canvas canvas, final InternalSeries internalSeries) {
		final int domainSize = mData.getDomain().size();
		float previousPointX = Float.NaN;
		float previousPointY = Float.NaN;
		float currentPointX = Float.NaN;
		float currentPointY = Float.NaN;
		float nextPointX = Float.NaN;
		float nextPointY = Float.NaN;
		for (int valueIndex = 0; valueIndex < domainSize - 1; ++valueIndex) {
			if (Float.isNaN(currentPointX)) {
				currentPointX = calculatePixelX(mData.getDomain().get(valueIndex));
				currentPointY = calculatePixelY(internalSeries.getValues().get(valueIndex).getPosition());
			}
			if (Float.isNaN(previousPointX)) {
				if (valueIndex > 0) {
					previousPointX = calculatePixelX(mData.getDomain().get(valueIndex - 1));
					previousPointY = calculatePixelY(internalSeries.getValues().get(valueIndex - 1).getPosition());
				} else {
					previousPointX = currentPointX;
					previousPointY = currentPointY;
				}
			}
			if (Float.isNaN(nextPointX)) {
				nextPointX = calculatePixelX(mData.getDomain().get(valueIndex + 1));
				nextPointY = calculatePixelY(internalSeries.getValues().get(valueIndex + 1).getPosition());
			}
			// afterNextPoint is always new one or it is equal nextPoint.
			final float afterNextPointX;
			final float afterNextPointY;
			if (valueIndex < domainSize - 2) {
				afterNextPointX = calculatePixelX(mData.getDomain().get(valueIndex + 2));
				afterNextPointY = calculatePixelY(internalSeries.getValues().get(valueIndex + 2).getPosition());
			} else {
				afterNextPointX = nextPointX;
				afterNextPointY = nextPointY;
			}
			// Calculate control points.
			final float firstDiffX = (nextPointX - previousPointX);
			final float firstDiffY = (nextPointY - previousPointY);
			final float secondDiffX = (afterNextPointX - currentPointX);
			final float secondDiffY = (afterNextPointY - currentPointY);
			final float firstControlPointX = currentPointX + (LINE_SMOOTHNES * firstDiffX);
			final float firstControlPointY = currentPointY + (LINE_SMOOTHNES * firstDiffY);
			final float secondControlPointX = nextPointX - (LINE_SMOOTHNES * secondDiffX);
			final float secondControlPointY = nextPointY - (LINE_SMOOTHNES * secondDiffY);
			// Move to start point.
			if (valueIndex == 0) {
				mLinePath.moveTo(currentPointX, currentPointY);
			}
			mLinePath.cubicTo(firstControlPointX, firstControlPointY, secondControlPointX, secondControlPointY,
					nextPointX, nextPointY);
			// Shift values to prevent recalculation of values that have been already calculated.
			previousPointX = currentPointX;
			previousPointY = currentPointY;
			currentPointX = nextPointX;
			currentPointY = nextPointY;
			nextPointX = afterNextPointX;
			nextPointY = afterNextPointY;
		}
		mLinePaint.setColor(internalSeries.getColor());
		canvas.drawPath(mLinePath, mLinePaint);
		drawArea(canvas);
	}

	private void drawArea(Canvas canvas) {
		// TODO: avoid coordinates recalculations
		final float rawStartValueX = calculatePixelX(mData.getDomain().get(0));
		final float rawStartValueY = calculatePixelY(mData.getMinYValue());
		final float rawEndValueX = calculatePixelX(mData.getDomain().get(mData.getDomain().size() - 1));
		final float rawEntValueY = rawStartValueY;
		mLinePaint.setStyle(Paint.Style.FILL);
		mLinePaint.setAlpha(DEFAULT_AREA_TRANSPARENCY);
		mLinePath.lineTo(rawEndValueX, rawEntValueY);
		mLinePath.lineTo(rawStartValueX, rawStartValueY);
		mLinePath.close();
		canvas.drawPath(mLinePath, mLinePaint);
		mLinePaint.setStyle(Paint.Style.STROKE);
	}

	private int calculatePixelX(float valueX) {
		final int pixelOffset = Math.round((valueX - mData.getMinXValue()) * (mPixelPerXValue * (1 + 2 * mZoomLevel)));
		return mContentArea.left + mCommonMargin + pixelOffset - (int) (mContentArea.width() * mZoomLevel);
	}

	private int calculatePixelY(float valueY) {
		final int pixelOffset = Math.round((valueY - mData.getMinYValue()) * (mPixelPerYValue * (1 + 2 * mZoomLevel)));
		// Subtracting from height because on android top left corner is 0,0 and bottom right is maxX,maxY.
		return mContentArea.bottom - mCommonMargin - pixelOffset + (int) (mContentArea.height() * mZoomLevel);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		mScaleGestureDetector.onTouchEvent(event);
		if (!mPointsOn) {
			// No point - no touch events.
			return true;
		}
		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
			// Select only the first value within touched area.
			// Reverse loop to starts with the line drawn on top.
			for (int seriesIndex = mData.getInternalsSeries().size() - 1; seriesIndex >= 0; --seriesIndex) {
				int valueIndex = 0;
				for (AnimatedValue value : mData.getInternalsSeries().get(seriesIndex).getValues()) {
					final float rawX = calculatePixelX(mData.getDomain().get(valueIndex));
					final float rawY = calculatePixelY(value.getPosition());
					if (Utils.isInArea(rawX, rawY, event.getX(), event.getY(), mTouchRadius)) {
						mSelectedSeriesIndex = seriesIndex;
						mSelectedValueIndex = valueIndex;
						invalidate();
						return true;
					}
					++valueIndex;
				}
			}
			return true;
		case MotionEvent.ACTION_UP:
			// If value was selected call click listener and clear selection.
			if (mSelectedValueIndex >= 0) {
				final float x = mData.getDomain().get(mSelectedValueIndex);
				final float y = mData.getInternalsSeries().get(mSelectedSeriesIndex).getValues()
						.get(mSelectedValueIndex).getPosition();
				mOnPointClickListener.onPointClick(mSelectedSeriesIndex, mSelectedValueIndex, x, y);
				mSelectedSeriesIndex = Integer.MIN_VALUE;
				mSelectedValueIndex = Integer.MIN_VALUE;
				invalidate();
			}
			return true;
		case MotionEvent.ACTION_MOVE:
			// Clear selection if user is now touching outside touch area.
			if (mSelectedValueIndex >= 0) {
				final float x = mData.getDomain().get(mSelectedValueIndex);
				final float y = mData.getInternalsSeries().get(mSelectedSeriesIndex).getValues()
						.get(mSelectedValueIndex).getPosition();
				final float rawX = calculatePixelX(x);
				final float rawY = calculatePixelY(y);
				if (!Utils.isInArea(rawX, rawY, event.getX(), event.getY(), mTouchRadius)) {
					mSelectedSeriesIndex = Integer.MIN_VALUE;
					mSelectedValueIndex = Integer.MIN_VALUE;
					invalidate();
				}
			}
			return true;
		case MotionEvent.ACTION_CANCEL:
			// Clear selection
			if (mSelectedValueIndex >= 0) {
				mSelectedSeriesIndex = Integer.MIN_VALUE;
				mSelectedValueIndex = Integer.MIN_VALUE;
				invalidate();
			}
			return true;
		default:
			return true;
		}
	}

	public void setData(final ChartData rawData) {
		mData = InternalLineChartData.createFromRawData(rawData);
		mData.calculateRanges();
		calculateYAxisMargin();
		calculateXAxisMargin();
		calculateContentArea();
		calculatePixelsPerValue();
		postInvalidate();
	}

	public ChartData getData() {
		return mData.getRawData();
	}

	public void animationUpdate(float scale) {
		for (AnimatedValue value : mData.getInternalsSeries().get(0).getValues()) {
			value.update(scale);
		}
		mData.calculateYRanges();
		calculateYAxisMargin();
		calculateXAxisMargin();
		calculateContentArea();
		calculatePixelsPerValue();
		invalidate();
	}

	public void animateSeries(int index, List<Float> values) {
		mAnimator.cancelAnimation();
		mData.updateSeriesTargetPositions(index, values);
		mAnimator.startAnimation();
	}

	public void updateSeries(int index, List<Float> values) {
		mData.updateSeries(index, values);
		invalidate();
	}

	public void setOnPointClickListener(OnPointClickListener listener) {
		if (null == listener) {
			mOnPointClickListener = new DummyOnPointListener();
		} else {
			mOnPointClickListener = listener;
		}
	}

	// Just empty listener to avoid NPE checks.
	private static class DummyOnPointListener implements OnPointClickListener {

		@Override
		public void onPointClick(int selectedSeriesIndex, int selectedValueIndex, float x, float y) {
			// Do nothing.
		}

	}

	private class ChartScaleGestureListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
		private float mScaleFactor = 1.0f;

		@Override
		public boolean onScaleBegin(ScaleGestureDetector detector) {
			return true;
		}

		@Override
		public void onScaleEnd(ScaleGestureDetector detector) {

		}

		@Override
		public boolean onScale(ScaleGestureDetector detector) {
			mScaleFactor = (mScaleFactor / detector.getScaleFactor());
			mZoomLevel = -(1 - mScaleFactor);
			ViewCompat.postInvalidateOnAnimation(LineChart.this);
			return true;
		}
	}

}
