package name.bizna.ocarmsim;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseEvent;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;

import javax.swing.JComponent;
import javax.swing.event.MouseInputListener;

public class SimScreenPanel extends JComponent implements MouseInputListener, FocusListener {
	public static final long serialVersionUID = 1;
	private static final int NUM_REDS = 6;
	private static final int NUM_GREENS = 8;
	private static final int NUM_BLUES = 5;
	
	private static FontRenderContext frc = new FontRenderContext(null, false, false);
	private static Font font = Font.decode("Monospaced-BOLD-14");
	private static int charWidth, charHeight, baseline;
	public static int[] oneBitPalette = new int[]{0x000000, 0xFFFFFF};
	public static int[] fourBitPalette = new int[]{
		0xFFFFFF, 0xFFCC33, 0xCC66CC, 0x6699FF,
	    0xFFFF33, 0x33CC33, 0xFF6699, 0x333333,
	    0xCCCCCC, 0x336699, 0x9933CC, 0x333399,
	    0x663300, 0x336600, 0xFF3333, 0x000000
	};
	public static int[] eightBitPalette = new int[256];
	static {
		Rectangle2D maxCharBounds = font.getMaxCharBounds(frc);
		charWidth = (int)Math.ceil(maxCharBounds.getWidth());
		LineMetrics metrics = font.getLineMetrics("The quick brown fox jumps over the lazy dog.", frc);
		charHeight = (int)Math.ceil(metrics.getHeight());
		if(charWidth > charHeight) charWidth /= 2;
		baseline = (int)Math.floor(metrics.getAscent());
		for(int n = 0; n < 16; ++n) {
			/* don't reach pure black or white with this ramp, since they'll be included in the color cube below */
			int nb = (n + 1) * 255 / 17;
			eightBitPalette[n] = (nb<<16)|(nb<<8)|nb;
		}
		int n = 16;
		for(int r = 0; r < NUM_REDS; ++r) {
			for(int g = 0; g < NUM_GREENS; ++g) {
				for(int b = 0; b < NUM_BLUES; ++b) {
					eightBitPalette[n++] = ((int)(r*255/(NUM_REDS-1.0)+0.5)<<24)
							|((int)(g*255/(NUM_GREENS-1.0)+0.5)<<24)
							|(int)(b*255/(NUM_BLUES-1.0)+0.5);
				}
			}
		}
	}
	private int rows, cols, maxRows, maxCols, bits, maxBits;
	private Dimension myDimensions;
	private int[] palette;
	private int mutableColorCount;
	private char[] glyphs;
	private short[] colors;
	
	private double colorDelta(int a, int b) {
		int ra = (a >> 16) & 255;
		int ga = (a >> 8) & 255;
		int ba = a & 255;
		int rb = (b >> 16) & 255;
		int gb = (b >> 8) & 255;
		int bb = b & 255;
		int dr = ra - rb;
		int dg = ga - gb;
		int db = ba - bb;
		return 0.2126 * dr * dr + 0.7152 * dg * dg + 0.0722 * db * db;
	}
	
	private int findColorIndex(int color) {
		double smallestDistance = colorDelta(color, palette[0]);
		int closestIndex = 0;
		for(int n = 1; n < palette.length; ++n) {
			double distance = colorDelta(color, palette[n]);
			if(distance < smallestDistance) {
				smallestDistance = distance;
				closestIndex = n;
			}
		}
		return closestIndex;
	}
	
	public SimScreenPanel(int maxRows, int maxCols, int bits, FakeMachine machine) {
		this.maxRows = maxRows;
		this.maxCols = maxCols;
		this.rows = maxRows;
		this.cols = maxCols;
		this.bits = bits;
		this.maxBits = bits;
		myDimensions = new Dimension(maxCols * charWidth, maxRows * charHeight);
		glyphs = new char[maxRows * maxCols];
		colors = new short[maxRows * maxCols];
		reset();
		machine.addNode(new SimGPU(this));
		machine.addNode(new SimScreen(this));
		machine.addNode(new SimKeyboard());
		setFocusable(true);
		addMouseListener(this);
		addFocusListener(this);
	}
	
