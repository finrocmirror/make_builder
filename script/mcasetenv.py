#!/usr/bin/env python
# this is a -*- python -*- file

import os
import platform
import sys
import re
import getopt
import ConfigParser

#
# global variables init
#
verbose=False
config_dict_cmd={}

mca2_config_name_default='.mca2-config.'+platform.node()
mca2_config_name=mca2_config_name_default

#
# Functions
#
regex_yes=re.compile("[Yy]([Ee][Ss])?")
def IsYes(str):
    return regex_yes.match(str)

def PrintSettings():
    sys.stderr.write("----------------------------------\n")
    sys.stderr.write(" MCA Settings\n")
    sys.stderr.write("----------------------------------\n")
    sys.stderr.write(" Home:         "+ env_dict["MCAHOME"]+"\n");
    sys.stderr.write(" Project:      "+ env_dict["MCAPROJECT"]+"\n");
    sys.stderr.write(" Project Home: "+ env_dict["MCAPROJECTHOME"]+"\n");
    sys.stderr.write(" SubProject:   "+ env_dict["MCASUBPROJECT"]+"\n");
    sys.stderr.write(" Target:       "+ env_dict["MCATARGET"]+"\n");
    if mca_debug:
        sys.stderr.write(" Debug:        On\n")
    else:
        sys.stderr.write(" Debug:        Off\n")
    if mca_lxrt:
        sys.stderr.write(" LXRT:         Yes\n")
    else:
        sys.stderr.write(" LXRT:         No\n")
        
    sys.stderr.write("----------------------------------\n")


def Usage():
	sys.stderr.write("usage:")
	sys.stderr.write( sys.argv[0]+"[options]\n")
	sys.stderr.write( "options:\n")
        sys.stderr.write( "-h,--help show this help\n")
        sys.stderr.write( "-c CONFIGFILE,--config=CONFIGFILE (use given CONFIGFILE instead of '.mca2-config.[hostname]')\n")
        sys.stderr.write( "-p PROJECT,--project=PROJECT (override project name)\n")
        sys.stderr.write( "-s SUBPROJECT,--subproject=SUBPROJECT (override subproject name)\n")
        sys.stderr.write( "-d,--debug=[y/n] (override debug settings)\n")
        sys.stderr.write( "-t TARGETNAME,--target=TARGETNAME (target name - will be appended to default name)\n")

def CmdConfig(key):
    if config_dict_cmd.has_key(key):
        return config_dict_cmd[key]
    else:
        return ''
    
#
# Main
#

# redirect output to stderr
#print >> sys.stderr

# Read command line arguments
cmdline_arguments={}
try:                                
    opts, args = getopt.getopt(sys.argv[1:], "hp:s:d:c:t:", ["help", "project=", "subproject=", "debug=","config=","target="]) 
except getopt.GetoptError:           
    Usage()                          
    sys.exit(2)
    
for opt, arg in opts:
    if opt in ("-h", "--help"):
        Usage()
        sys.exit(0)
    elif opt in ("-p","--project"):
        cmdline_arguments['project']=arg
        config_dict_cmd["Project_main"]=arg
    elif opt in ("-c","--config"):
        mca2_config_name=arg
    elif opt in ("-s","--subproject"):
        cmdline_arguments['subproject']=arg
        config_dict_cmd["Project_sub"]=arg
    elif opt in ("-d","--debug"):
        cmdline_arguments['debug_mode']=arg
        config_dict_cmd["Build_debug"]=arg
    elif opt in ("-t","--target"):
        cmdline_arguments['target_extra']=arg
        config_dict_cmd["target_extra"]=arg

# Read MCA config file if available
config_file_entries={}
if not os.access(mca2_config_name,os.R_OK):
    sys.stderr.write("Cannot open config file "+mca2_config_name+"\n")
    sys.stderr.write("Make sure you have already run scons or read the README file for more information.\n")
    sys.stderr.write("Creating empty config file "+mca2_config_name+" for now. (be aware of this fact!)\n")
    cf=open(mca2_config_name,'a')
    cf.close()
    #sys.exit(1)
    
cf=open(mca2_config_name,'r')
for line in cf.readlines():
    line_match=re.match("(.*?)\s*=\s*(['\"]?)(.*)\\2",line)
    key=line_match.group(1)
    value=line_match.group(3)
    config_file_entries[key]=value
cf.close()

# Overwrite values in the config file with the commandline arguments
for entry in ['project','subproject','debug_mode','target_extra']:
    if cmdline_arguments.has_key(entry):
        config_file_entries[entry]=cmdline_arguments[entry]

# Write back the config file
cf=open(mca2_config_name,'w')
for (key,value) in config_file_entries.iteritems():
    if value.isdigit():
    	cf.write('%s = %s\n' % (key,value))
    elif re.match(".*'.*",value):
        cf.write('%s = "%s"\n' % (key,value))
    else:
        cf.write("%s = '%s'\n" % (key,value))
cf.close()

## Extract necessary information from the configfile

