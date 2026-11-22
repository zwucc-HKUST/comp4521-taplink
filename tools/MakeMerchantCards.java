import com.google.zxing.BarcodeFormat;
import com.google.zxing.qrcode.QRCodeWriter;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Run from the project root with Java 17+ and the bundled ZXing jar on the classpath. */
class MakeMerchantCards {
    public static void main(String[] args) throws Exception {
        String[][] examples = {
            {"PIER", "Star Pier Coffee", "18.50"},
            {"FERRY", "Harbour Ferry", "8.20"},
            {"BOOKS", "Paper Lantern Books", "42.00"}
        };
        Path target = Files.createDirectories(Path.of("demo", "qr"));
        for (var sample : examples) {
            var matrix = new QRCodeWriter().encode("TL1|" + sample[0] + "|" + sample[2], BarcodeFormat.QR_CODE, 500, 500);
            var image = new BufferedImage(560, 680, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setColor(Color.WHITE); g.fillRect(0, 0, 560, 680);
            g.setColor(new Color(0x063e37)); g.fillRect(0, 0, 560, 70);
            g.setColor(Color.WHITE); g.setFont(new Font("SansSerif", Font.BOLD, 28));
            g.drawString("TapLink   /   Demo merchant", 28, 45);
            for (int y = 0; y < 500; y++) for (int x = 0; x < 500; x++)
                if (matrix.get(x, y)) image.setRGB(30+x, 82+y, 0x123630);
            g.setColor(new Color(0x063e37)); g.drawString(sample[1], 28, 610);
            g.setFont(new Font("SansSerif", Font.PLAIN, 24)); g.drawString("HK$" + sample[2] + "   ·   simulated purchase", 28, 650);
            g.dispose(); ImageIO.write(image, "png", target.resolve(sample[0]+".png").toFile());
        }
    }
}
