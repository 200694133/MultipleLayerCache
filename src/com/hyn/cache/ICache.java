package com.hyn.cache;

public interface ICache {
	
	public ICacheable get(Object key);
	
	public ICacheable put(Object key, ICacheable value);
	
	public ICacheable remove(Object key);

	public void reSize(long newMaxSize);
}

