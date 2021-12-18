#!/bin/bash

# This script creates bash script in the current directory
# called 'locoo3d'.  It will call the locoo3d jar file as a runnable jar.
#
# In order for database connectivity to work, users must find the location of
# the Oracle file ojdbc<x>.jar on their system and specify that location here
# (no spaces around the '=' sign!).  If this variable is omitted or blank
# database IO will not be possible but flat file IO will be possible.
#
# For wallet database connections, the user must specify the location of the
# Oracle oraclepki.jar file on their system and the location of the wallet. 
#
# If rstt/slbm is to be used, specify the directory where the file libslbmjni.jnilib resides.
# If any one of [ RSTT_ROOT, RSTT_HOME, SLBM_ROOT, SLBM_HOME ] is specified in your .bash_profile
# then use of rstt/slbm will be possible without specifying RSTT here.  

locoo3d_jar=$(pwd)/target/locoo3d-1.9.10-jar-with-dependencies.jar
OJDBC=/Library/Oracle/instantclient_12_2/ojdbc8.jar
OPKI=/Library/Oracle/instantclient_12_2/oraclepki.jar
wallet=/Users/$USER/wallet
RSTT=/Users/$USER/Documents/RSTT_v3.2.0/lib

# ---- LocOO3D
echo "Creating executable script file locoo3d that launches LocOO3D"
echo "#!/bin/bash" > locoo3d
echo "#" >> locoo3d
echo "# The substring '-Xmx????m' in the following execution" >> locoo3d
echo "# command specifies the amount of memory to make available" >> locoo3d
echo "# to the application, in megabytes." >> locoo3d
echo "#" >> locoo3d
echo "# Include java.library.path only if RSTT is specified." >> locoo3d
echo "#" >> locoo3d
echo "if [ -z $RSTT ]; then" >> locoo3d
echo "  java -Xmx1400m -classpath $locoo3d_jar:${OJDBC}:${OPKI} -Doracle.net.wallet_location=$wallet -Doracle.net.tns_admin=$wallet gov.sandia.gmp.locoo3d.LocOO  \$*" >> locoo3d
echo "else " >> locoo3d
echo "  java -Xmx1400m -Djava.library.path=$RSTT -classpath $locoo3d_jar:${OJDBC}:${OPKI} -Doracle.net.wallet_location=$wallet -Doracle.net.tns_admin=$wallet gov.sandia.gmp.locoo3d.LocOO  \$*" >> locoo3d
echo "fi" >> locoo3d
chmod 777 locoo3d

# ---- Support Map
echo "Creating executable script file supportmap that launches SupportMap"
echo "#!/bin/bash" > supportmap
echo "#" >> supportmap
echo "# The substring '-Xmx????m' in the following execution" >> supportmap
echo "# command specifies the amount of memory to make available" >> supportmap
echo "# to the application, in megabytes." >> supportmap
echo "#" >> supportmap
echo "java -Xmx1400m -classpath $locoo3d_jar:${OJDBC}:${OPKI} -Doracle.net.wallet_location=$wallet -Doracle.net.tns_admin=$wallet gov.sandia.gmp.libcorr3dgmp.SupportMap \$*" >> supportmap
chmod 777 supportmap

# ---- Add to path
# The script also prints to screen recommended addition to 
# the user's .cshrc, .bash_profile, or .profile that will make the new
# executable available via the PATH.  No changes to the user's
# environment are actually made.

if [ `uname -s` = Darwin ]; then
	echo "Add this line to your .bash_profile"
	echo "export PATH=$(pwd):\$PATH"
else
	echo "Add this line to your .cshrc file"
	echo "set path=( $(pwd) \$path )"
fi
