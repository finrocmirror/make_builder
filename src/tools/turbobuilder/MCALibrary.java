package tools.turbobuilder;


public class MCALibrary extends BuildEntity {

	public MCALibrary(TurboBuilder tb) {
		super(tb);
	}

	public String toString() {
		return "mca2_" + name;
	}
	
	//@Override
	//public void gccCall(List<File> cppX) throws Exception {
		//GCC.compile(cppX, new String[]{/*MCAROOT + FS + "libraries", MCAROOT, rootDir.getAbsolutePath(), MCAROOT + FS + "tools", MCAROOT + FS + "projects"*/}, dependencies, extlibs, new File("/tmp/lib" + toString() + ".so"), false, opts + getCudaOpts());
	//}
	
	public String getLibName() {
		return "lib" + toString() + ".so";
	}
}