	private static int focusColor(int x, boolean focused) {
		if(focused) return x;
		else return ((x>>>1)&0x7F7F7F)+0x404040;
	}
	
	@Override
	public void paintComponent(Graphics g) {
		int xo = ((maxCols * charWidth) - (cols * charWidth)) / 2;
		int yo = ((maxRows * charHeight) - (rows * charHeight)) / 2;
		g.setColor(Color.gray);
		g.fillRect(0, 0, maxCols * charWidth, maxRows * charHeight);
		g.setColor(Color.gray);
		g.draw3DRect(xo-1, yo-1, cols*charWidth+1, rows*charHeight+1, true);
		g.setFont(font);
		boolean focused = hasFocus();
		int n = 0;
		for(int y = 0; y < rows; ++y) {
			for(int x = 0; x < cols; ++x) {
				short color = colors[n];
				char glyph = glyphs[n];
				g.setColor(new Color(focusColor(palette[color&255], focused)));
				g.fillRect(xo+x*charWidth, yo+y*charHeight, charWidth, charHeight);
				if(glyph != 0 && glyph != ' ') {
					g.setColor(new Color(focusColor(palette[color>>>8], focused)));
					g.drawChars(glyphs, n, 1, xo+x*charWidth, yo+y*charHeight+baseline);
				}
				++n;
			}
		}
	}
	
	@Override
	public Dimension getPreferredSize() {
		return myDimensions;
	}

	@Override
	public Dimension getMinimumSize() {
		return myDimensions;
	}

	@Override
	public Dimension getMaximumSize() {
		return myDimensions;
	}
	
	public boolean isOn() { return true; }
	public boolean isPrecise() { return false; }
	public boolean isTouchModeInverted() { return false; }
	public boolean setPrecise(boolean wat) { return false; }
	public boolean setTouchModeInverted(boolean wat) { return false; }
	public boolean turnOff() { return false; }
	public boolean turnOn() { return true; }
	
	public void reset() {
		switch(bits) {
		case 1:
			palette = oneBitPalette;
			mutableColorCount = 0;
			break;
		case 4:
			palette = fourBitPalette.clone();
			mutableColorCount = 16;
			break;
		case 8:
			palette = eightBitPalette.clone();
			mutableColorCount = 16;
			break;
		default:
			throw new RuntimeException("It's 3:30 AM and I don't want to make my own exception type for this");
		}
		short color = (short)((findColorIndex(0xFFFFFF) << 8) | findColorIndex(0x000000));
		for(int n = 0; n < colors.length; ++n) {
			glyphs[n] = ' ';
			colors[n] = color;
		}
	}
	
	public int getPaletteColor(int index) {
		return palette[index];
	}
	
	public int setPaletteColor(int index, int color) {
		if(index >= mutableColorCount) throw new IndexOutOfBoundsException();
		palette[index] = color;
		return color;
	}
	
	public int maxBits() {
		return maxBits;
	}
	
	public int curBits() {
		return bits;
	}
	
	public boolean setBits(int bits) {
		if(bits <= maxBits && (bits == 1 || bits == 4 || bits == 8)) {
			this.bits = bits;
			reset();
			return true;
		}
		else return false;
	}
	
	public int getMaxCols() {
		return maxCols;
	}

	public int getMaxRows() {
		return maxRows;
	}
	
	public int getCurCols() {
		return cols;
	}

	public int getCurRows() {
		return rows;
	}
	
	public boolean setResolution(int width, int height) {
		if(width <= maxCols && height <= maxRows) {
			cols = width;
			rows = height;
			short color = (short)((findColorIndex(0xFFFFFF) << 8) | findColorIndex(0x000000));
			for(int n = 0; n < colors.length; ++n) {
				glyphs[n] = ' ';
				colors[n] = color;
			}
			return true;
		}
		else return false;
	}
	
