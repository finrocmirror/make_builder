package tools.turbobuilder;

import java.io.File;
import java.io.Serializable;

/**
 * @author max
 *
 * Basic file information
 */
public class FileInfo implements Serializable {

	/** UID */
	private static final long serialVersionUID = 3491289100288256837L;
	
	File f;
	transient long size, modTime;
	boolean virtual;

	public FileInfo(File f) {
		this(f, false);
	}
	
	public FileInfo(File f, boolean virtual) {
		this.f = f;
		this.virtual = virtual;
		if (!virtual) {
			size = f.length();
			modTime = f.lastModified();
		}
	}
	
	public FileInfo(String s) {
		String[] parts = s.split(",");
		f = new File(parts[0]);
		size = Long.parseLong(parts[1]);
		modTime = Long.parseLong(parts[2]);
		virtual = (size == 0) && (modTime == 0);
	}

	public String toString() {
		return virtual ? "" : f.getAbsolutePath() + "," + size + "," + modTime;
	}
	
	public boolean upToDate() {
		return virtual ? true : (f.exists() && f.length() == size && modTime == f.lastModified());
	}

	public void update() {
		if (!virtual) {
			size = f.length();
			modTime = f.lastModified();
		}
	}
}
