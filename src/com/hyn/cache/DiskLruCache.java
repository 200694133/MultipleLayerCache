package com.hyn.cache;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class DiskLruCache implements ICache{
	private static final String TAG = DiskLruCache.class.getSimpleName();
	/** Size of this cache in units. Not necessarily the number of elements. */
    private long mCurrSize = 0;
    private long mMaxSize = 0; 
	private boolean isDisposed = false;
    private final ReentrantLock mLock = new ReentrantLock();
    private final Condition mDeleteCondition = mLock.newCondition();
    private final Condition mWriteCondition = mLock.newCondition();
    
    /** 活跃区 */
    private final transient LinkedHashMap<Object, String> mActiveCachedMap = new LinkedHashMap<Object, String>(0, 0.75f, true);
    /** 死亡区, waiting for delete, it may be translate to active area. */
    private final transient Map<Object, String> mDeathingCachedMap = new LinkedHashMap<Object, String>();
    /** 非活跃区,waiting for cached to disk. */
    private final transient Map<Object, ICacheable> mActivingCachedMap = new LinkedHashMap<Object, ICacheable>();
    
    private final transient WeakHashMap<Object, Long> mCachableSizeMap = new WeakHashMap<Object, Long>();
        
    private Thread mDeleteThread = null;
    private Thread mCacheThread = null;
    private String mCacheFolder = null;
    
	public DiskLruCache(String cacheFolder, long size){
		if(null == cacheFolder || cacheFolder.length() <=0||size <=0) {
			throw new IllegalArgumentException("cacheFolder is empty or size <= 0");
		}
		mCacheFolder = cacheFolder;
		mMaxSize = size;
		
		mDeleteThread = new Thread(mDeleteCacheRunnable);
		mCacheThread = new Thread(mActivingRunnable);
		mDeleteThread.start();
		mCacheThread.start();
	}
	
	public void dispose(){
		mLock.lock();
		isDisposed = true;
		mActiveCachedMap.clear();
		mDeathingCachedMap.clear();
		mActivingCachedMap.clear();
		mDeleteCondition.signal();
		mWriteCondition.signal();
		mLock.unlock();
		if(null != mDeleteThread){
			mDeleteThread.interrupt();
			mDeleteThread = null;
		}
		if(null != mCacheThread){
			mCacheThread.interrupt();
			mCacheThread = null;
		}
		
		
	}

	@Override
	public ICacheable get(Object key) {
		ICacheable c = null;
		String path = null;
		mLock.lock();
		if(mActivingCachedMap.containsKey(key)){
			c = mActivingCachedMap.get(key);
		}else if(mActiveCachedMap.containsKey(key)){
			path = mActiveCachedMap.get(key);
		}
		//TODO 从死亡区域中查找
		mLock.unlock();
		System.out.println(TAG+" get From activing area get "+c+" , get from cached area get "+path);
		if(null != c) return c;
		if(null == path) return null;
		
		byte []data = readFile(path);
		return CachableFactory.parse(data);
	}

	@Override
	public ICacheable put(Object key, ICacheable value) {
		ICacheable res = null;
		mCachableSizeMap.put(key, Long.valueOf(value.sizeOf()));
		mLock.lock();
		res = remove(key);
		mActivingCachedMap.put(key,value);
		mWriteCondition.signal();
		mLock.unlock();
		
		
		return res;
	}

	@Override
	public ICacheable remove(Object key) {
		ICacheable c = null;
		mLock.lock();
		if(mActivingCachedMap.containsKey(key)){
			c = mActivingCachedMap.remove(key);
		}

		String p = null;
		if(mActiveCachedMap.containsKey(key)){
			p = mActiveCachedMap.get(key);
			mDeathingCachedMap.put(key, p);
		}
		mDeleteCondition.signal();
		mLock.unlock();
		if(null != p || null != c) {
			System.out.println(TAG+" remove From activing area get "+c+" , remove from cached area get "+p);
		}
		
		return c;
	}

	@Override
	public void reSize(long newMaxSize) {
		mLock.lock();
		mMaxSize = newMaxSize;
		mLock.unlock();
		
		trimToSize(newMaxSize);
	}
	
	public void trimToSize(long maxSize) {
		while (true) {
			Object key = null;
    		try{
    			mLock.lock();
    			if (mCurrSize < 0 || (mActiveCachedMap.isEmpty() && mCurrSize != 0)) {
                    throw new IllegalStateException(getClass().getName() + ".sizeOf() is reporting inconsistent results!");
                }

                if (mCurrSize <= mMaxSize || mActiveCachedMap.isEmpty()) {
                    break;
                }
                
                Map.Entry<Object, String> toEvict = mActiveCachedMap.entrySet().iterator().next();
                if (toEvict == null) {
                    break;
                }
                key = toEvict.getKey();
                mActiveCachedMap.remove(key);
                mDeathingCachedMap.put(toEvict.getKey(), toEvict.getValue());
                mCurrSize -= mCachableSizeMap.get(key);
                System.out.println(TAG+" trimToSize " + key + " move to death area ");
    		}finally{
    			mDeleteCondition.signal();
    			mLock.unlock();
    		}
    	 }
	}
	
	private void putToActive(Map.Entry<Object, ICacheable> entry, String path){
		try {
			mLock.lockInterruptibly();
			mCurrSize += entry.getValue().sizeOf();
			mActiveCachedMap.put(entry.getKey(), path);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}finally{
			mLock.unlock();
		}
		System.out.println(TAG+" putToActive " + entry.getKey() + " move to mActiveCachedMap area "+path);
		trimToSize(mMaxSize);
	}
	
	private byte[] readFile(String path){
		try {
			FileInputStream fi = new FileInputStream(path);
			int len = fi.available();
			byte[] content = new byte[len];
			fi.read(content, 0, len);
			fi.close();
			return content;
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	private String write(String path, byte[] content){
		File file = new File(path);
		if(!file.exists()){
			System.out.println(path + " is not exists, create new one");
			try {
				boolean res = file.createNewFile();
				if(!res){
					System.out.println("Create "+path+" failed!");
					return null;
				}
				
			} catch (IOException e) {
				e.printStackTrace();
				return null;
			}
		}
		
		try {
			FileOutputStream fw = new FileOutputStream(file, false);
			fw.write(content);
			fw.flush();
			fw.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return null;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		
		return path;
	}
	
	private String generatePath(Object obj){
		int code = obj.hashCode();
		String s = obj.toString();
		if(s == null){
			s = String.valueOf(System.currentTimeMillis());
		}
		
		return mCacheFolder+"/"+String.valueOf(code)+"_"+s;
	}
	
	private Map.Entry<Object, String> pullDeathingOne(){
		Map<Object, String> content = mDeathingCachedMap;
		Set<Map.Entry<Object, String>> entrySet = content.entrySet();
		if(null == entrySet) return null;
		Iterator<Map.Entry<Object, String>> iterators = content.entrySet().iterator();
		if(null == iterators) return null;
		if(iterators.hasNext()){
			return iterators.next();
		}
		return null;
	}

	private Map.Entry<Object, ICacheable> pullActivingOne(){
		Map<Object, ICacheable> content = mActivingCachedMap;
		Set<Map.Entry<Object, ICacheable>> entrySet = content.entrySet();
		if(null == entrySet) return null;
		Iterator<Map.Entry<Object, ICacheable>> iterators = content.entrySet().iterator();
		if(null == iterators) return null;
		if(iterators.hasNext()){
			return iterators.next();
		}
		return null;
	}
	
	
	private Runnable mActivingRunnable = new Runnable(){
		public void run(){
			while(!isDisposed){
				Map.Entry<Object, ICacheable> entry = null;
				try {
					mLock.lockInterruptibly();
					int size = mActivingCachedMap.size();
					if(size <= 0) {
						mWriteCondition.await();
						continue;
					}
					entry = pullActivingOne();
					if(null == entry) continue;
					mActivingCachedMap.remove(entry.getKey());
				} catch (InterruptedException e) {
					e.printStackTrace();
				}finally{
					mLock.unlock();
				}
				String path = generatePath(entry.getKey());
				path = write(path, entry.getValue().toByte());
				putToActive(entry, path);
			}
		}
	};
	
	private Runnable mDeleteCacheRunnable = new Runnable(){
		public void run(){
			while(!isDisposed){
				Map.Entry<Object, String> entry = null;
				try {
					mLock.lockInterruptibly();
					int size = mDeathingCachedMap.size();
					if(size <= 0) {
						mDeleteCondition.await();
						continue;
					}
					entry = pullDeathingOne();
					if(null == entry) continue;
					mDeathingCachedMap.remove(entry.getKey());
				} catch (InterruptedException e) {
					continue;
				}finally{
					mLock.unlock();
				}
				String path = entry.getValue();
				File file = new File(path);
				file.delete();
				file = null;
				System.out.println("mDeleteCacheRunnable remove file "+path);
			}
		}
	};
}
