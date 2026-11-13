rm -fv *.keystore.old
rm -fv bin/classes.dex
rm -fv bin/*.signed.*
rm -fv bin/*.unsigned.*
rm -fv src/net/murat/kenc/R.java
rm -rf obj/net

aapt package -v -f -m -S "res" -J "src" -M "AndroidManifest.xml" -I "/usr/java/android/platforms/android-33/android.jar"

javac -source 1.8 -target 1.8 -bootclasspath "/usr/java/android/platforms/android-33/android.jar": -sourcepath "src" -cp "/usr/java/android/platforms/android-33/android.jar":"obj" -g:none -proc:none -nowarn -O -Xmaxwarns 1 -d "obj" "src/net/murat/kenc/Kenc.java"

echo "rm -rf obj/net"
rm -rf obj/net
