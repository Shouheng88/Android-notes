
### 百度语音识别API异常：

异常1：

     java.lang.UnsatisfiedLinkError: No implementation found for void com.baidu.speech.core.BDSSDKLoader.SetLogLevel(int) (tried Java_com_baidu_speech_core_BDSSDKLoader_SetLogLevel and Java_com_baidu_speech_core_BDSSDKLoader_SetLogLevel__I)

so文件存在冲突，只要保证libs文件夹下面只有armeabi一个文件夹即可。
可能之前因为想要兼容各不同的系统所以在使用百度定位的时候创建了多个文件夹，这里需要只留下armeabi一个文件夹。

## 使用百度API进行定位（Android Studio）

### 1.步骤1：获取密钥

### 2.步骤2：下载API开发包

### 3.步骤3：配置环境

#### 3.1 导入jar文件

切换Android Studio的工作目录为Project，在lib文件夹下面导入解压后的API开发包。注意要将.so文件和jar文件同时导入，对jar文件还要在其上面右键单击，选择Add as Library，将其作为库导入进来。

#### 3.2 配置gradle 

对使用Android Studio开发的同学，仅仅做到这些还是不够的，还要在build.gradle(Modile:app)中添加
    
    sourceSets {
        main {
            jniLibs.srcDirs = ['libs']
        }
    }

这里的作用是添加.so文件，没有添加.so文件或者添加了.so文件而没有在gradle中进行配置的同学，都无法正常使用百度进行定位。

#### 3.3 在AndroidManifest.xml中进行配置

1.声明要使用的权限

    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
    <uses-permission android:name="android.permission.READ_PHONE_STATE" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.MOUNT_UNMOUNT_FILESYSTEMS" />
    <uses-permission android:name="android.permission.READ_LOGS" />
    <uses-permission android:name="android.permission.VIBRATE" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.WRITE_SETTINGS" />

2.在application中声明service组件

每个app拥有自己单独的定位

    <service android:name="com.baidu.location.f"
         android:enabled="true"
         android:process=":remote">
    </service>

3.在application中声明APP KEY：

    <meta-data android:name="com.baidu.lbsapi.API_KEY"
         android:value="你的APP KEY" />

### 步骤4：添加代码

我们将与定位相关的代码全部放在一个工具类中

	public class LocationUtils {
	
	    private static LocationUtils sInstance;
	
	    private LocationClient mLocationClient;
	
	    private BDLocationListener bdLocationListener;
	
	    public static LocationUtils getInstance(Context mContext){
	        if (sInstance == null){
	            sychronized(LocationUtils.class) {
                    if (sInstance == null) {
                         sInstance = new LocationUtils(mContext.getApplicationContext());
                    }
                }
	        }
	        return sInstance;
	    }
	
	    private LocationUtils(Context mContext){
	        mLocationClient = new LocationClient(mContext);
	    }
	
	    public void locate(BDLocationListener mListener){
	        bdLocationListener = mListener;
	        mLocationClient.registerLocationListener(bdLocationListener);
	        LocationClientOption option = new LocationClientOption();
	        option.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy);
	        option.setIsNeedAddress(true);
	        mLocationClient.setLocOption(option);
	        mLocationClient.requestLocation();
	        mLocationClient.start();
	    }
	
	    public void stop() {
	        if (mLocationClient.isStarted()) {
	            mLocationClient.stop();
	            if (bdLocationListener != null) {
	                mLocationClient.unRegisterLocationListener(bdLocationListener);
	                bdLocationListener = null;
	            }
	        }
	    }
	}

这样每当我们需要定位的时候就可以这样调用：

    ToastUtils.showShortToast(getContext(), R.string.trying_to_get_location);
    LocationUtils.getInstance(getContext()).locate(new BDLocationListener() {
        @Override
        public void onReceiveLocation(BDLocation bdLocation) {
            // ... 然后当bdLocation不为空的时候，直接从bdLocation上面获取位置信息就可以了
        }
    });

### 常见错误：

#### 1.无法获取位置信息：在监听器当中没有获取到位置的详细信息

原因可能是：

1. 没有添加

        option.setIsNeedAddress(true);
这行代码是用来设置用户需不需要获取返回信息的，老版本的方法是使用

        option.setAddrType(“all”)

实际上，根据源代码这两个代码的功能是相同的，只是后者现在被抛弃了，使用前者即可。

2. 没有在AndroidManifest.xml文件中添加

        <service  .........>

出现上面的这种情况也是无法获取的。

3. 没有添加.so文件或者添加了.so文件但是没有在gralde文件中进行声明。前面提到过关于.so文件在gralde中声明的方法。



