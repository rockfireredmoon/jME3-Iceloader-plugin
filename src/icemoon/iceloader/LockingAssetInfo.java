package icemoon.iceloader;

import java.io.IOException;
import java.io.InputStream;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;

public class LockingAssetInfo extends AssetInfo {

	private AssetInfo delegate;

	public LockingAssetInfo(AssetManager manager, AssetKey<?> key, AssetInfo delegate) {
		super(manager, key);
		this.delegate = delegate;
	}

	@Override
	public InputStream openStream() {
		((ServerAssetManager) manager).lockAsset(key);
		final InputStream in = delegate.openStream();
		if (in != null) {
			return new FilteredInputStream(in) {
				@Override
				public void close() throws IOException {
					try {
						super.close();
					} finally {
						((ServerAssetManager) manager).unlockAsset(key);
					}
				}
			};
		} else
			((ServerAssetManager) manager).unlockAsset(key);
		return null;
	}

}
