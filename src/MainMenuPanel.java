import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.swing.*;

public class MainMenuPanel extends JPanel {
    private final JFrame parentFrame;
    private Image backgroundImage;
    private Image backgroundImage2; // Background kedua
    private Image startButtonImage;
    private Image customButtonImage;
    private Image exitButtonImage;
    private Image backButtonImage; // Gambar tombol "Back"

    private Image redSnakeImage;
    private Image greenSnakeImage;
    private Image blueSnakeImage;
    private Image pinkSnakeImage;
    private Image appleImage;
    private Image bananaImage;
    private Image grapeImage;
    private Image orangeImage;
    private Image redSnake2Image;
    private Image greenSnake2Image;
    private Image blueSnake2Image;
    private Image pinkSnake2Image;
    private int selectedFoodDisplayX, selectedFoodDisplayY;
    private int selectedFoodDisplayWidth, selectedFoodDisplayHeight;


    private ImageIcon gif1;
    private ImageIcon gif2;
    private ImageIcon gif4; // GIF keempat
    private int gif1X, gif1Y;
    private int gif2X, gif2Y;
    private int gif3X, gif3Y;
    private int gif4X, gif4Y;

    private final int START_BUTTON_Y = 220;
    private final int CUSTOM_BUTTON_Y = 280;
    private final int EXIT_BUTTON_Y = 340;
    private final int BUTTON_WIDTH = 250;
    private final int BUTTON_HEIGHT = 50;
    private final int BACK_BUTTON_X = 0;
    private final int BACK_BUTTON_Y = 0;
    private final int BACK_BUTTON_WIDTH = 151;
    private final int BACK_BUTTON_HEIGHT = 74;

    private Rectangle startButtonRect;
    private Rectangle customButtonRect;
    private Rectangle exitButtonRect;
    private Rectangle backButtonRect;

    private String selectedColor = "green"; // Default selection
    private String selectedFood = "apple";  // Default selection
    private String selectedMap = "classic"; // Default map selection

    private final Map<String, Rectangle> colorRects = new HashMap<>();
    private final Map<String, Rectangle> foodRects = new HashMap<>();
    private final Map<String, Rectangle> mapRects = new HashMap<>();

    // Ini adalah LIST baru untuk mempertahankan urutan pilihan
    private final List<String> colorKeys = new ArrayList<>();
    private final List<String> foodKeys = new ArrayList<>();
    private final List<String> mapKeys = new ArrayList<>();

    private boolean isCustomizationActive = false; // Status tampilan kustomisasi
    private boolean isSelectingColor = true; // Melacak apakah sedang memilih warna atau makanan (untuk keyboard)
    
    // Customization category tracking: 0=color, 1=food, 2=map
    private int customCategory = 0;

    // --- WASD Menu Navigation ---
    private int menuSelectedIndex = 0; // 0=Start, 1=Custom, 2=Exit
    private static final int MENU_ITEM_COUNT = 3;
    
    // --- Smooth animation ---
    private float menuHoverScale[] = {1.0f, 1.0f, 1.0f};
    private float menuHoverTarget[] = {1.0f, 1.0f, 1.0f};
    
    // --- Particle system ---
    private List<Particle> particles = new ArrayList<>();
    private Random particleRandom = new Random();
    
    // --- Transition effect ---
    private float transitionAlpha = 1.0f; // Start fully black, fade in
    private boolean transitioningIn = true;
    private boolean transitioningOut = false;
    private Runnable transitionCallback = null;
    
    // --- Title animation ---
    private float titleGlow = 0f;
    private boolean titleGlowUp = true;

    // Map display names
    private final Map<String, String> mapDisplayNames = new HashMap<>();
    private final Map<String, String> mapDescriptions = new HashMap<>();

