#!/bin/sh
set -eu
cd "$(dirname "$0")"
# Recreate only generated files; package the supplied FastCGI library in one JAR.
rm -rf build/classes
mkdir -p build/classes httpd-root/fcgi-bin
javac --release 17 -encoding UTF-8 -cp lib/fastcgi-lib.jar -d build/classes src/main/java/org/example/*.java
(cd build/classes && jar -xf ../../lib/fastcgi-lib.jar)
jar --create --file httpd-root/fcgi-bin/app.jar --main-class org.example.Main -C build/classes .
echo 'Собрано: httpd-root/fcgi-bin/app.jar'
