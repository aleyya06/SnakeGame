import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

public class GameOverPanel extends JPanel {
    private int score;
    private int bestScore;
    private ActionListener tryAgainListener;
    private ActionListener closeListener;
    private String currentMessage;
    private String encouragementMessage;
    private Random random;
    private Image backgroundImage;
    
    // --- Animation ---
    private float fadeIn = 0f;
    private float titleShake = 0f;
    private float textPulse = 0f;
    private List<DeathParticle> deathParticles = new ArrayList<>();
    
    // --- WASD Navigation ---
    private int selectedOption = 0; // 0 = Try Again, 1 = Main Menu

    private String[] gameOverMessages = {
        "CUPU LU BRO!",
        "GAME OVER! SKILL ISSUE!",
        "KALAH LAGI? PAYAH!",
        "COBA LAGI DEH, MASA KALAH SAMA GAME BOCIL!",
        "ULAR AJA BISA MAKAN, KAMU KOK KALAH?",
        "UDAH KALAH, GAUSAH NANGIS!",
        "NEXT TIME PAKE OTAK YA!",
        "ULAR: 1, KAMU: 0",
        "MUNGKIN GAME INI TERLALU SUSAH BUAT KAMU",
        "COBA MAIN EPEP AJA DEH!"
    };

    private String[] encouragementMessages = {
        "TEKAN SPACE UNTUK COBA LAGI",
        "JANGAN MENYERAH! TEKAN SPACE",
        "MASIH MAU MAIN? TEKAN SPACE",
        "TEKAN SPACE UNTUK BUKTIKAN KAMU BISA",
        "TEKAN SPACE AGAR TIDAK CUPU"
    };

    // Use a functional interface for the listeners
    public interface GameOverActionListener {
        void onAction(ActionEvent e);
    }