	public Object[] getPixel(int x, int y) {
		if(x < 1 || x > cols || y < 1 || y > rows) return null;
		--x; --y;
		ArrayList<Object> list = new ArrayList<Object>();
		list.add(Character.valueOf(glyphs[x+y*cols]).toString());
		short c = colors[x+y*cols];
		int bg = (c&255);
		int fg = (c>>>8);
		list.add(Integer.valueOf(palette[fg]));
		list.add(Integer.valueOf(palette[bg]));
		list.add(Integer.valueOf(fg));
		list.add(Integer.valueOf(bg));
		return list.toArray();
	}
	
	public boolean set(int background, int foreground, int x, int y, String str, boolean vertical) {
		if(x < 1 || x > maxCols || y < 1 || y > maxRows) return false;
		--x; --y;
		int bgi = findColorIndex(background);
		int fgi = findColorIndex(foreground);
		short c = (short)((fgi<<8)|bgi);
		int q = x+y*cols;
		int i = 0;
		if(vertical) {
			while(y < rows && i < str.length()) {
				colors[q] = c;
				glyphs[q] = str.charAt(i);
				q += cols; ++y; ++i;
			}
		}
		else {
			while(x < cols && i < str.length()) {
				colors[q] = c;
				glyphs[q] = str.charAt(i);
				++q; ++x; ++i;
			}
		}
		repaint();
		return true;
	}

	private void copyRow(int sx, int sy, int w, int dx, int dy) {
		int sq = sy * cols + sx;
		int dq = dy * cols + dx;
		if(sy == dy && dx > sx) {
			for(int xd = w-1; xd >= 0; --xd) {
				colors[dq+xd] = colors[sq+xd];
				glyphs[dq+xd] = glyphs[sq+xd];
			}
		}
		else {
			for(int xd = 0; xd < w; ++xd) {
				colors[dq+xd] = colors[sq+xd];
				glyphs[dq+xd] = glyphs[sq+xd];
			}
		}
	}
	
	public boolean copy(int sx, int sy, int w, int h, int dx, int dy) {
		--sx; --sy; dx += sx; dy += sy;
		if(sx < 0) { w += sx; dx -= sx; sx = 0; }
		if(dx < 0) { w += dx; sx -= dx; dx = 0; }
		if(sy < 0) { h += sy; dy -= sy; sy = 0; }
		if(dy < 0) { h += dy; sy -= dy; dy = 0; }
		if(w+sx > cols) { w += cols-(w+sx); }
		if(w+dx > cols) { w += cols-(w+dx); }
		if(h+sy > rows) { h += rows-(h+sy); }
		if(h+dy > rows) { h += rows-(h+dy); }
		int sdo = sy - dy;
		if(dy <= sy) {
			for(int y = dy; y < dy + h; ++y) {
				copyRow(sx, y+sdo, w, dx, y);
			}
		}
		else {
			for(int y = dy + h - 1; y >= dy; --y) {
				copyRow(sx, y+sdo, w, dx, y);
			}
		}
		repaint();
		return true;
	}
	
	public boolean fill(int background, int foreground, int x, int y, int w, int h, char glyph) {
		if(x < 1 || x > cols || y < 1 || y > rows) return false;
		--x; --y;
		int bgi = findColorIndex(background);
		int fgi = findColorIndex(foreground);
		short c = (short)((fgi<<8)|bgi);
		while(h > 0) {
			int q = x + y * cols;
			int rem = w;
			while(rem > 0) {
				glyphs[q] = glyph;
				colors[q] = c;
				++q; --rem;
			}
			++y; --h;
		}
		repaint();
		return true;
	}

	@Override
	public void mouseClicked(MouseEvent arg0) {
		requestFocusInWindow();
	}

	@Override
	public void mouseEntered(MouseEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void mouseExited(MouseEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void mousePressed(MouseEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void mouseReleased(MouseEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void mouseDragged(MouseEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void mouseMoved(MouseEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void focusGained(FocusEvent arg0) {
		repaint();
	}

	@Override
	public void focusLost(FocusEvent arg0) {
		repaint();
	}
}
