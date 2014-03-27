package com.hyn.cache;

public class MultipleLayerLruCache implements ICache{
	private static final String TAG = MultipleLayerLruCache.class.getSimpleName();
	
	CMemLruCache mMemLruCache = null;
	DiskLruCache mDiskLruCache = null;
	public MultipleLayerLruCache(int maxMemSize, String path, long maxDiskSize){
		mMemLruCache = new CMemLruCache(maxMemSize);
		if(null != path && path.length() > 0){
			mDiskLruCache = new DiskLruCache(path, maxDiskSize);
		}
	}
	
	private class CMemLruCache extends MemLruCache{
		public CMemLruCache(int maxSize) {
			super(maxSize);
		}
		
	    protected void onEntryRemove(Object key, ICacheable data){
	    	if(null != mDiskLruCache){
	    		mDiskLruCache.put(key, data);
	    	}
	    }
	}



	@Override
	public ICacheable get(Object key) {
		ICacheable c = null;
		if(null != mMemLruCache){
			c = mMemLruCache.get(key);
		}
		if(null != c) {
    		System.out.println(TAG+" get key "+key+" from mMemLruCache");
			return c;
		}
		if(null != mDiskLruCache){
			c = mDiskLruCache.get(key);
			System.out.println(TAG+" get key "+key+" from mDiskLruCache");
		}
		return c;
	}



	@Override
	public ICacheable put(Object key, ICacheable value) {
		ICacheable c = null;
		if(null != mMemLruCache){
			c = mMemLruCache.put(key, value);
		}
		return c;
	}



	@Override
	public ICacheable remove(Object key) {
		ICacheable c = null;
		if(null != mMemLruCache){
			c = mMemLruCache.remove(key);
		}
		if(null != c) return c;
		if(null != mDiskLruCache){
			c = mDiskLruCache.remove(key);
		}
		return c;
	}



	@Override
	public void reSize(long newMaxSize) {
		// TODO Auto-generated method stub
		
	};
}
