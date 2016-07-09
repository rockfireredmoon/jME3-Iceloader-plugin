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

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetLocator;
import com.jme3.asset.DesktopAssetManager;

import icemoon.iceloader.locators.ServerLocator;

/**
 * Extension of {@link DesktopAssetManager}, mainly to allow indexes of assets
 * available. Also provided are events for progress of assets that are being
 * downloading from a remote location.
 */
public class ServerAssetManager extends DesktopAssetManager {

	/**
	 * Interface to be implemented to be notified of asset downloads from the
	 * {@link ServerLocator} and it's extensions.
	 */
	public interface DownloadingListener {
		/**
		 * An asset was requested.
		 * 
		 * @param key
		 *            asset key
		 */
		void assetRequested(AssetKey<?> key);

		/**
		 * An asset was suppliied.
		 * 
		 * @param key
		 *            asset key
		 */
		void assetSupplied(AssetKey<?> key);

		/**
		 * A download has started.
		 * 
		 * @param key
		 *            asset key
		 * @param size
		 *            size of download
		 */
		void downloadStarting(AssetKey<?> key, long size);

		/**
		 * A download has progressed.
		 * 
		 * @param key
		 *            asset key
		 * @param progress
		 *            the number of bytes of the asset now read
		 */
		void downloadProgress(AssetKey<?> key, long progress);

		/**
		 * A download has completed.
		 * 
		 * @param key
		 *            asset key
		 */
		void downloadComplete(AssetKey<?> key);
	}

	private static final Logger LOG = Logger.getLogger(ServerAssetManager.class.getName());
	private List<AssetIndex> indexes = new ArrayList<AssetIndex>();
	private Map<String, List<Class<? extends AssetLocator>>> locators;
	private List<DownloadingListener> downloadingListeners = new ArrayList<DownloadingListener>();
	private Map<String, Set<String>> assetPatternsCache = new LinkedHashMap<String, Set<String>>();
	private List<List<AssetKey<?>>> waitings = new LinkedList<List<AssetKey<?>>>();
	private Map<AssetKey<?>, ReentrantLock> keyLocks = new HashMap<AssetKey<?>, ReentrantLock>();

	public ServerAssetManager() {
		super();
	}

	public ServerAssetManager(URL configFile) {
		super(configFile);
	}

	public void lockAsset(AssetKey<?> key) {
		ReentrantLock s;
		synchronized (keyLocks) {
			s = keyLocks.get(key);
			if (s == null) {
				s = new ReentrantLock(true);
				keyLocks.put(key, s);
			}
		}
		if (LOG.isLoggable(Level.FINE))
			LOG.fine(String.format("Acquiring lock on %s", key));
		s.lock();
		if (LOG.isLoggable(Level.FINE))
			LOG.fine(String.format("Acquired lock on %s", key));
	}

	public void unlockAsset(AssetKey<?> key) {
		ReentrantLock s;
		synchronized (keyLocks) {
			s = keyLocks.get(key);
			if (s == null) {
				throw new IllegalArgumentException("Not locked.");
			}
		}

		if (LOG.isLoggable(Level.FINE))
			LOG.fine(String.format("Releasing lock on %s", key));
		s.unlock();
		;
	}

	/**
	 * Add a list of assists that we expect to load. This is purely for the
	 * benefit of loading screens. An operation indicates up front the list of
	 * assets that it will be attempt to load. These are stored until the files
	 * are either actually downloaded, or supplied from the cache.
	 * 
	 * @param assets
	 *            list of assets we expect to load
	 */
	public void require(List<AssetKey<?>> assets) {
		waitings.add(assets);
		for (AssetKey<?> r : assets)
			fireAssetRequested(r);
	}

	/**
	 * Add a listener to those notified when a remote asset download starts.
	 *
	 * @param downloadingListener
	 *            listener
	 */
	public void addDownloadingListener(DownloadingListener downloadingListener) {
		downloadingListeners.add(downloadingListener);
	}

	/**
	 * Remove a listener from those notified when a remote asset download
	 * starts.
	 *
	 * @param downloadingListener
	 *            listener
	 */
	public void removeDownloadingListener(DownloadingListener downloadingListener) {
		downloadingListeners.remove(downloadingListener);
	}

	public void serverAssetLocationChanged() {
		reregisterLocators();
		reindex();
	}

	/**
	 * Re-register (and so re-create) all of the locators.
	 */
	public void reregisterLocators() {
		if (locators != null) {
			Map<String, List<Class<? extends AssetLocator>>> lo;
			synchronized (locators) {
				lo = new LinkedHashMap<String, List<Class<? extends AssetLocator>>>();
				for (Map.Entry<String, List<Class<? extends AssetLocator>>> l : locators.entrySet()) {
					lo.put(l.getKey(), new ArrayList<Class<? extends AssetLocator>>(l.getValue()));
				}
			}
			for (Map.Entry<String, List<Class<? extends AssetLocator>>> l : lo.entrySet()) {
				for (Class<? extends AssetLocator> c : l.getValue()) {
					unregisterLocator(l.getKey(), c);
				}
			}
			for (Map.Entry<String, List<Class<? extends AssetLocator>>> l : lo.entrySet()) {
				for (Class<? extends AssetLocator> c : l.getValue()) {
					registerLocator(l.getKey(), c);
				}
			}
		}
	}

	@Override
	public void unregisterLocator(String rootPath, Class<? extends AssetLocator> clazz) {
		super.unregisterLocator(rootPath, clazz);
		if (IndexedAssetLocator.class.isAssignableFrom(clazz)) {
			if (locators != null) {
				List<Class<? extends AssetLocator>> list = locators.get(rootPath);
				if (list != null)
					list.remove(clazz);
			}
		}
	}

