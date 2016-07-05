/*
 * Copyright (c) 2013-2014 Emerald Icemoon All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *  *
 * * Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * * Neither the name of 'jMonkeyEngine' nor the names of its contributors
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package icemoon.iceloader.locators;

import icemoon.iceloader.IndexedAssetLocator;
import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetLoadException;
import com.jme3.asset.AssetManager;
import com.jme3.asset.AssetNotFoundException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import icemoon.iceloader.AssetIndex;
import icemoon.iceloader.ExtendedAssetInfo;
import icemoon.iceloader.ServerAssetManager;

/**
 */
public abstract class AbstractServerLocator implements IndexedAssetLocator {

	private static final Logger LOG = Logger.getLogger(AbstractServerLocator.class.getName());
	protected URL root;
	protected static URL serverRoot;

	public static URL getServerRoot() {
		return serverRoot;
	}

	public static void setServerRoot(URL serverRoot) {
		if(!Objects.equals(AbstractServerLocator.serverRoot, serverRoot))
		AbstractServerLocator.serverRoot = serverRoot;
	}

	private AssetIndex assetIndex;
	private boolean fireEvents = true;
	private boolean useCaching = true;
	private boolean loadedAssetIndex;
	private static int connectTimeout = 30000;
	private static int readTimeout = 30000;

	public AbstractServerLocator() {
		root = serverRoot;
	}

	public static int getConnectTimeout() {
		return connectTimeout;
	}

	public static void setConnectTimeout(int connectTimeout) {
		AbstractServerLocator.connectTimeout = connectTimeout;
	}

	public static int getReadTimeout() {
		return readTimeout;
	}

	public static void setReadTimeout(int readTimeout) {
		AbstractServerLocator.readTimeout = readTimeout;
	}

	public boolean isFireEvents() {
		return fireEvents;
	}

	public boolean isUseCaching() {
		return useCaching;
	}

	public void setUseCaching(boolean useCaching) {
		this.useCaching = useCaching;
	}

	public void setFireEvents(boolean fireEvents) {
		this.fireEvents = fireEvents;
	}

	public void setRootPath(String rootPath) {
		// TODO need to support this properly
		// try {
		// this.root = new URL(rootPath);
		// } catch (MalformedURLException ex) {
		// throw new IllegalArgumentException("Invalid root url.", ex);
		// }
	}

	public AssetInfo create(AssetManager assetManager, AssetKey key, URL url, long ifModifiedSince) throws IOException {
		// Check if URL can be reached. This will throw
		// IOException which calling code will handle.
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine(String.format("Attempting to download %s (from root %s) from the HTTP server", url, root));
		}
		URLConnection conn = url.openConnection();
		conn.setConnectTimeout(connectTimeout);
		conn.setReadTimeout(readTimeout);
		conn.setUseCaches(false);
		if (ifModifiedSince != -1 && useCaching) {
			conn.setIfModifiedSince(ifModifiedSince);
		}
		if (conn instanceof HttpURLConnection) {
			final HttpURLConnection httpConn = (HttpURLConnection) conn;
			int resp = httpConn.getResponseCode();
			if (resp == 304) {
				if (useCaching) {
					return getCachedAssetInfo(assetManager, key);
				} else {
					throw new AssetLoadException("Caching is not enabled, unexpected 304 response.");
				}
			}
		}
		long lastModified = conn.getLastModified();
		long size = conn.getContentLengthLong();

		InputStream in = assetManager instanceof ServerAssetManager ? getStream((ServerAssetManager) assetManager, key, conn, size)
				: conn.getInputStream();

