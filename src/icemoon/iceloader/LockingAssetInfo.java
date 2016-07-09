package icemoon.iceloader;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;

public class LockingAssetInfo extends LoaderAssetInfo {
	private static final Logger LOG = Logger.getLogger(ServerAssetManager.class.getName());

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
				
				private Throwable closed;
				
				@Override
				public void close() throws IOException {
					if(closed != null) {
						try {
							throw new Exception();
						}
						catch(Exception e) {
							LOG.log(Level.WARNING, "Attempt to close locked asset info more than once, ignoring", e);
							LOG.log(Level.WARNING, "First close occured at ..", closed);
						}
						return;
					}
					try {
						super.close();
					} finally {
						closed = new Exception();
						((ServerAssetManager) manager).unlockAsset(key);
					}
				}
			};
		} else
			((ServerAssetManager) manager).unlockAsset(key);
		return null;
	}

	@Override
	public boolean isDecryptedStream() {
		return delegate instanceof LoaderAssetInfo ? ((LoaderAssetInfo)delegate).isDecryptedStream() : false;
	}

}
