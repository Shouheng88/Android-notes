   private static PalmDB sInstance = null;

    private Context mContext;

    public static synchronized PalmDB getInstance(final Context context){
        if (sInstance == null){
            sInstance = new PalmDB(context.getApplicationContext());
        }
        return sInstance;
    }

    private PalmDB(Context context){
        super(context, DATABASE_NAME, null, VERSION);
        this.mContext = context;
    }
	
	使用上面的这种形式来写的话在Android Studio中会给一个提示(Lint), 提示我们在程序中将Context放在了静态字段里面，
实际上这种写法是不会造成内存泄漏的。但是必须注意在获取PalmDB的实例的时候要使用context.getApplicationContext()，
这是因为，加入我们使用了某个Activity的Context来获取PalmDB，那么该PalmDB必然是与该Activity关联的，当我们不需要该
Activity的时候，静态字段依然引用着该Activity的Context，这样就会造成该Activity无法被释放，当该Activity中又包含着很
多占用内存的操作的时候，势必会造成大量的内存泄漏。（注：静态字段的生命周期和整个Application是一样的。）
	但是上面的写法中在调用PalmDB(Context context)方法的时候传入的是整个Application的Context，也就是使用了
context.getApplicationContext()，这样获取到的Context是与整个Application的生命周期一样的，所以在该Application中的
PalmDB只引用着一个与整个Application生命周期一样长的Context，当然就不会造成内存泄漏了。