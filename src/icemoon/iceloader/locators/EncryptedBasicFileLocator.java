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

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetLoadException;
import com.jme3.asset.AssetManager;
import com.jme3.asset.AssetNotFoundException;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.logging.Logger;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import icemoon.iceloader.EncryptionContext;
import java.io.PushbackInputStream;
import java.util.logging.Level;


/**
 * Extension of the default JME {@link FileLocator} that expects the assets it
 * finds to be encrypted, and so will decrypt before returning to JME.
 */
public class EncryptedBasicFileLocator extends com.jme3.asset.plugins.FileLocator {

    private static final Logger LOG = Logger.getLogger(EncryptedBasicFileLocator.class.getName());
    private static File cacheRoot;

    static {
        File cache = new File(System.getProperty("iceloader.fileLocation", "enc_assets/"));
        try {
            cacheRoot = cache.getCanonicalFile();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
    private final SecretKeySpec secret;

    public EncryptedBasicFileLocator() throws Exception {
        secret = EncryptionContext.get().createKey();
    }

    private class AssetInfoFile extends AssetInfo {

        private File file;

        public AssetInfoFile(AssetManager manager, @SuppressWarnings("rawtypes") AssetKey key, File file) {
            super(manager, key);
            this.file = file;
        }

        @Override
        public InputStream openStream() {
            try {
                final FileInputStream in = new FileInputStream(file);
                byte[] header = EncryptionContext.get().getHeader();
                byte[] b = new byte[header.length];
                PushbackInputStream pin = new PushbackInputStream(in, header.length);
                DataInputStream din = new DataInputStream(pin);
                din.readFully(b);
                long actualSize = din.readLong();
                if (!Arrays.equals(b, header)) {
                    if (LOG.isLoggable(Level.FINE)) {
                        LOG.fine("Asset doesn't appear to be encrypted, returning as is");
                    }
                    pin.unread(b);
                    return pin;
                }
                int ivl = din.read();
                byte[] iv = new byte[ivl];
                din.readFully(iv);

                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine(String.format("Reading %s (decrypting %d bytes)", key.getName(), actualSize));
                }


                // Create the stream that decrypts as it is read
                Cipher c = Cipher.getInstance(EncryptionContext.get().getCipher());
                c.init(Cipher.DECRYPT_MODE, secret, new IvParameterSpec(iv));

                return new CipherInputStream(in, c) {
                    @Override
                    public int available() throws IOException {
                        // Deep in JME, checks are made to see if the stream is ready,
                        // CipherInputStream always returns zero, which screws this up
                        return (int) in.available();
                    }
                };

            } catch (Exception ex) {
                // NOTE: Can still happen even if file.exists() is true, e.g.
                // permissions issue and similar
                throw new AssetLoadException("Failed to open file: " + file, ex);
            }
        }
    }

    @Override
    public AssetInfo locate(AssetManager manager, @SuppressWarnings("rawtypes") AssetKey key) {
        String name = key.getName();
        File file = new File(cacheRoot, name);
        if (file.exists() && file.isFile()) {
            try {
                try {
                    // Now, check asset name requirements
                    String canonical = file.getCanonicalPath();
                    String absolute = file.getAbsolutePath();
                    if (!canonical.endsWith(absolute)) {
                        throw new AssetNotFoundException("Asset name doesn't match requirements.\n"
                                + "\"" + canonical + "\" doesn't match \"" + absolute + "\"");
                    }
                } catch (IOException ex) {
                    throw new AssetLoadException("Failed to get file canonical path " + file, ex);
                }

                return new AssetInfoFile(manager, key, file);
            } catch (Exception ex) {
                throw new AssetLoadException("Failed to load asset.", ex);
            }
        } else {
            System.exit(1);
            return null;
        }
    }
}
