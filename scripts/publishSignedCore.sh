#!/bin/bash
echo "Assumed that you ran 'sbt +clean +update dependencyUpdates evicted +test:compile +test' first!"
echo ""
if grep -q SNAP build.sbt
then
   echo "There are SNAPSHOTs in the build! Aborting."
   exit 1
fi

sbt "; + mellite-core/publishSigned ; sonatypeBundleRelease"
