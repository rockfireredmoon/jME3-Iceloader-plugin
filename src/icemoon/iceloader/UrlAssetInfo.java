/*
 * Copyright (c) 2013-2016 Emerald Icemoon All rights reserved.
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
package icemoon.iceloader;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetLoadException;
import com.jme3.asset.AssetManager;

import icemoon.iceloader.locators.AbstractServerLocator;
import icemoon.iceloader.locators.ServerLocator;

public class UrlAssetInfo extends ExtendedAssetInfo {
	private static final Logger LOG = Logger.getLogger(ServerLocator.class.getName());

	private URL url;
	private InputStream in;
	private long lastModified;
	private long size;
	private final long ifModifiedSince;
	private final AbstractServerLocator locator;
	private final long unprocessedSize;

	public UrlAssetInfo(AssetManager assetManager, AssetKey<?> key, URL url, InputStream in, long ifModifiedSince,
			AbstractServerLocator locator, long lastModified, long size, long unprocessedSize) throws IOException {
		super(assetManager, key);
		this.locator = locator;
		this.url = url;
		this.unprocessedSize = unprocessedSize;
		this.ifModifiedSince = ifModifiedSince;
		this.in = in;
		this.size = size;
		this.lastModified = lastModified;
	}

	public void setLastModified(long lastModified) {
		this.lastModified = lastModified;
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
				if (ifModifiedSince != -1 && locator.isUseCaching()) {
					conn.setIfModifiedSince(ifModifiedSince);
				}
				conn.setConnectTimeout(AbstractServerLocator.getConnectTimeout());
				conn.setReadTimeout(AbstractServerLocator.getReadTimeout());

				if (conn instanceof HttpURLConnection) {
					int resp = ((HttpURLConnection) conn).getResponseCode();
					if (resp == 304) {
						if (locator.isUseCaching()) {
							// Content has not changed, return original
							// cached content
							if (LOG.isLoggable(Level.FINE)) {
								LOG
										.fine(String.format("Content %s has not changed, using cached version"));
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
				return locator.getStream((ServerAssetManager) getManager(), getKey(), conn,
						unprocessedSize > -1 ? unprocessedSize : size);
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