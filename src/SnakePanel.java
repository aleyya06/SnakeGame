import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.swing.*;
import java.util.Random;
import java.util.ArrayList;
import java.util.List;

public class SnakePanel extends JPanel implements ActionListener {

    static final int SCREEN_WIDTH = 713;
    static final int SCREEN_HEIGHT = 613;
    static final int UNIT_SIZE = 25;
    static final int GAME_UNITS = (SCREEN_WIDTH * SCREEN_HEIGHT) / (UNIT_SIZE * UNIT_SIZE);
    static final int DELAY = 150;
    static final String BEST_SCORE_FILE = "best_score.dat";

    final int x[] = new int[GAME_UNITS];
    final int y[] = new int[GAME_UNITS];

    int bestScore = 0;
    int bodyParts;
    int applesEaten;
    int appleX;
    int appleY;

    char direction = 'R';
    boolean running;
    Timer timer;
    Random random;

    private String selectedColor = "green";
    private String selectedFood = "apple";
    private String selectedMap = "classic";
    private boolean paused = false;
    private Image pauseOverlay;
    private Rectangle resumeBtnRect;
    private Rectangle mainMenuBtnRect;
    
    // Images for snake parts
    private Image snakeHeadRight;
    private Image snakeHeadLeft;
    private Image snakeHeadUp;
    private Image snakeHeadDown;
    private Image snakeBody;
    private Image snakeTail;
    
    // Images for food
    private Image appleImage;
    private Image bananaImage;
    private Image grapeImage;
    private Image orangeImage;
    private Image foodImage;
    
    
    // Current food image
    private Image currentFoodImage;
    private JFrame parentFrame;
    
    // --- Smooth movement interpolation ---
    private float[] displayX;
    private float[] displayY;
    private float interpolation = 1.0f;
    private int[] prevX;
    private int[] prevY;
    
    // --- Particle effects ---
    private List<FoodParticle> foodParticles = new ArrayList<>();
    
    // --- Screen shake ---
    private float shakeX = 0, shakeY = 0;
    private float shakeMagnitude = 0;
    
    // --- Food animation ---
    private float foodBob = 0;
    private float foodScale = 1.0f;
    
    // --- Score popup ---
    private List<ScorePopup> scorePopups = new ArrayList<>();
    
    // --- Transition effect ---
    private float transitionAlpha = 1.0f;
    private boolean transitioningIn = true;
    
    // --- Countdown ---
    private int countdown = 3;
    private boolean countdownActive = true;
    private Timer countdownTimer;
    
    // --- Neon map glow effect ---
    private float neonPulse = 0;
    private boolean neonPulseUp = true;
    
    // --- Input queue to prevent missed inputs ---
    private char queuedDirection = 0;
    private boolean directionChangedThisTick = false;
    
    // --- Pause menu selection ---
    private int pauseSelectedIndex = 0; // 0 = Resume, 1 = Main Menu

    // Legacy constructor for backward compatibility
    public SnakePanel(String selectedColor, String selectedFood) {
        this(selectedColor, selectedFood, "classic");
    }

    public SnakePanel(String selectedColor, String selectedFood, String selectedMap) {
        this.selectedColor = selectedColor != null ? selectedColor : "green";
        this.selectedFood = selectedFood != null ? selectedFood : "apple";
        this.selectedMap = selectedMap != null ? selectedMap : "classic";
        
        random = new Random();
        displayX = new float[GAME_UNITS];
        displayY = new float[GAME_UNITS];
        prevX = new int[GAME_UNITS];
        prevY = new int[GAME_UNITS];
        
        setPreferredSize(new Dimension(SCREEN_WIDTH, SCREEN_HEIGHT));
        setBackground(Color.black);
        setFocusable(true);
        setDoubleBuffered(true);
        addKeyListener(new MyKeyAdapter());
        addMouseListener(new MyMouseAdapter());
        
        // Get parent frame reference
        parentFrame = (JFrame) SwingUtilities.getWindowAncestor(this);
        if (parentFrame == null) {
            parentFrame = (JFrame) SwingUtilities.getRoot(this);
        }
        
        // Load images
        loadImages();
        updateFoodImage();
        
        // Load best score
        loadBestScore();
        
        // Initialize pause menu
        initializePauseMenu();
        
        // Start countdown then game
        initGame();
        startCountdown();
    }
    
    private void startCountdown() {
        countdownActive = true;
        countdown = 3;
        paused = true; // Pause game during countdown
        if (timer != null) timer.stop();
        
        countdownTimer = new Timer(800, _ -> {
            countdown--;
            if (countdown <= 0) {
                countdownActive = false;
                countdownTimer.stop();
                paused = false;
                startGame();
            }
            repaint();
        });
        countdownTimer.setInitialDelay(800);
        countdownTimer.start();
        
        // Start render timer for smooth animations during countdown
        Timer renderTimer = new Timer(16, _ -> repaint());
        renderTimer.start();
    }


    private void loadImages() {
        try {
            // Load snake images based on selected color
            switch (selectedColor) {
                case "red":
                    snakeHeadRight = new ImageIcon("IMG/RSnake.png").getImage();
                    snakeBody = new ImageIcon("IMG/RSnake.png").getImage();
                    break;
                case "blue":
                    snakeHeadRight = new ImageIcon("IMG/BSnake.png").getImage();
                    snakeBody = new ImageIcon("IMG/BSnake.png").getImage();
                    break;
                case "pink":
                    snakeHeadRight = new ImageIcon("IMG/PSnake.png").getImage();
                    snakeBody = new ImageIcon("IMG/PSnake.png").getImage();
                    break;
                default: // "green" or any other case
                    snakeHeadRight = new ImageIcon("IMG/GSnake.png").getImage();
                    snakeBody = new ImageIcon("IMG/GSnake.png").getImage();
                    break;
            }
            
            // For now, use the same image for all directions
            snakeHeadLeft = snakeHeadRight;
            snakeHeadUp = snakeHeadRight;
            snakeHeadDown = snakeHeadRight;
            snakeTail = snakeBody;
            
            // Load food images
            appleImage = new ImageIcon("IMG/Apple.png").getImage();
            bananaImage = new ImageIcon("IMG/Banana.png").getImage();
            grapeImage = new ImageIcon("IMG/Grape.png").getImage();
            orangeImage = new ImageIcon("IMG/Orange.png").getImage();
            
            // Load pause overlay
            pauseOverlay = createDefaultPauseOverlay();
            
            System.out.println("Images loaded successfully");
        } catch (Exception e) {
            System.out.println("Error loading images: " + e.getMessage());
            e.printStackTrace();
            
            // Create default colored rectangles for snake parts if images fail to load
            createDefaultSnakeImages();
        }
    }
    private Image createDefaultPauseOverlay() {
        BufferedImage overlay = new BufferedImage(SCREEN_WIDTH, SCREEN_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = overlay.createGraphics();
        g2d.setColor(new Color(0, 0, 0, 150));
        g2d.fillRect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);
        g2d.dispose();
        return overlay;
    }
    
