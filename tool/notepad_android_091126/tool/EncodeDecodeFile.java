import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.IvParameterSpec;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;

public class EncodeDecodeFile {
  
  private static final String ALGORITHM = "AES";
  private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
  private static final int KEY_LENGTH = 16; // 128-bit AES
  
  public static void main(String[] args) {
    if (args.length != 4) {
      System.out.println("Usage: java EncodeDecodeFile enc|dec password srcFile dstFile");
      System.out.println("Password must be exactly 16 characters long");
      System.exit(1);
    }
    
    String mode = args[0];
    String password = args[1];
    String srcFile = args[2];
    String dstFile = args[3];
    
    // Validate password length
    if (password.length() != KEY_LENGTH) {
      System.err.println("Error: Password must be exactly 16 characters long");
      System.exit(1);
    }
    
    try {
      if ("enc".equalsIgnoreCase(mode)) {
        encryptFile(password, srcFile, dstFile);
        System.out.println("File encrypted successfully: " + dstFile);
        } else if ("dec".equalsIgnoreCase(mode)) {
        decryptFile(password, srcFile, dstFile);
        System.out.println("File decrypted successfully: " + dstFile);
        } else {
        System.err.println("Error: Mode must be 'enc' or 'dec'");
        System.exit(1);
      }
      } catch (Exception e) {
      System.err.println("Error: " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
  }
  
  private static void encryptFile(String password, String srcFile, String dstFile) throws Exception {
    // Create key from password
    SecretKeySpec keySpec = new SecretKeySpec(password.getBytes("UTF-8"), ALGORITHM);
    
    // Create fixed IV (you can modify this for better security)
    byte[] iv = new byte[16];
    Arrays.fill(iv, (byte) 0x00);
    IvParameterSpec ivSpec = new IvParameterSpec(iv);
    
    // Initialize cipher
    Cipher cipher = Cipher.getInstance(TRANSFORMATION);
    cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
    
    // Read source file
    Path srcPath = Paths.get(srcFile);
    byte[] inputBytes = Files.readAllBytes(srcPath);
    
    // Encrypt data
    byte[] encryptedBytes = cipher.doFinal(inputBytes);
    
    // Write encrypted data to destination file
    Path dstPath = Paths.get(dstFile);
    Files.write(dstPath, encryptedBytes);
  }
  
  private static void decryptFile(String password, String srcFile, String dstFile) throws Exception {
    // Create key from password
    SecretKeySpec keySpec = new SecretKeySpec(password.getBytes("UTF-8"), ALGORITHM);
    
    // Create fixed IV (must match encryption IV)
    byte[] iv = new byte[16];
    Arrays.fill(iv, (byte) 0x00);
    IvParameterSpec ivSpec = new IvParameterSpec(iv);
    
    // Initialize cipher
    Cipher cipher = Cipher.getInstance(TRANSFORMATION);
    cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
    
    // Read encrypted file
    Path srcPath = Paths.get(srcFile);
    byte[] encryptedBytes = Files.readAllBytes(srcPath);
    
    // Decrypt data
    byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
    
    // Write decrypted data to destination file
    Path dstPath = Paths.get(dstFile);
    Files.write(dstPath, decryptedBytes);
  }
  
  // Utility method to generate SHA-256 hash (optional)
  private static byte[] generateKey(String password) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] hash = digest.digest(password.getBytes("UTF-8"));
    return Arrays.copyOf(hash, KEY_LENGTH); // Take first 16 bytes for AES-128
  }
}

/**
# Compile Simple:
javac EncodeDecodeFile.java

Compile Detailled:
javac -source 1.8 -target 1.8 -sourcepath . -cp . -g -proc:none -nowarn -O -d . EncodeDecodeFile.java

# Usage Examples:

# 1. ENCRYPT a file
java EncodeDecodeFile enc "My16CharPasswd!" original.txt encrypted.dat

# 2. DECRYPT a file
java EncodeDecodeFile dec "My16CharPasswd!" encrypted.dat decrypted.txt

# 3. Encrypt Java source code
java EncodeDecodeFile enc "1234567890123456" MyProgram.java myprogram.enc

# 4. Decrypt back
java EncodeDecodeFile dec "1234567890123456" myprogram.enc MyProgramRestored.java
 */
