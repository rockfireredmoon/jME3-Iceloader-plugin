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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.util.Date;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.vfs2.FileObject;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetLoadException;
import com.jme3.asset.AssetManager;
import com.jme3.asset.AssetNotFoundException;

import icemoon.iceloader.AssetIndex;
import icemoon.iceloader.CachingAssetInfo;
import icemoon.iceloader.ExtendedAssetInfo;
import icemoon.iceloader.IndexItem;
import icemoon.iceloader.ServerAssetManager;

/**
 * This locator will download unencrypted assets from a remote HTTP server. It
 * also has all the support needed for interacting with Iceloader's caching
 * locators, and so is also used to do freshness checks.
 * <p>
 * If the {@link ServerAssetManager} is in use, it will also fire downloading
 * events (see {@link ServerAssetManager} for more information on these events).
 */
public class ServerLocator extends AbstractServerLocator {

	public class JarAssetInfo extends AssetInfo {

		private String suffix;
		private AssetInfo delegate;

		public JarAssetInfo(AssetManager manager, AssetKey<?> key, String suffix, AssetInfo delegate) {
			super(manager, key);
			this.suffix = suffix;
			this.delegate = delegate;
		}

		@Override
		public InputStream openStream() {
			if (delegate instanceof CachingAssetInfo) {
				CachingAssetInfo ca = (CachingAssetInfo) delegate;
				try {
					FileObject cacheRoot = ca.getCacheRoot();
					if (cacheRoot.getURL().getProtocol().equals("file")) {
						try {
							File cacheFile = new File(cacheRoot.getURL().toURI() + "/" + key.getName());
							if (cacheFile.exists()) {
								final JarFile jf = new JarFile(cacheFile);
								JarEntry jent = jf.getJarEntry(suffix);
								final InputStream is = jf.getInputStream(jent);
								return new InputStream() {
									@Override
									public int read() throws IOException {
										return is.read();
									}

									@Override
									public int read(byte[] b) throws IOException {
										return is.read(b);
									}

									@Override
									public int read(byte[] b, int off, int len) throws IOException {
										return is.read(b, off, len);
									}

									@Override
									public void close() throws IOException {
										try {
											is.close();
										} finally {
											jf.close();
										}
									}
								};
							}
						} catch (URISyntaxException e) {
							throw new AssetNotFoundException(
									String.format("Could not locate asset %s in cached archive %s.", suffix, key), e);
						}
					} else {
						FileObject cacheFile = cacheRoot.resolveFile(key.getName());
						if (cacheFile.exists()) {
							cacheFile = cacheRoot.getFileSystem().getFileSystemManager().resolveFile(
									"jar:" + cacheRoot.getName().getURI() + "/" + key.getName() + "!" + suffix);
							return cacheFile.getContent().getInputStream();
						}
					}
				} catch (IOException e) {
					throw new AssetNotFoundException(
							String.format("Could not locate asset %s in cached archive %s.", suffix, key), e);
				}
			}

			// Will be slow
			try {
				final InputStream stream = delegate.openStream();
				final JarInputStream jin = new JarInputStream(stream);
				JarEntry jen = null;
				while ((jen = jin.getNextJarEntry()) != null) {
					if (jen.getName().equals(suffix)) {
						return new InputStream() {

							@Override
							public int read(byte[] b) throws IOException {
								return jin.read(b);
							}

							@Override
							public int read(byte[] b, int off, int len) throws IOException {
								return jin.read(b, off, len);
							}

							@Override
							public void close() throws IOException {
								super.close();

								// Sink the end of the streamm so the entire
								// file gets cached
								byte[] buf = new byte[8192];
								while (stream.read(buf) != -1)
									;
								jin.close();
							}

							@Override
							public int read() throws IOException {
								return jin.read();
							}
						};
					}
				}
			} catch (IOException e) {
				throw new AssetNotFoundException(
						String.format("Could not extract asset %s from archive stream %s.", suffix, key));
			}
			throw new AssetNotFoundException(
					String.format("Could not find asset %s i archive stream %s.", suffix, key));

		}

	}

	private static final Logger LOG = Logger.getLogger(ServerLocator.class.getName());

