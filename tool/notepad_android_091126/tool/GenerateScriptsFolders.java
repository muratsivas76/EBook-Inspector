import java.io.*;

final
public class GenerateScriptsFolders
extends Object
implements Serializable
{

	private static String APPNAME="";
	
	private GenerateScriptsFolders ()
	{
		super ();
	}
	
	public String toString ()
	{
		return "Generates Scripts and Folders.";
	}
	
	final
	private static void makedirs ()
	throws IOException
	{
		File fd=new File ("arac");
		fd.mkdir ();
		System.out.println ("Generated: "+fd.getName ());
		
		fd=new File ("assets");
		fd.mkdir ();
		System.out.println ("Generated: "+fd.getName ());
		
		fd=new File ("bin");
		fd.mkdir ();
		System.out.println ("Generated: "+fd.getName ());
		
		fd=new File ("libs");
		fd.mkdir ();
		System.out.println ("Generated: "+fd.getName ());
		
		fd=new File ("obj");
		fd.mkdir ();
		System.out.println ("Generated: "+fd.getName ());
		
		fd=new File ("res");
		fd.mkdir ();
		System.out.println ("Generated: "+fd.getName ());
		
		fd=new File ("res\\drawable");
		fd.mkdir ();
		System.out.println ("Generated: res\\"+fd.getName ());
		
		fd=new File ("res\\layout");
		fd.mkdir ();
		System.out.println ("Generated: res\\"+fd.getName ());
		
		fd=new File ("res\\values");
		fd.mkdir ();
		System.out.println ("Generated: res\\"+fd.getName ());
		
		fd=new File ("src");
		fd.mkdir ();
		System.out.println ("Generated: "+fd.getName ());
		
		fd=new File ("src\\net");
		fd.mkdir ();
		System.out.println ("Generated: src\\"+fd.getName ());
		
		fd=new File ("src\\net\\murat");
		fd.mkdir ();
		System.out.println ("Generated: src\\net\\"+fd.getName ());
		
		fd=new File ("src\\net\\murat\\"+APPNAME+"");
		fd.mkdir ();
		System.out.println ("Generated: src\\net\\murat\\"+fd.getName ());
		
		return;
	}
	
	final
	private static String getAppName ()
	{
		char chr=APPNAME.charAt (0x0000);
		String fharf=Character.toString (chr);
		fharf=fharf.toUpperCase ();
		
		int alen=APPNAME.length ();
		
		StringBuffer sb=new StringBuffer ();
		
		sb.append (fharf);
		
		for (int i=1; i<alen; i++)
		{
			sb.append (APPNAME.charAt (i));
		}
		
		return sb.toString ();
	}
	
	//1.
	final
	private static void genManifest ()
	throws IOException
	{
		File fd=new File ("AndroidManifest.xml");
		
		if (fd.exists ())
		{
			System.out.println ("Skipping existing file: "+fd.getName ()+"");
			return;
		}
		
		//InputStream fis=new FileInputStream (fd);
		//Reader isr=new InputStreamReader (fis, "UTF-8");
		//BufferedReader br=new BufferedReader (isr);
		
		StringBuffer sb=new StringBuffer ();
		
		//String line=null;
		
		//while ( (line=br.readLine ()) != null )
		//{
			sb.append ("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
			sb.append ("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n");
			sb.append ("\t\tpackage=\"net.murat."+APPNAME+"\"\n");
			sb.append ("\t\tandroid:versionCode=\"1\"\n");
			sb.append ("\t\tandroid:versionName=\"1.0\">\n");
			sb.append ("\n");
			sb.append ("\t<uses-permission android:name=\"android.permission.READ_EXTERNAL_STORAGE\" />\n");
			sb.append ("\t<uses-permission android:name=\"android.permission.WRITE_EXTERNAL_STORAGE\" />\n");
			sb.append ("\n");
			sb.append ("\t<uses-sdk android:minSdkVersion=\"29\" />\n");
			sb.append ("\n");
			sb.append ("\t<application android:icon=\"@drawable/"+APPNAME+"logo"+"\"\n");
			sb.append ("\t\t\t\tandroid:label=\"@string/program_name\"\n");
			sb.append ("\t\t\t\tandroid:requestLegacyExternalStorage=\"true\">\n");
			sb.append ("\n");
			sb.append ("\t\t<activity android:name=\"net.murat."+APPNAME+"."+(getAppName ())+"\"\n");
	        sb.append ("\t\t\t\t\tandroid:label=\"@string/program_name\"\n");
			sb.append ("\t\t\t\t\tandroid:screenOrientation=\"portrait\"> <!-- landscape | portrait with lowerCase-->\n");
			sb.append ("\n");
			sb.append ("\t\t\t<intent-filter>\n");
			sb.append ("\t\t\t\t<action android:name=\"android.intent.action.MAIN\" />\n");
			sb.append ("\t\t\t\t<category android:name=\"android.intent.category.LAUNCHER\" />\n");
			sb.append ("\t\t\t</intent-filter>\n");
			sb.append ("\n");
			sb.append ("\t\t</activity>\n");
			sb.append ("\n");
			sb.append ("\t</application>\n");
			sb.append ("\n");
			sb.append ("</manifest>");
		//}
		
		//br.close ();
		//isr.close ();
		//fis.close ();
		
		OutputStream fos=new FileOutputStream (fd);
		PrintStream ps=new PrintStream (fos, true, "UTF-8");
		
		ps.println (sb.toString ());
		
		ps.flush ();  ps.close ();
		fos.flush (); fos.close ();
		
		System.out.println ("\nGenerated: AndroidManifest.xml");
		
		return;
	}
	
	//2.
	final
	private static void genMake1 ()
	throws IOException
	{
		File fd=new File ("make1app.bat");
		//InputStream fis=new FileInputStream (fd);
		//Reader isr=new InputStreamReader (fis, "UTF-8");
		//BufferedReader br=new BufferedReader (isr);
		
		StringBuffer sb=new StringBuffer ();
		
		//String line=null;
		
		//while ( (line=br.readLine ()) != null )
		//{
			sb.append ("del /f /s /q bin\\*.apk\n");
			sb.append ("\n");
			sb.append ("javac arac\\LogoGen.java -d arac\n");
			sb.append ("\n");
			sb.append ("java -cp arac; LogoGen "+APPNAME+"\n");
			sb.append ("\n");
			sb.append ("\"C:\\Users\\murat\\AppData\\Local\\Android\\Sdk\\build-tools\\29.0.0\\aapt.exe\" package -v -f -m -S \"res\" -J \"src\" -M \"AndroidManifest.xml\" -I \"C:\\Users\\murat\\AppData\\Local\\Android\\Sdk\\platforms\\android-29\\android.jar\"\n");
			sb.append ("\n");
			sb.append ("\"C:\\Program Files\\Java\\jdk-1.8\\bin\\javac.exe\" -bootclasspath \"C:\\Users\\murat\\AppData\\Local\\Android\\Sdk\\platforms\\android-29\\android.jar\"; -sourcepath \"src\"; -cp \"C:\\Users\\murat\\AppData\\Local\\Android\\Sdk\\platforms\\android-29\\android.jar\";\"obj\"; -g:none -proc:none -nowarn -O -Xlint:all \"src\\net\\murat\\"+APPNAME+"\\"+(getAppName ())+".java\" -d \"obj\"\n");
			sb.append ("\n");
			sb.append ("\"C:\\Users\\murat\\AppData\\Local\\Android\\Sdk\\build-tools\\29.0.0\\dx.bat\" --dex --verbose --output=\"classes.dex\" \"obj\"");
		//}
		
		//br.close ();
		//isr.close ();
		//fis.close ();
		
		OutputStream fos=new FileOutputStream (fd);
		PrintStream ps=new PrintStream (fos, true, "UTF-8");
		
		ps.println (sb.toString ());
		
		ps.flush ();  ps.close ();
		fos.flush (); fos.close ();
		
		System.out.println ("Generated: make1app.bat");
		
		return;
	}
	
	//3.
	final
	private static void genMake2 ()
	throws IOException
	{
		File fd=new File ("make2app.bat");
		//InputStream fis=new FileInputStream (fd);
		//Reader isr=new InputStreamReader (fis, "UTF-8");
		//BufferedReader br=new BufferedReader (isr);
		
		StringBuffer sb=new StringBuffer ();
		
		//String line=null;
		
		//while ( (line=br.readLine ()) != null )
		//{
			sb.append ("\"C:\\Users\\murat\\AppData\\Local\\Android\\Sdk\\build-tools\\29.0.0\\aapt.exe\" package -v -f -M \"AndroidManifest.xml\" -A \"assets\" -S \"res\" -I \"C:\\Users\\murat\\AppData\\Local\\Android\\Sdk\\platforms\\android-29\\android.jar\" -F \"bin"+"\\"+(getAppName ())+".unsigned.apk\" \"bin\"\n");
			sb.append ("\n");
			sb.append ("\"C:\\Users\\murat\\AppData\\Local\\Android\\Sdk\\build-tools\\29.0.0\\aapt.exe\" add \"bin"+"\\"+(getAppName ())+".unsigned.apk\" \"classes.dex\"\n");
			sb.append ("\n");
			sb.append ("\"C:\\Program Files\\java\\jdk-1.8\\bin\\jarsigner.exe\" -keystore "+(getAppName ())+".keystore -storepass 5EmrE432 -keypass 5EmrE432 -signedjar \"bin"+"\\"+(getAppName ())+".signed.apk\" \"bin\\"+(getAppName ())+".unsigned.apk\" "+(getAppName ())+".keystore\n");
			sb.append ("\n");
			sb.append ("\"C:\\Users\\murat\\AppData\\Local\\Android\\Sdk\\build-tools\\29.0.0\\zipalign.exe\" -v -f 4 \"bin"+"\\"+(getAppName ())+".signed.apk\" "+"\"bin"+"\\"+(getAppName ())+".apk\"\n");
			sb.append ("\n");
			sb.append ("del /f /s /q *.keystore.old\n");
			sb.append ("del /f /s /q classes.dex\n");
			sb.append ("del /f /s /q bin\\*.signed.*\n");
			sb.append ("del /f /s /q bin\\*.unsigned.*\n");
			sb.append ("del /f /s /q src\\net\\murat\\"+APPNAME+"\\R.java\n");
			sb.append ("del /f /s /q obj\\*\n");
			sb.append ("rmdir /s /q obj\\net\n");
			sb.append ("\n");
			sb.append ("dir bin");
		//}
		
		//br.close ();
		//isr.close ();
		//fis.close ();
		
		OutputStream fos=new FileOutputStream (fd);
		PrintStream ps=new PrintStream (fos, true, "UTF-8");
		
		ps.println (sb.toString ());
		
		ps.flush ();  ps.close ();
		fos.flush (); fos.close ();
		
		System.out.println ("Generated: make2app.bat");
		
		return;
	}
	
	//4.
	final
	private static void genKStore ()
	throws IOException
	{
		File fd=new File ("kstore.bat");
		//InputStream fis=new FileInputStream (fd);
		//Reader isr=new InputStreamReader (fis, "UTF-8");
		//BufferedReader br=new BufferedReader (isr);
		
		StringBuffer sb=new StringBuffer ();
		
		//String line=null;
		
		//while ( (line=br.readLine ()) != null )
		//{
			sb.append ("del /f /s /q *.keystore*\n");
			sb.append ("del /f /s /q classes.dex\n");
			sb.append ("del /f /s /q bin\\*.signed.*\n");
			sb.append ("del /f /s /q bin\\*.unsigned.*\n");
			sb.append ("del /f /s /q bin\\*.apk\n");
			sb.append ("del /f /s /q src\\net\\murat\\"+APPNAME+"\\R.java\n");
			sb.append ("del /f /s /q obj\\*\n");
			sb.append ("rmdir /s /q obj\\net\n");
			sb.append ("\n");
			sb.append ("\"C:\\Program Files\\java\\jdk-1.8\\bin\\keytool.exe\" -genkey -alias "+(getAppName ())+".keystore -keyalg RSA -validity 100000 -dname \"CN=Murat inan, OU=Freelance, O=Traductor, S=Sivas, C=TR\" -keystore "+(getAppName ())+".keystore -storepass 5EmrE432 -keypass 5EmrE432\n");
			sb.append ("\n");
			sb.append ("\"C:\\Program Files\\java\\jdk-1.8\\bin\\keytool.exe\" -importkeystore -srckeystore "+(getAppName ())+".keystore -destkeystore "+(getAppName ())+".keystore -deststoretype pkcs12");
		//}
		
		//br.close ();
		//isr.close ();
		//fis.close ();
		
		OutputStream fos=new FileOutputStream (fd);
		PrintStream ps=new PrintStream (fos, true, "UTF-8");
		
		ps.println (sb.toString ());
		
		ps.flush ();  ps.close ();
		fos.flush (); fos.close ();
		
		System.out.println ("Generated: kstore.bat");
		
		return;
	}
	
	//5.
	final
	private static void genClean ()
	throws IOException
	{
		File fd=new File ("clean.bat");
		//InputStream fis=new FileInputStream (fd);
		//Reader isr=new InputStreamReader (fis, "UTF-8");
		//BufferedReader br=new BufferedReader (isr);
		
		StringBuffer sb=new StringBuffer ();
		
		//String line=null;
		
		//while ( (line=br.readLine ()) != null )
		//{
			sb.append ("del /f /s /q *.keystore.old\n");
			sb.append ("del /f /s /q classes.dex\n");
			sb.append ("del /f /s /q bin\\*.signed.*\n");
			sb.append ("del /f /s /q bin\\*.unsigned.*\n");
			sb.append ("del /f /s /q obj\\*\n");
			sb.append ("del /f /s /q src\\net\\murat\\"+APPNAME+"\\R.java\n");
			sb.append ("rmdir /s /q obj\\net");
		//}
		
		//br.close ();
		//isr.close ();
		//fis.close ();
		
		OutputStream fos=new FileOutputStream (fd);
		PrintStream ps=new PrintStream (fos, true, "UTF-8");
		
		ps.println (sb.toString ());
		
		ps.flush ();  ps.close ();
		fos.flush (); fos.close ();
		
		System.out.println ("Generated: clean.bat");
		
		return;
	}
	
	//6.
	final
	private static void genControl ()
	throws IOException
	{
		File fd=new File ("control.bat");
		//InputStream fis=new FileInputStream (fd);
		//Reader isr=new InputStreamReader (fis, "UTF-8");
		//BufferedReader br=new BufferedReader (isr);
		
		StringBuffer sb=new StringBuffer ();
		
		//String line=null;
		
		//while ( (line=br.readLine ()) != null )
		//{
		sb.append ("del /f /s /q *.keystore*\n");
		sb.append ("del /f /s /q classes.dex\n");
		sb.append ("del /f /s /q bin\\*.signed.*\n");
		sb.append ("del /f /s /q bin\\*.unsigned.*\n");
		sb.append ("del /f /s /q src\\net\\murat\\"+APPNAME+"\\R.java\n");
		sb.append ("rmdir /s /q obj\\net\n");
		sb.append ("\n");
		sb.append ("\"C:\\Users\\murat\\AppData\\Local\\Android\\Sdk\\build-tools\\29.0.0\\aapt.exe\" package -v -f -m -S \"res\" -J \"src\" -M \"AndroidManifest.xml\" -I \"C:\\Users\\murat\\AppData\\Local\\Android\\Sdk\\platforms\\android-29\\android.jar\"\n");
		sb.append ("\n");
		sb.append ("\"C:\\Program Files\\Java\\jdk-1.8\\bin\\javac.exe\" -bootclasspath \"C:\\Users\\murat\\AppData\\Local\\Android\\Sdk\\platforms\\android-29\\android.jar\"; -sourcepath \"src\"; -cp \"C:\\Users\\murat\\AppData\\Local\\Android\\Sdk\\platforms\\android-29\\android.jar\";\"obj\"; -g:none -proc:none -nowarn -O -Xlint:all \"src\\net\\murat\\"+APPNAME+"\\"+(getAppName ())+".java\" -d \"obj\"\n");
		sb.append ("\n");
		sb.append ("del /f /s /q *.keystore*\n");
		sb.append ("del /f /s /q classes.dex\n");
		sb.append ("del /f /s /q bin\\*.signed.*\n");
		sb.append ("del /f /s /q bin\\*.unsigned.*\n");
		sb.append ("del /f /s /q obj\\*\n");
		sb.append ("del /f /s /q src\\net\\murat\\"+APPNAME+"\\R.java\n");
		sb.append ("rmdir /s /q obj\\net");
		//}
		
		//br.close ();
		//isr.close ();
		//fis.close ();
		
		OutputStream fos=new FileOutputStream (fd);
		PrintStream ps=new PrintStream (fos, true, "UTF-8");
		
		ps.println (sb.toString ());
		
		ps.flush ();  ps.close ();
		fos.flush (); fos.close ();
		
		System.out.println ("Generated: control.bat");
		
		return;
	}
	
	//7.
	final
	private static void genHowto ()
	throws IOException
	{
		File fd=new File ("howto_win.txt");

		StringBuffer sb=new StringBuffer ();
		
		sb.append ("Process orden:\n");
		sb.append ("\n");
		sb.append ("1. Run kstore bat file.\nWhen there is keystore file do not run this script again.\n");
		sb.append ("\n");
		sb.append ("2. Run make1app bat file.\n");
		sb.append ("\n");
		sb.append ("3. Run make2app bat file.");
		
		OutputStream fos=new FileOutputStream (fd);
		PrintStream ps=new PrintStream (fos, true, "UTF-8");
		
		ps.println (sb.toString ());
		
		ps.flush ();  ps.close ();
		fos.flush (); fos.close ();
		
		System.out.println ("Generated: howto_win.txt");
		
		return;
	}

	//8.
	final
	private static void genLogoGen ()
	throws IOException
	{
		File fd=new File ("arac\\LogoGen.java");
		
		if (fd.exists ())
		{
			System.out.println ("Skipping existing file: arac\\"+fd.getName ()+"");
			return;
		}
		
		StringBuffer sb=new StringBuffer ();
		
		/////
		sb.append ("import java.io.*;\n");
		sb.append ("\n");
		sb.append ("import java.awt.*;\n");
		sb.append ("\n");
		sb.append ("import java.awt.image.BufferedImage;\n");
		sb.append ("\n");
		sb.append ("import javax.imageio.ImageIO;\n");
		sb.append ("\n");
		sb.append ("final\n");
		sb.append ("public class LogoGen\n");
		sb.append ("{\n");
		sb.append ("\n");
		sb.append ("\tprivate LogoGen ()\n");
		sb.append ("\t{\n");
		sb.append ("\t\tsuper ();\n");
		sb.append ("\t}\n");
		sb.append ("\n");
		sb.append ("\tpublic String toString ()\n");
		sb.append ("\t{\n");
		sb.append ("\t\treturn \"\";\n");
		sb.append ("\t}\n");
		sb.append ("\n");
		sb.append ("\tfinal\n");
		sb.append ("\tprivate static void logo (String name)\n");
		sb.append ("\tthrows IOException\n");
		sb.append ("\t{\n");
		sb.append ("\t\tBufferedImage bi=new BufferedImage (144, 144, 1);\n");
		sb.append ("\t\tGraphics2D g2d=bi.createGraphics ();\n");
		sb.append ("\n");
		sb.append ("\t\tg2d.setBackground (Color.white);\n");
		sb.append ("\t\tg2d.clearRect (0, 0, 144, 144);\n");
		sb.append ("\n");
		sb.append ("\t\tg2d.setFont (new Font (\"Verdana\", 3, 60));\n");
		sb.append ("\t\tg2d.setColor (randomColor ());\n");
		sb.append ("\n");
		sb.append ("\t\tStringBuffer sb=new StringBuffer ();\n");
		sb.append ("\n");
		sb.append ("\t\tfinal int namelen=name.length ();\n");
		sb.append ("\n");
		sb.append ("\t\tif (namelen == 1)\n");
		sb.append ("\t\t{\n");
		sb.append ("\t\t\tsb.append (name.charAt (0));\n");
		sb.append ("\t\t}\n");
		sb.append ("\t\telse if (namelen > 1)\n");
		sb.append ("\t\t{\n");
		sb.append ("\t\t\tsb.append (name.charAt (0));\n");
		sb.append ("\t\t\tsb.append (name.charAt (1));\n");
		sb.append ("\t\t}\n");
		sb.append ("\t\telse\n");
		sb.append ("\t\t{\n");
		sb.append ("\t\t\tsb.append (name);\n");
		sb.append ("\t\t}\n");
		sb.append ("\n");
		sb.append ("\t\tname=sb.toString ();\n");
		sb.append ("\t\tname=name.toUpperCase ();\n");
		sb.append ("\n");
		sb.append ("\t\tint xc=70-(((g2d.getFontMetrics ()).stringWidth (name))/2);\n");
		sb.append ("\t\tint yc=90;//(((g2d.getFontMetrics ()).getHeight ())/2);\n");
		sb.append ("\n");
		sb.append ("\t\tg2d.drawString (name, xc, yc);\n");
		sb.append ("\n");
		sb.append ("\t\tg2d.setColor (randomColor ());\n");
		sb.append ("\t\tg2d.setStroke (new BasicStroke (3F));\n");
		sb.append ("\t\tg2d.drawOval (1, 1, 140, 140);\n");
		sb.append ("\n");
		sb.append ("\t\tFile fl=new File (\"res\\\\drawable\\\\"+APPNAME+"logo.png\");\n");
		sb.append ("\n");
		sb.append ("\t\tImageIO.write (bi, \"PNG\", fl);\n");
		sb.append ("\n");
		sb.append ("\t\tSystem.out.println (\"Generated: res\\\\drawable\\\\\"+fl.getName ()+\"\");\n");
		sb.append ("\n");
		sb.append ("\t\treturn;\n");
		sb.append ("\t}\n");
		sb.append ("\n");
		sb.append ("\tfinal\n");
		sb.append ("\tprivate static Color randomColor ()\n");
		sb.append ("\t{\n");
		sb.append ("\t\tjava.util.Random random=new java.util.Random ();\n");
		sb.append ("\n");
		sb.append ("\t\tint r=random.nextInt (255);\n");
		sb.append ("\t\tint g=random.nextInt (255);\n");
		sb.append ("\t\tint b=random.nextInt (255);\n");
		sb.append ("\n");
		sb.append ("\t\tif ( (r>200) && (g>200) && (b>200) )\n");
		sb.append ("\t\t{\n");
		sb.append ("\t\t\tg=0x0000;\n");
		sb.append ("\t\t}\n");
		sb.append ("\n");
		sb.append ("\t\tColor col=new Color (r, g, b);\n");
		sb.append ("\n");
		sb.append ("\t\treturn col;\n");
		sb.append ("\t}\n");
		sb.append ("\n");
		sb.append ("\tfinal\n");
		sb.append ("\tpublic static void main (final String [] args)\n");
		sb.append ("\t{\n");
		sb.append ("\t\tif (args.length < 1)\n");
        sb.append ("\t\t{\n");
        sb.append ("\t\t\tSystem.out.println (\"\\nUsage:\tjava -cp arac; LogoGen [<newPackageName>]\");\n");
        sb.append ("\t\t\tSystem.out.println (\"Example:\tjava -cp arac; LogoGen atcero\");\n");
        sb.append ("\t\t\tSystem.exit (-1);\n");
        sb.append ("\t\t}\n");
		sb.append ("\n");
        sb.append ("\t\tString pn=args [0].toLowerCase ();\n");
		sb.append ("\n");
		sb.append ("\t\ttry\n");
		sb.append ("\t\t{\n");
	    sb.append ("\t\t\tlogo (pn);\n");
		sb.append ("\t\t}\n");
		sb.append ("\t\tcatch (IOException ioe)\n");
		sb.append ("\t\t{\n");
	    sb.append ("\t\t\tioe.printStackTrace ();\n");
	    sb.append ("\t\t\tSystem.exit (-1);\n");
		sb.append ("\t\t}\n");
		sb.append ("\t}\n");
		sb.append ("\n");
		sb.append ("}");
		/////////
		OutputStream fos=new FileOutputStream (fd);
		PrintStream ps=new PrintStream (fos, true, "UTF-8");
		
		ps.println (sb.toString ());
		
		ps.flush ();  ps.close ();
		fos.flush (); fos.close ();
		
		System.out.println ("Generated: arac\\LogoGen.java");
		
		return;
	}
	
	//9.
	final
	private static void genIntegers ()
	throws IOException
	{
		File fd=new File ("res\\values\\integers.xml");
		
		if (fd.exists ())
		{
			System.out.println ("Skipping existing file: res\\values\\"+fd.getName ()+"");
			return;
		}
		
		StringBuffer sb=new StringBuffer ();

		sb.append ("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
		sb.append ("<resources>\n");
		sb.append ("\t<integer name=\"google_play_services_version\">8487000</integer>\n");
		sb.append ("\n");
		sb.append ("\t<integer name=\"bir\">1</integer>\n");
		sb.append ("\t<integer name=\"iki\">2</integer>\n");
		sb.append ("\t<integer name=\"ucc\">3</integer>\n");
		sb.append ("</resources>");

		OutputStream fos=new FileOutputStream (fd);
		PrintStream ps=new PrintStream (fos, true, "UTF-8");
		
		ps.println (sb.toString ());
		
		ps.flush ();  ps.close ();
		fos.flush (); fos.close ();
		
		System.out.println ("Generated: res\\values\\integers.xml");
		
		return;
	}
	
	//10.
	final
	private static void genColors ()
	throws IOException
	{
		File fd=new File ("res\\values\\colors.xml");
		
		if (fd.exists ())
		{
			System.out.println ("Skipping existing file: res\\values\\"+fd.getName ()+"");
			return;
		}
		
		StringBuffer sb=new StringBuffer ();

		sb.append ("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
		sb.append ("<resources>\n");
		sb.append ("\t<color name=\"whtA\">#FFFFFF</color>\n");
		sb.append ("\t<color name=\"whtB\">#EEEEEE</color>\n");
		sb.append ("\t<color name=\"redA\">#FF0000</color>\n");
		sb.append ("\t<color name=\"redB\">#AA0000</color>\n");
		sb.append ("\t<color name=\"grnA\">#00FF00</color>\n");
		sb.append ("\t<color name=\"grnB\">#00DD00</color>\n");
		sb.append ("\t<color name=\"bluA\">#0000FF</color>\n");
		sb.append ("\t<color name=\"bluB\">#0000AA</color>\n");
		sb.append ("\t<color name=\"bluC\">#3333DC</color>\n");
		sb.append ("\t<color name=\"blac\">#000000</color>\n");
		sb.append ("\t<color name=\"blad\">#777777</color>\n");
		sb.append ("\t<color name=\"ylwA\">#FFFF00</color>\n");
		sb.append ("\t<color name=\"ylwB\">#FFFFE0</color>\n");
		sb.append ("\t<color name=\"ylwC\">#EEEEA0</color>\n");
		sb.append ("\t<color name=\"sldA\">#0543FF</color>\n");
		sb.append ("\t<color name=\"bgsc\">#FFCC33</color>\n");
		sb.append ("\t<color name=\"bgsd\">#FFCC66</color>\n");
		sb.append ("\t<color name=\"trtr\">#909090</color>\n");
		sb.append ("\t<color name=\"cucu\">#535353</color>\n");
		sb.append ("</resources>");

		OutputStream fos=new FileOutputStream (fd);
		PrintStream ps=new PrintStream (fos, true, "UTF-8");
		
		ps.println (sb.toString ());
		
		ps.flush ();  ps.close ();
		fos.flush (); fos.close ();
		
		System.out.println ("Generated: res\\values\\colors.xml");
		
		return;
	}
	
	//11.
	final
	private static void genDimensions ()
	throws IOException
	{
		File fd=new File ("res\\values\\dimens.xml");
		
		if (fd.exists ())
		{
			System.out.println ("Skipping existing file: res\\values\\"+fd.getName ()+"");
			return;
		}
		
		StringBuffer sb=new StringBuffer ();
		
		sb.append ("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
		sb.append ("<resources>\n");
		sb.append ("\t<dimen name=\"cero\">0px</dimen>\n");
		sb.append ("\t<dimen name=\"unoo\">1px</dimen>\n");
		sb.append ("\t<dimen name=\"doss\">2px</dimen>\n");
		sb.append ("\t<dimen name=\"tres\">3px</dimen>\n");
		sb.append ("\t<dimen name=\"cuat\">4px</dimen>\n");
		sb.append ("\t<dimen name=\"mrgs\">4dp</dimen>\n");
		sb.append ("\t<dimen name=\"cinc\">5pt</dimen>\n");
		sb.append ("\t<dimen name=\"minc\">5px</dimen>\n");
		sb.append ("\t<dimen name=\"seis\">6pt</dimen>\n");
		sb.append ("\t<dimen name=\"siet\">7pt</dimen>\n");
		sb.append ("\t<dimen name=\"ocho\">8pt</dimen>\n");
		sb.append ("\t<dimen name=\"mrgn\">8dp</dimen>\n");
		sb.append ("\t<dimen name=\"nuev\">9pt</dimen>\n");
		sb.append ("\t<dimen name=\"diez\">10pt</dimen>\n");
		sb.append ("\t<dimen name=\"mtsz\">20sp</dimen>\n");
		sb.append ("\t<dimen name=\"doce\">12pt</dimen>\n");
		sb.append ("\t<dimen name=\"diss\">16pt</dimen>\n");
		sb.append ("\t<dimen name=\"vedo\">15pt</dimen>\n");
		sb.append ("\t<dimen name=\"okzo\">18pt</dimen>\n");
		sb.append ("\t<dimen name=\"vtmr\">20pt</dimen>\n");
		sb.append ("\t<dimen name=\"novn\">90pt</dimen>\n");
		sb.append ("\t<dimen name=\"vtre\">23pt</dimen>\n");
		sb.append ("\t<dimen name=\"vctr\">24pt</dimen>\n");
		sb.append ("\t<dimen name=\"tsie\">37pt</dimen>\n");
		sb.append ("\t<dimen name=\"kirk\">40dp</dimen>\n");
		sb.append ("\t<dimen name=\"elli\">50pt</dimen>\n");
		sb.append ("\t<dimen name=\"altm\">60pt</dimen>\n");
		sb.append ("\t<dimen name=\"minw\">256px</dimen>\n");
		sb.append ("\t<dimen name=\"minh\">156px</dimen>\n");
		sb.append ("\t<dimen name=\"maxw\">256px</dimen>\n");
		sb.append ("\t<dimen name=\"maxh\">75px</dimen>\n");
		sb.append ("\t<dimen name=\"ytbs\">240dp</dimen>\n");
		sb.append ("\t<dimen name=\"schg\">480dp</dimen>\n");
		sb.append ("</resources>");

		OutputStream fos=new FileOutputStream (fd);
		PrintStream ps=new PrintStream (fos, true, "UTF-8");
		
		ps.println (sb.toString ());
		
		ps.flush ();  ps.close ();
		fos.flush (); fos.close ();
		
		System.out.println ("Generated: res\\values\\dimens.xml");
		
		return;
	}
	
	//12.
	final
	private static void genStrings ()
	throws IOException
	{
		File fd=new File ("res\\values\\strings.xml");
		
		if (fd.exists ())
		{
			System.out.println ("Skipping existing file: res\\values\\"+fd.getName ()+"");
			return;
		}
		
		StringBuffer sb=new StringBuffer ();

		sb.append ("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
		sb.append ("<resources>\n");
		sb.append ("\t<string name=\"program_name\">"+(getAppName ())+"</string>\n");
		sb.append ("\t<string name=\"src\">src.txt[in Download]</string>\n");
		sb.append ("\t<string name=\"show\">Show</string>\n");
		sb.append ("\t<string name=\"list\">List</string>\n");
		sb.append ("\t<string name=\"exit\">Exit</string>\n");
		sb.append ("\t<string name=\"stop\">Stop</string>\n");		
		sb.append ("</resources>");

		OutputStream fos=new FileOutputStream (fd);
		PrintStream ps=new PrintStream (fos, true, "UTF-8");
		
		ps.println (sb.toString ());
		
		ps.flush ();  ps.close ();
		fos.flush (); fos.close ();
		
		System.out.println ("Generated: res\\values\\strings.xml");
		
		return;
	}
	
	//13.
	final
	private static void genLayout ()
	throws IOException
	{
		File fd=new File ("res\\layout\\main.xml");

		if (fd.exists ())
		{
			System.out.println ("Skipping existing file: res\\layout\\"+fd.getName ()+"");
			return;
		}
		
		StringBuffer sb=new StringBuffer ();
		
		///////
		sb.append ("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
		sb.append ("<LinearLayout\n");
		sb.append ("\txmlns:android=\"http://schemas.android.com/apk/res/android\"\n");
		sb.append ("\tandroid:orientation=\"vertical\"\n");
		sb.append ("\tandroid:layout_width=\"fill_parent\"\n");
		sb.append ("\tandroid:layout_height=\"fill_parent\"\n");
		sb.append ("\tandroid:background=\"@drawable/bga\">\n");
		sb.append ("\n");
		sb.append ("\t\t<LinearLayout\n");
	    sb.append ("\t\t\txmlns:android=\"http://schemas.android.com/apk/res/android\"\n");
	    sb.append ("\t\t\tandroid:orientation=\"horizontal\"\n");
	    sb.append ("\t\t\tandroid:layout_width=\"fill_parent\"\n");
	    sb.append ("\t\t\tandroid:layout_height=\"wrap_content\">\n");
		sb.append ("\t\t\t\t<Button\n");
	    sb.append ("\t\t\t\t\tandroid:id=\"@+id/show_button\"\n");
	    sb.append ("\t\t\t\t\tandroid:layout_width=\"wrap_content\"\n");
	    sb.append ("\t\t\t\t\tandroid:layout_height=\"wrap_content\"\n");
	    sb.append ("\t\t\t\t\tandroid:layout_weight=\"1\"\n");
	    sb.append ("\t\t\t\t\tandroid:focusable=\"true\"\n");
	    sb.append ("\t\t\t\t\tandroid:layout_marginTop=\"@dimen/mrgn\"\n");
	    sb.append ("\t\t\t\t\tandroid:layout_marginBottom=\"@dimen/mrgs\"\n");
		sb.append ("\t\t\t\t\tandroid:layout_marginLeft=\"@dimen/mrgn\"\n");
        sb.append ("\t\t\t\t\tandroid:layout_marginRight=\"@dimen/mrgn\"\n");
	    sb.append ("\t\t\t\t\tandroid:background=\"@drawable/bshape\"\n");
	    sb.append ("\t\t\t\t\tandroid:textColor=\"@drawable/rshape\"\n");
	    sb.append ("\t\t\t\t\tandroid:textSize=\"@dimen/mtsz\"\n");
	    sb.append ("\t\t\t\t\tandroid:text=\"@string/show\">\n");
		sb.append ("\t\t\t\t</Button>\n");
		sb.append ("\n");
		sb.append ("\t\t\t\t<Button\n");
	    sb.append ("\t\t\t\t\tandroid:id=\"@+id/list_button\"\n");
	    sb.append ("\t\t\t\t\tandroid:layout_width=\"wrap_content\"\n");
	    sb.append ("\t\t\t\t\tandroid:layout_height=\"wrap_content\"\n");
	    sb.append ("\t\t\t\t\tandroid:layout_weight=\"1\"\n");
	    sb.append ("\t\t\t\t\tandroid:focusable=\"true\"\n");
	    sb.append ("\t\t\t\t\tandroid:layout_marginTop=\"@dimen/mrgn\"\n");
	    sb.append ("\t\t\t\t\tandroid:layout_marginBottom=\"@dimen/mrgs\"\n");
		sb.append ("\t\t\t\t\tandroid:layout_marginLeft=\"@dimen/mrgn\"\n");
        sb.append ("\t\t\t\t\tandroid:layout_marginRight=\"@dimen/mrgn\"\n");
	    sb.append ("\t\t\t\t\tandroid:background=\"@drawable/bshape\"\n");
	    sb.append ("\t\t\t\t\tandroid:textColor=\"@drawable/rshape\"\n");
	    sb.append ("\t\t\t\t\tandroid:textSize=\"@dimen/mtsz\"\n");
	    sb.append ("\t\t\t\t\tandroid:text=\"@string/list\">\n");
		sb.append ("\t\t\t\t</Button>\n");
		sb.append ("\t\t</LinearLayout>\n");
		sb.append ("\n");
		sb.append ("\t\t<EditText\n");
    	sb.append ("\t\t\tandroid:id=\"@+id/src_tv\"\n");
    	sb.append ("\t\t\tandroid:layout_width=\"fill_parent\"\n");
    	sb.append ("\t\t\tandroid:layout_height=\"wrap_content\"\n");
        sb.append ("\t\t\tandroid:layout_weight=\"1\"\n");
        sb.append ("\t\t\tandroid:layout_marginTop=\"@dimen/mrgn\"\n");
        sb.append ("\t\t\tandroid:layout_marginBottom=\"@dimen/mrgn\"\n");
	    sb.append ("\t\t\tandroid:layout_marginLeft=\"@dimen/mrgn\"\n");
        sb.append ("\t\t\tandroid:layout_marginRight=\"@dimen/mrgn\"\n");
	    sb.append ("\t\t\tandroid:layout_centerHorizontal=\"true\"\n");
	    sb.append ("\t\t\tandroid:background=\"@drawable/border\"\n");
	    sb.append ("\t\t\tandroid:gravity=\"center\"\n");
		sb.append ("\t\t\tandroid:textSize=\"@dimen/mtsz\"\n");
        sb.append ("\t\t\tandroid:textColor=\"@color/bluB\"\n");
        sb.append ("\t\t\tandroid:textColorHint=\"@color/grnB\"\n");
        sb.append ("\t\t\tandroid:text=\"@string/src\">\n");
        sb.append ("\t\t</EditText>\n");
		sb.append ("\n");
		sb.append ("\t\t<EditText\n");
    	sb.append ("\t\t\tandroid:id=\"@+id/tv\"\n");
    	sb.append ("\t\t\tandroid:layout_width=\"fill_parent\"\n");
    	sb.append ("\t\t\tandroid:layout_height=\"@dimen/schg\"\n");
        sb.append ("\t\t\tandroid:layout_weight=\"1\"\n");
        sb.append ("\t\t\tandroid:minHeight=\"@dimen/ytbs\"\n");
        sb.append ("\t\t\tandroid:layout_marginTop=\"@dimen/mrgn\"\n");
        sb.append ("\t\t\tandroid:layout_marginBottom=\"@dimen/mrgs\"\n");
	    sb.append ("\t\t\tandroid:layout_marginLeft=\"@dimen/mrgn\"\n");
        sb.append ("\t\t\tandroid:layout_marginRight=\"@dimen/mrgn\"\n");
	    sb.append ("\t\t\tandroid:layout_centerHorizontal=\"true\"\n");
	    sb.append ("\t\t\tandroid:background=\"@drawable/border\"\n");
	    sb.append ("\t\t\tandroid:editable=\"true\"\n");
	    sb.append ("\t\t\tandroid:gravity=\"center\"\n");
        sb.append ("\t\t\tandroid:textSize=\"@dimen/mtsz\"\n");
        sb.append ("\t\t\tandroid:textColor=\"@color/bluB\"\n");
        sb.append ("\t\t\tandroid:textColorHint=\"@color/grnB\">\n");
        sb.append ("\t\t</EditText>\n");
		sb.append ("\n");
		sb.append ("\t\t<LinearLayout\n");
	    sb.append ("\t\t\txmlns:android=\"http://schemas.android.com/apk/res/android\"\n");
	    sb.append ("\t\t\tandroid:orientation=\"horizontal\"\n");
	    sb.append ("\t\t\tandroid:layout_width=\"fill_parent\"\n");
	    sb.append ("\t\t\tandroid:layout_height=\"wrap_content\">\n");
		sb.append ("\t\t\t\t<Button\n");
	    sb.append ("\t\t\t\t\tandroid:id=\"@+id/exit_button\"\n");
	    sb.append ("\t\t\t\t\tandroid:layout_width=\"wrap_content\"\n");
	    sb.append ("\t\t\t\t\tandroid:layout_height=\"fill_parent\"\n");
	    sb.append ("\t\t\t\t\tandroid:layout_weight=\"1\"\n");
	    sb.append ("\t\t\t\t\tandroid:layout_marginTop=\"@dimen/mrgn\"\n");
	    sb.append ("\t\t\t\t\tandroid:layout_marginBottom=\"@dimen/mrgn\"\n");
		sb.append ("\t\t\t\t\tandroid:layout_marginLeft=\"@dimen/mrgn\"\n");
        sb.append ("\t\t\t\t\tandroid:layout_marginRight=\"@dimen/mrgn\"\n");
	    sb.append ("\t\t\t\t\tandroid:background=\"@drawable/bshape\"\n");
	    sb.append ("\t\t\t\t\tandroid:textColor=\"@drawable/rshape\"\n");
	    sb.append ("\t\t\t\t\tandroid:textSize=\"@dimen/mtsz\"\n");
	    sb.append ("\t\t\t\t\tandroid:text=\"@string/exit\">\n");
		sb.append ("\t\t\t\t</Button>\n");
		sb.append ("\n");
		sb.append ("\t\t\t\t<Button\n");
	    sb.append ("\t\t\t\t\tandroid:id=\"@+id/stop_button\"\n");
	    sb.append ("\t\t\t\t\tandroid:layout_width=\"wrap_content\"\n");
	    sb.append ("\t\t\t\t\tandroid:layout_height=\"fill_parent\"\n");
	    sb.append ("\t\t\t\t\tandroid:layout_weight=\"1\"\n");
	    sb.append ("\t\t\t\t\tandroid:layout_marginTop=\"@dimen/mrgn\"\n");
	    sb.append ("\t\t\t\t\tandroid:layout_marginBottom=\"@dimen/mrgn\"\n");
		sb.append ("\t\t\t\t\tandroid:layout_marginLeft=\"@dimen/mrgn\"\n");
        sb.append ("\t\t\t\t\tandroid:layout_marginRight=\"@dimen/mrgn\"\n");
	    sb.append ("\t\t\t\t\tandroid:background=\"@drawable/bshape\"\n");
	    sb.append ("\t\t\t\t\tandroid:textColor=\"@drawable/rshape\"\n");
	    sb.append ("\t\t\t\t\tandroid:textSize=\"@dimen/mtsz\"\n");
	    sb.append ("\t\t\t\t\tandroid:text=\"@string/stop\">\n");
		sb.append ("\t\t\t\t</Button>\n");
		sb.append ("\t\t</LinearLayout>\n");
		sb.append ("</LinearLayout>");
		///////////
		
		OutputStream fos=new FileOutputStream (fd);
		PrintStream ps=new PrintStream (fos, true, "UTF-8");
		
		ps.println (sb.toString ());
		
		ps.flush ();  ps.close ();
		fos.flush (); fos.close ();
		
		System.out.println ("Generated: res\\layout\\main.xml");
		
		return;
	}
	
	//14.
	final
	private static void genBGA ()
	throws IOException
	{
		File fd=new File ("res\\drawable\\bga.xml");
		
		if (fd.exists ())
		{
			System.out.println ("Skipping existing file: res\\drawable\\"+fd.getName ()+"");
			return;
		}
		
		StringBuffer sb=new StringBuffer ();
		////
		sb.append ("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
		sb.append ("\n");
		sb.append ("<shape\n");
		sb.append ("\txmlns:android=\"http://schemas.android.com/apk/res/android\"\n");
		sb.append ("\tandroid:shape=\"rectangle\">\n");
		sb.append ("\n");
		sb.append ("\t<gradient\n");
		sb.append ("\t\tandroid:startColor=\"@color/bgsc\"\n");
		sb.append ("\t\tandroid:centerColor=\"@color/bgsd\"\n");
		sb.append ("\t\tandroid:endColor=\"@color/bgsc\" />\n");
		sb.append ("\n");
		sb.append ("</shape>");
		/////
		OutputStream fos=new FileOutputStream (fd);
		PrintStream ps=new PrintStream (fos, true, "UTF-8");
		
		ps.println (sb.toString ());
		
		ps.flush ();  ps.close ();
		fos.flush (); fos.close ();
		
		System.out.println ("Generated: res\\drawable\\bga.xml");
		
		return;
	}
	
	//15.
	final
	private static void genBorder ()
	throws IOException
	{
		File fd=new File ("res\\drawable\\border.xml");

		if (fd.exists ())
		{
			System.out.println ("Skipping existing file: res\\drawable\\"+fd.getName ()+"");
			return;
		}
		
		StringBuffer sb=new StringBuffer ();

		//////
		sb.append ("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
		sb.append ("<shape\n");
		sb.append ("\txmlns:android=\"http://schemas.android.com/apk/res/android\"\n");
		sb.append ("\tandroid:shape=\"rectangle\">\n");
		sb.append ("\n");
		sb.append ("\t<gradient android:startColor=\"@color/whtA\"\n");
		sb.append ("\t\tandroid:centerColor=\"@color/whtB\"\n");
		sb.append ("\t\tandroid:endColor=\"@color/ylwB\" />\n");
		sb.append ("\n");
		sb.append ("\t<corners android:radius=\"@dimen/diez\" />\n");
		sb.append ("\n");
		sb.append ("\t<stroke\n");
		sb.append ("\t\tandroid:width=\"@dimen/doss\"\n"); 
		sb.append ("\t\tandroid:color=\"@color/redA\"/>\n");
		sb.append ("</shape>");
		//////
		
		OutputStream fos=new FileOutputStream (fd);
		PrintStream ps=new PrintStream (fos, true, "UTF-8");
		
		ps.println (sb.toString ());
		
		ps.flush ();  ps.close ();
		fos.flush (); fos.close ();
		
		System.out.println ("Generated: res\\drawable\\border.xml");
		
		return;
	}
	
	//16.
	final
	private static void genBShape ()
	throws IOException
	{
		File fd=new File ("res\\drawable\\bshape.xml");
		
		if (fd.exists ())
		{
			System.out.println ("Skipping existing file: res\\drawable\\"+fd.getName ()+"");
			return;
		}
		
		StringBuffer sb=new StringBuffer ();

		///////////
		sb.append ("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
		sb.append ("<selector xmlns:android=\"http://schemas.android.com/apk/res/android\">\n");
		sb.append ("\t<item android:state_pressed=\"true\">\n");
        sb.append ("\t\t<shape android:shape=\"rectangle\">\n");
		sb.append ("\t\t\t<corners android:radius=\"@dimen/kirk\" />\n");
		sb.append ("\t\t\t<stroke android:width=\"@dimen/unoo\"\n");
		sb.append ("\t\t\t\tandroid:color=\"@color/bluB\" />\n");
		sb.append ("\t\t\t<solid android:color=\"@color/sldA\" />\n");
        sb.append ("\t\t</shape>\n");
		sb.append ("\t</item>\n");
		sb.append ("\n");
		sb.append ("\t<item>\n");
        sb.append ("\t\t<shape android:shape=\"rectangle\">\n");
        sb.append ("\t\t\t<corners android:radius=\"@dimen/kirk\" />\n");
		sb.append ("\t\t\t<stroke android:width=\"@dimen/unoo\"\n");
		sb.append ("\t\t\t\tandroid:color=\"@color/bluB\" />\n");
        sb.append ("\t\t\t<solid android:color=\"@color/sldA\" />\n");
        sb.append ("\t\t</shape>\n");
		sb.append ("\t</item>\n");
		sb.append ("</selector>");
		//////////
		
		OutputStream fos=new FileOutputStream (fd);
		PrintStream ps=new PrintStream (fos, true, "UTF-8");
		
		ps.println (sb.toString ());
		
		ps.flush ();  ps.close ();
		fos.flush (); fos.close ();
		
		System.out.println ("Generated: res\\drawable\\bshape.xml");
		
		return;
	}
	
	//17.
	final
	private static void genRShape ()
	throws IOException
	{
		File fd=new File ("res\\drawable\\rshape.xml");
		
		if (fd.exists ())
		{
			System.out.println ("Skipping existing file: res\\drawable\\"+fd.getName ()+"");
			return;
		}
		
		StringBuffer sb=new StringBuffer ();
		
		///////////
		sb.append ("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
		sb.append ("<selector xmlns:android=\"http://schemas.android.com/apk/res/android\">\n");
		sb.append ("\n");
		sb.append ("\t<item\n");
		sb.append ("\t\tandroid:state_pressed=\"true\"\n");
		sb.append ("\t\tandroid:color=\"@color/redA\" />\n");
		sb.append ("\n");
		sb.append ("\t<item\n");
		sb.append ("\t\tandroid:color=\"@color/whtA\" />\n");
		sb.append ("\n");
		sb.append ("</selector>");
		///////////

		OutputStream fos=new FileOutputStream (fd);
		PrintStream ps=new PrintStream (fos, true, "UTF-8");
		
		ps.println (sb.toString ());
		
		ps.flush ();  ps.close ();
		fos.flush (); fos.close ();
		
		System.out.println ("Generated: res\\drawable\\rshape.xml");
		
		return;
	}
	
	//18.
	final
	private static void genValues ()
	throws IOException
	{
		File fd=new File ("src\\net\\murat\\"+APPNAME+"\\Values.java");
		
		if (fd.exists ())
		{
			System.out.println ("Skipping existing file: "+fd.getAbsolutePath ()+"");
			return;
		}
		
		StringBuffer sb=new StringBuffer ();

		/////
		sb.append ("package net.murat."+APPNAME+";\n");
		sb.append ("\n");
		sb.append ("final\n");
		sb.append ("public class Values\n");
		sb.append ("\textends Object\n");
		sb.append ("{\n");
		sb.append ("\n");
		sb.append ("\tprivate Values ()\n");
		sb.append ("\t{\n");
		sb.append ("\t\tsuper ();\n");
		sb.append ("\t}\n");
		sb.append ("\n");
		sb.append ("\tpublic String toString ()\n");
		sb.append ("\t{\n");
		sb.append ("\t\treturn \"_\";\n");
		sb.append ("\t}\n");
		sb.append ("\n");
		sb.append ("\tfinal public static int CERO=0;\n");
		sb.append ("\tfinal public static int UNO=1;\n");
		sb.append ("\tfinal public static int DOS=2;\n");
		sb.append ("\tfinal public static int TRES=3;\n");
		sb.append ("\tfinal public static int DIEZ=10;\n");
		sb.append ("\tfinal public static int PLEN=16;\n");
		sb.append ("\tfinal public static int TREI=30;\n");
		sb.append ("\tfinal public static int KB=1024;\n");
		sb.append ("\tfinal public static int CSATKB=(KB*32);\n");
		sb.append ("\tfinal public static int NEGATIVO=-1;\n");
		sb.append ("\tfinal public static int SLEEP=16;\n");
		sb.append ("\n");
		sb.append ("\tpublic static int CIEN=72;\n");
		sb.append ("\n");
		sb.append ("\tfinal public static long KBL=1024L;\n");
		sb.append ("\n");
		sb.append ("\tfinal public static float CEROF=0.0F;\n");
		sb.append ("\tfinal public static float DOSF=2.0F;\n");
		sb.append ("\n");
		sb.append ("\tpublic static float CIENF=72.0F;\n");
		sb.append ("\n");
		sb.append ("\tpublic static String PREPATH=\"\";\n");
		sb.append ("\n");
		sb.append ("\tfinal public static String OK=\"OK\";\n");
		sb.append ("\tfinal public static String CANCEL=\"CANCEL\";\n");
		sb.append ("\tfinal public static String CANCELLEDTASKS=\"Cancelled All Async Tasks.\";\n");
		sb.append ("\tfinal public static String EXIT=\"EXIT\";\n");
		sb.append ("\tfinal public static String ESIT=\"=\";\n");
		sb.append ("\tfinal public static String NOKTA=\".\";\n");
		sb.append ("\tfinal public static String IKINOKTA=\":\";\n");
		sb.append ("\tfinal public static String NLINE=\"\\n\";\n");
		sb.append ("\tfinal public static String BOLU=\"/\";\n");
		sb.append ("\tfinal public static String KBSTR=\"KB\";\n");
		sb.append ("\tfinal public static String SPACE=\" \";\n");
		sb.append ("\tfinal public static String UTF8=\"UTF-8\";\n");
		sb.append ("}");
		/////

		OutputStream fos=new FileOutputStream (fd);
		PrintStream ps=new PrintStream (fos, true, "UTF-8");
		
		ps.println (sb.toString ());
		
		ps.flush ();  ps.close ();
		fos.flush (); fos.close ();
		
		System.out.println ("Generated: src\\net\\murat\\"+APPNAME+"\\Values.java");
		
		return;
	}

	//19.
	final
	private static void genTask ()
	throws IOException
	{
		File fd=new File ("src\\net\\murat\\"+APPNAME+"\\ProcessTask.java");
		
		if (fd.exists ())
		{
			System.out.println ("Skipping existing file: "+fd.getAbsolutePath ()+"");
			return;
		}
		
		StringBuffer sb=new StringBuffer ();
		
		//////////
		sb.append ("package net.murat."+APPNAME+";\n");
		sb.append ("\n");
		sb.append ("import android.os.AsyncTask;\n");
		sb.append ("\n");
		sb.append ("import android.widget.EditText;\n");
		sb.append ("\n");
		sb.append ("import java.io.File;\n");
		sb.append ("\n");
		sb.append ("public class ProcessTask\n");
		sb.append ("\textends AsyncTask <String, String, String>\n");
		sb.append ("{\n");
		sb.append ("\tprivate File fsrc=null;\n");
		sb.append ("\tprivate EditText pano=null;\n");
		sb.append ("\n");
		sb.append ("\tprivate String errorMessage=\"\";\n");
		sb.append ("\n");
		sb.append ("\tpublic ProcessTask (File src, EditText panx)\n");
		sb.append ("\t{\n");
		sb.append ("\t\tsuper ();\n");
		sb.append ("\n");
		sb.append ("\t\tthis.fsrc=src;\n");
		sb.append ("\t\tthis.pano=panx;\n");
		sb.append ("\t}//end constructor\n");
		sb.append ("\n");
		sb.append ("\tprotected void onPreExecute ()\n");
		sb.append ("\t{\n");
		sb.append ("\t\treturn;\n");
		sb.append ("\t}\n");
		sb.append ("\n");
		sb.append ("\tprotected String doInBackground (String... unused)\n");
		sb.append ("\t{\n");
	    sb.append ("\t\tboolean isTest=true;\n");
		sb.append ("\t\tboolean errorOcurred=false;\n");
		sb.append ("\n");
		sb.append ("\t\tint syc=Values.CERO;\n");
		sb.append ("\n");
		sb.append ("\t\tString message=\"\";\n");
		sb.append ("\n");
		sb.append ("\t\twhile (isTest)\n");
		sb.append ("\t\t{\n");
		sb.append ("\t\t\tif (isCancelled ()) break;\n");
		sb.append ("\t\t\tif (errorOcurred) break;\n");
		sb.append ("\n");
		sb.append ("\t\t\t++syc;\n");
		sb.append ("\n");
		sb.append ("\t\t\tif (syc > Values.DIEZ) break;\n");
		sb.append ("\n");
		sb.append ("\t\t\tmessage=(\"\"+syc+\"\");\n");
		sb.append ("\n");
		sb.append ("\t\t\tpublishProgress (message);\n");
		sb.append ("\n");
		sb.append ("\t\t\ttry\n");
		sb.append ("\t\t\t{\n");
		sb.append ("\t\t\t\tThread.sleep (Values.SLEEP);\n");
		sb.append ("\t\t\t}\n");
		sb.append ("\t\t\tcatch (InterruptedException ie)\n");
		sb.append ("\t\t\t{}\n");
		sb.append ("\t\t}//end inner while\n");
		sb.append ("\n");
		sb.append ("\t\tString resval=\"Tested is successfull.\";\n");
		sb.append ("\n");
		sb.append ("\t\treturn resval;\n");
		sb.append ("\t}\n");
		sb.append ("\n");
		sb.append ("\tprotected void onProgressUpdate (String... vals)\n");
		sb.append ("\t{\n");
		sb.append ("\t\t//vals, publishProgress ile donen argumanlar\n");
		sb.append ("\t\tpano.setText (vals [Values.CERO]);\n");
		sb.append ("\n");
		sb.append ("\t\treturn;\n");
		sb.append ("\t}\n");
		sb.append ("\n");
		sb.append ("\tprotected void onPostExecute (String res)\n");
		sb.append ("\t{\n");
		sb.append ("\t\t//res, doInBackground metodunun dondurdugu deger\n");
		sb.append ("\t\tpano.setText (res);\n");
		sb.append ("\n");
		sb.append ("\t\treturn;\n");
		sb.append ("\t}\n");
		sb.append ("\n");
		sb.append ("}//class end");
		//////////

		OutputStream fos=new FileOutputStream (fd);
		PrintStream ps=new PrintStream (fos, true, "UTF-8");
		
		ps.println (sb.toString ());
		
		ps.flush ();  ps.close ();
		fos.flush (); fos.close ();
		
		System.out.println ("Generated: src\\net\\murat\\"+APPNAME+"\\ProcessTask.java");
		
		return;
	}
	
	//20.
	final
	private static void genTaskOther ()
	throws IOException
	{
		File fd=new File ("src\\net\\murat\\"+APPNAME+"\\ListTask.java");
		
		if (fd.exists ())
		{
			System.out.println ("Skipping existing file: "+fd.getAbsolutePath ()+"");
			return;
		}
		
		StringBuffer sb=new StringBuffer ();
		
		//////////
		sb.append ("package net.murat."+APPNAME+";\n");
		sb.append ("\n");
		sb.append ("import android.os.AsyncTask;\n");
		sb.append ("\n");
		sb.append ("import android.widget.EditText;\n");
		sb.append ("\n");
		sb.append ("import java.io.File;\n");
		sb.append ("\n");
		sb.append ("public class ListTask\n");
		sb.append ("\textends AsyncTask <String, String, String>\n");
		sb.append ("{\n");
		sb.append ("\tprivate File fsrc=null;\n");
		sb.append ("\tprivate EditText pano=null;\n");
		sb.append ("\n");
		sb.append ("\tprivate String errorMessage=\"\";\n");
		sb.append ("\n");
		sb.append ("\tpublic ListTask (File src, EditText panx)\n");
		sb.append ("\t{\n");
		sb.append ("\t\tsuper ();\n");
		sb.append ("\n");
		sb.append ("\t\tthis.fsrc=src;\n");
		sb.append ("\t\tthis.pano=panx;\n");
		sb.append ("\t}//end constructor\n");
		sb.append ("\n");
		sb.append ("\tprotected void onPreExecute ()\n");
		sb.append ("\t{\n");
		sb.append ("\t\treturn;\n");
		sb.append ("\t}\n");
		sb.append ("\n");
		sb.append ("\tprotected String doInBackground (String... unused)\n");
		sb.append ("\t{\n");
	    sb.append ("\t\tboolean isTest=true;\n");
		sb.append ("\t\tboolean errorOcurred=false;\n");
		sb.append ("\n");
		sb.append ("\t\tint syc=Values.CERO;\n");
		sb.append ("\n");
		sb.append ("\t\tString message=\"\";\n");
		sb.append ("\n");
		sb.append ("\t\twhile (isTest)\n");
		sb.append ("\t\t{\n");
		sb.append ("\t\t\tif (isCancelled ()) break;\n");
		sb.append ("\t\t\tif (errorOcurred) break;\n");
		sb.append ("\n");
		sb.append ("\t\t\t++syc;\n");
		sb.append ("\n");
		sb.append ("\t\t\tif (syc > Values.TREI) break;\n");
		sb.append ("\n");
		sb.append ("\t\t\tmessage=(\"\"+syc+\"\");\n");
		sb.append ("\n");
		sb.append ("\t\t\tpublishProgress (message);\n");
		sb.append ("\n");
		sb.append ("\t\t\ttry\n");
		sb.append ("\t\t\t{\n");
		sb.append ("\t\t\t\tThread.sleep (Values.SLEEP);\n");
		sb.append ("\t\t\t}\n");
		sb.append ("\t\t\tcatch (InterruptedException ie)\n");
		sb.append ("\t\t\t{}\n");
		sb.append ("\t\t}//end inner while\n");
		sb.append ("\n");
		sb.append ("\t\tString resval=\"Tested is successfull.\";\n");
		sb.append ("\n");
		sb.append ("\t\treturn resval;\n");
		sb.append ("\t}\n");
		sb.append ("\n");
		sb.append ("\tprotected void onProgressUpdate (String... vals)\n");
		sb.append ("\t{\n");
		sb.append ("\t\t//vals, publishProgress ile donen argumanlar\n");
		sb.append ("\t\tpano.setText (vals [Values.CERO]);\n");
		sb.append ("\n");
		sb.append ("\t\treturn;\n");
		sb.append ("\t}\n");
		sb.append ("\n");
		sb.append ("\tprotected void onPostExecute (String res)\n");
		sb.append ("\t{\n");
		sb.append ("\t\t//res, doInBackground metodunun dondurdugu deger\n");
		sb.append ("\t\tpano.setText (res);\n");
		sb.append ("\n");
		sb.append ("\t\treturn;\n");
		sb.append ("\t}\n");
		sb.append ("\n");
		sb.append ("}//class end");
		//////////

		OutputStream fos=new FileOutputStream (fd);
		PrintStream ps=new PrintStream (fos, true, "UTF-8");
		
		ps.println (sb.toString ());
		
		ps.flush ();  ps.close ();
		fos.flush (); fos.close ();
		
		System.out.println ("Generated: src\\net\\murat\\"+APPNAME+"\\ListTask.java");
		
		return;
	}
	
	//21.
	final
	private static void genActivity ()
	throws IOException
	{
		File fd=new File ("src\\net\\murat\\"+APPNAME+"\\"+getAppName()+".java");
		
		if (fd.exists ())
		{
			System.out.println ("Skipping existing file: "+fd.getAbsolutePath ()+"");
			return;
		}
		
		StringBuffer sb=new StringBuffer ();

		///////
		sb.append ("package net.murat."+APPNAME+";\n");
		sb.append ("\n");
		sb.append ("import android.app.Activity;\n");
		sb.append ("\n");
		sb.append ("import android.os.Bundle;\n");
		sb.append ("\n");
		sb.append ("import android.os.AsyncTask;\n");
		sb.append ("import android.os.Environment;\n");
		sb.append ("\n");
		sb.append ("import android.view.View;\n");
		sb.append ("import android.view.Window;\n");
		sb.append ("\n");
		sb.append ("import android.widget.Button;\n");
		sb.append ("import android.widget.EditText;\n");
		sb.append ("\n");
		sb.append ("import java.io.File;\n");
		sb.append ("\n");
		sb.append ("public class "+(getAppName ())+"\n");
		sb.append ("\textends Activity\n");
		sb.append ("{\n");
		sb.append ("\n");
		sb.append ("\tprivate EditText src_tv=null;\n");
		sb.append ("\tprivate EditText tv=null;\n");
		sb.append ("\n");
		sb.append ("\tprivate Button show_button=null;\n");
		sb.append ("\tprivate Button list_button=null;\n");
		sb.append ("\tprivate Button exit_button=null;\n");
		sb.append ("\tprivate Button stop_button=null;\n");
		sb.append ("\n");
		sb.append ("\tprivate AsyncTask GOREV_TASK=null;\n");
		sb.append ("\tprivate AsyncTask LIST_TASK=null;\n");		
		sb.append ("\n");
		sb.append ("\tprivate String PATH=\"\";\n");
		sb.append ("\n");
		sb.append ("\tpublic void onCreate (Bundle paramBundle)\n");
		sb.append ("\t{\n");
		sb.append ("\t\tsuper.onCreate (paramBundle);\n");
		sb.append ("\n");
		sb.append ("\t\trequestWindowFeature (Window.FEATURE_NO_TITLE);\n");
		sb.append ("\n");
		sb.append ("\t\tsetContentView (R.layout.main);\n");
		sb.append ("\n");
		sb.append ("\t\tPATH=getMainPathName ();\n");
		sb.append ("\n");
		sb.append ("\t\tFile ff=new File (PATH+Values.BOLU+"+"\""+APPNAME+"\""+");\n");
		sb.append ("\n");
		sb.append ("\t\tif (ff.exists () == false)\n");
		sb.append ("\t\t{\n");
		sb.append ("\t\t\ttry\n");
		sb.append ("\t\t\t{\n");
        sb.append ("\t\t\t\tff.mkdir ();\n");
		sb.append ("\t\t\t}\n");
		sb.append ("\t\t\t catch (SecurityException se)\n");
		sb.append ("\t\t\t{}\n");
		sb.append ("\t\t}\n");
		sb.append ("\n");
		sb.append ("\t\tPATH=(PATH+Values.BOLU+"+"\""+APPNAME+"/"+"\""+");\n");
    	sb.append ("\n");
		sb.append ("\t\tsrc_tv= (EditText) findViewById (R.id.src_tv);\n");
		sb.append ("\t\ttv= (EditText) findViewById (R.id.tv);\n");
		sb.append ("\n");
		sb.append ("\t\texit_button= (Button) findViewById (R.id.exit_button);\n");
		sb.append ("\t\texit_button.setOnClickListener (new View.OnClickListener ()\n");
		sb.append ("\t\t{\n");
        sb.append ("\t\t\tpublic void onClick (View view)\n");
        sb.append ("\t\t\t{\n");
        sb.append ("\t\t\t\tonDestroy ();\n");
        sb.append ("\t\t\t}\n");
		sb.append ("\t\t});\n");
		sb.append ("\n");
		sb.append ("\t\tstop_button= (Button) findViewById (R.id.stop_button);\n");
		sb.append ("\t\tstop_button.setOnClickListener (new View.OnClickListener ()\n");
		sb.append ("\t\t{\n");
        sb.append ("\t\t\tpublic void onClick (View view)\n");
        sb.append ("\t\t\t{\n");
		sb.append ("\t\t\t\tcancelAllTasks (true);\n");
        sb.append ("\t\t\t\treturn;\n");
        sb.append ("\t\t\t}\n");
		sb.append ("\t\t});\n");
		sb.append ("\n");
		sb.append ("\t\tlist_button= (Button) findViewById (R.id.list_button);\n");
		sb.append ("\t\tlist_button.setOnClickListener (new View.OnClickListener ()\n");
		sb.append ("\t\t{\n");
        sb.append ("\t\t\tpublic void onClick (View view)\n");
        sb.append ("\t\t\t{\n");
        sb.append ("\t\t\t\tString str=((tv.getText ()).toString ()).trim ();\n");
        sb.append ("\n");
		sb.append ("\t\t\t\tFile f=new File (PATH+str);\n");
		sb.append ("\n");
        sb.append ("\t\t\t\tif (LIST_TASK != null)\n");
        sb.append ("\t\t\t\t{\n");
        sb.append ("\t\t\t\t\tAsyncTask.Status status=LIST_TASK.getStatus ();\n");
        sb.append ("\n");
		sb.append ("\t\t\t\t\tif ((status == AsyncTask.Status.PENDING) || (status == AsyncTask.Status.FINISHED))\n");
        sb.append ("\t\t\t\t\t{\n");
        sb.append ("\t\t\t\t\t\tLIST_TASK=new ListTask (f, tv).execute (\"\");\n");
        sb.append ("\t\t\t\t\t}\n");
        sb.append ("\t\t\t\t\telse\n");
        sb.append ("\t\t\t\t\t{\n");
        sb.append ("\t\t\t\t\t\tLIST_TASK.cancel (true);\n");
        sb.append ("\n");
        sb.append ("\t\t\t\t\t\twhile (LIST_TASK.isCancelled () == false) {}\n");
        sb.append ("\n");
        sb.append ("\t\t\t\t\t\tLIST_TASK=new ListTask (f, tv).execute (\"\");\n");
        sb.append ("\t\t\t\t\t}\n");
        sb.append ("\t\t\t\t}\n");
        sb.append ("\t\t\t\telse\n");
        sb.append ("\t\t\t\t{\n");
        sb.append ("\t\t\t\t\tLIST_TASK=new ListTask (f, tv).execute (\"\");\n");
        sb.append ("\t\t\t\t}\n");
        sb.append ("\t\t\t}\n");
		sb.append ("\t\t});\n");
		sb.append ("\n");
		sb.append ("\t\tshow_button= (Button) findViewById (R.id.show_button);//Process Task\n");
		sb.append ("\t\tshow_button.setOnClickListener (new View.OnClickListener ()\n");
		sb.append ("\t\t{\n");
        sb.append ("\t\t\tpublic void onClick (View view)\n");
        sb.append ("\t\t\t{\n");
        sb.append ("\t\t\t\tString str=((tv.getText ()).toString ()).trim ();\n");
        sb.append ("\n");
		sb.append ("\t\t\t\tFile f=new File (PATH+str);\n");
		sb.append ("\n");
        sb.append ("\t\t\t\tif (GOREV_TASK != null)\n");
        sb.append ("\t\t\t\t{\n");
        sb.append ("\t\t\t\t\tAsyncTask.Status status=GOREV_TASK.getStatus ();\n");
        sb.append ("\n");
		sb.append ("\t\t\t\t\tif ((status == AsyncTask.Status.PENDING) || (status == AsyncTask.Status.FINISHED))\n");
        sb.append ("\t\t\t\t\t{\n");
        sb.append ("\t\t\t\t\t\tGOREV_TASK=new ProcessTask (f, tv).execute (\"\");\n");
        sb.append ("\t\t\t\t\t}\n");
        sb.append ("\t\t\t\t\telse\n");
        sb.append ("\t\t\t\t\t{\n");
        sb.append ("\t\t\t\t\t\tGOREV_TASK.cancel (true);\n");
        sb.append ("\n");
        sb.append ("\t\t\t\t\t\twhile (GOREV_TASK.isCancelled () == false) {}\n");
        sb.append ("\n");
        sb.append ("\t\t\t\t\t\tGOREV_TASK=new ProcessTask (f, tv).execute (\"\");\n");
        sb.append ("\t\t\t\t\t}\n");
        sb.append ("\t\t\t\t}\n");
        sb.append ("\t\t\t\telse\n");
        sb.append ("\t\t\t\t{\n");
        sb.append ("\t\t\t\t\tGOREV_TASK=new ProcessTask (f, tv).execute (\"\");\n");
        sb.append ("\t\t\t\t}\n");
        sb.append ("\t\t\t}\n");
		sb.append ("\t\t});\n");
		sb.append ("\n");
		sb.append ("\t}//oncreate end\n");
		sb.append ("\n");
		sb.append ("\tprivate final String getMainPathName ()\n");
		sb.append ("\t{\n");
		sb.append ("\t\tFile path=getExternalFilesDir (Environment.DIRECTORY_DOWNLOADS);\n");
		sb.append ("\t\tString px=path.getAbsolutePath ();\n");
		sb.append ("\n");
		sb.append ("\t\tString exclude=\"Android/data/\"+getPackageName ()+\"/files/\";\n");
		sb.append ("\t\tpx=px.replace (exclude, \"\");\n");
		sb.append ("\n");
		sb.append ("\t\tValues.PREPATH=px;\n");
		sb.append ("\n");
		sb.append ("\t\treturn px;\n");
		sb.append ("\t}\n");
		sb.append ("\n");
		sb.append ("\tprotected void onStop()\n");
		sb.append ("\t{\n");
		sb.append ("\t\tsuper.onStop();\n");
		sb.append ("\t}\n");
		sb.append ("\n");
		sb.append ("\tprotected void onResume()\n");
		sb.append ("\t{\n");
		sb.append ("\t\tsuper.onResume();\n");
		sb.append ("\t}\n");
		sb.append ("\n");
		sb.append ("\tprotected void onPause()\n");
		sb.append ("\t{\n");
		sb.append ("\t\tsuper.onPause();\n");
		sb.append ("\t}\n");
		sb.append ("\n");
		sb.append ("\tprivate final void cancelAllTasks (boolean rule)\n");
		sb.append ("\t{\n");
		sb.append ("\t\ttry\n");
		sb.append ("\t\t{\n");
		sb.append ("\t\t\tif (GOREV_TASK != null)\n");
		sb.append ("\t\t\t{\n");
        sb.append ("\t\t\t\tGOREV_TASK.cancel (true);\n");
		sb.append ("\n");
        sb.append ("\t\t\t\twhile (GOREV_TASK.isCancelled () == false)\n");
        sb.append ("\t\t\t\t{}\n");
		sb.append ("\t\t\t}\n");
		sb.append ("\t\t}\n");
		sb.append ("\t\tcatch (Exception e)\n");
		sb.append ("\t\t{}\n");
	    sb.append ("\n");
		sb.append ("\t\ttry\n");
		sb.append ("\t\t{\n");
		sb.append ("\t\t\tif (LIST_TASK != null)\n");
		sb.append ("\t\t\t{\n");
        sb.append ("\t\t\t\tLIST_TASK.cancel (true);\n");
		sb.append ("\n");
        sb.append ("\t\t\t\twhile (LIST_TASK.isCancelled () == false)\n");
        sb.append ("\t\t\t\t{}\n");
		sb.append ("\t\t\t}\n");
		sb.append ("\t\t}\n");
		sb.append ("\t\tcatch (Exception e)\n");
		sb.append ("\t\t{}\n");
	    sb.append ("\n");
		sb.append ("\t\tif (rule)\n");
		sb.append ("\t\t{\n");
		sb.append ("\t\t\ttv.setText (Values.CANCELLEDTASKS);\n");
		sb.append ("\t\t}\n");
		sb.append ("\n");
		sb.append ("\t\treturn;\n");
		sb.append ("\t}\n");
		sb.append ("\n");
		sb.append ("\tprotected void onDestroy ()\n");
		sb.append ("\t{\n");
		sb.append ("\t\tcancelAllTasks (false);\n");
		sb.append ("\n");
		sb.append ("\t\tsuper.onDestroy ();\n");
		sb.append ("\n");
		sb.append ("\t\tandroid.os.Process.killProcess (android.os.Process.myPid ());\n");
		sb.append ("\n");
		sb.append ("\t\tfinishAffinity ();\n");
		sb.append ("\t\tSystem.exit (0);\n");
		sb.append ("\t}\n");
		sb.append ("\n");
		sb.append ("}//class end");
		///////

		OutputStream fos=new FileOutputStream (fd);
		PrintStream ps=new PrintStream (fos, true, "UTF-8");
		
		ps.println (sb.toString ());
		
		ps.flush ();  ps.close ();
		fos.flush (); fos.close ();
		
		System.out.println ("Generated: src\\net\\murat\\"+APPNAME+"\\"+getAppName()+".java");
		
		return;
	}
	
	//22.
	final
	private static void genIconSizes ()
	throws IOException
	{
		File fd=new File ("arac\\iconSizes.txt");

		StringBuffer sb=new StringBuffer ();

		sb.append ("Android app PNG icon sizes:\n");
		sb.append ("36x36\n");
		sb.append ("48x48\n");
		sb.append ("72x72\n");
		sb.append ("96x96\n");
		sb.append ("144x144\n");
		sb.append ("192x192\n");
		sb.append ("\n");
		sb.append ("Play Store high res icon size: 512x512");

		OutputStream fos=new FileOutputStream (fd);
		PrintStream ps=new PrintStream (fos, true, "UTF-8");
		
		ps.println (sb.toString ());
		
		ps.flush ();  ps.close ();
		fos.flush (); fos.close ();
		
		System.out.println ("Generated: arac\\iconSizes.txt");
		
		return;
	}
	
	//23.
	final
	private static void genAllClean ()
	throws IOException
	{
		File fd=new File ("allClean.bat");

		StringBuffer sb=new StringBuffer ();

		/////
		sb.append ("del /f /s /q *.keystore*\n");
		sb.append ("del /f /s /q classes.dex\n");
		sb.append ("rmdir /s /q arac\n");
		sb.append ("rmdir /s /q assets\n");
		sb.append ("rmdir /s /q bin\n");
		sb.append ("rmdir /s /q libs\n");
		sb.append ("rmdir /s /q obj\n");
		sb.append ("rmdir /s /q res\n");
		sb.append ("rmdir /s /q src\n");
		sb.append ("del /f /s /q howto_win.txt\n");
		sb.append ("del /f /s /q clean.bat\n");
		sb.append ("del /f /s /q control.bat\n");
		sb.append ("del /f /s /q kstore.bat\n");
		sb.append ("del /f /s /q make1app.bat\n");
		sb.append ("del /f /s /q make2app.bat\n");
		sb.append ("del /f /s /q AndroidManifest.xml");
		/////

		OutputStream fos=new FileOutputStream (fd);
		PrintStream ps=new PrintStream (fos, true, "UTF-8");
		
		ps.println (sb.toString ());
		
		ps.flush ();  ps.close ();
		fos.flush (); fos.close ();
		
		System.out.println ("Generated: allClean.bat");
		
		return;
	}
	
	final
	public static void main (final String [] args)
	{
		if (args.length < 1)
		{
			System.out.println ("\nUsage:\n\tjava GenerateScriptsFolders [<appName>]\n");
			System.out.println ("Example:\n\tjava GenerateScriptsFolders tg\n");
			System.exit (-1);
		}
		
		APPNAME=args [0x0000];
		
		try
		{
			makedirs ();
			
			genManifest ();
			genMake1 ();
			genMake2 ();
			genKStore ();
			genClean ();
			genAllClean ();
			genControl ();
			genHowto ();
			genLogoGen ();
			genIconSizes ();
			genIntegers ();
			genColors ();
			genDimensions ();
			genStrings ();
			genLayout ();
			genBGA ();
			genBorder ();
			genBShape ();
			genRShape ();
			genValues ();
			genTask ();
			genTaskOther ();
			genActivity ();
		}
		catch (IOException ioe)
		{
			ioe.printStackTrace ();
			System.exit (-1);
		}
		
		System.exit (0x0000);
	}
	
}
