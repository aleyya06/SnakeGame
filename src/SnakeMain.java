import javax.swing.*;

public class SnakeMain {
    public static void main(String[] args) {
        // Mengatur tampilan UI agar sesuai dengan sistem operasi
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.out.println("Error setting look and feel: " + e.getMessage());
            e.printStackTrace(); // Penting untuk melihat detail error
        }

        // Membuat frame utama
        JFrame frame = new JFrame("Snake Game");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        // --- KRITIS: Gunakan ukuran yang Anda inginkan (713, 613) ---
        frame.setSize(713, 613); // Tetap pakai ukuran ini sesuai permintaan Anda
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);

        // Menambahkan panel menu utama
        MainMenuPanel menuPanel = new MainMenuPanel(frame);
        frame.add(menuPanel);

        // Menampilkan frame
        frame.setVisible(true); // Ini harus tetap ada

        // Debugging info
        System.out.println("Frame size: " + frame.getSize());
        System.out.println("Menu panel size: " + menuPanel.getSize()); // Ini seharusnya 713x613
        System.out.println("Application started successfully");
    }
}