	@Override
	public void registerLocator(String rootPath, Class<? extends AssetLocator> locatorClass) {
		super.registerLocator(rootPath, locatorClass);

		// Capture the loaders so we can get asset indexes from those that
		// support it
		if (IndexedAssetLocator.class.isAssignableFrom(locatorClass)) {
			@SuppressWarnings("unchecked")
			final Class<? extends IndexedAssetLocator> clazz = (Class<? extends IndexedAssetLocator>) locatorClass;
			if (locators == null) {
				locators = new HashMap<String, List<Class<? extends AssetLocator>>>();
			}
			List<Class<? extends AssetLocator>> list = locators.get(rootPath);
			if (list == null) {
				list = new ArrayList<Class<? extends AssetLocator>>();
				locators.put(rootPath, list);
			}
			list.add(clazz);
		}
	}

	/**
	 * Build the indexes. Should be called only once after the asset manager and
	 * all the locators have been configured.
	 */
	public void index() {
		int indexers = 0;
		if (locators != null) {
			synchronized (locators) {
				for (Map.Entry<String, List<Class<? extends AssetLocator>>> clazz : locators.entrySet()) {
					List<Class<? extends AssetLocator>> list = clazz.getValue();
					synchronized (list) {
						for (Class<? extends AssetLocator> c : list) {
							if (IndexedAssetLocator.class.isAssignableFrom(c)) {
								indexers++;
								try {
									IndexedAssetLocator loc = (IndexedAssetLocator) c.newInstance();
									AssetIndex index = loc.getIndex(this);
									if (index == null) {
										LOG.info(String.format("No asset index for %s", c));
									} else {
										indexes.add(index);
										LOG.info(String.format("Asset index for %s contains %d entries", c,
												index.getBackingObject().size()));
									}
								} catch (Exception ex) {
									throw new RuntimeException(ex);
								}
							}
						}
					}
				}
			}
		}

		if (indexers == 0) {
			LOG.warning("No asset indexing done, no locators registered.");
		}
	}

	/**
	 * Get all indexes
	 */
	public List<AssetIndex> getIndexes() {
		return indexes;
	}

	/**
	 * Re-index assets.
	 */
	public void reindex() {
		indexes.clear();
		assetPatternsCache.clear();
		index();
	}

	/**
	 * Get the first asset item given an assets name. <code>null</code> will be
	 * returned if there is no such asset.
	 *
	 * @param name
	 * @return asset
	 */
	public IndexItem getAsset(String name) {
		for (AssetIndex index : indexes) {
			IndexItem i = index.getAsset(name);
			if (i != null) {
				return i;
			}
		}
		return null;
	}

	/**
	 * Get all of the indexed assets given a regular expression.
	 *
	 * @param pattern
	 *            pattern
	 * @return list of assets matching the pattern
	 */
	public Set<IndexItem> getAssetsMatching(String pattern) {
		Set<IndexItem> assets = new TreeSet<IndexItem>();
		Pattern p = Pattern.compile(pattern);
		for (AssetIndex index : indexes) {
			assets.addAll(index.getAssetsMatching(p));
		}
		return assets;
	}

	/**
	 * Get all of the indexed asset names given a regular expression.
	 *
	 * @param pattern
	 *            pattern
	 * @return list of asset names matching the pattern
	 */
	public Set<String> getAssetNamesMatching(String pattern) {
		return getAssetNamesMatching(pattern, 0);
	}

	/**
	 * Get all of the indexed asset names given a regular expression.
	 *
	 * @param pattern
	 *            pattern
	 * @param regexp
	 *            flags (see {@link Pattern}).
	 * @return list of asset names matching the pattern
	 */
	public Set<String> getAssetNamesMatching(String pattern, int flags) {
		String k = pattern + "_" + flags;
		long now = System.currentTimeMillis();
		Set<String> assets = assetPatternsCache.get(k);
		if (assets == null) {
			assets = new TreeSet<String>();
			Pattern p = Pattern.compile(pattern, flags);
			for (AssetIndex index : indexes) {
				assets.addAll(index.getAssetNamesMatching(p));
			}
			assetPatternsCache.put(k, assets);
		}
		LOG.info(String.format("Took %d ms to find %s (resulted in %d hits)", System.currentTimeMillis() - now, pattern,
				assets.size()));
		return assets;
	}

	/**
	 * Get if any asset index contains the given name.
	 *
	 * @param name
	 *            name
	 * @return contained in index
	 */
	public boolean hasAsset(String name) {
		for (AssetIndex index : indexes) {
			if (index.hasAsset(name)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public <T> T loadAsset(AssetKey<T> key) {
		T t = super.loadAsset(key);
		return t;
	}

	public void fireDownloadStarted(AssetKey<?> key, long length) {
		for (int i = downloadingListeners.size() - 1; i >= 0; i--) {
			downloadingListeners.get(i).downloadStarting(key, length);
		}
	}

	public void fireDownloadProgress(AssetKey<?> key, long progress) {
		for (int i = downloadingListeners.size() - 1; i >= 0; i--) {
			downloadingListeners.get(i).downloadProgress(key, progress);
		}
	}

	public void fireDownloadComplete(AssetKey<?> key) {
		for (int i = downloadingListeners.size() - 1; i >= 0; i--) {
			downloadingListeners.get(i).downloadComplete(key);
		}
	}

	public void fireAssetRequested(AssetKey<?> key) {
		for (int i = downloadingListeners.size() - 1; i >= 0; i--) {
			downloadingListeners.get(i).assetRequested(key);
		}
	}

	public void fireAssetSupplied(AssetKey<?> key) {
		for (int i = downloadingListeners.size() - 1; i >= 0; i--) {
			downloadingListeners.get(i).assetSupplied(key);
		}
	}

}
