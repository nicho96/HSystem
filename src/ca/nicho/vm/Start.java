package ca.nicho.vm;

import java.io.File;

public class Start {

	public static void main(String[] s) throws Exception {
		Assembler a = new Assembler(new File("test.na"));
		Debugger debugger = new Debugger(a.file_out);
	}
	
}
