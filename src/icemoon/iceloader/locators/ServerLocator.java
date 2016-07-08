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
package icemoon.iceloader.locators;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetLoadException;
import com.jme3.asset.AssetManager;

import icemoon.iceloader.AssetIndex;
import icemoon.iceloader.CachingAssetInfo;
import icemoon.iceloader.ExtendedAssetInfo;
import icemoon.iceloader.IndexItem;
import icemoon.iceloader.JarAssetInfo;
import icemoon.iceloader.LoaderUtils;
import icemoon.iceloader.LockingAssetInfo;
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

	private static final Logger LOG = Logger.getLogger(ServerLocator.class.getName());

	static {
		try {
			serverRoot = new URL(System.getProperty("icescene.serverLocation", "http://localhost:8080/Iceserver/"));
		} catch (Exception ex) {
			throw new AssetLoadException("Root path is invalid", ex);
		}
	}

	@SuppressWarnings("rawtypes")
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

							if (cachedInfo != null && !(cachedInfo instanceof JarAssetInfo) && suffix != null) {
								cachedInfo = new JarAssetInfo(manager, key, suffix, cachedInfo);
							}
							
							cachedInfo = new LockingAssetInfo(manager, key, cachedInfo);
							
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
			URL url = new URL(LoaderUtils.ensureEndsWithSlash(root), encName.toString());
			
			AssetInfo ai = create(manager, key, url, ifModifiedSince, indexItem== null ? -1 : indexItem.getUnprocessedSize());

			// If the asset is found, it is not already a cached asset, as the
			// cacher is in use, cache it
			if (ai != null && !ai.equals(cachedInfo) && AssetCacheLocator.isInUse()) {
				ai = new CachingAssetInfo(manager, key, ai, AssetCacheLocator.getVFSRoot());
			}

			if (ai != null && !(ai instanceof JarAssetInfo) && suffix != null) {
				ai = new JarAssetInfo(manager, key, suffix, ai);
			}
			
			ai = new LockingAssetInfo(manager, key, ai);

			return ai;
		} catch (FileNotFoundException e) {
			return null;
		} catch (IOException ex) {
			LOG.log(Level.WARNING, "Error while locating " + name, ex);
			return null;
		}
	}

}
