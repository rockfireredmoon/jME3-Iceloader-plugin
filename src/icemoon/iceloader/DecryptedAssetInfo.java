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

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetLoadException;
import com.jme3.asset.AssetManager;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class DecryptedAssetInfo extends ExtendedAssetInfo {

    private static final Logger LOG = Logger.getLogger(DecryptedAssetInfo.class.getName());
    private final AssetInfo info;
    private final SecretKeySpec secret;

    public DecryptedAssetInfo(AssetManager manager, AssetKey<?> key, AssetInfo info, SecretKeySpec secret) {
        super(manager, key);
        this.info = info;
        this.secret = secret;
    }

    @Override
    public InputStream openStream() {
        final InputStream in = info.openStream();
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine(String.format("Decrypting %s (from %s)", key.getName(), info.getClass().getName()));
        }
        try {
            byte[] header = EncryptionContext.get().getHeader();
            byte[] b = new byte[header.length];
            PushbackInputStream pin = new PushbackInputStream(in, header.length);
            DataInputStream din = new DataInputStream(pin);
            din.readFully(b);
            if (!Arrays.equals(b, header)) {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("Asset doesn't appear to be encrypted, returning as is");
                }
                pin.unread(b);
                return pin;
            }
            long actualSize = din.readLong();
            int ivl = din.read();
            byte[] iv = new byte[ivl];
            din.readFully(iv);

            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine(String.format("Reading %s (decrypting %d bytes)", key.getName(), actualSize));
            }

            Cipher c = Cipher.getInstance(EncryptionContext.get().getCipher());
            c.init(Cipher.DECRYPT_MODE, secret, new IvParameterSpec(iv));
            final long now = System.currentTimeMillis();
            return new CipherInputStream(in, c) {
                @Override
                public int available() throws IOException {
                    // Deep in JME, checks are made to see if the stream is ready,
                    // CipherInputStream always returns zero, which screws this up
                    return in.available();
                }

				@Override
				public void close() throws IOException {
					super.close();
		            if (LOG.isLoggable(Level.FINE)) {
		                LOG.fine(String.format("Decryption of %s took %dms", key.getName(), System.currentTimeMillis() - now));
		            }
				}
            };
        } catch (Exception e) {
            throw new AssetLoadException(String.format("Failed to load asset %s.", key.getName()), e);
        }
    }

    @Override
    public long getSize() {
        return info instanceof ExtendedAssetInfo ? ((ExtendedAssetInfo) info).getSize() : 0;
    }

    @Override
    public long getLastModified() {
        return info instanceof ExtendedAssetInfo ? ((ExtendedAssetInfo) info).getLastModified() : -1;
    }

	@Override
	public boolean isDecryptedStream() {
		return true;
	}
}