    private void createDefaultSnakeImages() {
        // Create default colored images for snake parts
        BufferedImage defaultImage = new BufferedImage(UNIT_SIZE, UNIT_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = defaultImage.createGraphics();
        g2d.setColor(getSnakeColor(selectedColor, 0));
        g2d.fillRect(0, 0, UNIT_SIZE, UNIT_SIZE);
        g2d.dispose();
        
        snakeHeadRight = defaultImage;
        snakeHeadLeft = defaultImage;
        snakeHeadUp = defaultImage;
        snakeHeadDown = defaultImage;
        snakeBody = defaultImage;
        snakeTail = defaultImage;
    }
    

    private void updateFoodImage() {
        try {
            switch (selectedFood) {
                case "banana":
                    foodImage = new ImageIcon("IMG/Banana.png").getImage();
                    break;
                case "grape":
                    foodImage = new ImageIcon("IMG/Grape.png").getImage();
                    break;
                case "orange":
                    foodImage = new ImageIcon("IMG/Orange.png").getImage();
                    break;
                default: // "apple" or any other case
                    foodImage = new ImageIcon("IMG/Apple.png").getImage();
                    break;
            }
            System.out.println("Food image updated to: " + selectedFood);
        } catch (Exception e) {
            System.out.println("Error loading food image: " + e.getMessage());
            e.printStackTrace();
            foodImage = null; // Set to null so we can use a fallback drawing method
        }
    }
    

private void drawFood(Graphics g) {
    Graphics2D g2d = (Graphics2D) g;
    
    // Bobbing animation
    foodBob += 0.08f;
    float bobOffset = (float) Math.sin(foodBob) * 3;
    float scaleOscillation = 1.0f + (float) Math.sin(foodBob * 1.5f) * 0.05f;
    
    int drawX = appleX;
    int drawY = (int)(appleY + bobOffset);
    int drawSize = (int)(UNIT_SIZE * scaleOscillation);
    int offset = (drawSize - UNIT_SIZE) / 2;
    
    // Glow effect behind food
    Composite oldComp = g2d.getComposite();
    Color glowColor = getFoodGlowColor(selectedFood);
    for (int i = 6; i > 0; i--) {
        float alpha = i / 6.0f * 0.15f;
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g2d.setColor(glowColor);
        g2d.fillOval(drawX - i - offset, drawY - i - offset, drawSize + i * 2, drawSize + i * 2);
    }
    g2d.setComposite(oldComp);
    
    if (foodImage != null) {
        // Draw the food image
        g2d.drawImage(foodImage, drawX - offset, drawY - offset, drawSize, drawSize, this);
    } else {
        // Fallback: Draw a colored oval if image is not available
        g2d.setColor(getFoodColor(selectedFood));
        g2d.fillOval(drawX - offset, drawY - offset, drawSize, drawSize);
    }
    
    // Shine effect
    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
    g2d.setColor(Color.WHITE);
    g2d.fillOval(drawX + UNIT_SIZE / 4, drawY + UNIT_SIZE / 4, UNIT_SIZE / 4, UNIT_SIZE / 4);
    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
}
    
    private Color getFoodGlowColor(String food) {
        switch (food) {
            case "banana": return new Color(255, 255, 0, 100);
            case "grape": return new Color(128, 0, 128, 100);
            case "orange": return new Color(255, 165, 0, 100);
            default: return new Color(255, 0, 0, 100);
        }
    }

private void loadBestScore() {
    try {
        if (new File(BEST_SCORE_FILE).exists()) {
            ObjectInputStream ois = new ObjectInputStream(new FileInputStream(BEST_SCORE_FILE));
            bestScore = (Integer) ois.readObject();
            ois.close();
        }
    } catch (Exception e) {
        System.out.println("Error loading best score: " + e.getMessage());
        e.printStackTrace();
    }
}

public void initGame() {
    bodyParts = 6;
    applesEaten = 0;
    direction = 'R';
    running = true;

    // Position snake at the center of the screen
    int maxStartX = (SCREEN_WIDTH / UNIT_SIZE) - bodyParts;
    int maxStartY = (SCREEN_HEIGHT / UNIT_SIZE);
    
    int startX = random.nextInt(maxStartX) * UNIT_SIZE;
    int startY = random.nextInt(maxStartY) * UNIT_SIZE;
    
    // Initialize snake position
    for (int i = 0; i < bodyParts; i++) {
        x[i] = startX - (i * UNIT_SIZE);
        y[i] = startY;
        displayX[i] = x[i];
        displayY[i] = y[i];
        prevX[i] = x[i];
        prevY[i] = y[i];
    }

    // Generate new apple
    newApple();
    
    // Start the game timer
    startGame();
}

public void startGame() {
    running = true;
    paused = false;
    if (timer != null) {
        timer.stop();
    }
    timer = new Timer(DELAY, this);
    timer.start();
    
    // High-frequency render timer for smooth animations
    Timer renderTimer = new Timer(16, _ -> {
        updateRenderState();
        repaint();
    });
    renderTimer.start();
    
    // Request focus to ensure key events are captured
    requestFocusInWindow();
    
    // Debug information
    System.out.println("Game started");
    printSnakeState();
}
   
    private void updateRenderState() {
        // Smooth screen shake decay
        if (shakeMagnitude > 0) {
            shakeX = (float)(Math.random() - 0.5) * shakeMagnitude * 2;
            shakeY = (float)(Math.random() - 0.5) * shakeMagnitude * 2;
            shakeMagnitude *= 0.85f;
            if (shakeMagnitude < 0.5f) {
                shakeMagnitude = 0;
                shakeX = 0;
                shakeY = 0;
            }
        }
        
        // Update food particles
        foodParticles.removeIf(p -> p.alpha <= 0);
        for (FoodParticle p : foodParticles) {
            p.update();
        }
        
        // Update score popups
        scorePopups.removeIf(p -> p.alpha <= 0);
        for (ScorePopup p : scorePopups) {
            p.update();
        }
        
        // Transition fade in
        if (transitioningIn) {
            transitionAlpha -= 0.04f;
            if (transitionAlpha <= 0) {
                transitionAlpha = 0;
                transitioningIn = false;
            }
        }
        
        // Neon pulse
        if (neonPulseUp) {
            neonPulse += 0.02f;
            if (neonPulse >= 1.0f) neonPulseUp = false;
        } else {
            neonPulse -= 0.02f;
            if (neonPulse <= 0.0f) neonPulseUp = true;
        }
    }

public void checkCollisions() {
    // Check if head collides with body
    for (int i = bodyParts; i > 0; i--) {
        if ((x[0] == x[i]) && (y[0] == y[i])) {
            running = false;
            break;
        }
    }
    
    // Check if head touches or exceeds any border
    if (x[0] < 0 || x[0] >= 680 || y[0] < 0 || y[0] >= 560) {
        running = false;
        System.out.println("Snake hit the wall at: (" + x[0] + ", " + y[0] + ")");
    }
    
    // If game is not running anymore, stop the timer
    if (!running) {
        timer.stop();
        // Screen shake on death
        shakeMagnitude = 10;
        // Play game over sound
        playSound("gameover");
        // Show game over panel
        showGameOverPanel();
    }
}
    @Override
    public void actionPerformed(ActionEvent e) {
        if (running && !paused) {
            // Save previous positions for interpolation
            for (int i = 0; i < bodyParts; i++) {
                prevX[i] = x[i];
                prevY[i] = y[i];
            }
            
            // Apply queued direction
            if (queuedDirection != 0) {
                direction = queuedDirection;
                queuedDirection = 0;
            }
            directionChangedThisTick = false;
            
            move();
            checkApple();
            checkCollisions();
            
            // Update display positions immediately after move
            for (int i = 0; i < bodyParts; i++) {
                displayX[i] = x[i];
                displayY[i] = y[i];
            }
        }
        repaint();
    }
    
        // Tambahkan metode untuk debugging
        private void printSnakeState() {
            System.out.println("Snake state:");
            System.out.println("Head position: (" + x[0] + ", " + y[0] + ")");
            System.out.println("Direction: " + direction);
            System.out.println("Body parts: " + bodyParts);
            System.out.println("Running: " + running);
            System.out.println("Paused: " + paused);
        }
    
        // Perbaiki MyKeyAdapter untuk menangani input dengan benar
        class MyKeyAdapter extends KeyAdapter {
            @Override
            public void keyPressed(KeyEvent e) {
                // Print debug info
                System.out.println("Key pressed: " + KeyEvent.getKeyText(e.getKeyCode()));
                printSnakeState();
                
                if (countdownActive) {
                    // Ignore input during countdown
                    return;
                }
                
                if (running) {
                    if (paused) {
                        // Game is paused - WASD/Arrow navigation for pause menu
                        switch (e.getKeyCode()) {
                            case KeyEvent.VK_ESCAPE:
                                // If already paused, ESC returns to main menu
                                returnToMainMenu();
                                break;
                            case KeyEvent.VK_SPACE:
                            case KeyEvent.VK_ENTER:
                                // Enter/Space selects current pause menu item
                                executePauseSelection();
                                break;
                            case KeyEvent.VK_W:
                            case KeyEvent.VK_UP:
                                pauseSelectedIndex = (pauseSelectedIndex - 1 + 2) % 2;
                                repaint();
                                break;
                            case KeyEvent.VK_S:
                            case KeyEvent.VK_DOWN:
                                pauseSelectedIndex = (pauseSelectedIndex + 1) % 2;
                                repaint();
                                break;
                        }
                    } else {
                        // Game is running - WASD + Arrow key controls
                        switch (e.getKeyCode()) {
                            case KeyEvent.VK_LEFT:
                            case KeyEvent.VK_A:
                                if (direction != 'R') {
                                    if (!directionChangedThisTick) {
                                        direction = 'L';
                                        directionChangedThisTick = true;
                                    } else {
                                        queuedDirection = 'L';
                                    }
                                }
                                break;
                            case KeyEvent.VK_RIGHT:
                            case KeyEvent.VK_D:
                                if (direction != 'L') {
                                    if (!directionChangedThisTick) {
                                        direction = 'R';
                                        directionChangedThisTick = true;
                                    } else {
                                        queuedDirection = 'R';
                                    }
                                }
                                break;
                            case KeyEvent.VK_UP:
                            case KeyEvent.VK_W:
                                if (direction != 'D') {
                                    if (!directionChangedThisTick) {
                                        direction = 'U';
                                        directionChangedThisTick = true;
                                    } else {
                                        queuedDirection = 'U';
                                    }
                                }
                                break;
                            case KeyEvent.VK_DOWN:
                            case KeyEvent.VK_S:
                                if (direction != 'U') {
                                    if (!directionChangedThisTick) {
                                        direction = 'D';
                                        directionChangedThisTick = true;
                                    } else {
                                        queuedDirection = 'D';
                                    }
                                }
                                break;
                            case KeyEvent.VK_ESCAPE:
                                // ESC pauses the game
                                pauseSelectedIndex = 0;
                                togglePause();
                                break;
                            case KeyEvent.VK_SPACE:
                                // Space also pauses the game
                                pauseSelectedIndex = 0;
                                togglePause();
                                break;
                        }
                    }
                } else {
                    // Game is over
                    if (e.getKeyCode() == KeyEvent.VK_SPACE) {
                        resetGameAndStart();
                        System.out.println("Space pressed - restarting game");
                    } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                        returnToMainMenu();
                    }
                }
            }
        }
        
