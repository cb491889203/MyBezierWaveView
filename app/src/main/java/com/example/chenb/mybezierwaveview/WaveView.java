package com.example.chenb.mybezierwaveview;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewTreeObserver;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class WaveView extends View {

	private Context context;

	private static final int STOPPED = 65;

	/** 每次刷新屏幕的时间,单位为毫秒,时间越小,则波纹的速度就越快 */
	private static final int FLUSHTIME = 10;

	/** View宽度 */
	private int mViewWidth;

	/** View高度 */
	private int mViewHeight;

	/** 波纹数量 */
	private int mWaveCount;

	/** 波纹速度,默认为5个像素,及每刷新一次屏幕波纹移动5个像素.值增加,则波纹移动越快 */
	private float mWaveSpeed;

	/** 波纹速度差 */
	private float mWaveSpeedOffset;

	/** 水位线  波纹的水平线的高度 */
	private float mLevelLine;

	/** 波浪起伏幅度 波峰高度 */
	private float mWaveHeight = 0;

	/** 贝塞尔曲线控制点的高度,以水平线高度为0计算. 在这里是规则的曲线,所以控制点高度为波峰的2倍*/
	private int mControllerPointHeight;

	/** 波长 */
	private float mWaveWidth = 0;

	/** 被隐藏的最左边的波形 */
	private float mLeftSide;

	/** 保存多条波纹被隐藏的最左边的波形 */
	private float[] mLeftSides;

	/** 保存多条波纹移动的距离 */
	private float[] mMoveLens;

	/** 存放多条波浪的速度 */
	private float[] mWaveSpeeds;

	/** 多条波纹的路径图 */
	private Path[] mWavePaths;

	/** 绘制波纹的画笔,多条波纹共用一个画笔 */
	private Paint mPaint;

	/** 存放多条波浪的贝塞尔曲线的点 */
	private List<List<Point>> mPointsLists = new ArrayList<>();

	/** 循环绘制的计时器 */
	private Timer timer;

	/** 循环绘制任务 */
	private MyTimerTask mTask;

	/** 数据是否已经加载完成(加载真实的数据,而不是预加载),保证只初始化一次,节约资源 */
	private boolean isDataSetted = false;

	/** 波纹的颜色 */
	private int waveColor;

	/** 波纹渐变色时的中心颜色 */
	private int waveBeginColor;

	/** 波纹渐变色时边缘的颜色 */
	private int waveEndColor;

	/** 波纹是否停止滚动,true:手动停止了 */
	private boolean isStopped;

	/** 是否重置了波纹数据,true:重置了 */
	private boolean isResetted = false;

	/** 循环绘制时的任务处理 */
	Handler updateHandler = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			for (int k = 0; k < mWaveCount; k++) {
				List<Point> pointList = mPointsLists.get(k);
				// 记录平移总位移
				mMoveLens[k] += mWaveSpeeds[k];
				mLeftSides[k] += mWaveSpeeds[k];
				// 波形平移
				for (int i = 0; i < pointList.size(); i++) {
					Point point = pointList.get(i);
					point.setX(point.getX() + mWaveSpeeds[k]);
					switch (i % 4) {
						case 0:
						case 2:
							point.setY(mLevelLine);
							break;
						case 1:
							point.setY(mLevelLine + mControllerPointHeight);
							break;
						case 3:
							point.setY(mLevelLine - mControllerPointHeight);
							break;
					}
				}
				if (mMoveLens[k] >= mWaveWidth) {
					// 波形平移超过一个完整波形后复位
					mMoveLens[k] = 0;
					resetPoints(k);
				}

				//如果停止了,需要判断当前波峰是否重叠,重叠了就继续滚动直到不重叠时才停止
				if (isStopped && Math.abs(mMoveLens[0] - mMoveLens[1]) > 200 && Math.abs(mMoveLens[1] - mMoveLens[2]) > 200 && Math.abs
						(mMoveLens[0] - mMoveLens[2]) > 200) {
					if (mTask != null) {
						mTask.cancel();
					}
				} else {
					invalidate();
				}
			}
		}
	};

	public WaveView(Context context) {
		this(context, null);
	}

	public WaveView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public WaveView(final Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		this.context = context;
		getAttr(context, attrs, defStyle);
		initPoints();
	}

	/**
	 获取自定义的属性值

	 @param attrs
	 */
	private void getAttr(Context context, AttributeSet attrs, int defStyle) {

		TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.WaveView, defStyle, 0);

		waveColor = a.getColor(R.styleable.WaveView_wave_wavecolor, getResources().getColor(R.color.colorPrimary));
		waveBeginColor = a.getColor(R.styleable.WaveView_wave_begin_color, getResources().getColor(R.color.colorPrimary));
		waveEndColor = a.getColor(R.styleable.WaveView_wave_end_color, getResources().getColor(R.color.colorPrimary));
		mWaveSpeed = a.getInteger(R.styleable.WaveView_wave_speed, 5);
		mWaveHeight = a.getDimension(R.styleable.WaveView_wave_height, 0);
		mLevelLine = a.getDimension(R.styleable.WaveView_wave_level_height, 0);
		mWaveSpeedOffset = a.getFloat(R.styleable.WaveView_wave_speed_offset, 0.7f);
		mWaveCount = a.getInteger(R.styleable.WaveView_wave_count, 3);
		a.recycle();
	}

	/**
	 初始化波浪的上的各个点 利用viewtree获取准确的控件高度和宽度,在onMeasure方法中获取mViewHeight = getMeasuredHeight();出现bug,
	 获取到的mViewHeight是整个屏幕的高度,其原因不明.
	 */
	private void initPoints() {
		this.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
			@Override
			public void onGlobalLayout() {
				if (!isDataSetted) {
					isDataSetted = true;
					mViewHeight = getHeight();
					mViewWidth = getWidth();
					initData();
					startWave(); //初始化贝塞尔点后,开始循环绘制
				}
			}
		});
	}

	/** 绘制波纹,开始移动,如果已经开始了,那么会先停止之前的,重新设置画笔\波纹数据,再重新开始 */
	public void startWave() {
		if (mTask != null) {
			mTask.cancel();
			mTask = null;
		}
		//如果数据有重置,则重新初始化数据
		if (isResetted) {
			isResetted = false;
			initData();
		}
		mTask = new MyTimerTask(updateHandler);
		timer.schedule(mTask, 0, FLUSHTIME); //每FLUSHTIME毫秒刷新一次,如果增加时间,则波纹移动速度变慢,反之变快.
		isStopped = false;
	}

	public void stopWave() {
		//标记flag,再handler中刷新绘制时进行判断
		isStopped = true;
	}

	/** 初始化 画笔 波纹等数据 */
	private void initData() {
		timer = new Timer();
		//波纹，下面填充颜色
		mPaint = new Paint();
		mPaint.setAntiAlias(true);
		mPaint.setStyle(Paint.Style.FILL_AND_STROKE);
		mPaint.setColor(waveColor);
		//渐变色
		Shader newShader = new LinearGradient(0, 0, 0, mViewHeight, new int[]{waveBeginColor, waveEndColor}, new float[]{0.0f, 1.0f},
				Shader.TileMode.CLAMP);
		mPaint.setShader(newShader);

		//如果没有手动指定波峰高度和水平线,则取控件高度的一半.
		// 波长等于四倍View宽度也就是View中只能看到四分之一个波形，这样可以使起伏更明显
		mWaveHeight = mWaveHeight == 0 ? mViewWidth / 20 : mWaveHeight;
		mLevelLine = mLevelLine == 0 ? mViewHeight / 2 : mLevelLine;
		mWaveWidth = mViewWidth;
		mControllerPointHeight = (int)(mWaveHeight * 2);
		// 左边隐藏的距离预留一个波形
		mLeftSide = -mWaveWidth;

		//多条波浪设置
		if (mWaveSpeed < 0) {
			mWaveSpeed = Math.abs(mWaveSpeed); //速度不能小于0
		}
		mLeftSides = new float[mWaveCount];
		mMoveLens = new float[mWaveCount];
		mWavePaths = new Path[mWaveCount];
		mWaveSpeeds = new float[mWaveCount];

		//分别添加波浪的贝塞尔控制点
		mPointsLists.clear();
		for (int k = 0; k < mWaveCount; k++) {
			if (k == 0) {
				mWaveSpeeds[k] = mWaveSpeed;
			} else {
				mWaveSpeeds[k] = mWaveSpeeds[k - 1] * (1 + mWaveSpeedOffset);
			}
			mWavePaths[k] = new Path();
			mLeftSides[k] = mLeftSide; //3条波纹分别设置左边预留的波形
			List<Point> pointList = new ArrayList<>();
			// 这里计算在可见的View宽度中能容纳几个波形，注意n上取整
			int n = (int) Math.round(mViewWidth / mWaveWidth + 0.5);
			// n个波形需要4n+1个点，但是我们要预留一个波形在左边隐藏区域，所以需要4n+5个点
			for (int i = 0; i < (4 * n + 5); i++) {
				// 从P0开始初始化到P4n+4，总共4n+5个点
				float x = i * mWaveWidth / 4 - mWaveWidth;
				float y = 0;
				switch (i % 4) {
					case 0:
					case 2:
						// 零点位于水位线上
						y = mLevelLine;
						break;
					case 1:
						// 往下波动的控制点
						y = mLevelLine + mControllerPointHeight;
						break;
					case 3:
						// 往上波动的控制点
						y = mLevelLine - mControllerPointHeight;
						break;
				}
				pointList.add(new Point(x, y));
			}
			mPointsLists.add(pointList);
		}
	}

	/**
	 所有点的x坐标都还原到初始状态，也就是一个周期前的状态
	 */
	private void resetPoints(int k) {
		mLeftSides[k] = -mWaveWidth;
		for (int i = 0; i < mPointsLists.get(k).size(); i++) {
			mPointsLists.get(k).get(i).setX(i * mWaveWidth / 4 - mWaveWidth);
		}
	}

	@Override
	public void onWindowFocusChanged(boolean hasWindowFocus) {
		super.onWindowFocusChanged(hasWindowFocus);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		if (!isDataSetted) {
			initData(); //先在onDraw中初始化一次(这里只是预加载). 在ViewTreeObserver中获取到真实的view宽高后再次初始化数据
		}
		if (!mPointsLists.isEmpty()) {
			//循环绘制3条波浪
			for (int k = 0; k < mWaveCount; k++) {
				Path mWavePath = mWavePaths[k];
				List<Point> pointList = mPointsLists.get(k);
				mWavePath.reset();
				int i = 0;
				mWavePath.moveTo(pointList.get(0).getX(), pointList.get(0).getY());
				for (; i < pointList.size() - 2; i = i + 2) {
					mWavePath.quadTo(pointList.get(i + 1).getX(), pointList.get(i + 1).getY(), pointList.get(i + 2)
							.getX(), pointList.get(i + 2).getY());
				}
				mWavePath.lineTo(pointList.get(i).getX(), 0);
				mWavePath.lineTo(mLeftSides[k], 0);
				mWavePath.close();

				// mPaint的Style是FILL，会填充整个Path区域
				canvas.drawPath(mWavePath, mPaint);
			}
		}
	}

	public int getmWaveCount() {
		return mWaveCount;
	}

	public void setmWaveCount(int mWaveCount) {
		if (this.mWaveCount != mWaveCount) {
			this.mWaveCount = mWaveCount;
			isResetted = true;
		}
	}

	public float getmWaveSpeed() {
		return mWaveSpeed;
	}

	public void setmWaveSpeed(float mWaveSpeed) {
		if (this.mWaveSpeed != mWaveSpeed) {
			this.mWaveSpeed = mWaveSpeed;
			isResetted = true;
		}
	}

	public float getmWaveSpeedOffset() {
		return mWaveSpeedOffset;
	}

	public void setmWaveSpeedOffset(float mWaveSpeedOffset) {
		if (this.mWaveSpeedOffset != mWaveSpeedOffset) {
			this.mWaveSpeedOffset = mWaveSpeedOffset;
			isResetted = true;
		}
	}

	public float getmLevelLine() {
		return mLevelLine;
	}

	public void setmLevelLine(float mLevelLine) {
		if (this.mLevelLine != mLevelLine) {
			this.mLevelLine = mLevelLine;
			isResetted = true;
		}
	}

	public float getmWaveHeight() {
		return mWaveHeight;
	}

	public void setmWaveHeight(float mWaveHeight) {
		if (this.mWaveHeight != mWaveHeight) {
			this.mWaveHeight = mWaveHeight;
			isResetted = true;
		}
	}

	public int getWaveColor() {
		return waveColor;
	}

	public void setWaveColor(int waveColor) {
		if (this.waveColor != waveColor) {
			this.waveColor = waveColor;
			isResetted = true;
		}
	}

	public int getWaveBeginColor() {
		return waveBeginColor;
	}

	public void setWaveBeginColor(int waveBeginColor) {
		if (this.waveBeginColor != waveBeginColor) {
			this.waveBeginColor = waveBeginColor;
			isResetted = true;
		}
	}

	public int getWaveEndColor() {
		return waveEndColor;
	}

	public void setWaveEndColor(int waveEndColor) {
		if (this.waveEndColor != waveEndColor) {
			this.waveEndColor = waveEndColor;
			isResetted = true;
		}
	}

	/**
	 dip转为PX
	 */
	public static int dp2px(Context context, float dipValue) {
		return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dipValue, context.getResources().getDisplayMetrics());
	}

	class MyTimerTask extends TimerTask {

		Handler handler;

		public MyTimerTask(Handler handler) {
			this.handler = handler;
		}

		@Override
		public void run() {
			handler.sendMessage(handler.obtainMessage());
		}

	}

	class Point {

		private float x;

		private float y;

		public float getX() {
			return x;
		}

		public void setX(float x) {
			this.x = x;
		}

		public float getY() {
			return y;
		}

		public void setY(float y) {
			this.y = y;
		}

		public Point() {
		}

		public Point(float x, float y) {
			this.x = x;
			this.y = y;
			Log.i("WaveViewDemo", "( " + x + " , " + y + " )");
		}

	}

}

