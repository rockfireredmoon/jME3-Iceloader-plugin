package icemoon.iceloader.tools;

import icemoon.iceloader.EncryptionContext;

import java.io.File;
import java.io.IOException;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public abstract class AbstractCrypt {

    protected final File sourceDir;
    protected final byte[] header;
    protected final File targetDir;
    protected boolean incremental;
	private SecretKeySpec secret;
	private Cipher c;

    protected AbstractCrypt(File sourceDir, File targetDir) throws Exception {
        this.sourceDir = sourceDir;
        this.targetDir = targetDir;
        header = EncryptionContext.get().getMagic().getBytes();
    }

    public void start() throws Exception {
        process(sourceDir);
    }

    private void process(File file) throws Exception {
        if (file.isDirectory()) {
            for (File f : file.listFiles()) {
                process(f);
            }
        } else {
            doProcess(file);
        }
    }

    private void doProcess(File file) throws Exception {
        // Work out path relative to target dir
    	if(secret == null) {
    		secret = EncryptionContext.get().createKey();
    	}
        String relpath = file.getAbsolutePath().substring(sourceDir.getAbsolutePath().length() + 1);
        File targetFile = new File(targetDir, relpath);
        
        long lastMod = targetFile.exists() ? targetFile.lastModified() / 60000 : -1;
		long thisMod = file.lastModified() / 60000;
		if(lastMod == thisMod) {
			return;
		}
		
        final File parentDir = targetFile.getParentFile();
        if (!parentDir.exists() && !parentDir.mkdirs()) {
            throw new IOException("Failed to create directory " + parentDir);
        }
        if(c == null) {
        	c = Cipher.getInstance( EncryptionContext.get().getCipher());
        }
        doStream(secret, targetFile, c, file);
    }

    protected abstract void doStream(SecretKeySpec secret, File targetFile, Cipher c, File file) throws Exception;
}
