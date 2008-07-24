package tools.turbobuilder;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import tools.turbobuilder.DefineManager.StringTable.Set;
import tools.turbobuilder.Util.TokenCallback;

/**
 * @author max
 *
 * Table with strings
 */
public class DefineManager implements Serializable {

	private static final long serialVersionUID = 3997627100997900641L;

	class DefineList extends ArrayList<Define> {

		/** uid */
		private static final long serialVersionUID = -6656056391235136659L;
	}

	static final int MAXENTRIES = 100000;
	
	StringTable defTable = new StringTable();
	StringTable valTable = new StringTable();
	
	private static transient DefineManager.Defines STD_DEFINES;

	public synchronized DefineManager.Defines getStdDefines() {
		if (STD_DEFINES == null) {
			STD_DEFINES = GCC.getStdDefines(this);
		}
		return STD_DEFINES;
	}
	
	public class StringTable implements Serializable {
		private static final long serialVersionUID = -6385916857096425848L;
		
		final List<String> strings = new ArrayList<String>();
		transient Map<String,Integer> lookup = new HashMap<String,Integer>();
	
		public Integer lookup(String s) {
			if (s == null) {
				return null;
			}
			if (lookup == null) {
				initLookup();
			}
		
			Integer i = lookup.get(s);
			if (i != null) {
				return i;
			}

			i = strings.size();
			strings.add(s);
			lookup.put(s, i);
			return i;
		}
	
		private void initLookup() {
			lookup = new HashMap<String,Integer>();
			for (int i = 0; i < strings.size(); i++) {
				lookup.put(strings.get(i), i);
			}
		}

		public String get(int i) {
			return strings.get(i);
		}
		
		/*private void writeObject(java.io.ObjectOutputStream out) throws IOException {
			for (String s : strings) {
				out.writeChars(s);
				out.writeChar('\n');
			}
			out.writeChars("}}}");
		}
		
		private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
			while(true) {
				String s = in.r
				if (i == -1) {
					break;
				}
				set.add(i);
			}
		}*/
		
		/** Set of strings from string table */
		public class Set implements Serializable {
			private static final long serialVersionUID = -665148936472049218L;
			
			SortedSet<Integer> set = new TreeSet<Integer>();

			public void add(String s) {
				Integer i = lookup(s);
				set.add(i);
			}

			// can only be called on sets from same string table
			public void merge(Set other) {
				set.addAll(other.set);
			}
			
			public String toString() {
				StringBuilder sb = new StringBuilder();
				for (Integer i : set) {
					sb.append(get(i) + "\n");
				}
				return sb.toString();
			}
			
			private void writeObject(java.io.ObjectOutputStream out) throws IOException {
				for (Integer i : set) {
					out.writeInt(i.intValue());
				}
				out.writeInt(-1);
			}
			