    public MainMenuPanel(JFrame frame) {
        this.parentFrame = frame;
        setLayout(null); // Penting untuk layout manual
    
        loadImages();
        loadGifs();
        initializeRectangles(); // Method ini akan mengisi colorKeys dan foodKeys juga
        initializeMapOptions();
        
        selectedFoodDisplayX = 570;
        selectedFoodDisplayY = 279;
        selectedFoodDisplayWidth = 45;
        selectedFoodDisplayHeight = 45;
    
        int buttonX = (713 - BUTTON_WIDTH) / 2; // Asumsi lebar frame 700px
        startButtonRect = new Rectangle(buttonX, START_BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT);
        customButtonRect = new Rectangle(buttonX, CUSTOM_BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT);
        exitButtonRect = new Rectangle(buttonX, EXIT_BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT);
        backButtonRect = new Rectangle(BACK_BUTTON_X, BACK_BUTTON_Y, BACK_BUTTON_WIDTH, BACK_BUTTON_HEIGHT);
    
        // Initialize particles
        for (int i = 0; i < 30; i++) {
            particles.add(new Particle(particleRandom, 713, 613));
        }
        
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent evt) {
                int x = evt.getX();
                int y = evt.getY();
    
                if (!isCustomizationActive) {
                    if (startButtonRect.contains(x, y)) {
                        startGameWithTransition();
                    } else if (customButtonRect.contains(x, y)) {
                        openCustomMenu();
                    } else if (exitButtonRect.contains(x, y)) {
                        System.exit(0);
                    }
                } else { // Customization is active
                    if (backButtonRect.contains(x, y)) {
                        closeCustomMenu();
                    } else {
                        handleCustomClick(x, y); // Ini juga akan mengatur isSelectingColor
                    }
                }
            }
        });
        
        // Mouse motion for hover effects
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent evt) {
                if (!isCustomizationActive) {
                    int x = evt.getX();
                    int y = evt.getY();
                    
                    if (startButtonRect.contains(x, y)) {
                        menuSelectedIndex = 0;
                    } else if (customButtonRect.contains(x, y)) {
                        menuSelectedIndex = 1;
                    } else if (exitButtonRect.contains(x, y)) {
                        menuSelectedIndex = 2;
                    }
                    updateHoverTargets();
                }
            }
        });
    
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int key = e.getKeyCode();
                
                if (key == KeyEvent.VK_ESCAPE) {
                    // If in customization menu, return to main menu
                    if (isCustomizationActive) {
                        closeCustomMenu();
                    } else {
                        // If in main menu, exit the application
                        System.exit(0);
                    }
                } else if (isCustomizationActive) {
                    handleCustomizationKeys(key);
                } else {
                    handleMainMenuKeys(key);
                }
            }
        });
    
        setFocusable(true);
        requestFocusInWindow(); // Pastikan panel ini mendapatkan fokus agar KeyListener bekerja
        
        // Animation timer - smooth 60fps
        Timer animTimer = new Timer(16, _ -> {
            updateAnimations();
            repaint();
        });
        animTimer.start();
    }
    
    private void handleMainMenuKeys(int key) {
        switch (key) {
            // W / Up = navigate up
            case KeyEvent.VK_W:
            case KeyEvent.VK_UP:
                menuSelectedIndex = (menuSelectedIndex - 1 + MENU_ITEM_COUNT) % MENU_ITEM_COUNT;
                updateHoverTargets();
                break;
            // S / Down = navigate down
            case KeyEvent.VK_S:
            case KeyEvent.VK_DOWN:
                menuSelectedIndex = (menuSelectedIndex + 1) % MENU_ITEM_COUNT;
                updateHoverTargets();
                break;
            // Enter / Space = select
            case KeyEvent.VK_ENTER:
            case KeyEvent.VK_SPACE:
                executeMenuSelection();
                break;
        }
    }
    
    private void handleCustomizationKeys(int key) {
        switch (key) {
            // A / Left = select previous item in current category
            case KeyEvent.VK_A:
            case KeyEvent.VK_LEFT:
                selectPrevious();
                break;
            // D / Right = select next item in current category
            case KeyEvent.VK_D:
            case KeyEvent.VK_RIGHT:
                selectNext();
                break;
            // W / Up = switch to previous category
            case KeyEvent.VK_W:
            case KeyEvent.VK_UP:
                customCategory = (customCategory - 1 + 3) % 3;
                updateCategoryState();
                repaint();
                break;
            // S / Down = switch to next category
            case KeyEvent.VK_S:
            case KeyEvent.VK_DOWN:
                customCategory = (customCategory + 1) % 3;
                updateCategoryState();
                repaint();
                break;
            case KeyEvent.VK_ENTER:
                // Confirm selection and return to main menu
                closeCustomMenu();
                break;
        }
    }
    
    private void updateCategoryState() {
        isSelectingColor = (customCategory == 0);
    }
    
    private void executeMenuSelection() {
        switch (menuSelectedIndex) {
            case 0: startGameWithTransition(); break;
            case 1: openCustomMenu(); break;
            case 2: System.exit(0); break;
        }
    }
    
    private void updateHoverTargets() {
        for (int i = 0; i < MENU_ITEM_COUNT; i++) {
            menuHoverTarget[i] = (i == menuSelectedIndex) ? 1.08f : 1.0f;
        }
    }
    
    private void updateAnimations() {
        // Smooth scale interpolation
        for (int i = 0; i < MENU_ITEM_COUNT; i++) {
            menuHoverScale[i] += (menuHoverTarget[i] - menuHoverScale[i]) * 0.15f;
        }
        
        // Update particles
        for (Particle p : particles) {
            p.update();
        }
        
        // Transition fade
        if (transitioningIn) {
            transitionAlpha -= 0.03f;
            if (transitionAlpha <= 0) {
                transitionAlpha = 0;
                transitioningIn = false;
            }
        }
        if (transitioningOut) {
            transitionAlpha += 0.05f;
            if (transitionAlpha >= 1.0f) {
                transitionAlpha = 1.0f;
                transitioningOut = false;
                if (transitionCallback != null) {
                    transitionCallback.run();
                    transitionCallback = null;
                }
            }
        }
        
        // Title glow
        if (titleGlowUp) {
            titleGlow += 0.02f;
            if (titleGlow >= 1.0f) titleGlowUp = false;
        } else {
            titleGlow -= 0.02f;
            if (titleGlow <= 0.0f) titleGlowUp = true;
        }
    }
    
    private void startGameWithTransition() {
        transitioningOut = true;
        transitionCallback = () -> startGame();
    }

// Add this method to the MainMenuPanel class
public boolean isInCustomizationMode() {
    return isCustomizationActive;
}

