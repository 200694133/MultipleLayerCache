package com.hyn.cache;

public class KeyEntry<K, V> {
	K mKey;
	V mValue;
	KeyEntry(K k, V v){
		mKey = k;
		mValue = v;
	}
	
	public int hashCode(){
		return mKey.hashCode();
	}
	
	public K getKey(){
		return mKey;
	}
	
	public V getValue(){
		return mValue;
	}
	
	public boolean equals(Object o){
		if(o == this || o == mKey) return true;
		return mKey.equals(o);
	}
}