			private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
				set = new TreeSet<Integer>();
				while(true) {
					int i = in.readInt();
					if (i == -1) {
						break;
					}
					set.add(i);
				}
			}
		}
	}
	
	// Flags
	public static final int ACTIVE = 1, LOCAL = 2, DEFAULT = 4, SYSTEM = 8, MACRO = 16;
	
	public class Define implements Comparable<Define>, Serializable {
		private static final long serialVersionUID = 4417137609838046215L;
		
		Integer key;
		Integer value;
		int flags;
		
		public Define(Integer key) {
			this.key = key;
		}

		public Define(Define d1) {
			key = d1.key;
			value = d1.value;
			flags = d1.flags;
		}
		
		private void writeObject(java.io.ObjectOutputStream out) throws IOException {
			out.writeInt(key.intValue());
			out.writeInt(value != null ? value.intValue() : Integer.MIN_VALUE);
			out.writeByte((byte)flags);
		}
		
		private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
			key = in.readInt();
			int tmp = in.readInt();
			value = (tmp > Integer.MIN_VALUE) ?	value = tmp : null;
			flags = in.readByte();
		}

		public String getKeyString() {
			return defTable.get(key);
		}
		
		public String getValueString() {
			return valTable.get(value);
		}

		public Integer getKey() {
			return key;
		}

		public Integer getValue() {
			return value;
		}

		public boolean isLocal() {
			return (flags & LOCAL) > 0;
		}
		
		public String toString() {
			if (isActive()) {
				return "#define " + getKeyString() + " " + getValueString();
			} else {
				return "#undef " + getKeyString();
			}
		}

		public boolean isActive() {
			return (flags & ACTIVE) > 0;
		}

		public boolean isDefault() {
			return (flags & DEFAULT) > 0;
		}

		public void apply(Define d) {
			if (d.isActive()) {
				value = d.value;
				flags = d.flags;
			}
		}

		public int compareTo(Define d2) {
			if (isActive() == d2.isActive()) {
				if (!isActive()) {
					return 0;
				}
				if (value.equals(d2.value)) {
					return 0;
				}
				return (value.compareTo(d2.value));
			}
			if (isActive()) {
				return -1;
			} else {
				return 1;
			}
		}

		public boolean isMacro() {
			return (flags & MACRO) > 0;
		}
	}
	
	public class Defines implements Comparable<Defines>, TokenCallback {
		private static final long serialVersionUID = 5019777531845251091L;
		
		Define[] defines = new Define[MAXENTRIES];
		int max;
		
		public Defines() {
			for (int i = 0; i < MAXENTRIES; i++) {
				defines[i] = new Define(i);
			}
		}
		
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append("Size: " + size() + "\n");
			for (int i = 0; i <= max; i++) {
				Define d = defines[i];
				if (d.isActive()) {
					sb.append(d.toString() + "\n");
				}
			}
			return sb.toString();
		}
		
		public int size() {
			int size = 0;
			for (int i = 0; i <= max; i++) {
				if (defines[i].isActive()) {
					size++;
				}
			}
			return size;
		}
		
		public int compareTo(Defines o) {
			for (int i = 0, n = Math.max(max, o.max); i <= n; i++) {
				Define d1 = defines[i];
				Define d2 = o.defines[i];
				int result = d1.compareTo(d2);
				if (result == 0) {
					continue;
				}
				return result;
			}
			return 0;
		}

		public void define(String key, String val, int... flags) {
			if (key.equals("NULL")) {
				//System.out.println("redefining NULL :-/ might not be a good idea... ignoring");
				return;
			}
			int k = defTable.lookup(key);
			define(k, valTable.lookup(val), flags);
			//System.out.println(toString());
		}

		/*public void add(Map.Entry<Integer, Integer> e) {
			set.put(e.getKey(), e.getValue());
		}*/
		
		private void define(Integer key, Integer val, int... flags) {
			int ikey = key.intValue();
			max = Math.max(max, ikey);
			Define d = defines[ikey];
			d.flags = ACTIVE;
			for (int i = 0; i < flags.length; i++) {
				d.flags |= flags[i];
			}
			d.value = val;
		}
		
		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof Defines)) {
				return false;
			}
			Defines d = (Defines)obj;
			if (max != d.max) {
				return false;
			}
			return compareTo(d) == 0;
		}

		public DefineList changesFrom(Defines o) {
			DefineList result = new DefineList();
			for (int i = 0, n = Math.max(max, o.max); i <= n; i++) {
				Define d1 = defines[i];
				Define d2 = o.defines[i];
				if (d1.isActive() == d2.isActive()) {
					if (!d1.isActive()) {
						continue;
					}
					if (d1.value.equals(d2.value)) {
						continue;
					}
				}
				result.add(new Define(d1));
			}
			return result;
		}

		public void clear() {
			for (int i = 0; i <= max; i++) {
				defines[i].flags = 0;
			}
			max = 0;
		}

		public void undefine(Integer key) {
			defines[key.intValue()].flags = 0;
		}

		public List<String> changesFromDefault() {
			List<String> result = new ArrayList<String>();
			for (int i = 0; i <= max; i++) {
				Define d = defines[i];
				if (!d.isDefault() && d.isActive()) {
					result.add(d.toString());
				}
			}
			return result;
		}

		public void addStdDefines() {
			applyAll(getStdDefines());
		}

		private void applyAll(Defines o) {
			for (int i = 0; i <= o.max; i++) {
				defines[i].apply(o.defines[i]);
			}
			max = Math.max(max, o.max);
		}

		public void undefine(String def) {
			undefine(defTable.lookup(def));
		}

		public boolean isDefined(String def) {
			return defines[defTable.lookup(def).intValue()].isActive();
		}

		public String process(String def) {
			return Util.findPossibleDefineTokens(def, this, false);
		}
		
		public String tokenCallback(String token) {
			Define d = defines[defTable.lookup(token).intValue()];
			if (d.isActive() && (!d.isMacro())) {
				return d.getValueString();
			}
			return null;
		}

		public String processCondition(String condition) {
			String s = Util.findPossibleDefineTokens(condition, jCallback, true);
			s = s.replace("defined(undefined)", "false");
			s = s.replace("defined((undefined))", "false");
			return s;
		}
		
		JCallback jCallback = new JCallback();
		
		class JCallback implements TokenCallback, Serializable {

			private static final long serialVersionUID = 7892685214146256869L;

			public String tokenCallback(String token) {
				if (token.equals("defined") || token.equals("undefined") || token.equals("true") || token.equals("false")) {
					return null;
				}
				Define d = defines[defTable.lookup(token).intValue()];
				if (!d.isActive()) {
					return "(undefined)";
				}
				String val = d.getValueString();
				
				try {
					if (!val.startsWith("0x")) {
						Double.parseDouble(val);
						return "(" + val + ")";
					} else {
						return "(" + Integer.parseInt(val.substring(2), 16) + ")";
					}

				} catch (Exception e2) {
					if (val.startsWith("\"")) {
						return "(" + val + ")";
					} else {
						val = val.replace("\"", "\\\"");
						return "(" + "\"" + val + "\"" + ")";
					}
				}
			}
		}
		
		public String getRelevant(Set relevantDefines2) {
			StringBuffer sb = new StringBuffer();
			for (Integer i : relevantDefines2.set) {
				Define d = defines[i.intValue()];
				if (d.isLocal()) {
					sb.append(d.toString() + "\n");
				}
			}
			return sb.toString();
		}

		public void apply(DefineList dl) {
			for (Define d : dl) {
				apply(d);
			}
		}

		private void apply(Define dNew) {
			int ikey = dNew.key.intValue();
			max = Math.max(max, ikey);
			Define d = defines[ikey];
			d.value = dNew.value;
			
			int newFlags = dNew.flags;
			if (d.isLocal()) {
				newFlags |= LOCAL;
			}
			d.flags = newFlags;
		}

		public CodeBlock undefineLocalDefines() {
			CodeBlock result = new CodeBlock();
			for (int i = 0; i <= max; i++) {
				Define d = defines[i];
				if (d.isLocal()) {
					result.add("#undef " + d.getKeyString());
				}
			}
			return result;
		}
		
		/*public void addAll(STMap defines) {
			set.putAll(defines.set);
		}

		public void remove(String s) {
			//set.put(lookup(s), null);
			set.remove(lookup(s));
		}

		public boolean containsKey(String def) {
			return set.containsKey(lookup(def));
		}

		public String get(String def) {
			Integer i = set.get(lookup(def));
			if (i == null) {
				return null;
			}
			return StringTable.this.get(i.intValue());
		}*/
	}
}
