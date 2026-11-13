import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.geom.AffineTransform;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

public class LogoGenerator extends JPanel {

    private BufferedImage logoBuffer;
    private int canvasW = 400, canvasH = 400;
    private CenterPanel centerPanel;
    private JPanel eastPanel;

    private JCheckBox chkCrop;
    private JCheckBox cropEdge;
    private JCheckBox chkTrans;

    private double rotateX = 0.0;
    private double rotateY = 0.0;
    private double imgRotateX = 0.0;

    private Color bgColor = Color.white;
    private Color borderColor = Color.red;
    private Color fontColor = Color.black;
    private Color selectedOvalColor = Color.BLUE;
    private Color edgeColor = Color.RED;

    private final JFileChooser fc = new JFileChooser(new File("."));

    private List<LogoImage> imageList = new ArrayList<LogoImage>();
    private List<LogoText> textList = new ArrayList<LogoText>();

    // UI Controls
    private JTextField txtW, txtH, txtX, txtY, txtSW, txtSH;
    private JTextField txtText, txtTX, txtTY, txtTSize;
    private JTextField txtIX, txtIY, txtIW, txtIH;
    private JTextField strokeField, rotateField, imgRotateField;

    private float strokeValue = 2.0f;

    private JComboBox<String> comboFont;
    private JComboBox<String> comboStyle;

	// Multi-Image Support
	private class LogoImage {
		BufferedImage img;
		int x, y, w, h;
		double rotate;
		LogoImage(BufferedImage img, int x, int y, int w, int h, double rotate) {
			this.img = img; this.x = x; this.y = y; this.w = w; this.h = h;
			this.rotate = rotate;
		}
	}

    // Multi-Text Support
    private class LogoText {
        String content;
        int x, y, size;
        String fontFamily;
        int fontStyle;
        Color color;
        String rotate; // "rotX*rotY" format

        LogoText(String content, int x, int y, int size,
                 String fontFamily, int fontStyle, Color color, String rotate) {
            this.content = content;
            this.x = x; this.y = y; this.size = size;
            this.fontFamily = fontFamily;
            this.fontStyle = fontStyle;
            this.color = color;
            this.rotate = rotate;
        }
    }

    public LogoGenerator() {
        super(new BorderLayout());
        initBuffer(canvasW, canvasH);
        centerPanel = new CenterPanel();
        eastPanel = new JPanel();
        setupEastPanel();
        add(centerPanel, BorderLayout.CENTER);
        add(eastPanel, BorderLayout.EAST);
        apply();
    }

    private void initBuffer(int w, int h) {
        logoBuffer = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    }

