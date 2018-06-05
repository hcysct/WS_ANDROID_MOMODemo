package project.android.imageprocessing.input;import android.annotation.TargetApi;import android.content.Context;import android.content.pm.ActivityInfo;import android.graphics.ImageFormat;import android.graphics.SurfaceTexture;import android.graphics.SurfaceTexture.OnFrameAvailableListener;import android.hardware.Camera;import android.hardware.Camera.Parameters;import android.hardware.Camera.Size;import android.opengl.GLES11Ext;import android.opengl.GLES20;import android.opengl.GLSurfaceView;import android.os.Handler;import android.os.HandlerThread;import android.os.Looper;import android.os.Message;import android.util.Log;import android.view.SurfaceHolder;import com.faceunity.wrapper.faceunity;import com.wushuangtech.api.JniWorkerThread;import com.wushuangtech.library.Constants;import com.wushuangtech.library.GlobalHolder;import com.wushuangtech.utils.PviewLog;import com.wushuangtech.videocore.MyVideoApi;import com.wushuangtech.videocore.com.wushuangtech.fbo.FBOTextureBinder;import net.ossrs.yasea.SrsEncoder;import java.io.IOException;import java.io.InputStream;import java.io.PrintWriter;import java.io.StringWriter;import java.util.ArrayList;import java.util.Arrays;import java.util.List;import javax.microedition.khronos.opengles.GL10;import project.android.imageprocessing.entity.Effect;import project.android.imageprocessing.output.GLTextureInputRenderer;/** * A Camera input extension of CameraPreviewInput. * This class takes advantage of the android camera preview to produce new textures for processing. <p> * Note: This class requires an API level of at least 14.  To change camera parameters or get access to the * camera directly before it is used by this class, createCamera() can be override. * @author Chris Batt */@TargetApi(value = 14)public class CameraPreviewInput extends GLTextureOutputRenderer implements OnFrameAvailableListener,Camera.PreviewCallback {	private static final String UNIFORM_CAM_MATRIX = "u_Matrix";	private int mPrevWidth=120;	private int mPrevHeight=160;	private int mOutWidth=0;	private int mOutHeight=0;	private CameraSizeCb _cb=null;	private boolean bMtk = true;    private boolean mIsFirstFrameCallback;    private Context mContext = null;	private byte[] mCameraNV21Byte;	private byte[] previewCallbackBuffer;	private boolean isNeedUpdateFaceBeauty = false;	public final int[] mItemsArray = new int[ITEM_ARRAYS_COUNT];	private int mFrameId = 0;	private int mFuTextureId = -1;	public int getmPrevWidth() {		return mPrevWidth;	}	public int getmPrevHeight() {		return mPrevHeight;	}	public int getOutWidth() {		return mOutWidth;	}	public int getOutHeight() {		return mOutHeight;	}	public void setActivityOrientation(int activityOrientation) {		this.activityOrientation = activityOrientation;	}	private int activityOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;	public int getPreviewRotation() {		return mPreviewRotation;	}	public int getTextID(){        return texture_in;    }	public void setmPreviewRotation(int mPreviewRotation) {		this.mPreviewRotation = mPreviewRotation;	}	private int mPreviewRotation=0;	public Camera getCamera() {		return camera;	}	private Camera camera=null;	public Size getClsSize() {		return clsSize;	}	private Camera.Size clsSize;	private int mCamId = Camera.CameraInfo.CAMERA_FACING_FRONT;	private SurfaceTexture camTex;	private int matrixHandle;	private float[] matrix = new float[16];	private GLSurfaceView view;	private FBOTextureBinder mFBOTextureBinder;	/**	 * Creates a CameraPreviewInput which captures the camera preview with all the default camera parameters and settings.	 */	public CameraPreviewInput(Context context, GLSurfaceView view) {		super();		mContext = context;		if(view==null){			int t = 1;		}else {			this.view = view;			view.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);		}		mFuItemHandlerThread = new HandlerThread("FUItemHandlerThread");		mFuItemHandlerThread.start();		mFuItemHandler = new FUItemHandler(mFuItemHandlerThread.getLooper());	}	private void bindTexture() {		GLES20.glActiveTexture(GLES20.GL_TEXTURE0);	    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture_in);	}	public void setFBOTextureBinder(FBOTextureBinder mFBOTextureBinder){	    this.mFBOTextureBinder = mFBOTextureBinder;    }	public void SetCameraParam(MyVideoApi.VideoConfig config) {		if (camera == null)			return;		Camera.Parameters params = camera.getParameters();		mPrevWidth=config.videoWidth;		mPrevHeight=config.videoHeight;        PviewLog.i(PviewLog.TAG , "mPrevWidth : " + mPrevWidth + " | mPrevHeight : " + mPrevHeight);		if( activityOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {			mPreviewRotation = 0;		} else {            mPreviewRotation = 90;        }        // TODO 小米3 4.4.4一旦设置了闪光灯参数，将会打开摄像头失败		if (config.enabeleFrontCam) {			mCamId = Camera.CameraInfo.CAMERA_FACING_FRONT;			params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);			config.openflash=false;		} else {			mCamId = Camera.CameraInfo.CAMERA_FACING_BACK;			if (config.openflash) {				params.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);			} else {				params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);			}		}		if (params.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {			params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);		}        clsSize = getCloselyPreSize(params);		params.setPreviewSize(clsSize.width, clsSize.height);		int[] range = findClosestFpsRange(config.videoFrameRate, params.getSupportedPreviewFpsRange());		params.setPreviewFpsRange(range[0], range[1]);		params.setPreviewFormat(ImageFormat.NV21);		params.setWhiteBalance(Camera.Parameters.FOCUS_MODE_AUTO);		params.setSceneMode(Camera.Parameters.SCENE_MODE_AUTO);		camera.setParameters(params);		camera.setDisplayOrientation(mPreviewRotation);		camera.setPreviewCallbackWithBuffer(this);		previewCallbackBuffer = new byte[clsSize.width * clsSize.height * 3 / 2];		camera.addCallbackBuffer(previewCallbackBuffer);		calOutSize();		if(_cb!=null) {			_cb.startPrieview();		}	}	public int  getmCamId(){		return  mCamId;	}	public void switchCarmera(int camid){		mCamId=camid;		onResume();	}	public void SwitchFlash(boolean open) {		if(camera==null||mCamId==1)			return;		Camera.Parameters params = camera.getParameters();		String flash = params.getFlashMode();		if(open&&flash.equals(Camera.Parameters.FLASH_MODE_OFF)){			params.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);		}else{			params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);		}		camera.setParameters(params);	}	public void StartCamera(){		if(createCamera()!=null) {			reInitialize();		}	}	public void StopCamera() {	    PviewLog.d("LocalCamera stop camera : " + camera);		if(camera != null) {			camera.setPreviewCallback(null);			camera.stopPreview();			camera.release();			camera = null;            mIsFirstFrameCallback = false;			Log.e("CameraPreviewInput","========StopCamera============");		}	}	protected Camera createCamera() {        PviewLog.d("LocalCamera create camera : " + camera);		if(camera == null) {            try{                Log.e("CameraPreviewInput","========createCamera======is null======");                MyVideoApi.VideoConfig  config = MyVideoApi.getInstance().getVideoConfig();                if(!config.enabeleFrontCam)                    mCamId=0;                else                    mCamId=1;                camera=Camera.open(mCamId);                SetCameraParam(config);            } catch (Exception e) {                e.printStackTrace();            }		}		return camera;	}	/* (non-Javadoc)	 * @see project.android.imageprocessing.input.GLTextureOutputRenderer#destroy()	 */	@Override	public void destroy() {		super.destroy();		StopCamera();		if(camTex != null) {			camTex.release();			camTex = null;		}		if(texture_in != 0) {			int[] tex = new int[1];			tex[0] = texture_in;			GLES20.glDeleteTextures(1, tex, 0);			texture_in = 0;		}		mFuItemHandler.removeMessages(FUItemHandler.HANDLE_CREATE_ITEM);		Arrays.fill(mItemsArray, 0);		faceunity.fuDestroyAllItems();		faceunity.fuOnDeviceLost();		mEventQueue.clear();	}	@Override	protected void drawFrame() {		try {			camTex.updateTexImage();			prepareDrawFrame();			mFuTextureId = faceunity.fuDualInputToTexture(mCameraNV21Byte, texture_in, faceunity.FU_ADM_FLAG_EXTERNAL_OES_TEXTURE, clsSize.width, clsSize.height, mFrameId++, mItemsArray);			for(GLTextureInputRenderer target : targets) {				target.newTextureReady(mFuTextureId, this, true);			}//FU		super.drawFrame();		}catch (Exception e){			e.printStackTrace();		}	}	private int mBlurLevel = 6;	private float mColorLevel = 0.5f;	private float mCheekThinning = 0.4f;	private float mEyeEnlarging = 0.4f;	/**	 * 磨皮 范围0~6 SDK默认为 6	 * @param blurLevel	 */	public void setBlurLevel(int blurLevel) {		this.mBlurLevel = blurLevel;		isNeedUpdateFaceBeauty = true;	}	/**	 * 美白 范围0~1 SDK默认为 0.5f	 * @param colorLevel	 */	public void setColorLevel(float colorLevel) {		this.mColorLevel = colorLevel;		isNeedUpdateFaceBeauty = true;	}	/**	 * 瘦脸 范围0~1 SDK默认为 0.4f	 * @param cheekThinning	 */	public void setCheekThinning(float cheekThinning) {		this.mCheekThinning = cheekThinning;		isNeedUpdateFaceBeauty = true;	}	/**	 * 大眼 范围0~1 SDK默认为 0.4f	 * @param eyeEnlarging	 */	public void setEyeEnlarging(float eyeEnlarging) {		this.mEyeEnlarging = eyeEnlarging;		isNeedUpdateFaceBeauty = true;	}	/**	 * 打开/关闭美颜	 * @param flag	 */	public void openFaceBeauty(boolean flag) {		if (flag) {			mBlurLevel = 6;			mColorLevel = 0.5f;			mCheekThinning = 0.4f;			mEyeEnlarging = 0.4f;		} else {			mBlurLevel = 0;			mColorLevel = 0f;			mCheekThinning = 0f;			mEyeEnlarging = 0f;		}		isNeedUpdateFaceBeauty = true;	}	/**	 * 每帧处理画面时被调用	 */	private void prepareDrawFrame() {		//修改美颜参数		if (isNeedUpdateFaceBeauty && mItemsArray[0] != 0) {			//filter_level 滤镜强度 范围0~1 SDK默认为 1			faceunity.fuItemSetParam(mItemsArray[0], "filter_level", 1.0f);			//filter_name 滤镜			faceunity.fuItemSetParam(mItemsArray[0], "filter_name", "ziran");			//skin_detect 精准美肤 0:关闭 1:开启 SDK默认为 0			faceunity.fuItemSetParam(mItemsArray[0], "skin_detect", 1.0f);			//heavy_blur 美肤类型 0:清晰美肤 1:朦胧美肤 SDK默认为 0			faceunity.fuItemSetParam(mItemsArray[0], "heavy_blur", 0.0f);			//blur_level 磨皮 范围0~6 SDK默认为 6			faceunity.fuItemSetParam(mItemsArray[0], "blur_level", mBlurLevel * 0.7f);			//blur_blend_ratio 磨皮结果和原图融合率 范围0~1 SDK默认为 1//          faceunity.fuItemSetParam(mItemsArray[0], "blur_blend_ratio", 1);			//color_level 美白 范围0~1 SDK默认为 1			faceunity.fuItemSetParam(mItemsArray[0], "color_level", mColorLevel);			//red_level 红润 范围0~1 SDK默认为 1			faceunity.fuItemSetParam(mItemsArray[0], "red_level", 0.5f);			//eye_bright 亮眼 范围0~1 SDK默认为 0			faceunity.fuItemSetParam(mItemsArray[0], "eye_bright", 0.0f);			//tooth_whiten 美牙 范围0~1 SDK默认为 0			faceunity.fuItemSetParam(mItemsArray[0], "tooth_whiten", 0.0f);			//face_shape_level 美型程度 范围0~1 SDK默认为1			faceunity.fuItemSetParam(mItemsArray[0], "face_shape_level", 1.0f);			//face_shape 脸型 0：女神 1：网红 2：自然 3：默认 4：自定义（新版美型） SDK默认为 3			faceunity.fuItemSetParam(mItemsArray[0], "face_shape", 3.0f);			//eye_enlarging 大眼 范围0~1 SDK默认为 0			faceunity.fuItemSetParam(mItemsArray[0], "eye_enlarging", mEyeEnlarging);			//cheek_thinning 瘦脸 范围0~1 SDK默认为 0			faceunity.fuItemSetParam(mItemsArray[0], "cheek_thinning", mCheekThinning);			//intensity_chin 下巴 范围0~1 SDK默认为 0.5    大于0.5变大，小于0.5变小			faceunity.fuItemSetParam(mItemsArray[0], "intensity_chin", 0.3f);			//intensity_forehead 额头 范围0~1 SDK默认为 0.5    大于0.5变大，小于0.5变小			faceunity.fuItemSetParam(mItemsArray[0], "intensity_forehead", 0.3f);			//intensity_nose 鼻子 范围0~1 SDK默认为 0			faceunity.fuItemSetParam(mItemsArray[0], "intensity_nose", 0.5f);			//intensity_mouth 嘴型 范围0~1 SDK默认为 0.5   大于0.5变大，小于0.5变小			faceunity.fuItemSetParam(mItemsArray[0], "intensity_mouth", 0.4f);			isNeedUpdateFaceBeauty = false;		}		//queueEvent的Runnable在此处被调用		while (!mEventQueue.isEmpty()) {			mEventQueue.remove(0).run();		}	}	//--------------------------------------道具（异步加载道具）----------------------------------------	private static final int ITEM_ARRAYS_EFFECT = 1;	private static final int ITEM_ARRAYS_COUNT = 3;	//美颜和其他道具的handle数组	//用于和异步加载道具的线程交互	private HandlerThread mFuItemHandlerThread;	private Handler mFuItemHandler;	//同时识别的最大人脸	private int mMaxFaces = 4;	private ArrayList<Runnable> mEventQueue = new ArrayList<>();	private int mInputImageOrientation = 0;	public void createItem(Effect item) {		if (item == null) {			return;		}		mFuItemHandler.removeMessages(FUItemHandler.HANDLE_CREATE_ITEM);		mFuItemHandler.sendMessage(Message.obtain(mFuItemHandler, FUItemHandler.HANDLE_CREATE_ITEM, item));	}	class FUItemHandler extends Handler {		static final int HANDLE_CREATE_ITEM = 1;		FUItemHandler(Looper looper) {			super(looper);		}		@Override		public void handleMessage(Message msg) {			super.handleMessage(msg);			switch (msg.what) {				//加载道具				case HANDLE_CREATE_ITEM:					final Effect effect = (Effect) msg.obj;					final int newEffectItem = loadItem(effect);					queueEvent(new Runnable() {						@Override						public void run() {							if (mItemsArray[ITEM_ARRAYS_EFFECT] > 0) {								faceunity.fuDestroyItem(mItemsArray[ITEM_ARRAYS_EFFECT]);							}							mItemsArray[ITEM_ARRAYS_EFFECT] = newEffectItem;							setMaxFaces(effect.maxFace());						}					});					break;				default:					break;			}		}	}	/**	 * fuCreateItemFromPackage 加载道具	 *	 * @param bundle（Effect本demo定义的道具实体类）	 * @return 大于0时加载成功	 */	private int loadItem(Effect bundle) {		int item = 0;		try {			if (bundle.effectType() == Effect.EFFECT_TYPE_NONE) {				item = 0;			} else {				InputStream is = mContext.getAssets().open(bundle.path());				byte[] itemData = new byte[is.available()];				int len = is.read(itemData);				is.close();				item = faceunity.fuCreateItemFromPackage(itemData);				updateEffectItemParams(item);			}		} catch (IOException e) {			e.printStackTrace();		}		return item;	}	/**	 * 设置对道具设置相应的参数	 *	 * @param itemHandle	 */	private void updateEffectItemParams(final int itemHandle) {		queueEvent(new Runnable() {			@Override			public void run() {				faceunity.fuItemSetParam(itemHandle, "isAndroid", 1.0);				//rotationAngle 参数是用于旋转普通道具				faceunity.fuItemSetParam(itemHandle, "rotationAngle", 360 - mInputImageOrientation);				//这两句代码用于识别人脸默认方向的修改，主要针对animoji道具的切换摄像头倒置问题				faceunity.fuItemSetParam(itemHandle, "camera_change", 1.0);				faceunity.fuSetDefaultRotationMode((360 - mInputImageOrientation) / 90);			}		});	}	/**	 * 类似GLSurfaceView的queueEvent机制	 */	public void queueEvent(Runnable r) {		mEventQueue.add(r);	}	/**	 * 设置需要识别的人脸个数	 *	 * @param maxFaces	 */	public void setMaxFaces(final int maxFaces) {		if (mMaxFaces != maxFaces && maxFaces > 0) {			queueEvent(new Runnable() {				@Override				public void run() {					mMaxFaces = maxFaces;					faceunity.fuSetMaxFaces(maxFaces);				}			});		}	}	@Override	protected String getFragmentShader() {		return					 "#extension GL_OES_EGL_image_external : require\n"					+"precision mediump float;\n"					+"uniform samplerExternalOES "+UNIFORM_TEXTURE0+";\n"					+"varying vec2 "+VARYING_TEXCOORD+";\n"		 			+ "void main() {\n"		 			+ "   gl_FragColor = texture2D("+UNIFORM_TEXTURE0+", "+VARYING_TEXCOORD+");\n"		 			+ "}\n";	}	@Override	protected String getVertexShader() {		return					"uniform mat4 "+UNIFORM_CAM_MATRIX+";\n"				  + "attribute vec4 "+ATTRIBUTE_POSITION+";\n"				  + "attribute vec2 "+ATTRIBUTE_TEXCOORD+";\n"				  + "varying vec2 "+VARYING_TEXCOORD+";\n"				  + "void main() {\n"				  + "   vec4 texPos = "+UNIFORM_CAM_MATRIX+" * vec4("+ATTRIBUTE_TEXCOORD+", 1, 1);\n"				  + "   "+VARYING_TEXCOORD+" = texPos.xy;\n"				  + "   gl_Position = "+ATTRIBUTE_POSITION+";\n"				  + "}\n";	}	@Override	protected void initShaderHandles() {		super.initShaderHandles();        matrixHandle = GLES20.glGetUniformLocation(programHandle, UNIFORM_CAM_MATRIX);	}	private void initPreviewGLContext(){		mFrameId = 0;		try {			InputStream beauty = mContext.getAssets().open("face_beautification.bundle");			byte[] beautyData = new byte[beauty.available()];			beauty.read(beautyData);			beauty.close();			mItemsArray[0] = faceunity.fuCreateItemFromPackage(beautyData);			isNeedUpdateFaceBeauty = true;		} catch (IOException e) {			e.printStackTrace();		}        int[] textures = new int[1];		GLES20.glGenTextures(1, textures, 0);		GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0]);		GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);		GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);		GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);		GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);		texture_in = textures[0];		camTex = new SurfaceTexture(texture_in);		camTex.setOnFrameAvailableListener(this);		boolean failed = true;		int trycount = 0 ;		while(failed) {			if(trycount>5)				break;			try {				StopCamera();				if(createCamera()!=null) {					camera.setPreviewTexture(camTex);					camera.startPreview();                    JniWorkerThread mJniWorkerThread = GlobalHolder.getInstance().getWorkerThread();                    if (mJniWorkerThread != null) {                        mJniWorkerThread.sendMessage(JniWorkerThread.JNI_CALL_BACK_CAMERA_READY, new Object[]{});                    }				}				setRenderSizeToCameraSize();				failed = false;			} catch (Exception e) {				trycount++;				StringWriter sw = new StringWriter();				PrintWriter pw = new PrintWriter(sw);				e.printStackTrace(pw);				Log.e("CameraInput", sw.toString());				StopCamera();				try {					Thread.sleep(100);				} catch (InterruptedException e1) {					e1.printStackTrace();				}			}		}	}	@Override	protected void initWithGLContext() {//    	super.initWithGLContext();		initPreviewGLContext();	}	@Override	public void onPreviewFrame(byte[] data, Camera camera) {		mCameraNV21Byte = data;		camera.addCallbackBuffer(data);	}	/* (non-Javadoc)	 * @see android.graphics.SurfaceTexture.OnFrameAvailableListener#onFrameAvailable(android.graphics.SurfaceTexture)	 */	@Override	public void onFrameAvailable(SurfaceTexture arg0) {        PviewLog.wf("LocalCamera onFrameAvailable..... mFBOTextureBinder : " + mFBOTextureBinder);        if (!mIsFirstFrameCallback) {            mIsFirstFrameCallback = true;            JniWorkerThread mJniWorkerThread = GlobalHolder.getInstance().getWorkerThread();            if (mJniWorkerThread != null) {                mJniWorkerThread.sendMessage(JniWorkerThread.JNI_CALL_BACK_LOCAL_VIDEO_FIRST_FRAME, new Object[]{mOutWidth , mOutHeight});            }        }		markAsDirty();		if (Constants.IS_UNITY) {            if (mFBOTextureBinder != null) {                PviewLog.wf("LocalCamera startGameRender.....");                mFBOTextureBinder.startGameRender();            }        } else {            view.requestRender();        }	}	/**	 * Closes and releases the camera for other applications to use.	 * Should be called when the pause is called in the activity.	 */	public void onPause() {		if(camera != null) {			camera.stopPreview();			camera.release();			camera = null;            mIsFirstFrameCallback = false;		}	}	/**	 * Re-initializes the camera and starts the preview again.	 * Should be called when resume is called in the activity.	 */	private void onResume() {		reInitialize();	}	@Override	protected void passShaderValues() {		renderVertices.position(0);		GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 8, renderVertices);		GLES20.glEnableVertexAttribArray(positionHandle);		textureVertices[curRotation].position(0);		GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 8, textureVertices[curRotation]);		GLES20.glEnableVertexAttribArray(texCoordHandle);		bindTexture();	    GLES20.glUniform1i(textureHandle, 0);	    camTex.getTransformMatrix(matrix);		GLES20.glUniformMatrix4fv(matrixHandle, 1, false, matrix, 0);	}	private void setRenderSizeToCameraSize() {		Parameters params = camera.getParameters();		Size previewSize = params.getPreviewSize();		if (mPreviewRotation==0)			setRenderSize(previewSize.width, previewSize.height);		else			setRenderSize(previewSize.height, previewSize.width);	}	//查找出预览和picture都支持的尺寸	private List<Size> getPreSiez(Camera.Parameters params) {//		List<Camera.Size> lprecam=params.getSupportedPreviewSizes();//		List<Camera.Size> lprecam1= new ArrayList<>();//		int mode = 16;//		if (isMTk()) {//			mode=32;//		}////		for (Camera.Size tmp : lprecam) {//能被mode的//			int modw = tmp.width % mode;//			int modh = tmp.height % mode;//			Log.e("lprecam"," width = "+tmp.width+" height="+tmp.height);//			if (modw == 0 && modh == 0) {//				lprecam1.add(tmp);//			}//		}////		lprecam.clear();//		return lprecam1;        return params.getSupportedPreviewSizes();	}	/**	 * 通过对比得到与宽高比最接近的预览尺寸（如果有相同尺寸，优先选择）	 *	 * @param params Camera.Parameters	 * @return 得到与原宽高比例最接近的尺寸	 */	private Camera.Size getCloselyPreSize(Camera.Parameters params) {//        List<Camera.Size> preSizeList = getPreSiez(params);//		int sumsize = mPrevWidth + mPrevHeight;//		float reqRatio = 1.0f;//		if (mPreviewRotation==90) {//			for (Camera.Size size : preSizeList) {//				if ((size.width == mPrevHeight) && (size.height == mPrevWidth)) {//					return size;//				}//			}//			reqRatio = ((float)mPrevHeight) / mPrevWidth;//		} else {//			for(Camera.Size size : preSizeList){//				if(((size.width == mPrevWidth) && (size.height == mPrevHeight))){//					return size;//				}//			}//			reqRatio = ((float)mPrevWidth) /mPrevHeight ;//		}////		boolean breturn =false;//		Camera.Size retSize = preSizeList.get(0);//		int mindis = Math.abs(retSize.width+retSize.height-sumsize);//		for (Camera.Size tmp : preSizeList) {//能被mode的//			float newdip  =(float)tmp.width/tmp.height;//			int nowsum = Math.abs(tmp.width+tmp.height-sumsize);//			//Log.e("lprecam"," width = "+tmp.width+" height="+tmp.height+" Math.abs(newdip-dip)="+Math.abs(newdip-dip)+" modw="+modw+" modh="+modh);//			if(Math.abs(newdip-reqRatio) <=0.00001f) {//比例相同，插值最小的//				if(mindis>nowsum) {//					retSize = tmp;//					breturn=true;//					mindis=nowsum;//				}//			}//		}//		if(breturn)//			return retSize;//		//查找最接近的//		for (Camera.Size tmp : preSizeList) { //能被mode的//			int nowsum = Math.abs(tmp.width+tmp.height-sumsize);//			//Log.e("lprecam"," width = "+tmp.width+" height="+tmp.height+" nowsum="+nowsum+" sumsize="+sumsize);//				if(mindis>nowsum) {//					mindis=nowsum;//					retSize = tmp;//				}//		}        List<Camera.Size> preSizeList = getPreSiez(params);        Camera.Size retSize = preSizeList.get(0);        if (mPreviewRotation==90) {            int mindis = vectorDis(retSize.width,mPrevHeight)+vectorDis(retSize.height,mPrevWidth);            for (Camera.Size size : preSizeList) {                int nowsum=vectorDis(size.width,mPrevHeight)+vectorDis(size.height,mPrevWidth);                if (nowsum<mindis) {                    mindis=nowsum;                    retSize=size;                }            }        } else {            int mindis = vectorDis(retSize.width,mPrevWidth)+vectorDis(retSize.height,mPrevHeight);            for (Camera.Size size : preSizeList) {                int nowsum=vectorDis(size.width,mPrevWidth)+vectorDis(size.height,mPrevHeight);                if (nowsum<mindis) {                    mindis=nowsum;                    retSize=size;                }            }        }		return retSize;	}    //当from小于to的时候，距离为一个很大的数，这里定为0xFFFF，即65536    public int vectorDis(int from,int to){        return from<to? 65536:from-to;    }	private boolean isMTk(){		bMtk = SrsEncoder.getInstance().isMtk();		return bMtk;	}	private void calOutSize() {        PviewLog.i("calOutSize mPrevWidth : " + mPrevWidth + " | mPrevHeight : " + mPrevHeight);		if (mPreviewRotation == 90) {			int wmax = Math.min(clsSize.width, mPrevHeight);			int hmax = Math.min(clsSize.height, mPrevWidth);			if (mPrevWidth == clsSize.height && mPrevHeight == clsSize.width) {				mOutHeight=clsSize.width;				mOutWidth=clsSize.height;			} else {				mOutHeight = wmax;				mOutWidth = hmax;			}		} else {			int wmax = Math.min(clsSize.width,  mPrevWidth);			int hmax = Math.min(clsSize.height,mPrevHeight);			if(mPrevWidth==clsSize.width&&mPrevHeight==clsSize.height) {				mOutWidth=clsSize.width;				mOutHeight=clsSize.height;			} else {				mOutWidth = wmax;				mOutHeight = hmax;			}		}		PviewLog.i("calOutSize clsSize.width : " + clsSize.width + " | clsSize.height : " + clsSize.height);        PviewLog.i("calOutSize mPrevWidth : " + mPrevWidth + " | mPrevHeight : " + mPrevHeight);        PviewLog.i("calOutSize mOutWidth : " + mOutWidth + " | mOutHeight : " + mOutHeight);        PviewLog.i("calOutSize mPreviewRotation : " + mPreviewRotation);		int mode = 16;        if (isMTk()) {            mode = 32;        }        int w = mOutWidth % mode;        int h= mOutHeight% mode;        if (mOutWidth >= 240 && mOutHeight >= 240) {            if (w != 0) {                mOutWidth = mOutWidth - w;            }            if (h!= 0) {                mOutHeight = mOutHeight - h;            }        }        PviewLog.w("calOutSize 32 mOutWidth : " + mOutWidth + " | mOutHeight : " + mOutHeight);        MyVideoApi.getInstance().updateEncodeSize(mOutWidth, mOutHeight);	}	private int[] findClosestFpsRange(int expectedFps, List<int[]> fpsRanges) {		expectedFps *= 1000;		int[] closestRange = fpsRanges.get(0);		int measure = Math.abs(closestRange[0] - expectedFps) + Math.abs(closestRange[1] - expectedFps);		for (int[] range : fpsRanges) {			if (range[0] <= expectedFps && range[1] >= expectedFps) {				int curMeasure = Math.abs(range[0] - expectedFps) + Math.abs(range[1] - expectedFps);				if (curMeasure < measure) {					closestRange = range;					measure = curMeasure;				}			}		}		return closestRange;	}	public void setCameraCbObj( CameraSizeCb cb){		_cb=cb;	}	public  interface CameraSizeCb{		void startPrieview();	}}