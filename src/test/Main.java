package test;

import com.hyn.cache.MultipleLayerLruCache;

public class Main {
	private static final int count = 32;
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		MultipleLayerLruCache cache = new MultipleLayerLruCache(128, "C:/cache/", 1024L);
		StringCache []datas = new StringCache[count];
		for(int i=0;i<count;++i){
			String s  = "";
			for(int j=0;j<48;++j) s += ""+i;
			StringCache sc = new StringCache();
			sc.mContent = s;
			datas[i] = sc;
		}
		
		for(int i=0;i<datas.length;++i){
			push(cache, ""+i, datas[i]);
		}
	}
	
	
	
	
	public static void push(MultipleLayerLruCache cache, String index, StringCache info){
		cache.put(index, info);
	}
}
