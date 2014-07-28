/*
 * Copyright (c) 2013-2014 Emeral Icemoon All rights reserved.
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

import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetLocator;
import com.jme3.asset.DesktopAssetManager;
import icemoon.iceloader.locators.ServerLocator;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.crypto.spec.SecretKeySpec;

/**
 * Extension of {@link DesktopAssetManager}, mainly to allow indexes of assets available.
 * Also provided are events for progress of assets that are being downloading from a
 * remote location.
 */
public class ServerAssetManager extends DesktopAssetManager {

    /**
     * Interface to be implemented to be notified of asset downloads from the
     * {@link ServerLocator} and it's extensions.
     */
    public interface DownloadingListener {

        /**
         * A download has started.
         * 
         * @param key asset key
         * @param size size of download
         */
        void downloadStarting(AssetKey key, long size);

        /**
         * A download has progressed.
         * 
         * @param key asset key
         * @param progress the number of bytes of the asset now read
         */
        void downloadProgress(AssetKey key, long progress);

        /**
         * A download has completed.
         * 
         * @param key asset key
         */
        void downloadComplete(AssetKey key);
    }

    private static final Logger LOG = Logger.getLogger(ServerAssetManager.class.getName());
    private SecretKeySpec secret;
    private List<AssetIndex> indexes = new ArrayList<AssetIndex>();
    private List<Class<? extends IndexedAssetLocator>> locators;
    private List<DownloadingListener> downloadingListeners = new ArrayList<DownloadingListener>();

    public ServerAssetManager() {
        init();
    }

    public ServerAssetManager(boolean loadDefaults) {
        super(loadDefaults);
        init();
    }

    public ServerAssetManager(URL configFile) {
        super(configFile);
        init();
    }
    
    /**
     * Add a listener to those notified when a remote asset download starts.
     *
     * @param downloadingListener listener
     */
    public void addDownloadingListener(DownloadingListener downloadingListener) {
        downloadingListeners.add(downloadingListener);
    }

    /**
     * Remove a listener from those notified when a remote asset download starts.
     *
     * @param downloadingListener listener
     */
    public void removeDownloadingListener(DownloadingListener downloadingListener) {
        downloadingListeners.remove(downloadingListener);
    }

    @Override
    public void registerLocator(String rootPath, Class<? extends AssetLocator> locatorClass) {
        super.registerLocator(rootPath, locatorClass);

        // Capture the loaders so we can get asset indexes from those that support it
        if (IndexedAssetLocator.class.isAssignableFrom(locatorClass)) {
            final Class<? extends IndexedAssetLocator> clazz = (Class<? extends IndexedAssetLocator>) locatorClass;
            if (locators == null) {
                locators = new ArrayList<Class<? extends IndexedAssetLocator>>();
            }
            locators.add(clazz);
        }
    }

    /**
     * Build the indexes. Should be called only once after the asset manager and all the
     * locators have been configured.
     */
    public void index() {
        if (locators != null) {
            for (Class<? extends IndexedAssetLocator> clazz : locators) {
                try {
                    IndexedAssetLocator loc = (IndexedAssetLocator) clazz.newInstance();
                    AssetIndex index = loc.getIndex(this);
                    if (index == null) {
                        LOG.info(String.format("No asset index for %s", clazz));
                    } else {
                        indexes.add(index);
                        LOG.info(String.format("Asset index for %s contains %d entries", clazz, index.getBackingObject().size()));
                    }
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        } else {
            LOG.warning("No asset indexing done, no locators registered.");
        }
    }
    
    /**
     * Re-index assets.
     */
    public void reindex() {
        indexes.clear();
        index();
    }

    /**
     * Get the first asset item given an assets name.
     * <code>null</code> will be returned if there is no such asset.
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
     * @param pattern pattern
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
     * @param pattern pattern
     * @return list of asset names matching the pattern
     */
    public Set<String> getAssetNamesMatching(String pattern) {
        Set<String> assets = new TreeSet<String>();
        Pattern p = Pattern.compile(pattern);
        for (AssetIndex index : indexes) {
            assets.addAll(index.getAssetNamesMatching(p));
        }
        return assets;
    }

    /**
     * Get if any asset index contains the given name.
     *
     * @param name name
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

    public void fireDownloadStarted(AssetKey key, long length) {
        for (int i = downloadingListeners.size() - 1; i >= 0; i--) {
            downloadingListeners.get(i).downloadStarting(key, length);
        }
    }

    public void fireDownloadProgress(AssetKey key, long progress) {
        for (int i = downloadingListeners.size() - 1; i >= 0; i--) {
            downloadingListeners.get(i).downloadProgress(key, progress);
        }
    }

    public void fireDownloadComplete(AssetKey key) {
        for (int i = downloadingListeners.size() - 1; i >= 0; i--) {
            downloadingListeners.get(i).downloadComplete(key);
        }
    }

    private void init() {
        try {
            secret = EncryptionContext.get().createKey();
        } catch (Exception ex) {
            throw new RuntimeException("Failed to initialize asset manager.", ex);
        }
    }
}