        private void executePauseSelection() {
            switch (pauseSelectedIndex) {
                case 0: togglePause(); break;  // Resume
                case 1: returnToMainMenu(); break;  // Main Menu
            }
        }
    
        private void resetGameAndStart() {
            // Reset game state
            bodyParts = 6;
            applesEaten = 0;
            direction = 'R';
            paused = false;
            foodParticles.clear();
            scorePopups.clear();
            shakeMagnitude = 0;
            shakeX = 0;
            shakeY = 0;
            
            // Reset snake position
            int startX = (SCREEN_WIDTH / UNIT_SIZE) / 2 * UNIT_SIZE;
            int startY = (SCREEN_HEIGHT / UNIT_SIZE) / 2 * UNIT_SIZE;
            
            for (int i = 0; i < bodyParts; i++) {
                x[i] = startX - (i * UNIT_SIZE);
                y[i] = startY;
                displayX[i] = x[i];
                displayY[i] = y[i];
                prevX[i] = x[i];
                prevY[i] = y[i];
            }
            
            // Generate new apple
            newApple();
            
            // Start countdown
            startCountdown();
            
            // Request focus to ensure key events are captured
            requestFocusInWindow();
            
            // Debug information
            System.out.println("Game reset and started");
            printSnakeState();
        }
    
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            
            // Apply screen shake
            if (shakeMagnitude > 0) {
                g2d.translate(shakeX, shakeY);
            }
            
            // Gambar grid terlebih dahulu sebagai latar belakang
            drawGrid(g);
            
            if (running) {
                // Gambar makanan
                drawFood(g);
                
                // Draw food particles
                drawFoodParticles(g2d);
                
                // Gambar ular
                drawSnake(g);
                
                // Draw score popups
                drawScorePopups(g2d);
                
                // Gambar skor
                drawScore(g);
                
                // Draw countdown
                if (countdownActive) {
                    drawCountdown(g2d);
                }
                
                // Jika game di-pause, gambar layar pause
                if (paused && !countdownActive) {
                    drawPauseScreen(g);
                }
            } else {
                // Game over, tetap gambar elemen game
                drawFood(g);
                drawSnake(g);
                drawScore(g);
                gameOver(g);
            }
            
            // Reset translate
            if (shakeMagnitude > 0) {
                g2d.translate(-shakeX, -shakeY);
            }
            
