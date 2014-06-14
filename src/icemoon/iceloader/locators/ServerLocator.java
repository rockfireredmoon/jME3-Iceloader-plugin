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

import icemoon.iceloader.IndexedAssetLocator;
import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetLoadException;
import com.jme3.asset.AssetManager;
import com.jme3.asset.AssetNotFoundException;
import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import icemoon.iceloader.AssetIndex;
import icemoon.iceloader.CachingAssetInfo;
import icemoon.iceloader.ExtendedAssetInfo;
import icemoon.iceloader.IndexItem;
import icemoon.iceloader.ServerAssetManager;

/**
 * This locator will download unencrypted assets from a remote HTTP server. It also has
 * all the support needed for interacting with Iceloader's caching locators, and so is
 * also used to do freshness checks.
 * <p>
 * If the {@link ServerAssetManager} is in use, it will also fire downloading events
 * (see {@link ServerAssetManager} for more information on these events).
 */
public class ServerLocator implements IndexedAssetLocator {

    private static final Logger LOG = Logger.getLogger(ServerLocator.class.getName());
    private URL root;
    private static URL serverRoot;

    static {
        try {
            serverRoot = new URL(System.getProperty("icescene.serverLocation", "http://localhost:8080/Iceserver/"));
        } catch (Exception ex) {
            throw new AssetLoadException("Root path is invalid", ex);
        }
    }

    public static URL getServerRoot() {
        return serverRoot;
    }

    public static void setServerRoot(URL serverRoot) {
        ServerLocator.serverRoot = serverRoot;
    }
    private AssetIndex assetIndex;
    private boolean loadedAssetIndex;

    public ServerLocator() {
        root = serverRoot;
    }

    public void setRootPath(String rootPath) {
        // TODO need to support this properly
//        try {
//            this.root = new URL(rootPath);
//        } catch (MalformedURLException ex) {
//            throw new IllegalArgumentException("Invalid root url.", ex);
//        }
    }

    public AssetInfo locate(AssetManager manager, AssetKey key) {
        String name = key.getName();
        long ifModifiedSince = -1;
        AssetInfo cachedInfo = null;

        // The server might have provided an index, we can use this to save a round-trip
        // using If-Modified-Since
        IndexItem indexItem = null;

        if (!name.equals(AssetIndex.DEFAULT_RESOURCE_NAME)) {
            if (manager instanceof ServerAssetManager) {
                indexItem = ((ServerAssetManager) manager).getAsset(key.getName());
            }

            // See if something was found in the cache. If it was, then we check the 
            // last modified time before actually using it
            if (AssetCacheLocator.isInUse()) {
                cachedInfo = AssetCacheLocator.getCachedAssetInfo(key);
                if (cachedInfo != null && cachedInfo instanceof ExtendedAssetInfo) {

                    if (LOG.isLoggable(Level.FINE)) {
                        LOG.fine(String.format("Will test if %s is modified since %s", key, DateFormat.getDateTimeInstance().format(new Date(((ExtendedAssetInfo) cachedInfo).getLastModified()))));
                    }
                    ifModifiedSince = ((ExtendedAssetInfo) cachedInfo).getLastModified();

                    // If we have an index item for this, we can test last modified now
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

            // If we have an index, we determine if the resource exists without going to the server
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
            final AssetInfo ai = create(manager, key, url, ifModifiedSince);

            // If the asset is found, it is not already a cached asset, as the cacher is in use, cache it            
            if (ai != null && !ai.equals(cachedInfo) && AssetCacheLocator.isInUse()) {
                return new CachingAssetInfo(manager, key, ai, AssetCacheLocator.getVFSRoot());
            }
            return ai;
        } catch (FileNotFoundException e) {
            return null;
        } catch (IOException ex) {
            LOG.log(Level.WARNING, "Error while locating " + name, ex);
            return null;
        }
    }

    public AssetInfo create(AssetManager assetManager, AssetKey key, URL url, long ifModifiedSince) throws IOException {
        // Check if URL can be reached. This will throw
        // IOException which calling code will handle.
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine(String.format("Attempting to download %s (from root %s) from the HTTP server", url, root));
        }
        URLConnection conn = url.openConnection();
        conn.setUseCaches(false);
        if (ifModifiedSince != -1) {
            conn.setIfModifiedSince(ifModifiedSince);
        }
        if (conn instanceof HttpURLConnection) {
            final HttpURLConnection httpConn = (HttpURLConnection) conn;
            int resp = httpConn.getResponseCode();
            if (resp == 304) {
                return getCachedAssetInfo(assetManager, key);
            }
        }
        long lastModified = conn.getLastModified();
        long size = conn.getContentLengthLong();
        
        InputStream in = assetManager instanceof ServerAssetManager ? 
                getStream((ServerAssetManager) assetManager, key, conn, size) : 
                conn.getInputStream();

        // For some reason url cannot be reached?
        if (in == null) {
            // 404 etc
            return null;
        } else {
            final UrlAssetInfo inf = new UrlAssetInfo(assetManager, key, url, in, ifModifiedSince);
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

    @Override
    public AssetIndex getIndex(AssetManager assetManager) {
        if (!loadedAssetIndex) {
            try {
                AssetInfo info = locate(assetManager,new AssetKey(AssetIndex.DEFAULT_RESOURCE_NAME));
                if(info != null) {
                assetIndex = new AssetIndex(assetManager);
                    try {
                        assetIndex.load(info.openStream());
                    } catch (IOException ex) {
                        throw new AssetLoadException("Failed to load index.", ex);
                    }
                }
                
            } catch (AssetNotFoundException anfe) {
            } finally {
                loadedAssetIndex = true;
            }
        }
        return assetIndex;
    }

    private static InputStream getStream(final ServerAssetManager assetManager, final AssetKey key, URLConnection conn, long fileLength) throws IOException {
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
    }

    static class UrlAssetInfo extends ExtendedAssetInfo {

        private URL url;
        private InputStream in;
        private long lastModified;
        private long size;
        private final long ifModifiedSince;

        private UrlAssetInfo(AssetManager assetManager, AssetKey key, URL url, InputStream in, long ifModifiedSince) throws IOException {
            super(assetManager, key);
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
                    if (ifModifiedSince != -1) {
                        conn.setIfModifiedSince(ifModifiedSince);
                    }

                    if (conn instanceof HttpURLConnection) {
                        int resp = ((HttpURLConnection) conn).getResponseCode();
                        if (resp == 304) {
                            // Content has not changed, return original cached content
                            if (LOG.isLoggable(Level.FINE)) {
                                LOG.fine(String.format("Content %s has not changed, using cached version"));
                            }
                            return AssetCacheLocator.getCachedAssetInfo(key).openStream();
                        }
                    }

                    lastModified = conn.getLastModified();
                    if (lastModified == 0) {
                        lastModified = -1;
                    }
                    size = conn.getContentLength();
                    conn.setUseCaches(false);
                    return getStream((ServerAssetManager) getManager(), getKey(), conn, size);
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
