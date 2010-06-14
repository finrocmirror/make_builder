/**
 * You received this file as part of an experimental
 * build tool ('makebuilder') - originally developed for MCA2.
 *
 * Copyright (C) 2008-2010 Max Reichardt,
 *   Robotics Research Lab, University of Kaiserslautern
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
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

    public final static String[] CAKE = new String[] {
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
