/**
 * 
 */
package makebuilder.ext;

import makebuilder.BuildEntity;
import makebuilder.MakeFileBuilder;
import makebuilder.Makefile;
import makebuilder.SourceFileHandler;
import makebuilder.SourceScanner;
import makebuilder.SrcFile;

/**
 * @author max
 *
 */
public class CakeHandler implements SourceFileHandler {

	boolean handled = false;
	
	public final static String[] CAKE = new String[]{
"",
"",
"                                        (",
"                           (",
"                   )                    )             (",
"                           )           (o)    )",
"                   (      (o)    )     ,|,            )",
"                  (o)     ,|,          |~\\    (      (o)",
"                  ,|,     |~\\    (     \\ |   (o)     ,|,",
"                  \\~|     \\ |   (o)    |`\\   ,|,     |~\\",
"                  |`\\     |`\\@@@,|,@@@@\\ |@@@\\~|     \\ |",
"                  \\ | o@@@\\ |@@@\\~|@@@@|`\\@@@|`\\@@@o |`\\",
"                 o|`\\@@@@@|`\\@@@|`\\@@@@\\ |@@@\\ |@@@@@\\ |o",
"               o@@\\ |@@@@@\\ |@@@\\ |@@@@@@@@@@|`\\@@@@@|`\\@@o",
"              @@@@|`\\@@@@@@@@@@@|`\\@@@@@@@@@@\\ |@@@@@\\ |@@@@",
"              p@@@@@@@@@@@@@@@@@\\ |@@@@@@@@@@|`\\@@@@@@@@@@@q",
"              @@o@@@@@@@@@@@@@@@|`\\@@@@@@@@@@@@@@@@@@@@@@o@@",
"              @:@@@o@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@o@@::@",
"              ::@@::@@o@@@@@@@@@@@@@@@@@@@@@@@@@@@@o@@:@@::@",
"              ::@@::@@@@::oo@@@@oo@@@@@ooo@@@@@o:::@@@::::::",
"              %::::::@::::::@@@@:::@@@:::::@@@@:::::@@:::::%",
"              %%::::::::::::@@::::::@:::::::@@::::::::::::%%",
"              ::%%%::::::::::@::::::::::::::@::::::::::%%%::",
"            .#::%::%%%%%%:::::::::::::::::::::::::%%%%%::%::#.",
"          .###::::::%%:::%:%%%%%%%%%%%%%%%%%%%%%:%:::%%:::::###.",
"        .#####::::::%:::::%%::::::%%%%:::::%%::::%::::::::::#####.",
"       .######`:::::::::::%:::::::%:::::::::%::::%:::::::::'\\''######.",
"       .#########``::::::::::::::::::::::::::::::::::::'\\'''\\''#########.",
"       `.#############```::::::::::::::::::::::::'\\'''\\'''\\''#############.'\\''",
"        `.######################################################.'\\''",
"          ` .###########,._.,,,. #######<_\\##################. '\\''",
"             ` .#######,;:      `,/____,__`\\_____,_________,_____",
"                `  .###;;;`.   _,;>-,------,,--------,----------'\\''",
"                    `  `,;'\\'' ~~~ ,'\\''\\######_/'\\''#######  .  '\\''",
"                        '\\'''\\''~`'\\'''\\'''\\'''\\''    -  .'\\''/;  -    '\\''       -Catalyst",
"",
"[from http://www.ascii-art.de/ascii/ab/birthday.txt]"
	};
	
	@Override
	public void init(Makefile makefile) {}

	@Override
	public void processSourceFile(SrcFile file, Makefile makefile, SourceScanner scanner, MakeFileBuilder builder) throws Exception {}

	@Override
	public void build(BuildEntity be, Makefile makefile, MakeFileBuilder builder) throws Exception {
		if (!handled) {
			Makefile.Target target = makefile.addPhonyTarget("cake");
			target.addCommand("echo Making cake...", false);
			target.addCommand("sleep 4", false);
			for (String s : CAKE) {
				target.addCommand("echo '" + s + "'", false);
			}
			target.addCommand("echo done", false);
			handled = true;
		}
	}
}
