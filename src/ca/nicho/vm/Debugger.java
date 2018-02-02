package ca.nicho.vm;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.File;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.PlainDocument;

public class Debugger extends JFrame {

	private HSystem sys;
	private HexEditor hex;
	private DebuggerToolbar tool;

	public Debugger(File in) {
		sys = new HSystem(in);
		hex = new HexEditor();
		tool = new DebuggerToolbar();
		hex.updateMemory(0);

		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		this.setVisible(true);
		this.setLayout(null);
		this.getContentPane().add(hex);
		this.getContentPane().add(tool);
		this.getContentPane().setPreferredSize(new Dimension(hex.getWidth() + tool.getWidth(), hex.getHeight()));
		this.pack();
		this.setResizable(false);
		this.requestFocus();
	}

	private void next(int amount) {
		for (int i = 0; i < amount; i++){
			sys.next();
			hex.updateMemory(0);
		}
	}

	private class HexEditor extends JPanel {

		private static final int ROW_WIDTH = 16;
		private static final int ROW_HEIGHT = 32;

		private static final int C_LABEL_WIDTH = 30;
		private static final int C_LABEL_HEIGHT = 18;

		private static final int R_LABEL_WIDTH = 45;

		private JLabel[] cLabels = new JLabel[ROW_WIDTH];
		private JLabel[] rLabels = new JLabel[ROW_HEIGHT];
		private JTextFieldLimit[] mFields = new JTextFieldLimit[ROW_HEIGHT * ROW_WIDTH];

		public HexEditor() {
			this.setBounds(0, 0, R_LABEL_WIDTH + C_LABEL_WIDTH * ROW_WIDTH, C_LABEL_HEIGHT * (ROW_HEIGHT + 1));
			this.setVisible(true);
			this.setLayout(null);

			createCLabels();
			createRLabels();
			createMFields();
		}

		private void createCLabels() {
			for (int i = 0; i < ROW_WIDTH; i++) {
				cLabels[i] = new JLabel(String.format("%02X", i), JLabel.CENTER);
				cLabels[i].setBounds(R_LABEL_WIDTH + i * C_LABEL_WIDTH, 0, C_LABEL_WIDTH, C_LABEL_HEIGHT);
				this.add(cLabels[i]);
			}
		}

		private void createRLabels() {
			for (int i = 0; i < ROW_HEIGHT; i++) {
				rLabels[i] = new JLabel(String.format("%03X", i * ROW_WIDTH), JLabel.CENTER);
				rLabels[i].setBounds(0, (i + 1) * C_LABEL_HEIGHT, R_LABEL_WIDTH, C_LABEL_HEIGHT);
				this.add(rLabels[i]);
			}
		}

		private void createMFields(){
			for(int x = 0; x < ROW_WIDTH; x++){
				for(int y = 0; y < ROW_HEIGHT; y++){
					int i = x + ROW_WIDTH * y;
					mFields[i] = new JTextFieldLimit();
					mFields[i].setHorizontalAlignment(JTextField.CENTER);
					mFields[i].setBounds(R_LABEL_WIDTH + x * C_LABEL_WIDTH, (y + 1) * C_LABEL_HEIGHT, C_LABEL_WIDTH, C_LABEL_HEIGHT);
					mFields[i].setEditable(false);
					this.add(mFields[i]);
				}
			}
		}

		private void updateMemory(int offset) {
			// Ensure offset is always within bounds of memory
			if (offset - ROW_HEIGHT > (HSystem.MEMORY_CAPACITY / ROW_WIDTH)) {
				offset = HSystem.MEMORY_CAPACITY / ROW_WIDTH;
			}
			for (int x = 0; x < ROW_WIDTH; x++) {
				for (int y = 0; y < ROW_HEIGHT; y++) {
					int dy = y + offset;
					mFields[x + y * ROW_WIDTH]
							.setText(String.format("%02X", sys.SYSTEM_MEMORY.get(x + dy * ROW_WIDTH)));
				}
			}
		}

	}
	
	private class DebuggerToolbar extends JPanel {
		
		private static final int TOOL_WIDTH = 200;
		private static final int BUTTON_HEIGHT = 32;
		
		private JButton nextB;
		
		public DebuggerToolbar(){
			
			this.setBounds(hex.getWidth(), 0, TOOL_WIDTH, 100);
			this.setLayout(null);
			this.setVisible(true);
			
			nextB = new JButton("Next");
			nextB.setBounds(0, 0, TOOL_WIDTH, BUTTON_HEIGHT);
			
			
			nextB.addActionListener((ActionEvent e) -> {
				next(1);
			});
			
			this.add(nextB);
						
		}
		
	}

	private class JTextFieldLimit extends JTextField {
	    private int limit;

	    public JTextFieldLimit() {
	        super();
	        this.limit = 2;
	    }

	    @Override
	    protected Document createDefaultModel() {
	        return new LimitDocument();
	    }

	    private class LimitDocument extends PlainDocument {
	        @Override
	        public void insertString( int offset, String  str, AttributeSet attr ) throws BadLocationException {
	            if (str == null) return;

	            if ((getLength() + str.length()) <= limit) {
	                super.insertString(offset, str, attr);
	            }
	        }       

	    }

	}

}
