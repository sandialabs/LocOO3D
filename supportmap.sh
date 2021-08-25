#!/bin/bash 

# Users may want to add the following line to their .bash_profile file
# PATH=$PATH:<path to directory where this file resides>

# the location of the locoo3d jar file on the user's system.
locoo3d_jar=/Users/$USER/github/LocOO3D-Devl/target/locoo3d-1.9.9-jar-with-dependencies.jar

# oracle's ojbdc jar file which is required to access an oracle database. This file is not delivered 
# with locoo3d.  Users must locate this file on their sytem and specify its location here.
ojdbc_jar=/Users/$USER/jarfiles/ojdbc8.jar

java -cp $locoo3d_jar:$ojdbc_jar gov.sandia.gmp.libcorr3dgmp.SupportMap $@
