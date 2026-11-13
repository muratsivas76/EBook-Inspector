rm -fv *.keystore*
rm -fv bin/classes.dex
rm -fv bin/*.signed.*
rm -fv bin/*.unsigned.*
rm -fv bin/*.apk
rm -fv src/net/murat/notepad/R.java
rm -rf obj/net

keytool -genkey -alias Notepad.keystore -keyalg RSA -validity 10000 -dname "CN=Murat inan, OU=Freelance, O=Traductor, S=Sivas, C=TR" -keystore Notepad.keystore -storepass 5EmrE432 -keypass 5EmrE432

keytool -importkeystore -srckeystore Notepad.keystore -destkeystore Notepad.keystore -deststoretype pkcs12 -srcstorepass 5EmrE432 -deststorepass 5EmrE432 -noprompt
