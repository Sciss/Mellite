#!/bin/bash
cd "$(dirname "$0")"

VERSION=$(cat ../build.sbt | grep 'lazy val appVersion' | cut -d'"' -f 2)
NAME="mellite-full_${VERSION}_mac_x64"
DIR="${HOME}/Downloads/"
UNZIP="$DIR/$NAME"
ZIP="${UNZIP}.zip"
echo "Assuming input is at $ZIP"

if [[ -f $ZIP ]]
then
    echo ""
else
    echo "$ZIP doesn't exist"
    exit 1
fi

cd $DIR
echo "Unzipping..."
rm -r $UNZIP
unzip -q $ZIP -d $DIR
# ls -la $UNZIP/bin/
echo "Correcting permissions..."
rm $UNZIP/bin/mellite.bat
chmod a+x $UNZIP/bin/mellite
chmod a+x $UNZIP/jre/bin/*
echo "Re-zipping..."
rm $ZIP
zip -q -y -r -9 $ZIP $NAME # crucial not to use absolute path here!
rm -r $UNZIP
echo "Done."

