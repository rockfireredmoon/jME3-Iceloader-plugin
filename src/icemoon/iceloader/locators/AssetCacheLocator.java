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
package icemoon.iceloader.locators;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetLoadException;
import com.jme3.asset.AssetManager;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.VFS;
import icemoon.iceloader.AbstractVFSLocator;

/**
 * This will find in your local cache, that is is populated by other locators that may
 * download assets. If an asset is found here, it will be returned to JME (eventually,
 * after some an optional freshness check).
 */
public class AssetCacheLocator extends AbstractVFSLocator {

    private static final Logger LOG = Logger.getLogger(AssetCacheLocator.class.getName());
    private static FileObject cacheRoot;
    private static boolean inUse;
    private static Map<String, AssetInfo> cachedAssetInfo = new HashMap<String, AssetInfo>();

    static {
        try {
            cacheRoot = VFS.getManager().resolveFile(System.getProperty("iceloader.assetCache", System.getProperty("java.io.tmpdir") + File.separator + "icescene-cache"));
        } catch (FileSystemException ex) {
            throw new AssetLoadException("Root path is invalid", ex);
        }
    }

    public AssetCacheLocator() {
        super(cacheRoot);
        inUse = true;
    }

    @Override
    public AssetInfo locate(AssetManager manager, AssetKey key) {
        AssetInfo info = super.locate(manager, key);
        if (info == null) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine(String.format("%s not located in cache.", key));
            }
            cachedAssetInfo.remove(key.getName());
        } else {
            if (cachedAssetInfo.containsKey(key.getName())) {
                // Already done this once, no need to check for freshness again, just return the cached resource
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine(String.format("Just returning cached copy of %s, no need to check for freshness within this runtime", key.getName()));
                }
            } else {
                cachedAssetInfo.put(key.getName(), info);
                if ("true".equalsIgnoreCase(System.getProperty("icescene.checkCacheForUpdates", "true"))) {
                    // Don't return the asset info just yet. Let other methods try first. For example,
                    // a locator that loads from HTTP might check if the cached version is out of date
                    // before downloading. If there are no changes, it can return this cached version.
                    // Note, this won't work with standard JME3 locators, it requires special support
                    // for retrieving the cached version via getCachedAssetInfo() here.
                    if (LOG.isLoggable(Level.FINE)) {
                        LOG.fine(String.format("%s located in cache, checking for updates before returning this copy.", key));
                    }
                    info = null;
                } else {
                    if (LOG.isLoggable(Level.FINE)) {
                        LOG.fine(String.format("%s located in cache, returning cached copy.", key));
                    }
                }
            }
        }
        return info;
    }

    public static AssetInfo getCachedAssetInfo(AssetKey key) {
        return cachedAssetInfo.get(key.getName());
    }

    public static boolean isInUse() {
        return inUse;
    }

    public static FileObject getVFSRoot() {
        return cacheRoot;
    }

    public static void setVFSRoot(FileObject cacheRoot) {
        AssetCacheLocator.cacheRoot = cacheRoot;
    }
}
