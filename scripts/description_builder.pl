#!/usr/bin/perl -w

# This program is free software; you can redistribute it and/or
# modify it under the terms of the GNU General Public License
# as published by the Free Software Foundation; either version 2
# of the License, or (at your option) any later version.
# 
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
# 
# You should have received a copy of the GNU General Public License
# along with this program; if not, write to the Free Software
# Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
# 
#----------------------------------------------------------------------
#\file     description_builder.pl
#
# \author  Bernd Helge Schaefer
# \author  Martin Proetzsch
# \author  Tobias Luksch
# \author  Tobias Foehst
# \date    2008-07-25
#
#----------------------------------------------------------------------
#
# For developers:
#
# Note that this programme by uses STDOUT to pipe the output through
# so for debug output it is necessary to use either a file or STDERR.
#

use strict;
use File::Basename;

my @suffix_list = qr{\..*};

my $processing_state = "idle";
my $number_of_chars_to_skip = 0;
my $format_mode = "Natural";

my $enum_list = "";

my ($input_file_name, $output_file_name);
my $init_complete = 0;

my @namespaces;

my $class_name;
my $var_name;
my $enum_dimension;
my $template_list_string;
my $template_list_with_types_string;

my $start_enum_from = "0";

my $open_bracket = "0";

#retrieve parameters and open files
if (@ARGV == 1) {
    ($input_file_name) = @ARGV;
#    print "input_file_name: ".$input_file_name."\n";
#    print "output_file_name: STDOUT\n";
    $init_complete = 1;
}
elsif (@ARGV == 2) {
    print "\n";
    print "##################################\n";
    print "#  MCA2-kl  Description Builder  #\n";
	print "#  (c)2008 Bernd Helge Schaefer  #\n";
	print "#          Martin Proetzsch      #\n";
	print "#          Tobias Luksch         #\n";
	print "#          Tobias Foehst         #\n";
	print "#     Robotics Research Lab      #\n";
	print "#  University of Kaiserslautern  #\n";
	print "##################################\n\n";

    ($input_file_name, $output_file_name) = @ARGV;
    print "input_file_name: ".$input_file_name."\n";
    print "output_file_name: ".$output_file_name."\n";

    if ($input_file_name eq $output_file_name) {
	print "input and output file may not be the same!\n\n";
	exit (-1);
    }
    $init_complete = 1;
}

