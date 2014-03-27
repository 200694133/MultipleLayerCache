package com.hyn.cache;

import test.StringCache;

public class CachableFactory {
	public static ICacheable parse(byte[] data){
		StringCache s = new StringCache();
		String s1 = new String(data);
		s.mContent = s1;
		return s;
	}
}
