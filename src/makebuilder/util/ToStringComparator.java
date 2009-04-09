package makebuilder.util;

import java.io.Serializable;
import java.util.Comparator;

/**
 * @author max
 *
 */
public class ToStringComparator implements Comparator<Object>, Serializable {

	/** UID */
	private static final long serialVersionUID = 4304587237585840299L;
	
	/** instance of comparator - if you don't want to instantiate it */
	public static final ToStringComparator instance = new ToStringComparator();
	
	@Override
	public int compare(Object o1, Object o2) {
		return o1.toString().compareTo(o2.toString());
	}
}
