Turbo-charged MCA Builder - Copyright 2008 by Max Reichardt

This is an experimental build tool for MCA.
(currently only suited for Linux/Unix platforms)

It can compile MCA projects comparably fast by merging source 
files to few large source files before compiling.

Furthermore, it can create Makefiles from the SConscripts.
This way, 'make' can be used to compile MCA libraries and projects.
Currently, this is the preferred way of building using this tool.
Among other things, this allows using Eclipse for debugging
(including stepping, breakpoints).

Instructions (for use with make):

1) Check out make_builder project in the <MCAHOME> directory:

   svn co https://agrosy.informatik.uni-kl.de/svn/mca2_make_builder/trunk make_builder

2) Build the project:
   In <MCAHOME>/make_builder directory, call...
   
   ant
   
3) Replace the <MCAHOME>/script/mcasetenv.py script with the 
   modified version found in <MCAHOME>/make_builder/script/ .
   
   It has two modifications:
   - The path also includes (<MCAHOME>/make_builder/script)
   - It accepts alternative targets (with the -t command line
     option; see below)

4) Set MCA environment variable (in <MCAHOME> directory):

   Syntax: source script/mcasetenv [-p <project>] [-t <target>]
   
   The [-t <target>] is new and allows specifying an alternative
   build target to avoid collisions with SCons.
   
   source script/mcasetenv -t make
   
   This is necessary in every console dealing with MCA - 
   just as MCA's ordinary mcasetenv command.

   To reset the target to the default call (any suggestions for 
   a nicer solution are appreciated):

   source script/mcasetenv --target=

5) Perform library check:
   
   updatelibdb
   
   (A file <MCAHOME>/make_builder/etc/libdb.txt is created from 
    <MCAHOME>/make_builder/etc/libdb.raw)
   
   This operation can take a few minutes to complete (typically,
   significantly faster on a local system). 
   This has to be done again when relevant system libraries have 
   changed.   

   The libdb.raw is the "raw external library database".
   It can be edited by hand. Syntax is hopefully more or less
   self-explanatory. There is one line per external library.
   (Each line replaces one respective Scons library check script)
   Format:

   <libname>: <compiler flags>

   If the 'compiler flags' are 'N/A' this indicates that
   the library is not/never available.

   Note: -D <define>  can be used to set any defines when the 
                      external library is used.

   In the transformation to the libdb.txt:
   '$MCAHOME$' is replaced with the MCA root directory.
   -I<headers files> is replaced with -I<path to these headers>
   -L<path to library> are automatically generated.
   
6) Make Makefile:

   There are two scripts for doing this: 'makeMakefile' and
   'makeSafeMakefile'.
   
   'makeSafeMakefile' will generate a Makefile in <MCAHOME>
   that compiles every source file separately (similar to SCons).
   It should always work and is a little faster than SCons
   (it took 75% the time on my system for a complete build).

   'makeMakefile' will generate a Makefile in <MCAHOME> that
   copies all source files from a SCons build entity to a single
   source file first and then compiles this. 
   This is significantly faster (approximately 25% the time
   compared to SCons).
   However, this does not always work.
   Reasons include:
   - Global method/variable/enum declarations with the same names.
   - #define's that interfere with other source files
   - global namespace imports that make method calls ambiguous.
   Therefore, problematic build entities are listed in
   <MCAHOME>/make_builder/etc/blacklist.txt. There is one line per
   problematic entity (currently there are 12 entries; Syntax
   of this file is hopefully self-explanatory... generally, the
   listed source files are compiled separately).  
   
   Default target is $MCATARGET which is set by the mcasetenv
   script. An alternative target can be set with the 
   --build=<target> command line option - e.g.
   
   makeMakefile --build=<target>
   
   Both scripts should complete within a few seconds. They need
   to be called again whenever SConscript's are changed.
   If #include statements are added/removed (only local includes
   are relevant), they might need to be called again, in order to 
   detect changes cleanly. 

7) Build:

   The project can now be built by calling 'make' in <MCAHOME>
   from the console directly - or from some IDE such as Eclipse
   (see below). Note: when using Eclipse a complete rebuild in
   this IDE is recommended so that it will find all relevant
   include files.
   
   make
   
   ... will compile all libraries and projects (of course,
   make -j 2  or  make -j 4  is possible, too - for speedup).
   
   make <library name>   or
   make <project name>
   
   ... will only build a specific library/project including its
   dependencies. 
   
   Calling 'colormake' instead of 'make' leads to output that is
   easier to read.


Setting up the Eclipse IDE for debugging:

0) Requirements: 

   Java >= 1.5, Eclipse >= 3.4 (Ganymede) and matching CDT >= 5.0. 
   (Older versions might work, too. However, the newest versions 
    should be the most powerful and convenient.)

   Enough RAM... Eclipse typically occupies ~500 MB.

1) Setting up Eclipse:

   To enable the c++ support follow these instructions:

   Enable "Classic Updates" in Window/Preferences: General/Capabilities
   Help/Software Updates > Manage Configuration...
	Right Click on "Eclipse SDK"
	Add > Extension Location: "/usr/local/lib/eclipse-3.4"
   Restart Eclipse

2) Setting up Project:

   Select:
   - File->New Project...
   - C++ Project
   - Project name: mca2
     Location: <MCAHOME> directory; 
     Project Type: Makefile Project
   - Finish 

   This will take a while... (with many MCA libraries/projects
   in the source tree up to a few minutes).
   
   If the project has not been built previously, it will be built
   now (see Console View; this will take longer).
   If it has been built using make before, a complete rebuild is
   recommended so that Eclipse will find all necessary include
   files.
   
3) Debugging an MCA-based application:

   Typically - after building, all binaries are available in the
   'Project Explorer' View. Selecting a binary
   Right-Click->Debug As...->Local C/C++ Application will start
   the application and open the Debugger.
   
   The binaries built with SCons are available, too.
   When trying to debug those, Eclipse cannot find the required
   libraries. However, it is possible to debug these by adding 
   <MCAHOME>/export/i686Linux.../lib  to the LD_LIBRARY_PATH
   before starting Eclipse. 

