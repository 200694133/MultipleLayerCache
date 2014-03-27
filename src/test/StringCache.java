package test;
import com.hyn.cache.ICacheable;


public class StringCache implements ICacheable{
	public String mContent;

	@Override
	public long sizeOf() {
		return mContent.length();
	}

	@Override
	public byte[] toByte() {
		return mContent.getBytes();
	}

}