		// For some reason url cannot be reached?
		if (in == null) {
			// 404 etc
			return null;
		} else {
			final UrlAssetInfo inf = new UrlAssetInfo(assetManager, key, url, in, ifModifiedSince, this);
			inf.lastModified = lastModified == 0 ? -1 : lastModified;
			inf.size = size;
			return inf;
		}
	}

	protected AssetInfo getCachedAssetInfo(AssetManager manager, AssetKey key) {
		// Content has not changed, return original cached content
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine(String.format("Content %s has not changed, using cached version", key.getName()));
		}
		return AssetCacheLocator.getCachedAssetInfo(key);
	}

	public AssetIndex getIndex(AssetManager assetManager) {
		if (!loadedAssetIndex) {
			try {
				AssetInfo info = locate(assetManager, new AssetKey<AssetIndex>(AssetIndex.DEFAULT_RESOURCE_NAME));
				if (info != null) {
					assetIndex = new AssetIndex(assetManager);
					try {
						assetIndex.load(info.openStream());
					} catch (IOException ex) {
						throw new AssetLoadException("Failed to load index.", ex);
					}
					assetIndex.configure(info instanceof ExtendedAssetInfo ? ((ExtendedAssetInfo) info).getLastModified() : 0,
							getClass().getSimpleName().toLowerCase() + "://" + info.getKey().getName());
				}

			} catch (AssetNotFoundException anfe) {
			} finally {
				loadedAssetIndex = true;
			}
		}
		return assetIndex;
	}

	private InputStream getStream(final ServerAssetManager assetManager, final AssetKey key, URLConnection conn, long fileLength)
			throws IOException {
		if (fireEvents) {
			assetManager.fireDownloadStarted(key, fileLength);
			InputStream in = conn.getInputStream();
			return new FilterInputStream(in) {
				private long total;

				@Override
				public int read() throws IOException {
					int r = super.read();
					if (r != -1) {
						total++;
						assetManager.fireDownloadProgress(key, total);
					}
					return r;
				}

				@Override
				public int read(byte[] b) throws IOException {
					int r = super.read(b);
					if (r != -1) {
						total += r;
						assetManager.fireDownloadProgress(key, total);
					}
					return r;
				}

				@Override
				public int read(byte[] b, int off, int len) throws IOException {
					int r = super.read(b, off, len);
					if (r != -1) {
						total += r;
						assetManager.fireDownloadProgress(key, total);
					}
					return r;
				}

				@Override
				public void close() throws IOException {
					super.close();
					assetManager.fireDownloadComplete(key);
				}
			};
		} else {
			return conn.getInputStream();
		}
	}

	public static class UrlAssetInfo extends ExtendedAssetInfo {

		private URL url;
		private InputStream in;
		private long lastModified;
		private long size;
		private final long ifModifiedSince;
		private final AbstractServerLocator locator;

		private UrlAssetInfo(AssetManager assetManager, AssetKey key, URL url, InputStream in, long ifModifiedSince,
				AbstractServerLocator locator) throws IOException {
			super(assetManager, key);
			this.locator = locator;
			this.url = url;
			this.ifModifiedSince = ifModifiedSince;
			this.in = in;
		}

		public boolean hasInitialConnection() {
			return in != null;
		}

		@Override
		public InputStream openStream() {
			if (in != null) {
				// Reuse the already existing stream (only once)
				InputStream in2 = in;
				in = null;
				return in2;
			} else {
				// Create a new stream for subsequent invocations.
				try {
					URLConnection conn = url.openConnection();
					if (ifModifiedSince != -1 && locator.useCaching) {
						conn.setIfModifiedSince(ifModifiedSince);
					}
					conn.setConnectTimeout(connectTimeout);
					conn.setReadTimeout(readTimeout);

					if (conn instanceof HttpURLConnection) {
						int resp = ((HttpURLConnection) conn).getResponseCode();
						if (resp == 304) {
							if (locator.useCaching) {
								// Content has not changed, return original
								// cached content
								if (LOG.isLoggable(Level.FINE)) {
									LOG.fine(String.format("Content %s has not changed, using cached version"));
								}
								return locator.getCachedAssetInfo(manager, key).openStream();
							} else {
								throw new AssetLoadException("Caching is not enabled, unexpected 304 response.");
							}
						}
					}

					lastModified = conn.getLastModified();
					if (lastModified == 0) {
						lastModified = -1;
					}
					size = conn.getContentLength();
					conn.setUseCaches(false);
					return locator.getStream((ServerAssetManager) getManager(), getKey(), conn, size);
				} catch (IOException ex) {
					throw new AssetLoadException("Failed to read URL " + url, ex);
				}
			}
		}

		@Override
		public long getSize() {
			return size;
		}

		@Override
		public long getLastModified() {
			return lastModified;
		}
	}
}
