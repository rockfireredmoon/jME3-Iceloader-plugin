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
package icemoon.iceloader;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetLoadException;
import com.jme3.asset.AssetManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;

public class CachingAssetInfo extends AssetInfo {

    private static final Logger LOG = Logger.getLogger(CachingAssetInfo.class.getName());
    private final AssetInfo delegate;
    private final FileObject cacheRoot;

    public CachingAssetInfo(AssetManager manager, AssetKey key, AssetInfo delegate, FileObject cacheRoot) {
        super(manager, key);
        this.delegate = delegate;
        this.cacheRoot = cacheRoot;
    }

    public AssetInfo getDelegate() {
		return delegate;
	}

	public FileObject getCacheRoot() {
		return cacheRoot;
	}

	@Override
    public InputStream openStream() {
        final InputStream in = delegate.openStream();
        long lastModified = -1;
        if (delegate instanceof ExtendedAssetInfo) {
            lastModified = ((ExtendedAssetInfo) delegate).getLastModified();
        }

        try {
            final FileObject cacheFile = cacheRoot.resolveFile(key.getName());
//                    final FileObject cacheTempFile = cacheRoot.resolveFile(key.getName() + ".cch");
            final FileObject cacheTempFile = cacheFile;
            return createCachingStream(in, cacheTempFile, lastModified);
        } catch (FileSystemException fse) {
            throw new AssetLoadException("Failed to create cache file.", fse);
        }
    }

    private InputStream createCachingStream(final InputStream in, final FileObject cacheTempFile, final long lastModified) {
        return new InputStream() {
            private OutputStream out;
            private long written;
            private boolean closed;

            private void checkOut() throws FileSystemException {
                if (out == null && !closed) {
                    if (LOG.isLoggable(Level.FINE)) {
                        LOG.fine(String.format("Thread '%s' Caching %s to %s", Thread.currentThread().getName(), key.getName(), cacheTempFile));
                    }
                    out = cacheTempFile.getContent().getOutputStream();
                }
            }

            @Override
            public int read() throws IOException {
                checkOut();
                final int read = in.read();
                if (read != -1) {
                    out.write(read);
                    out.flush();
                    written += 1;
                } else {
                    closeOut();
                }
                return read;
            }

            @Override
            public int read(byte[] b) throws IOException {
                checkOut();
                int r = in.read(b);
                if (r != -1) {
                    out.write(b, 0, r);
                    out.flush();
                    written += r;
                } else {
                    closeOut();
                }
                return r;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                checkOut();
                int r = in.read(b, off, len);
                if (r != -1) {
                    out.write(b, off, r);
                    out.flush();
                    written += r;
                } else {
                    closeOut();
                }
                return r;
            }

            @Override
            public void close() throws IOException {
                try {
                    in.close();
                } finally {
                    closeOut();
                }
            }

            @Override
            public int available() throws IOException {
                return in.available();
            }

            @Override
            public boolean markSupported() {
                return false;
            }

            private void closeOut() throws IOException {
                if (!closed) {
                    if (LOG.isLoggable(Level.FINE)) {
                        LOG.fine(String.format("Flushing %d bytes %s to cache ", written, key.getName()));
                    }
                    out.flush();
                    out.close();
                    closed = true;
                    out = null;
//                                LOG.info(String.format("Commiting cache file %s by renaming from %s", cacheFile, cacheTempFile));
//                                cacheTempFile.moveTo(cacheFile);
                    if (lastModified != -1) {
                        if (LOG.isLoggable(Level.FINE)) {
                            LOG.fine(String.format("Setting timestamp to %s", DateFormat.getDateTimeInstance().format(new Date(lastModified))));
                        }
                        cacheTempFile.getContent().setLastModifiedTime(lastModified);
                    }
                }
            }
        };
    }
}
