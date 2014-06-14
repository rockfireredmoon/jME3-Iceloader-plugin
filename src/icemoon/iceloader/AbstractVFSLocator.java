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

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetLoadException;
import com.jme3.asset.AssetLocator;
import com.jme3.asset.AssetManager;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.vfs2.Capability;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileType;

/**
 * Abstract locator that supports Commons VFS locations.
 */
public abstract class AbstractVFSLocator implements AssetLocator {

    protected static FileObject defaultStoreRoot;
    private static final Logger LOG = Logger.getLogger(AbstractVFSLocator.class.getName());

    static {
        LOG.setLevel(Level.FINE);
    }
    private FileObject storeRoot;
    private String rootPath = "/";
    private static boolean alreadyLoaded;

    public static FileObject getDefaultStoreRoot() {
        return defaultStoreRoot;
    }

    public static void setDefaultStoreRoot(FileObject defaultStoreRoot) {
        if (alreadyLoaded) {
            throw new IllegalStateException("Must set the default store root before the first instantiation.");
        }
        LOG.fine(String.format("Setting defalt store root to %s", defaultStoreRoot));
        AbstractVFSLocator.defaultStoreRoot = defaultStoreRoot;
    }

    public AbstractVFSLocator() {
    }

    public AbstractVFSLocator(FileObject root) {
        alreadyLoaded = true;
        setStoreRoot(root);
    }

    public final void setStoreRoot(FileObject storeRoot) {
        LOG.fine(String.format("Setting store root to %s", storeRoot));
        this.storeRoot = storeRoot;
        try {
            final FileObject actualStoreRoot = getStoreRoot();
            if (actualStoreRoot.getFileSystem().hasCapability(Capability.CREATE) && !actualStoreRoot.exists()) {
                actualStoreRoot.createFolder();
            }
        } catch (FileSystemException fse) {
            throw new AssetLoadException("Failed to set store root.", fse);
        }
    }

    public FileObject getStoreRoot() {
        return storeRoot == null ? defaultStoreRoot : storeRoot;
    }

    public void setRootPath(String rootPath) {
        while (rootPath.endsWith("/")) {
            rootPath = rootPath.substring(0, rootPath.length() - 1);
        }
        this.rootPath = rootPath;
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine(String.format("Root path of store root %s is now %s", storeRoot == null ? defaultStoreRoot : storeRoot.getName().getURI(), rootPath));
        }
    }

    private static class AssetInfoFileObject extends ExtendedAssetInfo {

        private FileObject file;

        public AssetInfoFileObject(AssetManager manager, AssetKey key, FileObject file) {
            super(manager, key);
            this.file = file;
        }

        @Override
        public InputStream openStream() {
            try {
                return file.getContent().getInputStream();
            } catch (FileSystemException ex) {
                throw new AssetLoadException("Failed to open file: " + file, ex);
            }
        }

        @Override
        public long getSize() {
            try {
                return file.getContent().getSize();
            } catch (FileSystemException ex) {
                LOG.log(Level.WARNING, String.format("Failed to determine size of %s", file), ex);
                return 0;
            }
        }

        @Override
        public long getLastModified() {
            try {
                return file.getContent().getLastModifiedTime();
            } catch (FileSystemException ex) {
                LOG.log(Level.WARNING, String.format("Failed to determine last modified time of %s", file), ex);
                return -1;
            }
        }
    }

    public AssetInfo locate(AssetManager manager, AssetKey key) {
        FileObject actualStoreRoot = getStoreRoot();
        StringBuilder name = new StringBuilder(rootPath);
        final String keyName = key.getName();
        if (name.length() > 0 && !rootPath.endsWith("/") && !keyName.startsWith("/")) {
            name.append("/");
        }
        name.append(keyName);
        try {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine(String.format("Trying to find %s in %s (%s)", key.getName(), actualStoreRoot.getName().getURI(), name.toString()));
            }
            FileObject file = actualStoreRoot.resolveFile(name.toString());
            if (file.exists() && file.getType().equals(FileType.FILE)) {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine(String.format("Found %s in %s", key.getName(), actualStoreRoot.getName().getURI()));
                }
                return new AssetInfoFileObject(manager, key, file);
            } else {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine(String.format("Didn't find %s in %s", key.getName(), actualStoreRoot.getName().getURI()));
                }
                return null;
            }
        } catch (FileSystemException fnfne) {
            throw new AssetLoadException("Failed to open file: " + name, fnfne);
        }
    }
}
