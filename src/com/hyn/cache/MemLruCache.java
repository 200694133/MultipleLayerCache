package com.hyn.cache;

import java.util.LinkedHashMap;
import java.util.Map;


public class MemLruCache implements ICache {
	private static final String TAG = MemLruCache.class.getSimpleName();
	private transient LinkedHashMap<Object, ICacheable> mCacheMap = null;
	/** Size of this cache in units. Not necessarily the number of elements. */
    private long mCurrSize = -1;
    private long mMaxSize = -1;
	
    private int mPutCount;
    private int mEvictionCount;
    private int mHitCount;
    private int mMissCount;
	
    /**
     * @param maxSize for caches in memory, this is the maximum sum of the sizes of the entries in this cache.
     */
    public MemLruCache(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize <= 0");
        }
        mMaxSize = maxSize;
        mCacheMap = new LinkedHashMap<Object, ICacheable>(0, 0.75f, true);        
    }
    
    public String toString(){
    	return new String("Put Count: "+mPutCount+" ,  Eviction Count"+mEvictionCount+" ,  Hit Count "+mHitCount+" ,  Miss Count"+mMissCount);
    }
    
    protected void onEntryRemove(Object key, ICacheable data){
    	
    }
    
    /**
     * Remove the eldest entries until the total of remaining entries is at or
     * below the requested size.
     *
     * @param maxSize the maximum size of the cache before returning. May be -1
     *            to evict even 0-sized elements.
     * @return
     */
    public void trimToSize(long maxSize) {
    	while (true) {
    		Object key = null;
    		ICacheable value = null;
    		synchronized (this) {
    			if (mCurrSize < 0 || (mCacheMap.isEmpty() && mCurrSize != 0)) {
                    throw new IllegalStateException(getClass().getName() + ".sizeOf() is reporting inconsistent results!");
                }

                if (mCurrSize <= maxSize || mCacheMap.isEmpty()) {
                    break;
                }
                
                Map.Entry<Object, ICacheable> toEvict = mCacheMap.entrySet().iterator().next();
                if (toEvict == null) {
                    break;
                }
                key = toEvict.getKey();
                value = toEvict.getValue();
                mCacheMap.remove(key);
                mCurrSize -= safeSizeOf(key, value);
                ++mEvictionCount;
        		System.out.println(TAG+" trimToSize remove "+key+" , value "+value);
    		}
    		if(null != value && null != key) onEntryRemove(key, value);
    	}
    }
    
    private long safeSizeOf(Object key, ICacheable value) {
        long result = value.sizeOf();
        if (result < 0) {
            throw new IllegalStateException("Negative size: " + key + "=" + value);
        }
        return result;
    }
    
	@Override
	public ICacheable get(Object key) {
		if (key == null) {
			throw new NullPointerException("key == null");
		}

	    ICacheable mapValue;
		synchronized (this) {
			mapValue = mCacheMap.get(key);
			if (mapValue != null) {
				mHitCount++;
				return mapValue;
			}
			mMissCount++;
		}
		return null;
	}
	
	
	@Override
	public ICacheable put(Object key, ICacheable value) {
		if (key == null || value == null) {
            throw new NullPointerException("key == null || value == null");
        }

		ICacheable previous = null;
        synchronized (this) {
            mPutCount++;
            mCurrSize += safeSizeOf(key, value);
            previous = mCacheMap.put(key, value);
            if (previous != null) {
            	mCurrSize -= safeSizeOf(key, previous);
            }
        }

        trimToSize(mMaxSize);
        return previous;
	}
	
	public ICacheable remove(Object key){
		if (key == null) {
            throw new NullPointerException("key == null");
        }

		ICacheable previous = null;
        synchronized (this) {
            previous = mCacheMap.remove(key);
            if (previous != null) {
                mCurrSize -= safeSizeOf(key, previous);
            }
        }

        return previous;
	}
	
	@Override
	public void reSize(long newMaxSize) {
		if (newMaxSize <= 0) {
            throw new IllegalArgumentException("newMaxSize <= 0");
        }

        synchronized (this) {
            mMaxSize = newMaxSize;
        }
        trimToSize(newMaxSize);
	}
}