    private void setupEastPanel() {
        eastPanel.setPreferredSize(new Dimension(320, 0));
        eastPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
        JScrollPane scroll = new JScrollPane(eastPanel);
        scroll.setPreferredSize(new Dimension(320, 600));

        addSectionTitle("--- Canvas & Background ---");
        txtW = new JTextField("512", 4); txtH = new JTextField("512", 4);
        eastPanel.add(new JLabel("W:")); eastPanel.add(txtW);
        eastPanel.add(new JLabel("H:")); eastPanel.add(txtH);
        chkTrans = new JCheckBox("Transparent");
        eastPanel.add(chkTrans);

        addSectionTitle("--- Oval (X, Y, W, H) ---");
        txtX = new JTextField("6", 3); txtY = new JTextField("6", 3);
        txtSW = new JTextField("500", 3); txtSH = new JTextField("500", 3);
        eastPanel.add(txtX); eastPanel.add(txtY); eastPanel.add(txtSW); eastPanel.add(txtSH);

        eastPanel.add(new JLabel("                            ", JLabel.CENTER));

        JButton btnBGColor = new JButton("BG");
        btnBGColor.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Color c = JColorChooser.showDialog(null, "Choose BG Color", bgColor);
                if (c != null) bgColor = c;
                apply();
            }
        });
        eastPanel.add(btnBGColor);

        JButton btnOvalColor = new JButton("Oval");
        btnOvalColor.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Color c = JColorChooser.showDialog(null, "Choose Oval Color", selectedOvalColor);
                if (c != null) selectedOvalColor = c;
                apply();
            }
        });
        eastPanel.add(btnOvalColor);

        JButton btnBorderColor = new JButton("Border");
        btnBorderColor.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Color c = JColorChooser.showDialog(null, "Choose Border Color", borderColor);
                if (c != null) borderColor = c;
                else borderColor = null;
                apply();
            }
        });
        eastPanel.add(btnBorderColor);

        JButton btnFontColor = new JButton("Font");
        btnFontColor.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Color c = JColorChooser.showDialog(null, "Choose Font Color", fontColor);
                if (c != null) fontColor = c;
                apply();
            }
        });
        eastPanel.add(btnFontColor);

        JButton btnEdgeColor = new JButton("Edge");
        btnEdgeColor.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Color c = JColorChooser.showDialog(null, "Choose Edge Color", edgeColor);
                if (c != null) edgeColor = c;
                apply();
            }
        });
        eastPanel.add(btnEdgeColor);

        addSectionTitle("--- Text (Content, X, Y, Size) ---");
        txtText = new JTextField("", 10);
        txtTX = new JTextField("50", 3); txtTY = new JTextField("50", 3);
        txtTSize = new JTextField("30", 3);
        eastPanel.add(txtText); eastPanel.add(txtTX); eastPanel.add(txtTY); eastPanel.add(txtTSize);

        String[] families = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        comboFont = new JComboBox<String>(families);
        comboFont.setPreferredSize(new Dimension(280, 25));
        String[] styles = {"Plain", "Bold", "Italic", "BoldItalic"};
        comboStyle = new JComboBox<String>(styles);
        eastPanel.add(comboFont); eastPanel.add(comboStyle);

        eastPanel.add(new JLabel("Text RotX*RotY:", JLabel.LEFT));
        rotateField = new JTextField("0.0*0.0", 9);
        eastPanel.add(rotateField);

        // Add Text / Remove Last Text buttons
        JButton btnAddText = new JButton("Add Text");
        btnAddText.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                addText();
            }
        });

        JButton btnRemText = new JButton("Remove Last Text");
        btnRemText.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!textList.isEmpty()) {
                    textList.remove(textList.size() - 1);
                    apply();
                }
            }
        });

        eastPanel.add(btnAddText);
        eastPanel.add(btnRemText);

        addSectionTitle("--- Image (X, Y, W, H) ---");
        txtIX = new JTextField("150", 3); txtIY = new JTextField("150", 3);
        txtIW = new JTextField("100", 3); txtIH = new JTextField("100", 3);
        eastPanel.add(txtIX); eastPanel.add(txtIY); eastPanel.add(txtIW); eastPanel.add(txtIH);

        chkCrop = new JCheckBox("Crop Images to Oval");
        eastPanel.add(chkCrop);

        cropEdge = new JCheckBox("Edge to Oval Clip");
        eastPanel.add(cropEdge);

        eastPanel.add(new JLabel("                                ", JLabel.CENTER));
        eastPanel.add(new JLabel("Stroke Clip:", JLabel.LEFT));
        strokeField = new JTextField("5.0f", 5);
        eastPanel.add(strokeField);

        eastPanel.add(new JLabel("                                  ", JLabel.CENTER));
        eastPanel.add(new JLabel("Image RotateX:", JLabel.LEFT));
        imgRotateField = new JTextField("0.0", 6);
        eastPanel.add(imgRotateField);

        addSectionTitle("--- Actions ---");
        JButton btnApply = new JButton("Update View");
        btnApply.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { apply(); }
        });

        JButton btnImg = new JButton("Add Image");
        btnImg.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { loadImg(); }
        });

        JButton btnRemImg = new JButton("Remove Last Image");
        btnRemImg.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!imageList.isEmpty()) { imageList.remove(imageList.size() - 1); apply(); }
            }
        });

        JButton btnSave = new JButton("Save PNG");
        btnSave.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { save(); }
        });

        JButton btnExit = new JButton("Exit");
        btnExit.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { System.exit(0); }
        });

        eastPanel.add(btnApply); eastPanel.add(btnImg); eastPanel.add(btnRemImg);
        eastPanel.add(btnSave); eastPanel.add(btnExit);

        this.add(scroll, BorderLayout.EAST);
    }

    private void addSectionTitle(String title) {
        JLabel lbl = new JLabel(title);
        lbl.setPreferredSize(new Dimension(300, 20));
        lbl.setForeground(Color.BLUE);
        eastPanel.add(lbl);
    }

    // Captures current text field state and pushes into textList
    private void addText() {
        try {
            String content = txtText.getText();
            int tx = Integer.parseInt(txtTX.getText());
            int ty = Integer.parseInt(txtTY.getText());
            int ts = Integer.parseInt(txtTSize.getText());
            String fontFamily = (String) comboFont.getSelectedItem();
            int fontStyle = comboStyle.getSelectedIndex();
            String rotate = rotateField.getText();
            textList.add(new LogoText(content, tx, ty, ts, fontFamily, fontStyle, fontColor, rotate));
            apply();
        } catch (NumberFormatException ex) {
            ex.printStackTrace();
        }
    }

    private void apply() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                eastPanel.requestFocus();
            }
        });

        // 1. Buffer re-initialization if size changed
        int nw = Integer.parseInt(txtW.getText());
        int nh = Integer.parseInt(txtH.getText());
        if (nw != canvasW || nh != canvasH) {
            canvasW = nw;
            canvasH = nh;
            initBuffer(canvasW, canvasH);
        }

        Graphics2D g2d = logoBuffer.createGraphics();

        // Set high quality rendering hints
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        // 2. Background Handling
        if (chkTrans.isSelected()) {
            g2d.setComposite(AlphaComposite.Clear);
            g2d.fillRect(0, 0, canvasW, canvasH);
            g2d.setComposite(AlphaComposite.SrcOver);
        } else {
            g2d.setColor(bgColor);
            g2d.fillRect(0, 0, canvasW, canvasH);
        }

        // 3. Get Shape/Oval Coordinates
        int ox = Integer.parseInt(txtX.getText());
        int oy = Integer.parseInt(txtY.getText());
        int ow = Integer.parseInt(txtSW.getText());
        int oh = Integer.parseInt(txtSH.getText());

        // Draw the base oval background color
        g2d.setColor(selectedOvalColor);
        g2d.fillOval(ox, oy, ow, oh);

        // 4. Draw Images with Optional Masking (Crop)
        Shape originalClip = g2d.getClip();

        if (chkCrop.isSelected()) {
            java.awt.geom.Ellipse2D ovalMask = new java.awt.geom.Ellipse2D.Double(ox, oy, ow, oh);
            g2d.setClip(ovalMask);
        }

        for (int i = 0; i < imageList.size(); i++) {
            LogoImage li = (LogoImage) imageList.get(i);
            if (chkCrop.isSelected()) {
                g2d.drawImage(li.img, ox, oy, ow, oh, null);
            } else {
                java.awt.geom.AffineTransform oldAT = g2d.getTransform();
                try {
                    //double angleDegrees = Double.parseDouble(imgRotateField.getText());
                    double angleDegrees = li.rotate;
                    double angleRadians = Math.toRadians(angleDegrees);
                    double centerX = li.x + (li.w / 2.0);
                    double centerY = li.y + (li.h / 2.0);
                    g2d.rotate(angleRadians, centerX, centerY);
                    g2d.drawImage(li.img, li.x, li.y, li.w, li.h, null);
                } catch (Exception ex) {
                    g2d.drawImage(li.img, li.x, li.y, li.w, li.h, null);
                } finally {
                    g2d.setTransform(oldAT);
                }
            }
        }

        // 5. Restore Clip and Draw Decorative Elements
        g2d.setClip(originalClip);

        if (cropEdge.isSelected()) {
            try {
                strokeValue = Float.parseFloat(strokeField.getText().trim());
            } catch (NumberFormatException nfe) {
                nfe.printStackTrace();
                strokeValue = 5.0f;
            }
            g2d.setStroke(new BasicStroke(strokeValue));
            g2d.setColor(edgeColor);
            g2d.draw(new java.awt.geom.Ellipse2D.Double(
                    ox + strokeValue / 2.0,
                    oy + strokeValue / 2.0,
                    ow - strokeValue,
                    oh - strokeValue
            ));
        }

        // 6. Draw all texts from textList
        for (int i = 0; i < textList.size(); i++) {
            LogoText lt = (LogoText) textList.get(i);
            int style = lt.fontStyle;
            g2d.setColor(lt.color);
            g2d.setFont(new Font(lt.fontFamily, style, lt.size));

            AffineTransform oldTransform = g2d.getTransform();
            try {
                if (lt.rotate != null && lt.rotate.contains("*")) {
                    String[] parts = lt.rotate.split("\\*");
                    double rX = Double.parseDouble(parts[0].trim());
                    double rY = Double.parseDouble(parts[1].trim());
                    g2d.rotate(Math.toRadians(rX), lt.x, lt.y);
                    g2d.shear(0, Math.tan(Math.toRadians(rY)));
                }
                g2d.drawString(lt.content, lt.x, lt.y);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                g2d.setTransform(oldTransform);
            }
        }

        // 7. Final Canvas Outline
        if (borderColor != null) {
            g2d.setColor(borderColor);
            g2d.setStroke(new BasicStroke(1.0f));
            g2d.drawRect(0, 0, canvasW - 1, canvasH - 1);
        }

        g2d.dispose();
        centerPanel.repaint();
    }

	private void loadImg() {
		if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			try {
				BufferedImage bi = ImageIO.read(fc.getSelectedFile());
				int ix = Integer.parseInt(txtIX.getText());
				int iy = Integer.parseInt(txtIY.getText());
				int iw = Integer.parseInt(txtIW.getText());
				int ih = Integer.parseInt(txtIH.getText());
				double rot = Double.parseDouble(imgRotateField.getText());
				imageList.add(new LogoImage(bi, ix, iy, iw, ih, rot));
				apply();
			} catch (Exception ex) {}
		}
	}

    private void save() {
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = fc.getSelectedFile();
        if (file.exists()) {
            String input = JOptionPane.showInputDialog(this, "This file already exists. Overwrite (y/e)?");
            if (input == null) return;
            if (!input.trim().toLowerCase().equals("y") &&
                    !input.trim().toLowerCase().equals("e")) return;
        }

        boolean isError = false;
        try {
            ImageIO.write(logoBuffer, "PNG", file);
        } catch (Exception ex) {
            isError = true;
        }

        if (!isError) {
            JOptionPane.showMessageDialog(this, "Saved successfully: " + file.getName());
        } else {
            JOptionPane.showMessageDialog(this, "UN ERROR OCURRED!");
        }

        centerPanel.requestFocus();
    }

    private class CenterPanel extends JPanel {
        public CenterPanel() {
            super(true);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (logoBuffer != null) g.drawImage(logoBuffer, 0, 0, null);
        }
    }

    public static void showScreen() {
        JFrame fr = new JFrame("LogoGenerator v3");
        fr.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JComponent jc = new LogoGenerator();
        jc.setOpaque(true);
        fr.setContentPane(jc);
        fr.pack();
        fr.setSize(900, 640);
        fr.setResizable(true);
        fr.setLocationRelativeTo(null);
        fr.setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                showScreen();
            }
        });
    }
    
}
