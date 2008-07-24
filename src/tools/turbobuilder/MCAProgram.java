package tools.turbobuilder;

public class MCAProgram extends BuildEntity {

	public MCAProgram(TurboBuilder tb) {
		super(tb);
	}

	//@Override
	//public void gccCall(List<File> cppX) throws Exception {
	//	GCC.compile(cppX, new String[]{/*MCAROOT + FS + "libraries", MCAROOT, rootDir.getAbsolutePath(), MCAROOT + FS + "tools", MCAROOT + FS + "projects"*/}, dependencies, extlibs, new File("/tmp/" + toString()), true, opts + getCudaOpts());
	//}
}
