import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.HashMap;
import java.util.Map;

class PixelFont {
    private static final Map<Character, boolean[][]> glyphs = new HashMap<>();

    static {
        add('0', "11111,10001,10011,10101,11001,10001,11111");
        add('1', "00100,01100,00100,00100,00100,00100,01110");
        add('2', "11110,00001,00001,11110,10000,10000,11111");
        add('3', "11110,00001,00001,01110,00001,00001,11110");
        add('4', "10010,10010,10010,11111,00010,00010,00010");
        add('5', "11111,10000,10000,11110,00001,00001,11110");
        add('6', "01110,10000,10000,11110,10001,10001,01110");
        add('7', "11111,00001,00010,00100,01000,01000,01000");
        add('8', "01110,10001,10001,01110,10001,10001,01110");
        add('9', "01110,10001,10001,01111,00001,00001,01110");
        add('A', "01110,10001,10001,11111,10001,10001,10001");
        add('B', "11110,10001,10001,11110,10001,10001,11110");
        add('C', "01110,10001,10000,10000,10000,10001,01110");
        add('D', "11110,10001,10001,10001,10001,10001,11110");
        add('E', "11111,10000,10000,11110,10000,10000,11111");
        add('F', "11111,10000,10000,11110,10000,10000,10000");
        add('G', "01110,10001,10000,10111,10001,10001,01111");
        add('H', "10001,10001,10001,11111,10001,10001,10001");
        add('I', "01110,00100,00100,00100,00100,00100,01110");
        add('J', "00001,00001,00001,00001,10001,10001,01110");
        add('K', "10001,10010,10100,11000,10100,10010,10001");
        add('L', "10000,10000,10000,10000,10000,10000,11111");
        add('M', "10001,11011,10101,10101,10001,10001,10001");
        add('N', "10001,10001,11001,10101,10011,10001,10001");
        add('O', "01110,10001,10001,10001,10001,10001,01110");
        add('P', "11110,10001,10001,11110,10000,10000,10000");
        add('Q', "01110,10001,10001,10001,10101,10010,01101");
        add('R', "11110,10001,10001,11110,10100,10010,10001");
        add('S', "01111,10000,10000,01110,00001,00001,11110");
        add('T', "11111,00100,00100,00100,00100,00100,00100");
        add('U', "10001,10001,10001,10001,10001,10001,01110");
        add('V', "10001,10001,10001,10001,10001,01010,00100");
        add('W', "10001,10001,10001,10101,10101,11011,10001");
        add('X', "10001,10001,01010,00100,01010,10001,10001");
        add('Y', "10001,10001,01010,00100,00100,00100,00100");
        add('Z', "11111,00001,00010,00100,01000,10000,11111");
        add(' ', "00000,00000,00000,00000,00000,00000,00000");
        add('>', "00100,00010,00001,00001,00010,00100,00000");
        add('<', "00100,01000,10000,10000,01000,00100,00000");
        add('-', "00000,00000,00000,11111,00000,00000,00000");
        add('_', "00000,00000,00000,00000,00000,00000,11111");
        add('.', "00000,00000,00000,00000,00000,01100,01100");
        add(',', "00000,00000,00000,00000,00000,01100,01000");
        add(':', "00000,01100,01100,00000,01100,01100,00000");
        add('/', "00001,00010,00100,00100,01000,10000,10000");
        add('!', "00100,00100,00100,00100,00100,00000,00100");
        add('?', "01110,10001,00010,00100,00100,00000,00100");
        add('\'', "00100,00100,00000,00000,00000,00000,00000");
        add('"', "01010,01010,00000,00000,00000,00000,00000");
        add('*', "00100,10101,01110,11111,01110,10101,00100");
        add('+', "00100,00100,11111,00100,00100,00000,00000");
        add('(', "00010,00100,01000,01000,01000,00100,00010");
        add(')', "01000,00100,00010,00010,00010,00100,01000");
    }

    private static void add(char ch, String rows) {
        String[] parts = rows.split(",");
        boolean[][] grid = new boolean[7][5];
        for (int r = 0; r < Math.min(parts.length, 7); r++) {
            String row = parts[r];
            for (int c = 0; c < Math.min(row.length(), 5); c++) {
                grid[r][c] = row.charAt(c) == '1';
            }
        }
        glyphs.put(ch, grid);
    }

    static void draw(Graphics2D g2, String text, Rectangle bounds, int scale, Color color) {
        g2.setColor(color);
        String upper = text.toUpperCase();
        int charWidth = 5 * scale;
        int charHeight = 7 * scale;
        int spacing = scale;
        int totalWidth = 0;
        for (int i = 0; i < upper.length(); i++) {
            char ch = upper.charAt(i);
            if (!glyphs.containsKey(ch)) {
                ch = ' ';
            }
            totalWidth += charWidth + spacing;
        }
        if (upper.length() > 0) {
            totalWidth -= spacing;
        }
        int startX = bounds.x + Math.max(0, (bounds.width - totalWidth) / 2);
        int startY = bounds.y + Math.max(0, (bounds.height - charHeight) / 2);

        int x = startX;
        for (int i = 0; i < upper.length(); i++) {
            char ch = upper.charAt(i);
            boolean[][] grid = glyphs.getOrDefault(ch, glyphs.get(' '));
            for (int r = 0; r < 7; r++) {
                for (int c = 0; c < 5; c++) {
                    if (grid[r][c]) {
                        int px = x + c * scale;
                        int py = startY + r * scale;
                        g2.fillRect(px, py, scale, scale);
                    }
                }
            }
            x += charWidth + spacing;
        }
    }

    static void drawLeft(Graphics2D g2, String text, Rectangle bounds, int scale, Color color) {
        g2.setColor(color);
        String upper = text.toUpperCase();
        int charWidth = 5 * scale;
        int charHeight = 7 * scale;
        int spacing = scale;

        int startX = bounds.x;
        int startY = bounds.y + Math.max(0, (bounds.height - charHeight) / 2);

        int x = startX;
        for (int i = 0; i < upper.length(); i++) {
            char ch = upper.charAt(i);
            boolean[][] grid = glyphs.getOrDefault(ch, glyphs.get(' '));
            for (int r = 0; r < 7; r++) {
                for (int c = 0; c < 5; c++) {
                    if (grid[r][c]) {
                        int px = x + c * scale;
                        int py = startY + r * scale;
                        g2.fillRect(px, py, scale, scale);
                    }
                }
            }
            x += charWidth + spacing;
        }
    }
}