            // Draw transition overlay
            if (transitionAlpha > 0) {
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1.0f, Math.max(0f, transitionAlpha))));
                g2d.setColor(Color.BLACK);
                g2d.fillRect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
            }
        }

    public void draw(Graphics g) {
        if (running) {
            drawGrid(g);
            drawApple(g);
            drawSnake(g); // Changed from drawSnakeWithImages(g) to drawSnake(g)
            drawScore(g);
        } else {
            gameOver(g);
        }
        
        if (paused) {
            drawPauseScreen(g);
        }
    }

    private void drawGrid(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        
        // Get map-specific colors
        Color lightColor, darkColor;
        
        switch (selectedMap) {
            case "desert":
                lightColor = new Color(237, 201, 140);
                darkColor = new Color(210, 175, 115);
                break;
            case "ocean":
                lightColor = new Color(100, 180, 220);
                darkColor = new Color(70, 150, 195);
                break;
            case "neon":
                float pulse = neonPulse * 0.3f;
                lightColor = new Color(
                    (int)(30 + pulse * 20), 
                    (int)(30 + pulse * 10), 
                    (int)(50 + pulse * 30)
                );
                darkColor = new Color(
                    (int)(20 + pulse * 15), 
                    (int)(20 + pulse * 8), 
                    (int)(40 + pulse * 25)
                );
                break;
            default: // classic
                lightColor = new Color(150, 240, 130);
                darkColor = new Color(120, 210, 100);
                break;
        }
        
        // Calculate the exact number of cells that fit in the screen dimensions
        int horizontalCells = SCREEN_WIDTH / UNIT_SIZE;
        int verticalCells = SCREEN_HEIGHT / UNIT_SIZE;
        
        for (int i = 0; i < verticalCells; i++) {
            for (int j = 0; j < horizontalCells; j++) {
                if ((i + j) % 2 == 0) {
                    g.setColor(lightColor);
                } else {
                    g.setColor(darkColor);
                }
                g.fillRect(j * UNIT_SIZE, i * UNIT_SIZE, UNIT_SIZE, UNIT_SIZE);
            }
        }
        
        // Grid lines based on map type
        if (selectedMap.equals("neon")) {
            // Neon grid with glowing lines
            float glowAlpha = 0.08f + neonPulse * 0.05f;
            g2d.setColor(new Color(0, 255, 255, (int)(glowAlpha * 255)));
            g2d.setStroke(new BasicStroke(0.5f));
        } else {
            g.setColor(new Color(0, 0, 0, 30)); // Very transparent black
        }
        
        for (int i = 0; i <= verticalCells; i++) {
            g.drawLine(0, i * UNIT_SIZE, SCREEN_WIDTH, i * UNIT_SIZE);
        }
        for (int i = 0; i <= horizontalCells; i++) {
            g.drawLine(i * UNIT_SIZE, 0, i * UNIT_SIZE, SCREEN_HEIGHT);
        }
        
        // Neon map border glow
        if (selectedMap.equals("neon")) {
            Composite oldComp = g2d.getComposite();
            float borderGlow = 0.2f + neonPulse * 0.15f;
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, borderGlow));
            g2d.setColor(new Color(0, 255, 255));
            g2d.setStroke(new BasicStroke(3));
            g2d.drawRect(0, 0, horizontalCells * UNIT_SIZE, verticalCells * UNIT_SIZE);
            g2d.setComposite(oldComp);
        }
        
        // Desert map - draw subtle sand texture dots
        if (selectedMap.equals("desert")) {
            Composite oldComp = g2d.getComposite();
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.1f));
            g2d.setColor(new Color(180, 140, 80));
            Random texRand = new Random(42); // Fixed seed for consistent texture
            for (int i = 0; i < 100; i++) {
                int tx = texRand.nextInt(SCREEN_WIDTH);
                int ty = texRand.nextInt(SCREEN_HEIGHT);
                g2d.fillOval(tx, ty, 2, 2);
            }
            g2d.setComposite(oldComp);
        }
        
        // Ocean map - draw subtle wave effect  
        if (selectedMap.equals("ocean")) {
            Composite oldComp = g2d.getComposite();
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.06f));
            g2d.setColor(new Color(255, 255, 255));
            long time = System.currentTimeMillis();
            for (int i = 0; i < 5; i++) {
                float waveY = (float)(SCREEN_HEIGHT / 6.0 * (i + 1) + Math.sin(time * 0.001 + i) * 10);
                g2d.setStroke(new BasicStroke(2));
                Path2D wave = new Path2D.Float();
                wave.moveTo(0, waveY);
                for (int wx = 0; wx < SCREEN_WIDTH; wx += 20) {
                    float wy = (float)(waveY + Math.sin(wx * 0.03 + time * 0.002 + i) * 8);
                    wave.lineTo(wx, wy);
                }
                g2d.draw(wave);
            }
            g2d.setComposite(oldComp);
        }
        
        g2d.setStroke(new BasicStroke(1)); // Reset stroke
    }


    private void drawApple(Graphics g) {
        if (currentFoodImage != null) {
            g.drawImage(currentFoodImage, appleX, appleY, UNIT_SIZE, UNIT_SIZE, this);
        } else {
            // Fallback to colored oval if image is not available
            g.setColor(getFoodColor(selectedFood));
            g.fillOval(appleX, appleY, UNIT_SIZE, UNIT_SIZE);
        }
    }
    
    private void drawSnake(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Draw from tail to head so head is on top
        for (int i = bodyParts - 1; i >= 0; i--) {
            Color baseColor = getSnakeColor(selectedColor, i);
            
            float dx = displayX[i];
            float dy = displayY[i];
            
            // Neon map: add glow to snake
            if (selectedMap.equals("neon")) {
                Composite oldComp = g2.getComposite();
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f + neonPulse * 0.1f));
                g2.setColor(baseColor.brighter());
                g2.fillRoundRect((int)dx - 3, (int)dy - 3, UNIT_SIZE + 6, UNIT_SIZE + 6, 12, 12);
                g2.setComposite(oldComp);
            }
            
            // Create a gradient for the snake parts
            if (i == 0) {
                // Head with a radial gradient
                RadialGradientPaint gradient = new RadialGradientPaint(
                    dx + UNIT_SIZE/2, dy + UNIT_SIZE/2, UNIT_SIZE/2,
                    new float[]{0.0f, 1.0f},
                    new Color[]{baseColor.brighter(), baseColor}
                );
                g2.setPaint(gradient);
            } else {
                // Body with a linear gradient - vary brightness based on position
                float brightness = 1.0f - (float)i / bodyParts * 0.3f;
                Color bodyColor = new Color(
                    Math.min(255, (int)(baseColor.getRed() * brightness)),
                    Math.min(255, (int)(baseColor.getGreen() * brightness)),
                    Math.min(255, (int)(baseColor.getBlue() * brightness))
                );
                GradientPaint gradient = new GradientPaint(
                    dx, dy, bodyColor,
                    dx + UNIT_SIZE, dy + UNIT_SIZE, bodyColor.darker()
                );
                g2.setPaint(gradient);
            }
            
            // Draw rounded rectangle for smoother appearance
            g2.fillRoundRect((int)dx, (int)dy, UNIT_SIZE, UNIT_SIZE, 10, 10);
            
            // Subtle highlight on top
            Composite oldComp = g2.getComposite();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.15f));
            g2.setColor(Color.WHITE);
            g2.fillRoundRect((int)dx + 2, (int)dy + 1, UNIT_SIZE - 4, UNIT_SIZE / 2, 6, 6);
            g2.setComposite(oldComp);
            
            // Add eyes to the head
            if (i == 0) {
                drawSnakeEyes(g2, dx, dy);
            }
            
            // Add a connection between body parts for smoother appearance
            if (i > 0) {
                g2.setColor(baseColor);
                float prevDx = displayX[i - 1];
                float prevDy = displayY[i - 1];
                
                // Draw a line to connect segments if they're not diagonal
                if (Math.abs(dx - prevDx) < UNIT_SIZE * 2 && Math.abs(dy - prevDy) < UNIT_SIZE * 2) {
                    if (Math.abs(dx - prevDx) < 1) {
                        // Vertical connection
                        int connectionWidth = UNIT_SIZE / 2;
                        g2.fillRect((int)dx + UNIT_SIZE / 4, (int)Math.min(dy, prevDy) + UNIT_SIZE / 2,
                                   connectionWidth, (int)Math.abs(dy - prevDy));
                    } else if (Math.abs(dy - prevDy) < 1) {
                        // Horizontal connection
                        int connectionWidth = UNIT_SIZE / 2;
                        g2.fillRect((int)Math.min(dx, prevDx) + UNIT_SIZE / 2, (int)dy + UNIT_SIZE / 4,
                                   (int)Math.abs(dx - prevDx), connectionWidth);
                    }
                }
            }
        }
        
        // Draw tongue
        if (bodyParts > 0) {
            drawSnakeTongue(g2);
        }
    }
    
    private void drawSnakeEyes(Graphics2D g2, float dx, float dy) {
        g2.setColor(Color.WHITE);
        
        // Position eyes based on direction
        int eyeSize = UNIT_SIZE / 5;
        int eyeOffset = UNIT_SIZE / 4;
        
        switch (direction) {
            case 'R':
                g2.fillOval((int)dx + UNIT_SIZE - eyeOffset - eyeSize/2, (int)dy + eyeOffset, eyeSize, eyeSize);
                g2.fillOval((int)dx + UNIT_SIZE - eyeOffset - eyeSize/2, (int)dy + UNIT_SIZE - eyeOffset - eyeSize, eyeSize, eyeSize);
                g2.setColor(Color.BLACK);
                g2.fillOval((int)dx + UNIT_SIZE - eyeOffset, (int)dy + eyeOffset + eyeSize/4, eyeSize/2, eyeSize/2);
                g2.fillOval((int)dx + UNIT_SIZE - eyeOffset, (int)dy + UNIT_SIZE - eyeOffset - eyeSize + eyeSize/4, eyeSize/2, eyeSize/2);
                break;
            case 'L':
                g2.fillOval((int)dx + eyeOffset - eyeSize/2, (int)dy + eyeOffset, eyeSize, eyeSize);
                g2.fillOval((int)dx + eyeOffset - eyeSize/2, (int)dy + UNIT_SIZE - eyeOffset - eyeSize, eyeSize, eyeSize);
                g2.setColor(Color.BLACK);
                g2.fillOval((int)dx + eyeOffset - eyeSize/2, (int)dy + eyeOffset + eyeSize/4, eyeSize/2, eyeSize/2);
                g2.fillOval((int)dx + eyeOffset - eyeSize/2, (int)dy + UNIT_SIZE - eyeOffset - eyeSize + eyeSize/4, eyeSize/2, eyeSize/2);
                break;
            case 'U':
                g2.fillOval((int)dx + eyeOffset, (int)dy + eyeOffset - eyeSize/2, eyeSize, eyeSize);
                g2.fillOval((int)dx + UNIT_SIZE - eyeOffset - eyeSize, (int)dy + eyeOffset - eyeSize/2, eyeSize, eyeSize);
                g2.setColor(Color.BLACK);
                g2.fillOval((int)dx + eyeOffset + eyeSize/4, (int)dy + eyeOffset - eyeSize/2, eyeSize/2, eyeSize/2);
                g2.fillOval((int)dx + UNIT_SIZE - eyeOffset - eyeSize + eyeSize/4, (int)dy + eyeOffset - eyeSize/2, eyeSize/2, eyeSize/2);
                break;
            case 'D':
                g2.fillOval((int)dx + eyeOffset, (int)dy + UNIT_SIZE - eyeOffset - eyeSize/2, eyeSize, eyeSize);
                g2.fillOval((int)dx + UNIT_SIZE - eyeOffset - eyeSize, (int)dy + UNIT_SIZE - eyeOffset - eyeSize/2, eyeSize, eyeSize);
                g2.setColor(Color.BLACK);
                g2.fillOval((int)dx + eyeOffset + eyeSize/4, (int)dy + UNIT_SIZE - eyeOffset, eyeSize/2, eyeSize/2);
                g2.fillOval((int)dx + UNIT_SIZE - eyeOffset - eyeSize + eyeSize/4, (int)dy + UNIT_SIZE - eyeOffset, eyeSize/2, eyeSize/2);
                break;
        }
    }
    
    private void drawSnakeTongue(Graphics2D g2) {
        g2.setColor(Color.RED);
        int tongueLength = UNIT_SIZE / 2;
        int tongueWidth = UNIT_SIZE / 8;
        int tongueY = y[0] + UNIT_SIZE / 2;
        int tongueX = x[0] + UNIT_SIZE / 2;
        
        // Tongue flicker animation
        float flicker = (float)Math.sin(System.currentTimeMillis() * 0.01) > 0 ? 1 : 0;
        if (flicker < 0.5f) return; // Tongue flickers
        
        switch (direction) {
            case 'R':
                g2.fillRect(x[0] + UNIT_SIZE, tongueY - tongueWidth/2, tongueLength, tongueWidth);
                g2.fillRect(x[0] + UNIT_SIZE + tongueLength, tongueY - tongueWidth*2, tongueLength/2, tongueWidth);
                g2.fillRect(x[0] + UNIT_SIZE + tongueLength, tongueY + tongueWidth, tongueLength/2, tongueWidth);
                break;
            case 'L':
                g2.fillRect(x[0] - tongueLength, tongueY - tongueWidth/2, tongueLength, tongueWidth);
                g2.fillRect(x[0] - tongueLength - tongueLength/2, tongueY - tongueWidth*2, tongueLength/2, tongueWidth);
                g2.fillRect(x[0] - tongueLength - tongueLength/2, tongueY + tongueWidth, tongueLength/2, tongueWidth);
                break;
            case 'U':
                g2.fillRect(tongueX - tongueWidth/2, y[0] - tongueLength, tongueWidth, tongueLength);
                g2.fillRect(tongueX - tongueWidth*2, y[0] - tongueLength - tongueLength/2, tongueWidth, tongueLength/2);
                g2.fillRect(tongueX + tongueWidth, y[0] - tongueLength - tongueLength/2, tongueWidth, tongueLength/2);
                break;
            case 'D':
                g2.fillRect(tongueX - tongueWidth/2, y[0] + UNIT_SIZE, tongueWidth, tongueLength);
                g2.fillRect(tongueX - tongueWidth*2, y[0] + UNIT_SIZE + tongueLength, tongueWidth, tongueLength/2);
                g2.fillRect(tongueX + tongueWidth, y[0] + UNIT_SIZE + tongueLength, tongueWidth, tongueLength/2);
                break;
        }
    }
    
   private void drawScore(Graphics g) {
    Graphics2D g2d = (Graphics2D) g;
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    
    // Define score panel dimensions and position - more compact design
    int panelWidth = 150;
    int panelHeight = 60;
    int panelX = 15; // Left side positioning for minimalism
    int panelY = 15; // Top margin
    
    // Create a semi-transparent background with rounded corners
    g2d.setColor(new Color(0, 0, 0, 130)); // More transparent for minimalist look
    g2d.fillRoundRect(panelX, panelY, panelWidth, panelHeight, 10, 10);
    
    // Add a subtle border based on map theme
    Color borderColor;
    switch (selectedMap) {
        case "neon": borderColor = new Color(0, 255, 255, 120); break;
        case "ocean": borderColor = new Color(100, 200, 255, 100); break;
        case "desert": borderColor = new Color(255, 200, 100, 100); break;
        default: borderColor = new Color(255, 255, 255, 100); break;
    }
    g2d.setColor(borderColor);
    g2d.setStroke(new BasicStroke(1.5f));
    g2d.drawRoundRect(panelX, panelY, panelWidth, panelHeight, 10, 10);
    
    // Draw score values with clean typography
    g2d.setFont(new Font("Arial", Font.BOLD, 16));
    
    // Current score
    g2d.setColor(Color.WHITE);
    g2d.drawString("SCORE", panelX + 10, panelY + 25);
    
    String scoreText = String.format("%d", applesEaten);
    int scoreWidth = g2d.getFontMetrics().stringWidth(scoreText);
    g2d.drawString(scoreText, panelX + panelWidth - scoreWidth - 10, panelY + 25);
    
    // Best score
    g2d.setColor(new Color(255, 215, 0)); // Gold color for best score
    g2d.drawString("BEST", panelX + 10, panelY + 45);
    
    // Highlight if current score is higher
    if (applesEaten > bestScore) {
        g2d.setColor(new Color(255, 100, 100)); // Light red for new record
        
        // Make the text pulse by changing its alpha
        long currentTime = System.currentTimeMillis();
        float alpha = (float) Math.abs(Math.sin(currentTime * 0.005)) * 0.7f + 0.3f;
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        
        scoreText = String.format("%d", applesEaten);
    } else {
        scoreText = String.format("%d", bestScore);
    }
    
    scoreWidth = g2d.getFontMetrics().stringWidth(scoreText);
    g2d.drawString(scoreText, panelX + panelWidth - scoreWidth - 10, panelY + 45);
    
    // Reset composite if it was changed
    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
    
    // Add control hints at the bottom
    g2d.setFont(new Font("Arial", Font.PLAIN, 11));
    g2d.setColor(new Color(255, 255, 255, 120));
    
    // Map name badge
    String mapName = selectedMap.toUpperCase();
    g2d.setFont(new Font("Arial", Font.BOLD, 10));
    int mapNameWidth = g2d.getFontMetrics().stringWidth(mapName);
    int badgeX = SCREEN_WIDTH - mapNameWidth - 25;
    int badgeY = 15;
    
    g2d.setColor(new Color(0, 0, 0, 120));
    g2d.fillRoundRect(badgeX - 5, badgeY, mapNameWidth + 10, 18, 6, 6);
    g2d.setColor(borderColor);
    g2d.drawRoundRect(badgeX - 5, badgeY, mapNameWidth + 10, 18, 6, 6);
    g2d.setColor(new Color(255, 255, 255, 200));
    g2d.drawString(mapName, badgeX, badgeY + 13);
    
    // Control hint
    g2d.setFont(new Font("Arial", Font.PLAIN, 11));
    g2d.setColor(new Color(255, 255, 255, 120));
    String hintText = "WASD/Arrow: Gerak  |  ESC: Pause";
    int hintWidth = g2d.getFontMetrics().stringWidth(hintText);
    g2d.drawString(hintText, (SCREEN_WIDTH - hintWidth) / 2, SCREEN_HEIGHT - 12);
}
private void saveBestScore() {
    try {
        if (applesEaten > bestScore) {
            bestScore = applesEaten;
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(BEST_SCORE_FILE));
            oos.writeObject(bestScore);
            oos.close();
            System.out.println("New best score saved: " + bestScore);
        }
    } catch (Exception e) {
        System.out.println("Error saving best score: " + e.getMessage());
        e.printStackTrace();
    }
}
    private Color getFoodColor(String food) {
        switch (food) {
            case "banana":
                return Color.YELLOW;
            case "grape":
                return new Color(128, 0, 128);
            case "orange":
                return Color.ORANGE;
            default:
                return Color.RED;
        }
    }

    private Color getSnakeColor(String color, int index) {
        Color baseColor;
        switch (color) {
            case "red":
                baseColor = new Color(255, 50, 50);
                break;
            case "blue":
                baseColor = new Color(50, 100, 255);
                break;
            case "pink":
                baseColor = new Color(255, 130, 180);
                break;
            default:
                baseColor = new Color(50, 220, 50);
                break;
        }
        
        // Make body parts slightly darker for visual distinction  
        if (index > 0) {
            float factor = 1.0f - (float)index / (bodyParts + 5) * 0.4f;
            return new Color(
                Math.max(0, (int)(baseColor.getRed() * factor)),
                Math.max(0, (int)(baseColor.getGreen() * factor)),
                Math.max(0, (int)(baseColor.getBlue() * factor))
            );
        }
        return baseColor;
    }

   
    public void newApple() {
        // Generate apple position ensuring it's not on the snake
        boolean validPosition = false;
        
        while (!validPosition) {
            // Calculate grid positions (not pixel positions)
            int maxX = 675 / UNIT_SIZE; // Max x-coordinate is 640
            int maxY = 550 / UNIT_SIZE; // Max y-coordinate is 540
            
            appleX = random.nextInt(maxX) * UNIT_SIZE;
            appleY = random.nextInt(maxY) * UNIT_SIZE;
            
            // Check if apple is on snake
            validPosition = true;
            for (int i = 0; i < bodyParts; i++) {
                if (appleX == x[i] && appleY == y[i]) {
                    validPosition = false;
                    break;
                }
            }
        }
        
        System.out.println("New apple generated at: (" + appleX + ", " + appleY + ")");
    } 
    public void move() {
        for (int i = bodyParts; i > 0; i--) {
            x[i] = x[i - 1];
            y[i] = y[i - 1];
        }

        switch (direction) {
            case 'U':
                y[0] = y[0] - UNIT_SIZE;
                break;
            case 'D':
                y[0] = y[0] + UNIT_SIZE;
                break;
            case 'L':
                x[0] = x[0] - UNIT_SIZE;
                break;
            case 'R':
                x[0] = x[0] + UNIT_SIZE;
                break;
        }
    }

    public void checkApple() {
        // Check if the snake's head position matches the apple position
        if ((x[0] == appleX) && (y[0] == appleY)) {
            // Increase body parts
            bodyParts++;
            
            // Increase score
            applesEaten++;
            
            // Spawn food particles
            spawnFoodParticles(appleX, appleY);
            
            // Add score popup
            scorePopups.add(new ScorePopup(appleX, appleY, "+1"));
            
            // Small screen shake on eat
            shakeMagnitude = 3;
            
            // Update best score if needed
            if (applesEaten > bestScore) {
                bestScore = applesEaten;
                saveBestScore();
            }
            
            // Generate new apple
            newApple();
            
            // Play sound
            playSound("eat");
            
            // Debug information
            System.out.println("Apple eaten! Score: " + applesEaten + ", Body parts: " + bodyParts);
        }
    }
    
    private void spawnFoodParticles(int fx, int fy) {
        Color pColor = getFoodColor(selectedFood);
        for (int i = 0; i < 12; i++) {
            foodParticles.add(new FoodParticle(fx + UNIT_SIZE / 2, fy + UNIT_SIZE / 2, pColor, random));
        }
    }
    
    private void drawFoodParticles(Graphics2D g2d) {
        Composite old = g2d.getComposite();
        for (FoodParticle p : foodParticles) {
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0, p.alpha)));
            g2d.setColor(p.color);
            g2d.fillOval((int)p.x, (int)p.y, (int)p.size, (int)p.size);
        }
        g2d.setComposite(old);
    }
    
    private void drawScorePopups(Graphics2D g2d) {
        Composite old = g2d.getComposite();
        g2d.setFont(new Font("Arial", Font.BOLD, 18));
        for (ScorePopup p : scorePopups) {
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0, p.alpha)));
            g2d.setColor(new Color(255, 255, 100));
            g2d.drawString(p.text, (int)p.x, (int)p.y);
        }
        g2d.setComposite(old);
    }
    
    private void drawCountdown(Graphics2D g2d) {
        // Darken background
        g2d.setColor(new Color(0, 0, 0, 150));
        g2d.fillRect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);
        
        String text = countdown > 0 ? String.valueOf(countdown) : "GO!";
        
        // Scale animation
        float scale = 1.0f + (float)Math.sin(System.currentTimeMillis() * 0.005) * 0.1f;
        
        g2d.setFont(new Font("Arial", Font.BOLD, (int)(80 * scale)));
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        
        // Shadow
        g2d.setColor(new Color(0, 0, 0, 150));
        g2d.drawString(text, (SCREEN_WIDTH - textWidth) / 2 + 3, SCREEN_HEIGHT / 2 + 3);
        
        // Main text with gradient
        if (countdown > 0) {
            g2d.setColor(new Color(255, 255, 100));
        } else {
            g2d.setColor(new Color(100, 255, 100));
        }
        g2d.drawString(text, (SCREEN_WIDTH - textWidth) / 2, SCREEN_HEIGHT / 2);
        
        // Subtitle
        g2d.setFont(new Font("Arial", Font.PLAIN, 16));
        String sub = "Bersiap...";
        fm = g2d.getFontMetrics();
        g2d.setColor(new Color(200, 200, 200));
        g2d.drawString(sub, (SCREEN_WIDTH - fm.stringWidth(sub)) / 2, SCREEN_HEIGHT / 2 + 50);
    }
    
    private void playSound(String soundType) {
        // This is a placeholder for sound implementation
        // You can add actual sound playing code here
    }
            private void showGameOverPanel() {
                JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(this);
                if (frame != null) {
                    frame.remove(this);
                    GameOverPanel gameOverPanel = new GameOverPanel(
                        applesEaten, 
                        bestScore, 
                        _ -> {
                            frame.getContentPane().removeAll();
                            SnakePanel newPanel = new SnakePanel(selectedColor, selectedFood, selectedMap);
                            frame.add(newPanel);
                            frame.revalidate();
                            frame.repaint();
                            newPanel.requestFocusInWindow();
                        }, 
                        _ -> {
                            frame.getContentPane().removeAll();
                            MainMenuPanel menuPanel = new MainMenuPanel(frame);
                            frame.add(menuPanel);
                            frame.revalidate();
                            frame.repaint();
                            menuPanel.requestFocusInWindow();
                        }
                    );
                    frame.add(gameOverPanel);
                    frame.revalidate();
                    frame.repaint();
                    gameOverPanel.requestFocusInWindow();
                }
            }
   
    private void drawButton(Graphics g, String text, int x, int y, int width, int height, Color bgColor, Color textColor) {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Draw button background with gradient
        GradientPaint gradient = new GradientPaint(
            x, y, bgColor, 
            x, y + height, bgColor.darker()
        );
        g2d.setPaint(gradient);
        g2d.fillRoundRect(x, y, width, height, 15, 15);
        
        // Draw button border
        g2d.setColor(bgColor.darker().darker());
        g2d.setStroke(new BasicStroke(2));
        g2d.drawRoundRect(x, y, width, height, 15, 15);
        
        // Draw button text
        g2d.setColor(textColor);
        g2d.setFont(new Font("Arial", Font.BOLD, 20));
        FontMetrics metrics = g2d.getFontMetrics();
        int textX = x + (width - metrics.stringWidth(text)) / 2;
        int textY = y + ((height - metrics.getHeight()) / 2) + metrics.getAscent();
        g2d.drawString(text, textX, textY);
    }
    
    private void drawButtonWithSelection(Graphics g, String text, int x, int y, int width, int height, 
                                          Color bgColor, Color textColor, boolean isSelected) {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Glow effect for selected button
        if (isSelected) {
            Composite oldComp = g2d.getComposite();
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
            g2d.setColor(new Color(255, 255, 200));
            for (int i = 6; i > 0; i--) {
                g2d.fillRoundRect(x - i, y - i, width + i * 2, height + i * 2, 15, 15);
            }
            g2d.setComposite(oldComp);
        }
        
        // Draw button background with gradient
        Color actualBg = isSelected ? bgColor.brighter() : bgColor;
        GradientPaint gradient = new GradientPaint(
            x, y, actualBg, 
            x, y + height, actualBg.darker()
        );
        g2d.setPaint(gradient);
        g2d.fillRoundRect(x, y, width, height, 15, 15);
        
        // Draw button border
        g2d.setColor(isSelected ? Color.WHITE : actualBg.darker().darker());
        g2d.setStroke(new BasicStroke(isSelected ? 3 : 2));
        g2d.drawRoundRect(x, y, width, height, 15, 15);
        
        // Draw selection arrow
        if (isSelected) {
            float pulse = (float)Math.sin(System.currentTimeMillis() * 0.005) * 3;
            int arrowX = x - 20 + (int)pulse;
            g2d.setColor(new Color(255, 255, 100));
            int[] xp = {arrowX, arrowX - 10, arrowX - 10};
            int[] yp = {y + height / 2, y + height / 2 - 7, y + height / 2 + 7};
            g2d.fillPolygon(xp, yp, 3);
        }
        
        // Draw button text
        g2d.setColor(textColor);
        g2d.setFont(new Font("Arial", Font.BOLD, 20));
        FontMetrics metrics = g2d.getFontMetrics();
        int textX = x + (width - metrics.stringWidth(text)) / 2;
        int textY = y + ((height - metrics.getHeight()) / 2) + metrics.getAscent();
        g2d.drawString(text, textX, textY);
    }
    
    public void gameOver(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Create a semi-transparent overlay
        g2d.setColor(new Color(0, 0, 0, 180));
        g2d.fillRect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);
        
        int topY = 50;
        
        // Draw best score
        g2d.setColor(Color.BLUE);
        g2d.setFont(new Font("Ink Free", Font.BOLD, 30));
        FontMetrics metrics0 = getFontMetrics(g2d.getFont());
        g2d.drawString("Best Score: " + bestScore, (SCREEN_WIDTH - metrics0.stringWidth("Best Score: " + bestScore)) / 2, topY);
        
        // Draw current score
        g2d.setColor(Color.RED);
        g2d.setFont(new Font("Ink Free", Font.BOLD, 40));
        FontMetrics metrics1 = getFontMetrics(g2d.getFont());
        g2d.drawString("Score: " + applesEaten, (SCREEN_WIDTH - metrics1.stringWidth("Score: " + applesEaten)) / 2, topY + 50);
        
        // Draw Game Over text with gradient
        g2d.setFont(new Font("Metropolis", Font.BOLD, 75));
        FontMetrics metrics2 = getFontMetrics(g2d.getFont());
        String gameOverText = "Game Over";
        
        // Create a gradient for the text
        GradientPaint gradient = new GradientPaint(
            0, SCREEN_HEIGHT / 2, Color.RED,
            SCREEN_WIDTH, SCREEN_HEIGHT / 2, new Color(255, 100, 0)
        );
        g2d.setPaint(gradient);
        
        // Draw the text with a shadow effect
        g2d.setColor(new Color(0, 0, 0, 100));
        g2d.drawString(gameOverText, (SCREEN_WIDTH - metrics2.stringWidth(gameOverText)) / 2 + 3, 
                      SCREEN_HEIGHT / 2 + 3);
        g2d.setPaint(gradient);
        g2d.drawString(gameOverText, (SCREEN_WIDTH - metrics2.stringWidth(gameOverText)) / 2, 
                      SCREEN_HEIGHT / 2);
        
        // Draw buttons
        drawButton(g, "Try Again", (SCREEN_WIDTH - 200) / 2, SCREEN_HEIGHT / 2 + 70, 200, 50, Color.GREEN.darker(), Color.WHITE);
        drawButton(g, "Main Menu", (SCREEN_WIDTH - 200) / 2, SCREEN_HEIGHT / 2 + 140, 200, 50, Color.RED.darker(), Color.WHITE);
        
        // Control hints for game over
        g2d.setFont(new Font("Arial", Font.PLAIN, 13));
        g2d.setColor(new Color(200, 200, 200, 180));
        String hint = "SPACE: Coba Lagi  |  ESC: Menu Utama";
        FontMetrics fm = g2d.getFontMetrics();
        g2d.drawString(hint, (SCREEN_WIDTH - fm.stringWidth(hint)) / 2, SCREEN_HEIGHT - 30);
    }
    
    // Add these methods to handle pause functionality
    public void togglePause() {
        paused = !paused;
        if (paused) {
            timer.stop();
        } else {
            timer.start();
        }
        repaint();
    }
    
    public void returnToMainMenu() {
        timer.stop();
        if (countdownTimer != null) countdownTimer.stop();
        
        if (applesEaten > bestScore) {
            bestScore = applesEaten;
            saveBestScore();
        }
        
        JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(this);
        if (frame != null) {
            frame.getContentPane().removeAll();
            MainMenuPanel mainMenuPanel = new MainMenuPanel(frame);
            frame.add(mainMenuPanel);
            frame.revalidate();
            frame.repaint();
            mainMenuPanel.requestFocusInWindow();
        }
    }
    
    private void initializePauseMenu() {
        // Define pause menu button areas
        int buttonWidth = 200;
        int buttonHeight = 50;
        int centerX = SCREEN_WIDTH / 2 - buttonWidth / 2;
        
        resumeBtnRect = new Rectangle(centerX, SCREEN_HEIGHT / 2 - 60, buttonWidth, buttonHeight);
        mainMenuBtnRect = new Rectangle(centerX, SCREEN_HEIGHT / 2 + 10, buttonWidth, buttonHeight);
    }
    
    private Image createPauseOverlay() {
        BufferedImage overlay = new BufferedImage(SCREEN_WIDTH, SCREEN_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = overlay.createGraphics();
        g2d.setColor(new Color(0, 0, 0, 150)); // Semi-transparent black
        g2d.fillRect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);
        g2d.dispose();
        return overlay;
    }
    
    private void drawPauseScreen(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        
        g.drawImage(pauseOverlay, 0, 0, this);
        
        g2d.setFont(new Font("Arial", Font.BOLD, 40));
        g2d.setColor(Color.WHITE);
        String pauseText = "PAUSED";
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(pauseText);
        g2d.drawString(pauseText, (SCREEN_WIDTH - textWidth) / 2, SCREEN_HEIGHT / 3);
        
        // Draw buttons with selection highlighting
        drawButtonWithSelection(g, "RESUME", resumeBtnRect.x, resumeBtnRect.y, 
                  resumeBtnRect.width, resumeBtnRect.height, 
                  new Color(0, 150, 0), Color.WHITE, pauseSelectedIndex == 0);
        
        drawButtonWithSelection(g, "MAIN MENU", mainMenuBtnRect.x, mainMenuBtnRect.y, 
                  mainMenuBtnRect.width, mainMenuBtnRect.height, 
                  new Color(150, 0, 0), Color.WHITE, pauseSelectedIndex == 1);
        
        g2d.setFont(new Font("Arial", Font.BOLD, 13));
        g2d.setColor(new Color(200, 200, 200));
        
        String controlText = "[W/S] Navigasi  |  [ENTER] Pilih  |  [ESC] Menu Utama";
        fm = g2d.getFontMetrics();
        int ctrlWidth = fm.stringWidth(controlText);
        g2d.drawString(controlText, (SCREEN_WIDTH - ctrlWidth) / 2, SCREEN_HEIGHT / 2 + 100);
    }
    
    // Update the actionPerformed method to handle paused state
    
    // Update the MyMouseAdapter class to handle pause menu clicks
    class MyMouseAdapter extends MouseAdapter {
        @Override
        public void mouseClicked(MouseEvent e) {
            int mouseX = e.getX();
            int mouseY = e.getY();
            
            if (paused && !countdownActive) {
                // Handle clicks in pause menu
                if (resumeBtnRect.contains(mouseX, mouseY)) {
                    togglePause();
                } else if (mainMenuBtnRect.contains(mouseX, mouseY)) {
                    returnToMainMenu();
                }
            } else if (!running) {
                // Handle clicks in game over screen
                int tryAgainBtnX = (SCREEN_WIDTH - 200) / 2;
                int tryAgainBtnY = SCREEN_HEIGHT / 2 + 70;
                int btnWidth = 200;
                int btnHeight = 50;
    
                if (mouseX >= tryAgainBtnX && mouseX <= tryAgainBtnX + btnWidth &&
                    mouseY >= tryAgainBtnY && mouseY <= tryAgainBtnY + btnHeight) {
                    resetGameAndStart();
                }
    
                int closeBtnX = (SCREEN_WIDTH - 200) / 2;
                int closeBtnY = SCREEN_HEIGHT / 2 + 140;
    
                if (mouseX >= closeBtnX && mouseX <= closeBtnX + btnWidth &&
                    mouseY >= closeBtnY && mouseY <= closeBtnY + btnHeight) {
                    returnToMainMenu();
                }
            }
        }
    }
    
    // --- Inner classes for effects ---
    
    private static class FoodParticle {
        float x, y, vx, vy, size, alpha;
        Color color;
        
        FoodParticle(int startX, int startY, Color color, Random rand) {
            this.x = startX;
            this.y = startY;
            this.color = new Color(
                Math.min(255, color.getRed() + rand.nextInt(50)),
                Math.min(255, color.getGreen() + rand.nextInt(50)),
                Math.min(255, color.getBlue() + rand.nextInt(50))
            );
            this.vx = (rand.nextFloat() - 0.5f) * 6;
            this.vy = (rand.nextFloat() - 0.5f) * 6;
            this.size = rand.nextFloat() * 5 + 2;
            this.alpha = 1.0f;
        }
        
        void update() {
            x += vx;
            y += vy;
            vy += 0.1f; // Gravity
            alpha -= 0.03f;
            size *= 0.97f;
        }
    }
    
    private static class ScorePopup {
        float x, y, alpha;
        String text;
        
        ScorePopup(int startX, int startY, String text) {
            this.x = startX;
            this.y = startY;
            this.text = text;
            this.alpha = 1.0f;
        }
        
        void update() {
            y -= 1.5f;
            alpha -= 0.02f;
        }
    }
}