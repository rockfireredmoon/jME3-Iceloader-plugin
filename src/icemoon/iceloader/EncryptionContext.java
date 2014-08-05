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

import com.jme3.asset.AssetManager;
import java.security.spec.KeySpec;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encryption context supplies the key used to encrypt/decrypt assets and some other
 * configuration. To provide a custom key or other custom configuration, you can extend
 * this class and then use
 * {@link EncryptionContext#set(EncryptionContext)} to set the new
 * context. Make sure you do this before the {@link AssetManager} is initialized.
 * <p>
 * An alernative context may also be specified using a runtime system property. Set
 * <strong>iceloader.encryptionContextClassName</strong> to the required class.
 */
public abstract class EncryptionContext {

    private static EncryptionContext instance;

    public static EncryptionContext get() {
        if (instance == null) {
            instance = new EncryptionContext() {
                @Override
                public SecretKeySpec createKey() throws Exception {
                    return createKey(System.getProperty("iceloader.password", "password123?"), System.getProperty("iceloader.salt", "12345678"));
                }

                @Override
                public String getMagic() {
                    return MAGIC;
                }

                @Override
                public String getCipher() {
                    return CIPHER;
                }
            };
        }
        return instance;
    }
    static final String MAGIC = "!@ENC/PF_0";
    static final byte[] HEADER = MAGIC.getBytes();
    static String CIPHER = "AES/CFB8/NoPadding";
//    static String CIPHER = "AES/CBC/PKCS5Padding";  

    /**
     * Set an alternative encryption context.
     *
     * @param instance encrpytion context
     */
    public static void set(EncryptionContext instance) {
        EncryptionContext.instance = instance;
    }

    /**
     * Implement to create the key.
     *
     * @return key
     * @throws Exception
     */
    public abstract SecretKeySpec createKey() throws Exception;

    /**
     * Implement to return the magic bytes used to detect encrypted assets.
     *
     * @return magic
     */
    public abstract String getMagic();

    /**
     * Implement to return the encryption cipher used.
     *
     * @return cipher
     */
    public abstract String getCipher();

    public byte[] getHeader() {
        return getMagic().getBytes();
    }

    protected SecretKeySpec createKey(String p, String s) throws Exception {
        return createKey(p.toCharArray(), p.getBytes("UTF-8"));
    }

    protected SecretKeySpec createKey(char[] p, byte[] s) throws Exception {
        /* Derive the key, given password and salt. */
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        KeySpec spec = new PBEKeySpec(p, s, 65536, 128);
        SecretKey tmp = factory.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), "AES");
    }
}
