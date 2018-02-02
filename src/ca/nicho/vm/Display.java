package ca.nicho.vm;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

import javax.swing.JFrame;
import javax.swing.JPanel;

public class Display extends JFrame {
	
	public static final int WIDTH = 256;
	public static final int HEIGHT = 224;
	
	private JPanel panel;
	private BufferedImage screen;
	private byte[] raster;
	
	public Display(){
		panel = new Screen();
		panel.setSize(WIDTH * 2, HEIGHT * 2);
		
		screen = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_3BYTE_BGR);
		raster = ((DataBufferByte)screen.getRaster().getDataBuffer()).getData();
		
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		this.setVisible(true);
		this.getContentPane().add(panel);
		this.getContentPane().setPreferredSize(new Dimension(WIDTH * 2, HEIGHT * 2));
		this.pack();
		this.setResizable(false);
		this.update();
		
	}
	
	//Each color is 12 bit RGB,
	public void drawVRAM(byte[] b){
		for(int i = 0; i < WIDTH * HEIGHT; i++){
			int v = b[i];
			byte cr = (byte)(((v & 0b1100000) >> 5) / 4f * 256);
			byte cg = (byte)(((v & 0b0011100) >> 2) / 8f * 256);
			byte cb = (byte)((v & 0b0000011) / 4f * 256);
			raster[i * 3] = cb;
			raster[i * 3 + 1] = cg;
			raster[i * 3 + 2] = cr;
		}
	}
		
	public void update(){
		panel.repaint();
		this.repaint();
	}
	
	private class Screen extends JPanel {
		
		@Override
	    protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			Image scaledImage = screen.getScaledInstance(getWidth(), getHeight(), Image.SCALE_FAST);
			g.drawImage(scaledImage, 0, 0, null);
		}
		
	}
	
}