if ($init_complete) {
    
    #we have an input file in any case
    open (INPUT_FILE, $input_file_name) || die ("Could not open file: ".$input_file_name);

#    print STDERR "descriptions for ".$input_file_name."\n";

    #in case we have an input file -> redirect STDOUT to this file
    if ($output_file_name) {
	close (STDOUT);
	open (STDOUT, ">".$output_file_name) || die ("Could not open file: ".$output_file_name);
    }   

    my ($basename, $dirname, $suffix) =
	fileparse($input_file_name, @suffix_list);

    #write preamble to file
    print STDOUT "// this is a -*- C++ -*- file\n";
    print STDOUT "//----------------------------------------------------------------------\n";
    print STDOUT "// This file is automatically created from ".$basename.$suffix."\n";
    print STDOUT "// by description_builder.pl \n";
    print STDOUT "// (c) 2008 Bernd Helge Schaefer, Tobias Luksch, Martin Proetzsch, Tobias Foehst\n";
    print STDOUT "//----------------------------------------------------------------------\n\n";

    my ($head, $include_file_name) = split ("/", $input_file_name, 2);
    if ($head eq "rrlib") {
	print STDOUT "#include \"".$input_file_name."\"\n\n" if defined $include_file_name;
    }
    else {
	print STDOUT "#include \"".$include_file_name."\"\n\n" if defined $include_file_name;
    }
    print STDOUT "#include \<cstring\>\n\n";
    print STDOUT "#include \<cstdio\>\n\n";

    while (<INPUT_FILE>) {
	my $line = $_;
	chomp ($line);

	# replace | separator token which is needed in template parameter lists 
	# due to the definition of the C preprocessor makro in Descr.h
	$line =~ s/\|/,/g;
	
	####################
	#ignore comments
	####################
	my $before_multi_line_comment;
	$line =~ s%/\*.*\*/%%g;  #remove all /**/ comments which are in a single line
	if ($line =~ m%/\*% || $line =~ m%//%) {	    

	    my $multi_comment_detected = 0;
	    #detect multi comments before single comments
	    if ($line =~ m%/\*.*//%){
		$multi_comment_detected = 1;
	    }
	    #detect single comments
	    elsif ($line =~ m%//.*/\*% or $line =~ /\/\//) {
		my @tokens = split(/\/\//, $line);
		$line = $tokens [0];
	    }
	    #the rest must be multi comments
	    else {
		$multi_comment_detected = 1;
	    }

	    if ($multi_comment_detected) {
		my $end_of_comment_found = 0;

		my @tokens = split(/\/\*/, $line);
		# save the text before the opening of a multi line comment 
		# and append it to the text following the 
		# closing of the multi line comment (*)
		$before_multi_line_comment = $tokens [0];

		do {

		    if (!($line =~ /\*\//)) {
			#skip lines which do not contain '*/'
		    }
		    else {
			my @tokens = split(/\*\//, $line);
			$line = $tokens [$#tokens];
			$end_of_comment_found = 1;
		    }
		    
		    $line = <INPUT_FILE> unless ($end_of_comment_found);
		} while (!$end_of_comment_found);
		# (*) here the text before and after the multi line comment 
		# are put together for further processing
		$line = $before_multi_line_comment." ".$line if defined $before_multi_line_comment;
	    }
	}
	
	#skip empty lines
	if (not $line) {
	    next;
	}

    ####################
    # handle namespace pollution
    ####################
    if ($line =~ /^using\s+namespace/) {
        print (STDERR "\n\n\nfound namespace using declaration in header ".$input_file_name." : '".$line."'\n");
        print (STDERR "namespace pollution warning\n\n\n");
        print (STDOUT "#warning \"namespace pollution warning\"\n");
    }

	####################
	# handle namespace opening
	####################
    if ($line =~ /^namespace\s+(\S+)/) {
        #print STDERR "found namespace: '$1'\n";
        push @namespaces, { "name" => $1, "scopes" => 0 };
	}

	####################
	# handle namespace closing
	####################
    if ($line =~ /\{/ and scalar @namespaces > 0)
    {
        $namespaces[@namespaces - 1]{"scopes"}++;
	}
    if ($line =~ /\}/ and scalar @namespaces > 0)
    {
        pop @namespaces unless --$namespaces[@namespaces - 1]{"scopes"};
    }

	####################
	# process DESCR makro line
	####################
	if ($line and
	    ($line =~ /_DESCR_/) and 
	    ($processing_state eq "idle")) {
	    $start_enum_from = "0";
	    (my $parameters = $line) =~ s/.*_DESCR_\s*\((.*)\).*/$1/;

	    #parse modifiers; note that these are only needed by the C preprocessor makro in Descr.h
	    my ($modifiers, $tail) = split (",", $parameters, 2);

	    ####################
	    #parse class name
	    ####################
	    #$class_name;

	    # check whether we have a template class
	    if ($tail =~ /</) {
		#retrieve template parameter list
		($class_name , $tail) = split(/>\s*,/,$tail,2);
		($class_name , my $template_parameter_list) = split("<",$class_name,2);
		$class_name = trim ($class_name);
		$template_list_with_types_string = $template_parameter_list;

        if (scalar @namespaces)
        {
            my $namespace = join "::", map { $_->{"name"} } @namespaces;
            $class_name = $namespace . ($class_name ? "::$class_name" : "");
        }
		
		#pipe start to output
		print (STDOUT "template <".$template_parameter_list.">\n");
		print (STDOUT "const char* const ".$class_name."<");

		####################
		# process template parameter (list)
		####################
		
		# The template parameters in the makro come with type and parameter name.
                # If we encounter template-template parameters we have to cut the nested types
                # off the line, before we can go on with further processing
                # e.g.: 'template<class A, class B> class C' -> 'class C'
                while ($template_parameter_list =~ /(template[^>]*)/) {
                    my @level_intros = split "<", $1;
                    $template_parameter_list =~ s/template([^>]*>){$#level_intros}//;
                }

		# In the description initialisation the types have to go.
		# So at this point we split the comma separated list and take the last token 
		# of the spaces separated remainder
		# e.g: 'class A, unsigned int B, float C' -> A, B, C
		my @template_parameters = split(",", $template_parameter_list);
		my $counter = 0;

		$template_list_string = "";
		foreach my $template_parameter (@template_parameters) {
		    $template_parameter = trim ($template_parameter);
		    my @template_parameter_entities = split(" ",$template_parameter);
		    
		    #print (STDOUT $template_parameter_entities [$#template_parameter_entities]);
		    $template_list_string = $template_list_string.$template_parameter_entities [$#template_parameter_entities];
		    
		    #add inner commas
		    if ($counter < $#template_parameters) {
			#print (STDOUT ", ");
			$template_list_string = $template_list_string.", ";
		    }
		    $counter++;
		}
		print STDOUT $template_list_string;
		print (STDOUT ">::");
	    }
	    # if we are not dealing with templates, things are a lot simpler -> we only need the class name.
	    else {
		$template_list_with_types_string = "";
		$template_list_string = "";
		# retrieve the class name and write to output
		($class_name, $tail) = split (",", $tail, 2);
		$class_name = trim ($class_name);

		if (scalar @namespaces)
		{
            my $namespace = join "::", map { $_->{"name"} } @namespaces;
		    $class_name = $namespace . ($class_name ? "::$class_name" : "");
		}

		print STDOUT "const char* const ";
		if ($class_name) {
		    print STDOUT $class_name."::";
		}
	    }
	    
	    # retrieve the variable name and write to output
	    ($var_name, $tail) = split(",",$tail,2);
	    
	    $var_name = trim ($var_name);
	    print (STDOUT $var_name."[] = {\n");

	    # retrieve the number of characters to skip for leaving away the prefixes eCI_, eSI, etc.
	    ($number_of_chars_to_skip , $tail) = split(",",$tail,2);

	    # retrieve the string format mode
	    ($format_mode, $tail) = split(",",$tail,2);

	    # the description makro was processed -> we proceed to processing the following enums.
	    $processing_state = "collecting_enums";
	    next ();
	}

	####################
	# aggregate the enum into one string
	####################
	
	# at first we will only append all enum lines to one string without new lines
	if ($processing_state eq "collecting_enums") {
	    if ($line =~ m/=/) {
		my @tokens = split ("=", $line, 2);

		if ($#tokens > 0) {
		    $line = $tokens [0];
		    $start_enum_from = $tokens [1];
		    $start_enum_from =~ s/,.*//;
		    #print STDERR "start_enum_from".$start_enum_from."\n";
		}
                
	    }

	    chomp ($line);
	    if ($line) {
		$enum_list .= $line.",";
	    }
	}

	####################
	# On the next closing '}' we stop collecting enums and process them
	####################
	if ($line and
	    ($line =~ /\}/) and 
	    ($processing_state eq "collecting_enums")) {

#	    print STDERR "enum_list: ".$enum_list."\n";

	    # remove the curly brackets such that only the comma separated enum values remain.
	    $enum_list =~ s/.*\{(.*)\}.*/$1/;

	    # split the list of enum values
	    my @enum_values = split(",",$enum_list);
	    
#	    @enum_values = map BeforeEqualsSign ($_), @enum_values;   #split a '=' to remove assignments to enum values
	    @enum_values = map trim($_), @enum_values;                #remove leading and trailing white space
	    @enum_values = grep $_ ne "", @enum_values;                   #remove empty strings
	    $enum_dimension = $enum_values [$#enum_values]; #save dimension string as is
	    @enum_values = map substr ($_, $number_of_chars_to_skip), @enum_values;  #remove the leading characters to be skipped (e.g.: eCI_, eSO_, etc.)

	    # if the last value in the array is not DIMENSION -> we have an illegal enumeration declaration
	    my $lower_case = lc $enum_values [$#enum_values];
	    if ($lower_case ne "dimension") {
		print STDOUT "#error \"description_builder.pl: missing DIMENSION marker in ".$basename.$suffix."\"\n";
	    }
	    else {
		# remove the last entry (the DIMENSION) before proceeding as we do not want to create a description for this entry.
		splice (@enum_values, -1);
	    }

	    # print the formatted description to stdout
	    my $counter = 0;
	    foreach my $enum_value (@enum_values) {
		print STDOUT "  QT_TR_NOOP( \"".(FormatEnum ($enum_value, $format_mode))."\" )";

		if ($counter < $#enum_values) {
		    print STDOUT ",\n";
		}
		else {
		    print STDOUT "\n";
		}
		$counter++;
	    }

	    # add closing bracket on end of description enum
	    print STDOUT "};\n\n";
	    
	    # if ($template_list_string) {
	    # 	$class_name = $class_name."<".$template_list_string.">";
	    # 	print STDOUT "template <".$template_list_with_types_string.">\n";
	    # }
	    # print STDOUT "bool ";
	    # if ($class_name) {
	    # 	print STDOUT $class_name."::";
	    # }
	    # print STDOUT $var_name."ToEnum (size_t& id, const char* string, bool quiet) {\n";
	    # print STDOUT "\tfor (size_t i = ".$start_enum_from."; i < ".$enum_dimension."; i++) {\n";
	    # print STDOUT "\t\tif (strncmp (string, ".$var_name." [i-".$start_enum_from."], 255) == 0) {\n";
	    # print STDOUT "\t\t\tid = i;\n";
	    # print STDOUT "\t\t\treturn true;\n";
	    # print STDOUT "\t\t}\n";
	    # print STDOUT "\t}\n";
            # print STDOUT "\tif (!quiet){\n";
	    # print STDOUT "\t\tfprintf (stdout, \"".$class_name."::ToEnum>> illegal string '%s'\\nPossible options are:\\n\", string);\n";
	    # print STDOUT "\t\tfor (size_t i = ".$start_enum_from."; i < ".$enum_dimension."; i++) {\n";
	    # print STDOUT "\t\t\tfprintf (stdout, \"\\t%zd -> %s\\n\", i, ".$var_name."[i-".$start_enum_from."]);\n";
	    # print STDOUT "\t\t}\n";
	    # print STDOUT "\t}\n";
	    # print STDOUT "\treturn false;\n";
	    # print STDOUT "}\n\n";

	    $enum_list = "";
	    $processing_state = "idle";
	}
    }

#    if ($class_name) {
#	print STDOUT "const char* ".$class_name."::ClassName () const {\n";
#	print STDOUT "\treturn \"".$class_name."\";\n";
#	print STDOUT "}\n";
#    }

    # when our programme finishes in a state different from 'idle' 
    # there is either something wrong with the input file 
    # or we have a bug in description_builder.pl
    if ($processing_state ne "idle") {
	print STDOUT "#error \"description_builder.pl: description makro processing incomplete in ".$basename.$suffix."\" (processing_state: ".$processing_state." should be 'idle')\n";
    }

    # close files and exit
    close (INPUT_FILE);
    close (STDOUT);
    exit (0);
}
# in case no parameter was provided -> show the usage of description_builder.pl
else {
    print "\n";
    print "##################################\n";
    print "#  MCA2-kl  Description Builder  #\n";
	print "#  (c)2008 Bernd Helge Schaefer  #\n";
	print "#          Martin Proetzsch      #\n";
	print "#          Tobias Luksch         #\n";
    print "#          Tobias Foehst         #\n";
	print "#     Robotics Research Lab      #\n";
	print "#  University of Kaiserslautern  #\n";
	print "##################################\n\n";

    print "Usage:\ndescription_builder.pl <input_file_name> <output_file_name>\n\n";

    print "Creates description files considering the corresponding .h-files.\n";

    print "Using the define \n";
    print "_DESCR_(modifiers, class_name, var_name, ignore, type, last)\n";
    print "before any enumerations\n";
    print "results in creation of string arrays (descriptions) for the defined enums.\n";
    print "<modifiers class_name::var_name> will be the identifier of the string-array.\n";
    print "The first <ignore> characters of each enum will not be used in order to create \n";
    print "the descriptions. <type> is one of \n";
    print "\"AsIs\", \"Natural\", \"AsIs\", \"CAPS\", \"small\" or \"NoSpaces\"\n";
    print "and switches between different output possibilities.\n";
#    print "If the <last> parameter is given, the last enumeration is replaced by it.\n";
    print "EXAMPLE:\n";
    print "#include <descr.h>\n";
    print "_DESCR_(static, mGpsDevice, so_description, 4, Natural)\n";
    print "enum {\n";
    print "eSO_CHANGED, \n";
    print "eSO_DIMENSION }; \n";
    print "RESULT:\n";
    print "#include\"gps/mGpsDevice.h\" \n";
    print "const tConstDescription mGpsDevice::so_description[]= {\n";
    print "QT_TR_NOOP( \"Changed\" )\n";
    print "};\n";

    exit (0);
}

sub trim
{
    my $string = shift;
    $string =~ s/^\s+//;
    $string =~ s/\s+$//;
    return $string;
}

sub BeforeEqualsSign 
{
    my $temp = shift;
    my @tokens = split ("=", $temp, 2);

    return $tokens [0];
}

sub FormatEnum {
    my $enum_value = shift;
    $_ = shift;

    SWITCH: {
	/AsIs/ && do { last SWITCH; };
	/Natural/ && do { 
	    my @words = split ("_", $enum_value);
	    @words = map lc, @words;
	    @words = map ucfirst, @words;
	    $enum_value = join (" ", @words);
	    last SWITCH;
	};
	/CAPS/ && do { 
	    my @words = split ("_", $enum_value);
	    @words = map uc, @words;
	    $enum_value = join (" ", @words);
	    last SWITCH; };
	/small/ && do { 
	    my @words = split ("_", $enum_value);
	    @words = map lc, @words;
	    $enum_value = join (" ", @words);
	    last SWITCH; 
	};
	/NoSpaces/ && do { 
	    my @words = split ("_", $enum_value);
	    @words = map lc, @words;
	    @words = map ucfirst, @words;
	    $enum_value = join ("", @words);
	    last SWITCH; 
	};

	{
	    print STDOUT "#error \"description_builder.pl: FormatEnum (".$enum_value.", ".$_.")>> illegal format mode: '".$_."'!\"\n";
	}
    }
    return $enum_value;
}
