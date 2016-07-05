package icemoon.iceloader.tools;

import java.io.File;

public abstract class AbstractProcessor {

	protected final File sourceDir;
	protected final File targetDir;

	protected AbstractProcessor(File sourceDir, File targetDir) throws Exception {
		this.sourceDir = sourceDir;
		this.targetDir = targetDir;
	}

	public void start() throws Exception {
		process(sourceDir);
	}

	protected void process(File file) throws Exception {
		if (file.isDirectory()) {
			processDir(file);
		} else {
			doProcess(file);
		}
	}

	protected void processDir(File file) throws Exception {
		for (File f : file.listFiles()) {
			process(f);
		}
	}

	protected abstract void doProcess(File file) throws Exception;
}
