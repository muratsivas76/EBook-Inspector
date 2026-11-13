import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class GenerateOvalImage {
  
  public static void main(String[] args) {
    // Check if the filename argument is provided
    if (args.length < 1) {
      System.out.println("Usage: java GenerateOvalImage <input_image_path>");
      return;
    }
    
    String inputPath = args[0];
    String outputPath = "icon_final.png";
    
    try {
      // Load the source image from the provided argument
      File inputFile = new File(inputPath);
      if (!inputFile.exists()) {
        System.out.println("Error: File not found - " + inputPath);
        return;
      }
      BufferedImage sourceImage = ImageIO.read(inputFile);
      
      // Create a 512x512 canvas with transparency (ARGB)
      BufferedImage outputImage = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g2 = outputImage.createGraphics();
      
      // Enable Anti-aliasing for smooth circular edges
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      
      // Step 1: Clear the background to 0x00000000 (Full Transparent)
      g2.setComposite(AlphaComposite.Clear);
      g2.fillRect(0, 0, 512, 512);
      g2.setComposite(AlphaComposite.SrcOver);
      
      // Step 2: Define the circle that touches the edges (512x512)
      Ellipse2D.Double circle = new Ellipse2D.Double(0, 0, 512, 512);
      
      // Step 3: Draw an opaque background color for the circle (e.g., Gold/Yellow)
      g2.setColor(new Color(255, 215, 0)); // Golden color
      g2.fill(circle);
      
      // Step 4: Clip the drawing area to the circle
      g2.setClip(circle);
      
      // Step 5: Draw the source image inside the clipped circle area
      // Scales the image to fit exactly 512x512
      g2.drawImage(sourceImage, 0, 0, 512, 512, null);
      
      // Cleanup
      g2.dispose();
      
      // Save the final icon as PNG
      ImageIO.write(outputImage, "PNG", new File(outputPath));
      System.out.println("Success! Icon generated: " + outputPath);
      
      } catch (Exception e) {
      System.err.println("An error occurred during image processing.");
      e.printStackTrace();
    }
  }
  
}