    public GameOverPanel(int score, int bestScore, GameOverActionListener tryAgainListener, GameOverActionListener closeListener) {
        this.score = score;
        this.bestScore = bestScore;
        this.tryAgainListener = e -> tryAgainListener.onAction(e);
        this.closeListener = e -> closeListener.onAction(e);
        random = new Random();
        selectRandomMessages();
        setPreferredSize(new Dimension(713, 613));
        setBackground(Color.WHITE);
        setFocusable(true);
    
        // Load the background image using ImageLoader
        backgroundImage = ImageLoader.loadImage("IMG/Background3.png");
        
        // Initialize death particles
        for (int i = 0; i < 40; i++) {
            deathParticles.add(new DeathParticle(random, 713, 613));
        }
    
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_SPACE:
                    case KeyEvent.VK_ENTER:
                        // Execute currently selected option
                        executeSelection();
                        break;
                    case KeyEvent.VK_ESCAPE:
                        // Go to main menu
                        goToMainMenu();
                        break;
                    case KeyEvent.VK_W:
                    case KeyEvent.VK_UP:
                        selectedOption = (selectedOption - 1 + 2) % 2;
                        repaint();
                        break;
                    case KeyEvent.VK_S:
                    case KeyEvent.VK_DOWN:
                        selectedOption = (selectedOption + 1) % 2;
                        repaint();
                        break;
                }
            }
        });
        
        // Smooth animation timer
        Timer animTimer = new Timer(16, _ -> {
            updateAnimations();
            repaint();
        });
        animTimer.start();
    }
    
    private void executeSelection() {
        if (selectedOption == 0) {
            tryAgainListener.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "Try Again"));
        } else {
            goToMainMenu();
        }
    }
    
    private void goToMainMenu() {
        JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(GameOverPanel.this);
        if (frame != null) {
            frame.getContentPane().removeAll();
            MainMenuPanel mainMenuPanel = new MainMenuPanel(frame);
            frame.add(mainMenuPanel);
            frame.revalidate();
            frame.repaint();
            mainMenuPanel.requestFocusInWindow();
        }
    }
    
    private void updateAnimations() {
        // Fade in
        if (fadeIn < 1.0f) {
            fadeIn += 0.03f;
            if (fadeIn > 1.0f) fadeIn = 1.0f;
        }
        
        // Title shake decay
        titleShake *= 0.95f;
        
        // Text pulse
        textPulse += 0.05f;
        
        // Update particles
        for (DeathParticle p : deathParticles) {
            p.update();
        }
    }

    public void selectRandomMessages() {
        currentMessage = gameOverMessages[random.nextInt(gameOverMessages.length)];
        encouragementMessage = encouragementMessages[random.nextInt(encouragementMessages.length)];
        titleShake = 15f; // Initial shake
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Draw the background image
        if (backgroundImage != null) {
            g2d.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), this);
        }
        
        // Draw particles behind overlay
        drawDeathParticles(g2d);

        draw(g2d, getWidth(), getHeight(), score);
    }
    
    private void drawDeathParticles(Graphics2D g2d) {
        Composite old = g2d.getComposite();
        for (DeathParticle p : deathParticles) {
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0, p.alpha)));
            g2d.setColor(p.color);
            g2d.fillOval((int)p.x, (int)p.y, (int)p.size, (int)p.size);
        }
        g2d.setComposite(old);
    }

    public void draw(Graphics2D g2d, int width, int height, int score) {
        // Semi-transparent overlay with fade
        float overlayAlpha = Math.min(200, (int)(200 * fadeIn));
        g2d.setColor(new Color(0, 0, 0, (int)overlayAlpha));
        g2d.fillRect(0, 0, width, height);
        
        // Apply fade-in to all text
        Composite oldComp = g2d.getComposite();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, fadeIn));

        // Game over text with shake
        float shakeOffset = (float)(Math.sin(System.currentTimeMillis() * 0.02) * titleShake);
        g2d.setColor(Color.RED);
        Font gameOverFont = new Font("Arial", Font.BOLD, 42);
        g2d.setFont(gameOverFont);

        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth("GAME OVER");
        
        // Shadow
        g2d.setColor(new Color(100, 0, 0, 150));
        g2d.drawString("GAME OVER", (width - textWidth) / 2 + 3 + (int)shakeOffset, height / 3 + 3);
        
        // Main text with gradient
        GradientPaint gradient = new GradientPaint(
            0, height / 3 - 30, new Color(255, 50, 50),
            width, height / 3 - 30, new Color(255, 150, 0)
        );
        g2d.setPaint(gradient);
        g2d.drawString("GAME OVER", (width - textWidth) / 2 + (int)shakeOffset, height / 3);

        // Custom message
        g2d.setColor(Color.YELLOW);
        Font messageFont = new Font("Arial", Font.BOLD, 22);
        g2d.setFont(messageFont);

        fm = g2d.getFontMetrics();
        textWidth = fm.stringWidth(currentMessage);
        g2d.drawString(currentMessage, (width - textWidth) / 2, height / 2 - 50);

        // Score with glow
        g2d.setColor(Color.WHITE);
        Font scoreFont = new Font("Arial", Font.BOLD, 22);
        g2d.setFont(scoreFont);

        String scoreText = "SKOR AKHIR: " + score;
        fm = g2d.getFontMetrics();
        textWidth = fm.stringWidth(scoreText);
        
        // Score shadow
        g2d.setColor(new Color(0, 0, 0, (int)(150 * fadeIn)));
        g2d.drawString(scoreText, (width - textWidth) / 2 + 2, height / 2 + 2);
        
        g2d.setColor(Color.WHITE);
        g2d.drawString(scoreText, (width - textWidth) / 2, height / 2);
        
        // Best score
        if (score >= bestScore && score > 0) {
            float pulse = (float)(Math.sin(textPulse) * 0.3f + 0.7f);
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, pulse * fadeIn));
            g2d.setColor(new Color(255, 215, 0));
            g2d.setFont(new Font("Arial", Font.BOLD, 18));
            String newRecord = "★ REKOR BARU! ★";
            fm = g2d.getFontMetrics();
            g2d.drawString(newRecord, (width - fm.stringWidth(newRecord)) / 2, height / 2 + 30);
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, fadeIn));
        }

        // Draw option buttons
        int btnWidth = 200;
        int btnHeight = 40;
        int btnX = (width - btnWidth) / 2;
        int btn1Y = height * 2 / 3 - 25;
        int btn2Y = height * 2 / 3 + 25;
        
        drawOptionButton(g2d, "▶ COBA LAGI", btnX, btn1Y, btnWidth, btnHeight, 
                         new Color(0, 180, 0), selectedOption == 0);
        drawOptionButton(g2d, "▶ MENU UTAMA", btnX, btn2Y, btnWidth, btnHeight, 
                         new Color(180, 0, 0), selectedOption == 1);

        // Control hints
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, fadeIn));
        g2d.setColor(new Color(180, 180, 180));
        Font hintFont = new Font("Arial", Font.BOLD, 12);
        g2d.setFont(hintFont);

        String hintText = "[W/S] Navigasi  |  [ENTER/SPACE] Pilih  |  [ESC] Menu Utama";
        fm = g2d.getFontMetrics();
        textWidth = fm.stringWidth(hintText);
        
        // Hint background
        g2d.setColor(new Color(0, 0, 0, 120));
        g2d.fillRoundRect((width - textWidth) / 2 - 10, height - 40, textWidth + 20, 20, 8, 8);
        
        g2d.setColor(new Color(200, 200, 200));
        g2d.drawString(hintText, (width - textWidth) / 2, height - 26);
        
        g2d.setComposite(oldComp);
    }
    
    private void drawOptionButton(Graphics2D g2d, String text, int x, int y, int width, int height, 
                                   Color baseColor, boolean isSelected) {
        // Glow for selected
        if (isSelected) {
            Composite old = g2d.getComposite();
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f * fadeIn));
            g2d.setColor(new Color(255, 255, 200));
            for (int i = 5; i > 0; i--) {
                g2d.fillRoundRect(x - i, y - i, width + i * 2, height + i * 2, 12, 12);
            }
            g2d.setComposite(old);
        }
        
        // Button background
        Color bg = isSelected ? baseColor.brighter() : baseColor;
        GradientPaint grad = new GradientPaint(x, y, bg, x, y + height, bg.darker());
        g2d.setPaint(grad);
        g2d.fillRoundRect(x, y, width, height, 10, 10);
        
        // Border
        g2d.setColor(isSelected ? Color.WHITE : bg.darker().darker());
        g2d.setStroke(new BasicStroke(isSelected ? 2.5f : 1.5f));
        g2d.drawRoundRect(x, y, width, height, 10, 10);
        
        // Text
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 16));
        FontMetrics fm = g2d.getFontMetrics();
        int textX = x + (width - fm.stringWidth(text)) / 2;
        int textY = y + ((height - fm.getHeight()) / 2) + fm.getAscent();
        g2d.drawString(text, textX, textY);
    }
    
    // --- Death particle ---
    private static class DeathParticle {
        float x, y, vx, vy, size, alpha;
        Color color;
        Random rand;
        int screenW, screenH;
        
        DeathParticle(Random rand, int screenW, int screenH) {
            this.rand = rand;
            this.screenW = screenW;
            this.screenH = screenH;
            reset();
        }
        
        void reset() {
            x = rand.nextFloat() * screenW;
            y = rand.nextFloat() * screenH;
            vx = (rand.nextFloat() - 0.5f) * 1.5f;
            vy = rand.nextFloat() * 1.0f + 0.5f;
            size = rand.nextFloat() * 3 + 1;
            alpha = rand.nextFloat() * 0.3f + 0.05f;
            
            int type = rand.nextInt(3);
            switch (type) {
                case 0: color = new Color(255, 50, 50); break;
                case 1: color = new Color(255, 100, 50); break;
                case 2: color = new Color(255, 150, 50); break;
            }
        }
        
        void update() {
            x += vx;
            y += vy;
            alpha -= 0.001f;
            
            if (y > screenH + 10 || alpha <= 0) {
                reset();
                y = -10;
            }
        }
    }
}