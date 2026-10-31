./clean.sh

/usr/java/android/build-tools/36.0.0/aapt package -v -f -m -S res -J src -M AndroidManifest.xml -I /usr/java/android/platforms/android-36/android.jar

javac -source 1.8 -target 1.8 -bootclasspath /usr/java/android/platforms/android-36/android.jar -sourcepath src -cp /usr/java/android/platforms/android-36/android.jar:obj -g:none -proc:none -nowarn -O -d obj src/net/murat/decodebook/DecodeBook.java