	static {
		try {
			serverRoot = new URL(System.getProperty("icescene.serverLocation", "http://localhost:8080/Iceserver/"));
		} catch (Exception ex) {
			throw new AssetLoadException("Root path is invalid", ex);
		}
	}

	public AssetInfo locate(AssetManager manager, AssetKey key) {
		String name = key.getName();
		long ifModifiedSince = -1;
		AssetInfo cachedInfo = null;
		String suffix = null;

		// The server might have provided an index, we can use this to save a
		// round-trip
		// using If-Modified-Since
		IndexItem indexItem = null;

		if (!name.equals(AssetIndex.DEFAULT_RESOURCE_NAME)) {
			if (manager instanceof ServerAssetManager) {
				indexItem = ((ServerAssetManager) manager).getAsset(key.getName());
				if (indexItem != null) {
					String folder = key.getFolder();
					while (folder.endsWith("/"))
						folder = folder.substring(0, folder.length() - 1);
					IndexItem archiveIndexItem = ((ServerAssetManager) manager).getAsset(folder + ".jar");
					if (archiveIndexItem == null) {
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine(String.format("%s is not in an indexed archive.", key));
						}
					} else {
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine(String.format("%s has an indexed archive.", key));
						}
						suffix = indexItem.getName().substring(folder.length() + 1);
						indexItem = archiveIndexItem;
						key = new AssetKey(name = archiveIndexItem.getName());
					}
				}
			}

			// See if something was found in the cache. If it was, then we check
			// the
			// last modified time before actually using it
			if (AssetCacheLocator.isInUse()) {
				cachedInfo = AssetCacheLocator.getCachedAssetInfo(key);
				if (cachedInfo != null && cachedInfo instanceof ExtendedAssetInfo) {

					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine(String.format("Will test if %s is modified since %s", key,
								DateFormat.getDateTimeInstance()
										.format(new Date(((ExtendedAssetInfo) cachedInfo).getLastModified()))));
					}
					ifModifiedSince = ((ExtendedAssetInfo) cachedInfo).getLastModified();

					// If we have an index item for this, we can test last
					// modified now
					if (indexItem != null) {
						long diff = indexItem.getLastModified() - ifModifiedSince;
						if (diff < 10000) {

							if (LOG.isLoggable(Level.FINE)) {
								LOG.fine("Index item says this is not modified, just use cached version");
							}
							return cachedInfo;
						} else {
							if (LOG.isLoggable(Level.FINE)) {
								LOG.fine(String.format(
										"Looks like we'll have to download this as modification dates in index are different "
												+ "(%d compared to %d = %d) (%s, %s, %d)",
										indexItem.getLastModified(), ifModifiedSince, diff,
										DateFormat.getDateTimeInstance().format(new Date(indexItem.getLastModified())),
										DateFormat.getDateTimeInstance().format(new Date(ifModifiedSince)),
										diff / 1000));
							}
						}
					}

				}
			}

			// If we have an index, we determine if the resource exists without
			// going to the server
			if (manager instanceof ServerAssetManager && indexItem == null) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("Asset in known not to exist (due to index not containing it)");
				}
				return null;
			}
		}

		try {
			// Encode each part of the path
			String[] parts = name.split("/");
			StringBuilder encName = new StringBuilder();
			for (String part : parts) {
				if (encName.length() > 0) {
					encName.append("/");
				}
				encName.append(URLEncoder.encode(part, "UTF-8"));
			}
			URL url = new URL(root, encName.toString());
			AssetInfo ai = create(manager, key, url, ifModifiedSince);

			// If the asset is found, it is not already a cached asset, as the
			// cacher is in use, cache it
			if (ai != null && !ai.equals(cachedInfo) && AssetCacheLocator.isInUse()) {
				ai = new CachingAssetInfo(manager, key, ai, AssetCacheLocator.getVFSRoot());
			}

			if (ai != null && !(ai instanceof JarAssetInfo) && suffix != null) {
				ai = new JarAssetInfo(manager, key, suffix, ai);
			}

			return ai;
		} catch (FileNotFoundException e) {
			return null;
		} catch (IOException ex) {
			LOG.log(Level.WARNING, "Error while locating " + name, ex);
			return null;
		}
	}

}
