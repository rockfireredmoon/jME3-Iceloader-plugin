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

import com.jme3.asset.AssetLoadException;
import com.jme3.asset.AssetManager;
import icemoon.iceloader.AbstractVFSLocator;
import static icemoon.iceloader.AbstractVFSLocator.setDefaultStoreRoot;
import icemoon.iceloader.AssetIndex;
import icemoon.iceloader.IndexItem;
import icemoon.iceloader.IndexedAssetLocator;
import java.io.File;
import java.util.logging.Logger;
import org.apache.commons.vfs2.AllFileSelector;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileType;
import org.apache.commons.vfs2.VFS;

/**
 * A locator that will work with any Commons VFS file. The default location is determined
 * by the system property <strong>iceloader.fileLocation</strong> which itself defaults to
 * the local folder <strong>./assets</strong>.
 */
public class FileLocator extends AbstractVFSLocator implements IndexedAssetLocator {

    private static final Logger LOG = Logger.getLogger(FileLocator.class.getName());

    static {
        try {
            setDefaultStoreRoot(VFS.getManager().resolveFile(System.getProperty("iceloader.fileLocation", new File("assets/").getCanonicalPath())));
        } catch (Exception ex) {
            throw new AssetLoadException("Root path is invalid", ex);
        }
    }
    private boolean loadedAssetIndex;
    private AssetIndex assetIndex;

    public AssetIndex getIndex(AssetManager assetManager) {
        if (!loadedAssetIndex) {
            assetIndex = new AssetIndex(assetManager);
            FileObject storeRoot = getStoreRoot();
            try {
                for (FileObject ob : storeRoot.findFiles(new AllFileSelector())) {
                    if (ob.getType().equals(FileType.FILE)) {
                        final String path = storeRoot.getName().getRelativeName(ob.getName());
                        assetIndex.getBackingObject().add(new IndexItem(path));
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to load asset index.", e);
            }
            loadedAssetIndex = true;
        }
        return assetIndex;
    }
}