public void returnToMainMenu() {
    if (isCustomizationActive) {
        closeCustomMenu();
    }
}

    public String getSelectedMap() {
        return selectedMap;
    }

    private void selectPrevious() {
        if (customCategory == 0) {
            // Color selection
            int currentIndex = colorKeys.indexOf(selectedColor);
            if (currentIndex > 0) {
                selectedColor = colorKeys.get(currentIndex - 1);
            } else {
                selectedColor = colorKeys.get(colorKeys.size() - 1); // Melingkar ke item terakhir
            }
        } else if (customCategory == 1) {
            // Food selection
            int currentIndex = foodKeys.indexOf(selectedFood);
            if (currentIndex > 0) {
                selectedFood = foodKeys.get(currentIndex - 1);
            } else {
                selectedFood = foodKeys.get(foodKeys.size() - 1); // Melingkar ke item terakhir
            }
        } else {
            // Map selection
            int currentIndex = mapKeys.indexOf(selectedMap);
            if (currentIndex > 0) {
                selectedMap = mapKeys.get(currentIndex - 1);
            } else {
                selectedMap = mapKeys.get(mapKeys.size() - 1);
            }
        }
        repaint();
    }

    private void selectNext() {
        if (customCategory == 0) {
            int currentIndex = colorKeys.indexOf(selectedColor);
            if (currentIndex < colorKeys.size() - 1) {
                selectedColor = colorKeys.get(currentIndex + 1);
            } else {
                selectedColor = colorKeys.get(0); // Melingkar ke item pertama
            }
        } else if (customCategory == 1) {
            int currentIndex = foodKeys.indexOf(selectedFood);
            if (currentIndex < foodKeys.size() - 1) {
                selectedFood = foodKeys.get(currentIndex + 1);
            } else {
                selectedFood = foodKeys.get(0); // Melingkar ke item pertama
            }
        } else {
            int currentIndex = mapKeys.indexOf(selectedMap);
            if (currentIndex < mapKeys.size() - 1) {
                selectedMap = mapKeys.get(currentIndex + 1);
            } else {
                selectedMap = mapKeys.get(0);
            }
        }
        repaint();
    }

    private void toggleSelectionType() {
        if (isSelectingColor) {
            isSelectingColor = false; // Beralih ke pemilihan makanan
            // Pastikan ada food yang terpilih jika sebelumnya tidak ada
            if (selectedFood == null && !foodKeys.isEmpty()) {
                selectedFood = foodKeys.get(0);
            }
        } else {
            isSelectingColor = true; // Beralih ke pemilihan warna
            // Pastikan ada color yang terpilih jika sebelumnya tidak ada
            if (selectedColor == null && !colorKeys.isEmpty()) {
                selectedColor = colorKeys.get(0);
            }
        }
        repaint();
    }

    private void loadImages() {
        try {
            backgroundImage = new ImageIcon("IMG/Background1.png").getImage();
            backgroundImage2 = new ImageIcon("IMG/Background2.png").getImage();
            startButtonImage = new ImageIcon("IMG/Start.png").getImage();
            customButtonImage = new ImageIcon("IMG/Custom.png").getImage();
            exitButtonImage = new ImageIcon("IMG/Exit.png").getImage();
            backButtonImage = new ImageIcon("IMG/Back.png").getImage();

            redSnakeImage = new ImageIcon("IMG/RSnake.png").getImage();
            greenSnakeImage = new ImageIcon("IMG/GSnake.png").getImage();
            blueSnakeImage = new ImageIcon("IMG/BSnake.png").getImage();
            pinkSnakeImage = new ImageIcon("IMG/PSnake.png").getImage();
            appleImage = new ImageIcon("IMG/Apple.png").getImage();
            bananaImage = new ImageIcon("IMG/Banana.png").getImage();
            grapeImage = new ImageIcon("IMG/Grape.png").getImage();
            orangeImage = new ImageIcon("IMG/Orange.png").getImage();
            redSnake2Image = new ImageIcon("IMG/Snake2R.png").getImage();
            greenSnake2Image = new ImageIcon("IMG/Snake2G.png").getImage();
            blueSnake2Image = new ImageIcon("IMG/Snake2B.png").getImage();
            pinkSnake2Image = new ImageIcon("IMG/Snake2P.png").getImage();



            System.out.println("Gambar berhasil dimuat");
        } catch (Exception e) {
            System.out.println("Error memuat gambar: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadGifs() {
        try {
            gif1 = new ImageIcon("IMG/Snake.gif");
            gif2 = new ImageIcon("IMG/Flame.gif");
            gif4 = new ImageIcon("IMG/Chest.gif");
            gif1X = 445;
            gif1Y = 243;
            gif2X = 140;
            gif2Y = 260;
            gif3X = 550;
            gif3Y = 130;
            gif4X = -5;
            gif4Y = 365;
            System.out.println("GIF berhasil dimuat");
        } catch (Exception e) {
            System.out.println("Error memuat GIF: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void initializeRectangles() {
        // --- Warna Ular ---
        // Tambahkan ke HashMap dan List sesuai urutan di UI (kiri ke kanan)
        colorRects.put("red", new Rectangle(124, 163, 40, 42));
        colorKeys.add("red");
        colorRects.put("green", new Rectangle(238, 163, 40, 42));
        colorKeys.add("green");
        colorRects.put("blue", new Rectangle(354, 163, 40, 42));
        colorKeys.add("blue");
        colorRects.put("pink", new Rectangle(475, 163, 40, 42));
        colorKeys.add("pink");

        // --- Makanan Ular ---
        // Tambahkan ke HashMap dan List sesuai urutan di UI (kiri ke kanan)
        foodRects.put("apple", new Rectangle(124, 279, 45, 45));
        foodKeys.add("apple");
        foodRects.put("banana", new Rectangle(238, 279, 45, 45));
        foodKeys.add("banana");
        foodRects.put("grape", new Rectangle(354, 279, 45, 45));
        foodKeys.add("grape");
        foodRects.put("orange", new Rectangle(475, 279, 45, 45));
        foodKeys.add("orange");

        // Pastikan ada pilihan default yang aktif
        if (!colorKeys.isEmpty() && selectedColor == null) {
            selectedColor = colorKeys.get(0);
        }
        if (!foodKeys.isEmpty() && selectedFood == null) {
            selectedFood = foodKeys.get(0);
        }
        // Jika kustomisasi aktif, pastikan salah satu jenis (warna/makanan) disorot untuk keyboard
        if (isCustomizationActive && selectedColor == null && selectedFood == null) {
             isSelectingColor = true; // Default fokus ke warna
             selectedColor = colorKeys.get(0);
        }
    }
    
    private void initializeMapOptions() {
        // Map selection options - positioned below food selection
        int mapY = 395;
        mapRects.put("classic", new Rectangle(80, mapY, 100, 50));
        mapKeys.add("classic");
        mapRects.put("desert", new Rectangle(200, mapY, 100, 50));
        mapKeys.add("desert");
        mapRects.put("ocean", new Rectangle(320, mapY, 100, 50));
        mapKeys.add("ocean");
        mapRects.put("neon", new Rectangle(440, mapY, 100, 50));
        mapKeys.add("neon");
        
        // Display names
        mapDisplayNames.put("classic", "CLASSIC");
        mapDisplayNames.put("desert", "DESERT");
        mapDisplayNames.put("ocean", "OCEAN");
        mapDisplayNames.put("neon", "NEON");
        
        // Descriptions
        mapDescriptions.put("classic", "Lapangan hijau klasik");
        mapDescriptions.put("desert", "Padang pasir yang panas");
        mapDescriptions.put("ocean", "Lautan biru yang sejuk");
        mapDescriptions.put("neon", "Dunia neon futuristik");
    }

    private void handleCustomClick(int x, int y) {
        // Check color selections
        for (Map.Entry<String, Rectangle> entry : colorRects.entrySet()) {
            if (entry.getValue().contains(x, y)) {
                selectedColor = entry.getKey();
                customCategory = 0;
                isSelectingColor = true;
                repaint();
                return;
            }
        }
        
        // Check food selections
        for (Map.Entry<String, Rectangle> entry : foodRects.entrySet()) {
            if (entry.getValue().contains(x, y)) {
                selectedFood = entry.getKey();
                customCategory = 1;
                isSelectingColor = false;
                repaint();
                return;
            }
        }
        
        // Check map selections
        for (Map.Entry<String, Rectangle> entry : mapRects.entrySet()) {
            if (entry.getValue().contains(x, y)) {
                selectedMap = entry.getKey();
                customCategory = 2;
                repaint();
                return;
            }
        }
    }

    private void startGame() {
        try {
            parentFrame.getContentPane().removeAll();
            // Penting: Pastikan SnakePanel memiliki konstruktor yang menerima (String color, String food, String map)
            SnakePanel snakePanel = new SnakePanel(selectedColor, selectedFood, selectedMap);
            parentFrame.add(snakePanel);
            parentFrame.revalidate();
            parentFrame.repaint();
            snakePanel.requestFocusInWindow();
        } catch (Exception e) {
            System.out.println("Error starting game: " + e.getMessage());
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error starting game: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openCustomMenu() {
        System.out.println("Membuka menu kustomisasi...");
        isCustomizationActive = true;
        // Saat membuka menu kustomisasi, pastikan ada item yang disorot
        if (selectedColor == null && !colorKeys.isEmpty()) {
            selectedColor = colorKeys.get(0);
        }
        customCategory = 0;
        isSelectingColor = true; // Default fokus ke pemilihan warna
        repaint();
    }

    private void closeCustomMenu() {
        System.out.println("Kembali ke menu utama...");
        isCustomizationActive = false;
        repaint();
        requestFocusInWindow(); // Pastikan MainMenuPanel mendapatkan fokus kembali
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        if (isCustomizationActive) {
            // Gambar elemen untuk halaman kustomisasi
            if (backgroundImage2 != null) {
                g2d.drawImage(backgroundImage2, 0, 0, getWidth(), getHeight(), this);
            }

            drawCustomizationOptions(g2d);

             // Display the selected snake color
            Image currentSnake2Image = getSnake2ImageForColor(selectedColor);
            if (currentSnake2Image != null) {
                 g2d.drawImage(currentSnake2Image, gif3X, gif3Y, 100, 100, this);
            }
            
            // Draw map selection options
            drawMapOptions(g2d);
            
            // Display the selected food
            Image currentFoodImage = getFoodImageForType(selectedFood);
            if (currentFoodImage != null) {
                g2d.drawImage(currentFoodImage, selectedFoodDisplayX, selectedFoodDisplayY, selectedFoodDisplayWidth, selectedFoodDisplayHeight, this);
            }
            drawSelectedFood(g2d);
            if (gif4 != null) {
                g2d.drawImage(gif4.getImage(), gif4X, gif4Y, 80, 80, this);
            }

            // Gambar tombol "Back"
            if (backButtonImage != null) {
                g2d.drawImage(backButtonImage, BACK_BUTTON_X, BACK_BUTTON_Y, BACK_BUTTON_WIDTH, BACK_BUTTON_HEIGHT, this);
            } else {
                g2d.setColor(Color.GRAY);
                g2d.fillRect(BACK_BUTTON_X, BACK_BUTTON_Y, BACK_BUTTON_WIDTH, BACK_BUTTON_HEIGHT);
                g2d.setColor(Color.WHITE);
                g2d.drawString("Back", BACK_BUTTON_X + 30, BACK_BUTTON_Y + 25);
            }
            
            // Draw category indicator
            drawCategoryIndicator(g2d);
            
            // Draw control hints for customization
            drawCustomControlHints(g2d);
            
        } else {
            // Gambar elemen untuk halaman utama
            if (backgroundImage != null) {
                g2d.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), this);
            }
            
            // Draw particles
            drawParticles(g2d);

            if (gif1 != null) {
                gif1.paintIcon(this, g, gif1X, gif1Y);
            }

            if (gif2 != null) {
                gif2.paintIcon(this, g, gif2X, gif2Y);
            }

            int buttonX = (getWidth() - BUTTON_WIDTH) / 2;
            
            // Draw buttons with hover animation
            drawAnimatedButton(g2d, startButtonImage, buttonX, START_BUTTON_Y, 0, "START");
            drawAnimatedButton(g2d, customButtonImage, buttonX, CUSTOM_BUTTON_Y, 1, "CUSTOM");
            drawAnimatedButton(g2d, exitButtonImage, buttonX, EXIT_BUTTON_Y, 2, "EXIT");
            
            // Draw selection indicator arrow
            drawSelectionArrow(g2d, buttonX);
            
            // Draw control hints
            drawControlHints(g2d);
        }

        // Gambar border untuk debug (setelah kamu yakin posisi benar, bisa dihapus atau diubah warna transparan)
        g2d.setColor(new Color(0, 0, 0, 0)); // Transparan
        g2d.draw(startButtonRect);
        g2d.draw(customButtonRect);
        g2d.draw(exitButtonRect);
        g2d.draw(backButtonRect);
        
        // Draw transition overlay
        if (transitionAlpha > 0) {
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1.0f, Math.max(0f, transitionAlpha))));
            g2d.setColor(Color.BLACK);
            g2d.fillRect(0, 0, getWidth(), getHeight());
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        }
    }
    
    private void drawAnimatedButton(Graphics2D g2d, Image btnImage, int x, int y, int index, String fallbackText) {
        float scale = menuHoverScale[index];
        int scaledW = (int)(BUTTON_WIDTH * scale);
        int scaledH = (int)(BUTTON_HEIGHT * scale);
        int offsetX = x - (scaledW - BUTTON_WIDTH) / 2;
        int offsetY = y - (scaledH - BUTTON_HEIGHT) / 2;
        
        // Glow effect for selected button
        if (index == menuSelectedIndex) {
            Composite oldComp = g2d.getComposite();
            float glowAlpha = 0.3f + titleGlow * 0.2f;
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, glowAlpha));
            g2d.setColor(new Color(255, 255, 200));
            for (int i = 8; i > 0; i--) {
                g2d.fillRoundRect(offsetX - i, offsetY - i, scaledW + i * 2, scaledH + i * 2, 15, 15);
            }
            g2d.setComposite(oldComp);
        }
        
        if (btnImage != null) {
            g2d.drawImage(btnImage, offsetX, offsetY, scaledW, scaledH, this);
        } else {
            Color bgColor = index == 0 ? Color.GREEN : (index == 1 ? Color.BLUE : Color.RED);
            g2d.setColor(bgColor);
            g2d.fillRoundRect(offsetX, offsetY, scaledW, scaledH, 10, 10);
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.BOLD, 18));
            FontMetrics fm = g2d.getFontMetrics();
            g2d.drawString(fallbackText, offsetX + (scaledW - fm.stringWidth(fallbackText)) / 2, offsetY + scaledH / 2 + 6);
        }
    }
    
    private void drawSelectionArrow(Graphics2D g2d, int buttonX) {
        int arrowX = buttonX - 35;
        int arrowY;
        switch (menuSelectedIndex) {
            case 0: arrowY = START_BUTTON_Y + BUTTON_HEIGHT / 2; break;
            case 1: arrowY = CUSTOM_BUTTON_Y + BUTTON_HEIGHT / 2; break;
            case 2: arrowY = EXIT_BUTTON_Y + BUTTON_HEIGHT / 2; break;
            default: arrowY = START_BUTTON_Y + BUTTON_HEIGHT / 2;
        }
        
        // Pulsing arrow
        float pulse = (float) Math.sin(System.currentTimeMillis() * 0.005) * 3;
        int ax = (int)(arrowX + pulse);
        
        g2d.setColor(new Color(255, 255, 100));
        int[] xPoints = {ax, ax - 12, ax - 12};
        int[] yPoints = {arrowY, arrowY - 8, arrowY + 8};
        g2d.fillPolygon(xPoints, yPoints, 3);
        
        // Arrow glow
        Composite old = g2d.getComposite();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
        g2d.setColor(new Color(255, 255, 100));
        int[] xp2 = {ax + 3, ax - 15, ax - 15};
        int[] yp2 = {arrowY, arrowY - 11, arrowY + 11};
        g2d.fillPolygon(xp2, yp2, 3);
        g2d.setComposite(old);
    }
    
    private void drawControlHints(Graphics2D g2d) {
        g2d.setFont(new Font("Arial", Font.BOLD, 11));
        g2d.setColor(new Color(255, 255, 255, 180));
        
        String hint = "[W/S] Navigasi   [ENTER] Pilih   [ESC] Keluar";
        FontMetrics fm = g2d.getFontMetrics();
        int hintWidth = fm.stringWidth(hint);
        
        // Background for hint
        g2d.setColor(new Color(0, 0, 0, 120));
        g2d.fillRoundRect((getWidth() - hintWidth) / 2 - 10, getHeight() - 32, hintWidth + 20, 22, 8, 8);
        
        g2d.setColor(new Color(255, 255, 255, 200));
        g2d.drawString(hint, (getWidth() - hintWidth) / 2, getHeight() - 16);
    }
    
    private void drawCustomControlHints(Graphics2D g2d) {
        g2d.setFont(new Font("Arial", Font.BOLD, 10));
        
        String hint = "[W/S] Kategori   [A/D] Pilih   [ENTER] Simpan   [ESC] Kembali";
        FontMetrics fm = g2d.getFontMetrics();
        int hintWidth = fm.stringWidth(hint);
        
        g2d.setColor(new Color(0, 0, 0, 150));
        g2d.fillRoundRect((getWidth() - hintWidth) / 2 - 10, getHeight() - 30, hintWidth + 20, 20, 8, 8);
        
        g2d.setColor(new Color(255, 255, 255, 220));
        g2d.drawString(hint, (getWidth() - hintWidth) / 2, getHeight() - 15);
    }
    
    private void drawParticles(Graphics2D g2d) {
        Composite old = g2d.getComposite();
        for (Particle p : particles) {
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, p.alpha));
            g2d.setColor(p.color);
            g2d.fillOval((int)p.x, (int)p.y, (int)p.size, (int)p.size);
        }
        g2d.setComposite(old);
    }
    
    private void drawCategoryIndicator(Graphics2D g2d) {
        // Draw small indicator showing which category is active
        String[] categories = {"▶ WARNA ULAR", "▶ MAKANAN", "▶ MAP"};
        int indicatorX = 560;
        int indicatorStartY = 163;
        
        g2d.setFont(new Font("Arial", Font.BOLD, 11));
        
        for (int i = 0; i < categories.length; i++) {
            int iy = indicatorStartY + i * 116;
            if (i == customCategory) {
                g2d.setColor(Color.WHITE);
            } else {
                g2d.setColor(new Color(255, 255, 255, 120));
            }
            g2d.drawString(categories[i], indicatorX, iy);
        }
    }
    
    private void drawMapOptions(Graphics2D g2d) {
        // Draw map category label
        g2d.setFont(new Font("Arial", Font.BOLD, 14));
        g2d.setColor(new Color(255, 255, 255, 220));
        g2d.drawString("PILIH MAP:", 80, 385);
        
        for (String key : mapKeys) {
            Rectangle rect = mapRects.get(key);
            boolean isSelected = key.equals(selectedMap);
            boolean isCategoryActive = (customCategory == 2);
            
            drawMapOptionBox(g2d, rect, key, isSelected, isCategoryActive);
        }
        
        // Draw map description
        if (selectedMap != null && mapDescriptions.containsKey(selectedMap)) {
            g2d.setFont(new Font("Arial", Font.ITALIC, 12));
            g2d.setColor(new Color(255, 255, 200, 200));
            g2d.drawString(mapDescriptions.get(selectedMap), 80, 460);
        }
    }
    
    private void drawMapOptionBox(Graphics2D g2d, Rectangle rect, String key, boolean isSelected, boolean isCategoryActive) {
        // Get map theme colors
        Color[] mapColors = getMapPreviewColors(key);
        
        // Draw mini preview of the map grid
        int cellSize = 10;
        int cols = rect.width / cellSize;
        int rows = rect.height / cellSize;
        
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if ((r + c) % 2 == 0) {
                    g2d.setColor(mapColors[0]);
                } else {
                    g2d.setColor(mapColors[1]);
                }
                g2d.fillRect(rect.x + c * cellSize, rect.y + r * cellSize, cellSize, cellSize);
            }
        }
        
        // Draw map name overlay
        g2d.setColor(new Color(0, 0, 0, 140));
        g2d.fillRect(rect.x, rect.y + rect.height - 18, rect.width, 18);
        g2d.setFont(new Font("Arial", Font.BOLD, 11));
        g2d.setColor(Color.WHITE);
        String name = mapDisplayNames.get(key);
        FontMetrics fm = g2d.getFontMetrics();
        g2d.drawString(name, rect.x + (rect.width - fm.stringWidth(name)) / 2, rect.y + rect.height - 5);
        
        // Selection highlight
        if (isSelected) {
            // Glow effect
            Composite oldComp = g2d.getComposite();
            Color glowColor = new Color(255, 255, 100, 150);
            for (int i = 5; i > 0; i--) {
                float alpha = i / 5.0f * 0.5f;
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                g2d.setColor(glowColor);
                g2d.fillRoundRect(rect.x - i, rect.y - i, rect.width + 2 * i, rect.height + 2 * i, 6, 6);
            }
            g2d.setComposite(oldComp);
            
            // Border
            g2d.setColor(Color.WHITE);
            g2d.setStroke(new BasicStroke(3));
            g2d.drawRoundRect(rect.x - 2, rect.y - 2, rect.width + 4, rect.height + 4, 6, 6);
        }
        
        // Category active indicator
        if (isCategoryActive && !isSelected) {
            g2d.setColor(new Color(255, 255, 255, 60));
            g2d.setStroke(new BasicStroke(1));
            g2d.drawRoundRect(rect.x, rect.y, rect.width, rect.height, 4, 4);
        }
    }
    
    private Color[] getMapPreviewColors(String mapKey) {
        switch (mapKey) {
            case "classic":
                return new Color[]{new Color(150, 240, 130), new Color(120, 210, 100)};
            case "desert":
                return new Color[]{new Color(237, 201, 140), new Color(210, 175, 115)};
            case "ocean":
                return new Color[]{new Color(100, 180, 220), new Color(70, 150, 195)};
            case "neon":
                return new Color[]{new Color(30, 30, 50), new Color(20, 20, 40)};
            default:
                return new Color[]{new Color(150, 240, 130), new Color(120, 210, 100)};
        }
    }
    
    private Image getFoodImageForType(String foodType) {
        switch (foodType) {
            case "apple":
                return appleImage;
            case "banana":
                return bananaImage;
            case "grape":
                return grapeImage;
            case "orange":
                return orangeImage;
            default:
                return appleImage; // Default to apple if food type is not recognized
        }
    }
    private void drawSelectedFood(Graphics2D g2d) {
        Image currentFoodImage = getFoodImageForType(selectedFood);
        if (currentFoodImage != null) {
            // Create a glowing effect around the selected food
            int glowSize = 10;
            Color glowColor = getFoodGlowColor(selectedFood);
            
            // Draw glow effect
            for (int i = glowSize; i > 0; i--) {
                float alpha = i / (float)glowSize * 0.5f;
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                g2d.setColor(glowColor);
                g2d.fillOval(
                    selectedFoodDisplayX - i, 
                    selectedFoodDisplayY - i, 
                    selectedFoodDisplayWidth + 2*i, 
                    selectedFoodDisplayHeight + 2*i
                );
            }
            
            // Reset composite to fully opaque
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
            
            // Draw the food image
            g2d.drawImage(currentFoodImage, selectedFoodDisplayX, selectedFoodDisplayY, 
                          selectedFoodDisplayWidth, selectedFoodDisplayHeight, this);
        }
    }
    
    private Color getFoodGlowColor(String foodType) {
        switch (foodType) {
            case "apple":
                return new Color(255, 0, 0, 100); // Red glow
            case "banana":
                return new Color(255, 255, 0, 100); // Yellow glow
            case "grape":
                return new Color(128, 0, 128, 100); // Purple glow
            case "orange":
                return new Color(255, 165, 0, 100); // Orange glow
            default:
                return new Color(255, 255, 255, 100); // White glow
        }
    }
    private Image getSnake2ImageForColor(String color) {
        switch (color) {
            case "red":
                return redSnake2Image;
            case "green":
                return greenSnake2Image;
            case "blue":
                return blueSnake2Image;
            case "pink":
                return pinkSnake2Image;
            default:
                return greenSnake2Image; // Default to green if color is not recognized
        }
    }
    private void drawImageWithSelection(Graphics2D g2d, Image image, Rectangle rect, String key, boolean isSelected) {
    // Gambar background kotak item
    // Ini akan menutupi focus border default Swing dan memberikan background solid.
    // Jika Anda ingin background kotak transparan (agar gambar Background2.png terlihat di belakang),
    // gunakan alpha value yang lebih rendah (misal, 50, 100, atau 150).
    g2d.setColor(new Color(50, 50, 50, 0)); // KRITIS: Alpha 0 agar background kotak sepenuhnya transparan
    g2d.fillRoundRect(rect.x, rect.y, rect.width, rect.height, 10, 10);

    // KRITIS: HAPUS kode untuk border putih default jika tidak ingin ada border sama sekali.
    // Jika Anda TIDAK ingin border putih default, JANGAN tambahkan baris berikut:
    // g2d.setColor(Color.WHITE);
    // g2d.setStroke(new BasicStroke(2));
    // g2d.drawRoundRect(rect.x, rect.y, rect.width, rect.height, 10, 10);

    // Gambar gambar item itu sendiri (ular/makanan) di atas background
    if (image != null) {
        g2d.drawImage(image, rect.x, rect.y, rect.width, rect.height, this);
    } else {
        // Fallback jika gambar null
        g2d.setColor(Color.GRAY);
        g2d.fillRect(rect.x, rect.y, rect.width, rect.height);
        g2d.setColor(Color.BLACK);
        g2d.drawString(key, rect.x + 5, rect.y + rect.height / 2);
    }

    // Logika gambar highlight (glow) dan border seleksi (HANYA jika item terpilih)
    if (isSelected) {
        int glowSize = 5;
        Color glowColor;

        // Pilih warna glow berdasarkan kategori dan kunci
        if (colorKeys.contains(key)) {
            switch (key) {
                case "red": glowColor = new Color(255, 0, 0, 150); break;
                case "green": glowColor = new Color(0, 255, 0, 150); break;
                case "blue": glowColor = new Color(0, 0, 255, 150); break;
                case "pink": glowColor = new Color(255, 105, 180, 150); break;
                default: glowColor = new Color(255, 255, 255, 150); break;
            }
        } else {
            switch (key) {
                case "apple": glowColor = new Color(255, 0, 0, 150); break;
                case "banana": glowColor = new Color(255, 255, 0, 150); break;
                case "grape": glowColor = new Color(128, 0, 128, 150); break;
                case "orange": glowColor = new Color(255, 165, 0, 150); break;
                default: glowColor = new Color(255, 255, 255, 150); break;
            }
        }

        // Gambar efek glow
        Composite originalComposite = g2d.getComposite();
        for (int i = glowSize; i > 0; i--) {
            float alpha = i / (float)glowSize * 0.7f;
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g2d.setColor(glowColor);
            g2d.fillRoundRect(
                rect.x - i, rect.y - i, rect.width + 2 * i, rect.height + 2 * i,
                10, 10
            );
        }
        g2d.setComposite(originalComposite);

        // KRITIS: Gambar border highlight putih (bukan kuning)
        g2d.setColor(Color.WHITE); // Warna putih untuk border seleksi
        g2d.setStroke(new BasicStroke(4)); // Ketebalan 4 piksel
        g2d.drawRoundRect(rect.x - 2, rect.y - 2, rect.width + 4, rect.height + 4, 10, 10); // Gambar dengan sudut bulat
    }
}
       
    private void drawCustomizationOptions(Graphics2D g2d) {
        // Draw snake color options
        drawImageWithSelection(g2d, redSnakeImage, colorRects.get("red"), "red", selectedColor.equals("red"));
        drawImageWithSelection(g2d, greenSnakeImage, colorRects.get("green"), "green", selectedColor.equals("green"));
        drawImageWithSelection(g2d, blueSnakeImage, colorRects.get("blue"), "blue", selectedColor.equals("blue"));
        drawImageWithSelection(g2d, pinkSnakeImage, colorRects.get("pink"), "pink", selectedColor.equals("pink"));
    
        // Draw food options
        drawImageWithSelection(g2d, appleImage, foodRects.get("apple"), "apple", selectedFood.equals("apple"));
        drawImageWithSelection(g2d, bananaImage, foodRects.get("banana"), "banana", selectedFood.equals("banana"));
        drawImageWithSelection(g2d, grapeImage, foodRects.get("grape"), "grape", selectedFood.equals("grape"));
        drawImageWithSelection(g2d, orangeImage, foodRects.get("orange"), "orange", selectedFood.equals("orange"));
        
        // Draw a navigation box around the currently active selection category
        g2d.setColor(new Color(255, 255, 100, 80));
        g2d.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{6, 4}, 0));
        
        if (customCategory == 0) {
            // Draw box around the color selection area
            Rectangle firstColorRect = colorRects.get(colorKeys.get(0));
            Rectangle lastColorRect = colorRects.get(colorKeys.get(colorKeys.size() - 1));
            int x = firstColorRect.x - 10;
            int y = firstColorRect.y - 10;
            int width = (lastColorRect.x + lastColorRect.width) - firstColorRect.x + 20;
            int height = firstColorRect.height + 20;
            g2d.drawRoundRect(x, y, width, height, 8, 8);
        } else if (customCategory == 1) {
            // Draw box around the food selection area
            Rectangle firstFoodRect = foodRects.get(foodKeys.get(0));
            Rectangle lastFoodRect = foodRects.get(foodKeys.get(foodKeys.size() - 1));
            int x = firstFoodRect.x - 10;
            int y = firstFoodRect.y - 10;
            int width = (lastFoodRect.x + lastFoodRect.width) - firstFoodRect.x + 20;
            int height = firstFoodRect.height + 20;
            g2d.drawRoundRect(x, y, width, height, 8, 8);
        } else {
            // Draw box around the map selection area
            Rectangle firstMapRect = mapRects.get(mapKeys.get(0));
            Rectangle lastMapRect = mapRects.get(mapKeys.get(mapKeys.size() - 1));
            int x = firstMapRect.x - 10;
            int y = firstMapRect.y - 10;
            int width = (lastMapRect.x + lastMapRect.width) - firstMapRect.x + 20;
            int height = firstMapRect.height + 20;
            g2d.drawRoundRect(x, y, width, height, 8, 8);
        }
        
        // Reset stroke
        g2d.setStroke(new BasicStroke(1));
    }
    
    // --- Particle inner class ---
    private static class Particle {
        float x, y, vx, vy, size, alpha;
        Color color;
        Random rand;
        int screenW, screenH;
        
        Particle(Random rand, int screenW, int screenH) {
            this.rand = rand;
            this.screenW = screenW;
            this.screenH = screenH;
            reset();
        }
        
        void reset() {
            x = rand.nextFloat() * screenW;
            y = rand.nextFloat() * screenH;
            vx = (rand.nextFloat() - 0.5f) * 0.8f;
            vy = -rand.nextFloat() * 0.5f - 0.2f;
            size = rand.nextFloat() * 4 + 1;
            alpha = rand.nextFloat() * 0.4f + 0.1f;
            
            int type = rand.nextInt(3);
            switch (type) {
                case 0: color = new Color(255, 255, 200); break;
                case 1: color = new Color(200, 255, 200); break;
                case 2: color = new Color(200, 220, 255); break;
            }
        }
        
        void update() {
            x += vx;
            y += vy;
            alpha -= 0.002f;
            
            if (alpha <= 0 || y < -10 || x < -10 || x > screenW + 10) {
                reset();
                y = screenH + 10;
                alpha = rand.nextFloat() * 0.4f + 0.1f;
            }
        }
    }
}