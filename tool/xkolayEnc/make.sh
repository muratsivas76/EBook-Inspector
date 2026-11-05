rm -fv bin/*.apk

aapt package -v -f -m -S res -J src -M AndroidManifest.xml -I /usr/java/android/platforms/android-33/android.jar

javac -source 1.8 -target 1.8 -bootclasspath "/usr/java/android/platforms/android-33/android.jar": -sourcepath "src" -cp "/usr/java/android/platforms/android-33/android.jar":"obj" -g:none -proc:none -nowarn -O -Xmaxwarns 1 -d "obj" "src/net/murat/kenc/Kenc.java"

/usr/java/android/build-tools/29.0.0/dx --dex --verbose --output="bin/classes.dex" "obj"

aapt package -v -f -M "AndroidManifest.xml" -A "assets" -S "res" -I "/usr/java/android/platforms/android-33/android.jar" -F "bin/Kenc.unsigned.apk" "bin"

jarsigner -keystore Kenc.keystore -storepass 5EmrE432 -keypass 5EmrE432 -signedjar "bin/Kenc.signed.apk" "bin/Kenc.unsigned.apk" Kenc.keystore

zipalign -v -f 4 "bin/Kenc.signed.apk" "bin/Kenc.apk"

rm -fv *.keystore.old
rm -fv bin/classes.dex
rm -fv bin/*.signed.*
rm -fv bin/*.unsigned.*
rm -fv src/net/murat/kenc/R.java

echo "rm -rf obj/net"
rm -rf obj/net

ls bin
