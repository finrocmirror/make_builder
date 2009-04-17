.PHONY: all

all : etc/libdb.txt dist/build.jar

etc/libdb.txt : etc/libdb.raw dist/build.jar src/makebuilder/libdb/LibDBBuilder.java
	updatelibdb

dist/build.jar : src/makebuilder/SrcDir.java src/makebuilder/BuildEntity.java src/makebuilder/ext/mca/SConscriptParser.java src/makebuilder/ext/mca/MCALibrary.java src/makebuilder/ext/mca/MCAPlugin.java src/makebuilder/ext/mca/MCABuilder.java src/makebuilder/ext/mca/MCABuildEntity.java src/makebuilder/ext/mca/MCAProgram.java src/makebuilder/ext/mca/DescriptionBuilderHandler.java src/makebuilder/Makefile.java src/makebuilder/Library.java src/makebuilder/Blacklist.java src/makebuilder/BuildFileLoader.java src/makebuilder/Program.java src/makebuilder/handler/NvccHandler.java src/makebuilder/handler/CppMerger.java src/makebuilder/handler/Qt4Handler.java src/makebuilder/handler/MakeXMLLoader.java src/makebuilder/handler/CppHandler.java src/makebuilder/SrcFile.java src/makebuilder/libdb/LibDB.java src/makebuilder/libdb/LibDBBuilder.java src/makebuilder/util/Util.java src/makebuilder/util/GCC.java src/makebuilder/util/Files.java src/makebuilder/util/CCOptions.java src/makebuilder/util/ToStringComparator.java src/makebuilder/util/CodeBlock.java src/makebuilder/util/ExtensionFilenameFilter.java src/makebuilder/SourceScanner.java src/makebuilder/SourceFileHandler.java src/makebuilder/MakeFileBuilder.java src/makebuilder/Options.java src/makebuilder/ext/mca/LibInfoGenerator.java src/makebuilder/ext/mca/MCASystemLibLoader.java src/makebuilder/ext/mca/virtualrepo/VirtualRepositoryBuilder.java src/makebuilder/ext/mca/EtcDirCopier.java src/makebuilder/ext/mca/HFileCopier.java
	ant


