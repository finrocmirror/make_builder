package tools.turbobuilder;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import tools.turbobuilder.LibDB.ExtLib;

/**
 * @author max
 *
 * Include file
 */
public abstract class SourceFile implements Serializable {

	/** */
	private static final long serialVersionUID = 514453458339582230L;

	public static class System extends SourceFile implements Util.TokenCallback, Serializable {

		/** includes in this file */
		final List<SourceFile> includes = new ArrayList<SourceFile>();
		
		/** UID */
		private static final long serialVersionUID = -532788430792096195L;
		final DefineManager.StringTable.Set relevantDefines;
		transient DefineManager.StringTable.Set relevantDefines2;
		final SortedMap<String, DefineManager.DefineList> initialStates = new TreeMap<String, DefineManager.DefineList>();
		final SourceCache ic;
		final File f;
		
		class Buffers {
			DefineManager.Defines resultBuffer = ic.defineManager.new Defines();
		}

		final static ThreadLocal<Buffers> buffers = new ThreadLocal<Buffers>();

		public System() {
			ic = null;
			relevantDefines = null;
			f = null;
		}
		
		public System(File include, SourceCache ic, List<ExtLib> extlibs) throws Exception {
			f = include;
			if (include == null) {
				TurboBuilder.println("Null file in Include");
			}
			this.ic = ic;
			ic.fileSystem.put(include, this); // needed, because of cyclic dependencies
			TurboBuilder.println("Analyzing & Caching " + include.toString());
			relevantDefines = ic.defineManager.defTable.new Set();
			
			// scan include for defines
			List<String> lines = Util.readLinesWithoutComments(include, false);
			
			for (String s : lines) {
				s = Util.normalize(s);
				Util.findPossibleDefineTokens(s, this, false);
			
				// handle includes
				if (s.startsWith("#")) {
					String command = Util.normalize(s.substring(1));
					if (command.startsWith("include ")) {
						String inc = command.substring(7).trim();
						char c = inc.charAt(0);
						if (c != '"' && c != '<') {
							continue;
						}
						inc = inc.substring(1, inc.length() - 1);
						SourceFile.System incFile = (SourceFile.System)ic.getSystemInclude(inc, include.getParent(), extlibs);
						if (incFile != null) { 
							// relevantDefines.merge(ic.getSystemInclude(incFile).relevantDefines);
							includes.add(incFile);
						}
					}
				}
			}
		}
		
		public String toString() {
			return f.toString();
		}
		
		public String tokenCallback(String token) {
			relevantDefines.add(token);
			return null;
		}		
		
		public synchronized void simulateDefines(DefineManager.Defines current, SourceCache ic) throws Exception {
			if (relevantDefines2 == null) {
				relevantDefines2 = ic.defineManager.defTable.new Set();
				getRelevantDefines2(relevantDefines2, new ArrayList<SourceFile>());
			}
			
			String reduced = current.getRelevant(relevantDefines2);
			if (reduced.length() > 1) {
				TurboBuilder.println("Local define relevant");
			}
			DefineManager.DefineList result = initialStates.get(reduced);
			if (result != null) {
				//java.lang.System.out.println("Cache hit");
				//current.clear();
				current.apply(result);
				return;
			}
			
			Buffers buf = buffers.get();
			if (buf == null) {
				buf = new Buffers(); 
				buffers.set(buf);
			}
			buf.resultBuffer.clear();
			//buf.tempBuffer.clear();
			//buf.tempBuffer.addStdDefines();
			/*if (!reduced.equals("")) {
				throw new Exception("not implemented yet");
			}*/
			
			GCC.getDefines(reduced, buf.resultBuffer, f);
			//result = preResult.retain(relevantDefines2);
			result = buf.resultBuffer.changesFrom(ic.defineManager.getStdDefines());
			initialStates.put(reduced, result);
			current.apply(result);
		}

		private void getRelevantDefines2(DefineManager.StringTable.Set defines2, List<SourceFile> callStack) {
			defines2.merge(relevantDefines);
			callStack.add(this);
			for (SourceFile i : includes) {
				if (callStack.contains(i)) {
					continue;
				}
				((SourceFile.System)i).getRelevantDefines2(defines2, callStack);
			}
		}
	}
	
	public static class Local extends SourceFile implements Serializable {
		
		private static final long serialVersionUID = 8823822562832188896L;

		transient List<String> lines;
		transient CodeBlock descr = new CodeBlock();
		transient boolean upToDate = false;
		transient boolean template = false;

		/** version of file that is cached */
		FileInfo fileInfo;
		
		public Local() {}
		
		public Local(File include) throws Exception {
			fileInfo = new FileInfo(include);
		}

		public Local(File include, CodeBlock cb) {
			fileInfo = new FileInfo(include);
			lines = new ArrayList<String>();
			for (Object o : cb) {
				lines.add((String)o);
			}
			upToDate = true;
		}

		public List<String> getLines(BuildEntity caller) throws Exception {
			update(caller);
			return lines;
		}

		public Object getDescr(BuildEntity caller) throws Exception {
			update(caller);
			return descr;
		}

		private void update(BuildEntity caller) throws Exception {
			/*if (fileInfo.f.getName().equals("tDraw2DWorld.h")) {
				int i = 5;
			}*/
			
			if (upToDate && (!(lines == null))) {
				return;
			}
			
			// reinit variables
			if (descr == null) {
				descr = new CodeBlock();
			}
			if (fileInfo.modTime == 0) {
				fileInfo.update();
			}

			// read lines
			lines = Util.readLinesWithoutComments(fileInfo.f, true);
			
			if (fileInfo.f.getAbsolutePath().endsWith(".h") || fileInfo.f.getAbsolutePath().endsWith(".hpp")) {
				for (String s : lines) {
					
					// search for _DESCR_
					s = s.trim();
					if (s.startsWith("_DESCR_")) {
						descr.add(caller.callDescriptionBuilder(fileInfo.f));
						break;
					}
					
					// search for template <
					if (s.startsWith("template <")) {
						template = true;
					}
				}

				if (caller.qt3 || caller.qt4) {
					for (String s : lines) {
						if (s.contains("Q_OBJECT") || s.contains("Q_PROPERTY") || s.contains("Q_CLASSINFO")) {
							descr.add(caller.callMoc(fileInfo.f));
							break;
						}
					}
				}
			}
			
			upToDate = true;
		}
		
		public String toString() {
			return fileInfo.f.toString();
		}

		public boolean isTemplate() {
			return template;
		}
		
		/*public void addInclude(SourceFile include) {
			if (!includes.contains(include)) {
				includes.add(include);
			}
		}*/
	}
}