# Check for debug mode
mca_debug=True
if config_file_entries.has_key('debug_mode'):
    mca_debug=IsYes(config_file_entries['debug_mode'])
else:
    mca_debug=True

# Detect architecture and system
config_arch=platform.machine()
if config_arch=='':
    (config_arch,_)=platform.architecture()

config_system=platform.system()

# Build target string
mca_target=config_arch+'_'+config_system
try:
    if config_file_entries['target_extra']!='':
        mca_target+='_'+config_file_entries['target_extra']
except:
    pass
if mca_debug:
    mca_target+='_debug'

# Check if LXRT is available
mca_lxrt=False
if config_file_entries.has_key('with_lxrt')!='not specified':
    mca_lxrt=True

## Evaluate environment variables
env_dict={}

# MCAPROJECT
if config_file_entries.has_key('project'):
    env_dict['MCAPROJECT']=config_file_entries['project']
else:
    env_dict['MCAPROJECT']='test'

# MCASUBPROJECT
if config_file_entries.has_key('subproject'):
    env_dict['MCASUBPROJECT']=config_file_entries['subproject']
else:
    env_dict['MCASUBPROJECT']=''

# MCAHOME
env_dict['MCAHOME']=os.getcwd()

# MCAPROJECTHOME
mca_project_directory=env_dict['MCAHOME']
project_directories=[os.path.join(env_dict['MCAHOME'],env_dict['MCAPROJECT']),
                     os.path.join(env_dict['MCAHOME'],'projects',env_dict['MCAPROJECT'])]
for project_directory in project_directories:
    if os.path.exists(project_directory):
        mca_project_directory=project_directory
env_dict['MCAPROJECTHOME']=mca_project_directory

# MCATARGET
#env_dict['MCATARGET']=mca_target
env_name = "MCATARGET"
env_dict[env_name] = ''
if config_file_entries.has_key('target'):
    env_dict[env_name] = config_file_entries['target']
if env_dict[env_name] == "":
    env_dict[env_name] = mca_target


# PATH
env_name='PATH'
if os.environ.has_key(env_name):
    env_dict[env_name]=os.environ[env_name]
else:
    env_dict[env_name]=''

#clean old mca2 PATH entries
env_dict[env_name]=re.sub(":?"+env_dict["MCAHOME"]+"[^:]*","",env_dict[env_name])
#add new mca2 PATH entries
mca2_path_directories=[]
mca2_path_directories.append(os.path.join(env_dict["MCAHOME"],"bin"))
mca2_path_directories.append(os.path.join(env_dict["MCAPROJECTHOME"],"script"))
mca2_path_directories.append(os.path.join(env_dict["MCAHOME"],"script"))
mca2_path_directories.append(os.path.join(env_dict["MCAHOME"],"make_builder","script"))
for mca2_path_directory in mca2_path_directories:
    if (os.path.exists(mca2_path_directory)):
        env_dict[env_name]= mca2_path_directory + ":" + env_dict[env_name]
# always add $MCAHOME/export/$MCATARGET/bin
env_dict[env_name]=os.path.join(env_dict["MCAHOME"],"export",env_dict["MCATARGET"],"bin") + ":" + env_dict[env_name]
#delete :: 
env_dict[env_name]=re.sub("::",":",env_dict[env_name])

# LDLIBRARYPATH
env_name='LD_LIBRARY_PATH'
if os.environ.has_key(env_name):
    env_dict[env_name]=os.environ[env_name]
else:
    env_dict[env_name]=''
#clean old mca2 LD_LIBRARY_PATH entries
env_dict[env_name]=re.sub(env_dict["MCAHOME"]+":?[^:]*","",env_dict[env_name])
# add new  mca2 LD_LIBRARY_PATH entries
mca2_lib_directories=[]
mca2_lib_directories.append(os.path.join(env_dict["MCAHOME"],"lib"))
for mca2_lib_directory in mca2_lib_directories:
    if (os.path.exists(mca2_lib_directory)):
        env_dict[env_name]=mca2_lib_directory + ":" + env_dict[env_name]
# always add $MCAHOME/export/$MCATARGET/lib
env_dict[env_name]=os.path.join(env_dict["MCAHOME"],"export",env_dict["MCATARGET"],"lib") + ":" + env_dict[env_name]
#delete :: 
env_dict[env_name]=re.sub("::",":",env_dict[env_name])

PrintSettings()

# Add other shell detection and environment set commands to the following code detect shell
shell=''
for line in sys.stdin:
    if re.match(".*tcsh.*",line):
        shell="tcsh"
        break
    elif re.match(".*(z|ba)sh.*",line):
        shell="bash"
        break

#write setenv/export commands
if shell=="bash":
    for element in env_dict:
        sys.stdout.write("export "+element+"="+env_dict[element]+";")
elif shell=="tcsh":
    for element in env_dict:
        sys.stdout.write("setenv "+element+" "+env_dict[element]+";")
#place any other shell command output here!
else:
    sys.stderr.write("echo Unknown shell type\nEnvironment variables not set\n")
