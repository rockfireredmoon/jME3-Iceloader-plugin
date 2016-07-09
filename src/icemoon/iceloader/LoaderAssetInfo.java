package icemoon.iceloader;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;

public abstract class LoaderAssetInfo extends AssetInfo {

	public LoaderAssetInfo(AssetManager manager, AssetKey<?> key) {
		super(manager, key);
	}

	public abstract boolean isDecryptedStream();
}
