package ca.nicho.vm;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

public class Assembler {

	public static HashMap<String, Integer> MAP = new HashMap<String, Integer>();
	
	static {
		
		MAP.put("WIM", 0x00000000);
		MAP.put("MOV", 0x10000000);
		MAP.put("WBM", 0x20000000);
		MAP.put("RBM", 0x30000000);
		MAP.put("ADD", 0x40000000);
		MAP.put("SUB", 0x44000000);
		MAP.put("MUL", 0x48000000);
		MAP.put("DIV", 0x4C000000);
		MAP.put("JMP", 0x50000000);
		MAP.put("CALL", 0x50800000);
		MAP.put("RET", 0x50000000); // Just to keep op names consistent
		MAP.put("JE", 0x54000000);
		MAP.put("JLT", 0x58000000);
		MAP.put("JGT", 0x5C000000);
		MAP.put("PUSHI", 0x60000000);
		MAP.put("POPI", 0x64000000);
		MAP.put("PUSHB", 0x68000000);
		MAP.put("POPB", 0x6C000000);
		MAP.put("WVR", 0x80000000);
		MAP.put("UVR", 0x90000000);

	}
	
	private HashMap<String, Integer> labels = new HashMap<String, Integer>();
	
	public File file_out;
	
	private int stack_size;
	private int data_size;
	
	public Assembler(File f) throws Exception {
				
		File fo = new File(f.getName().substring(0, f.getName().lastIndexOf('.')) + ".nx");
		
		if(!fo.exists()){
			fo.createNewFile();
		}
		
		Scanner sc = new Scanner(f);
		DataOutputStream out = new DataOutputStream(new FileOutputStream(fo));
			
		ArrayList<String> all = new ArrayList<String>();
		int mem_pos = 0;
		//First pass finds all labels, and assigns them a position in memory.
		while(sc.hasNextLine()){
			String line = sc.nextLine().split(";")[0].trim();
			if(line.length() == 0) continue;
			if(line.startsWith(":")){
				labels.put(line, mem_pos);
				continue;
			}else if(line.startsWith(".")){ 
				String[] split = line.split(" ");
				if(split[0].equalsIgnoreCase(".stack")){
					stack_size = Integer.parseInt(split[1]);
				}
			}else{
				mem_pos += 4;
				all.add(line);
			}
		}
		sc.close();
					
		//Write the size of the program
		out.writeInt(mem_pos);
		out.writeShort(stack_size);

		for(String line : all){
			try {
				String[] split = line.split(" ");
				int op_code = MAP.get(split[0]);	
				int instr = handle_opcode(op_code, split);
				out.writeInt(instr);
			}catch(Exception e){
				System.err.println("ERROR AT " + line + " - " + e.getMessage());
			}
			
		}
			
		out.close();
		file_out = fo;
	}
	
	
	private int handle_opcode(int op_code, String[] split) throws Exception {
		switch(op_code){
			case 0x00000000: return WIM(op_code, split);
			case 0x10000000: return MOV(op_code, split);
			case 0x20000000: return WBM(op_code, split);
			case 0x30000000: return RBM(op_code, split);
			case 0x40000000: return ARITH(op_code, split);
			case 0x44000000: return ARITH(op_code, split);
			case 0x48000000: return ARITH(op_code, split);
			case 0x4C000000: return ARITH(op_code, split);
			case 0x50000000: return JMP(op_code, split);
			case 0x50800000: return JMP(op_code, split);
			case 0x54000000: return JMP_CONDITION(op_code, split);
			case 0x58000000: return JMP_CONDITION(op_code, split);
			case 0x5C000000: return JMP_CONDITION(op_code, split);
			case 0x60000000: return PUSH_POP(op_code, split);
			case 0x64000000: return PUSH_POP(op_code, split);
			case 0x68000000: return PUSH_POP(op_code, split);
			case 0x6C000000: return PUSH_POP(op_code, split);
			case 0x80000000: return WVR(op_code, split);
			case 0x90000000: return UVR(op_code, split);
			default: System.out.println("OPCODE NOT FOUND");
		}
		return 0;
	}
	
	@Deprecated
	private int WIM(int op_code, String[] a) throws Exception{
		if(a[1].charAt(0) == '&' && a[2].charAt(0) == '#'){
			int ind = Integer.parseInt(a[1].substring(1, a[1].length()));
			short val = Short.parseShort(a[2].substring(1, a[2].length()));
			return op_code | (ind << 16) | val;
		}
		throw new ASMParseException("Invalid syntax.");
	}
	
	@Deprecated
	private int RIM(int op_code, String[] a) throws Exception{
		if(a[1].charAt(0) == '&'){
			int ind = Integer.parseInt(a[1].substring(1, a[1].length()));
			return op_code | (ind << 16);
		}
		throw new ASMParseException("Invalid syntax.");
	}
	
	private int MOV(int op_code, String[] a){
		int mode = 0;
		int reg1 = 0;
		int reg2 = 0;
		short val = 0;
		int ind1 = 0;
		int ind2 = 0;
		int pre_ind = 0;
		short pre_val = 0;
		
		if(a[1].charAt(0) == '$'){
			reg1 = Integer.parseInt(a[1].substring(1, a[1].length()));
			if(a[2].charAt(0) == '#'){ //MOV <REG> <LIT>
				mode = 0b000;
				val = (short)Integer.parseInt(a[2].substring(1, a[2].length()));
			}else if(a[2].charAt(0) == '&'){ //MOV <REG> <MEM>
				mode = 0b001;
				ind1 = Integer.parseInt(a[2].substring(1, a[2].length()));
			}else if(a[2].charAt(0) == '$'){ //MOV <REG> <REG>
				mode = 0b010;
				reg2 = Integer.parseInt(a[2].substring(1, a[2].length()));
			}
		}else if(a[1].charAt(0) == '&') {
			if(a[2].charAt(0) == '&'){
				mode = 0b011;
				ind1 = Integer.parseInt(a[1].substring(1, a[1].length()));
				ind2 = Integer.parseInt(a[2].substring(1, a[2].length()));
			}else if(a[2].charAt(0) == '$'){
				mode = 0b100;
				ind1 = Integer.parseInt(a[1].substring(1, a[1].length()));
				reg1 = Integer.parseInt(a[2].substring(1, a[2].length()));
			}else if(a[2].charAt(0) == '#'){
				mode = 0b110;
				pre_ind = Integer.parseInt(a[1].substring(1, a[1].length()));
				pre_val = (short)Integer.parseInt(a[2].substring(1, a[2].length()));
			}
		}
		
		return op_code | (mode << 25) | (reg1 << 22) | (reg2 << 20)
				| (pre_ind << 14) | (ind2 << 12) | ind1 | val | pre_val;
	}
	
	private int WBM(int op_code, String[] a) throws Exception{
		if(a[1].charAt(0) == '&' && a[2].charAt(0) == '#'){
			int ind = Integer.parseInt(a[1].substring(1, a[1].length()));
			byte val = Byte.parseByte(a[2].substring(1, a[2].length()));
			return op_code | (ind << 16) | val;
		}
		throw new ASMParseException("Invalid syntax.");
	}
	
	private int RBM(int op_code, String[] a) throws Exception{
		if(a[1].charAt(0) == '&'){
			int ind = Integer.parseInt(a[1].substring(1, a[1].length()));
			return op_code | (ind << 16);
		}
		throw new ASMParseException("Invalid syntax.");
	}
	
	private int ARITH(int op_code, String[] a) throws Exception{
		int mode = 0;
		int reg1 = 0;
		int reg2 = 0;
		int i1 = 0;
		int i2 = 0;
		int val = 0;
		if(a[1].charAt(0) == '&' && a[2].charAt(0) == '&'){
			mode = 0b00;
			i1 = Integer.parseInt(a[1].substring(1, a[1].length()));
			i2 = Integer.parseInt(a[2].substring(1, a[2].length()));
		}else{
			if(a[1].charAt(0) == '$'){
				reg1 = Integer.parseInt(a[1].substring(1, a[1].length()));
				if(a[2].charAt(0) == '#'){
					mode = 0b01;
					val = Integer.parseInt(a[2].substring(1, a[2].length()));
				}else if(a[2].charAt(0) == '&'){
					mode = 0b10;
					i2 = Integer.parseInt(a[2].substring(1, a[2].length()));
				}else if(a[2].charAt(0) == '$'){
					mode = 0b11;
					reg2 = Integer.parseInt(a[2].substring(1, a[2].length()));
				}
			}
		}
		
		return op_code | (mode << 24) | (reg1 << 22)
				| (reg2 << 20) | (i1 << 12) | i2 | val;
	}

	private int JMP(int op_code, String[] a) throws Exception{
		int mode = 0;
		int reg = 0;
		int ind = 0;
		if(a.length == 2){
			if(a[1].charAt(0) == '#'){
				ind = Integer.parseInt(a[1].substring(1, a[1].length()));
				mode = 0b00;
			}else if(a[1].charAt(0) == ':'){
				Integer pos = labels.get(a[1]) + stack_size;
				if(pos != null){
					ind = pos;
				}
				mode = 0b00;
			}else if(a[1].charAt(0) == '$'){
				reg = Integer.parseInt(a[1].substring(1, a[1].length()));
				mode = 0b01;
			}else if(a[1].charAt(0) == '&'){
				ind = Integer.parseInt(a[1].substring(1, a[1].length()));
				mode = 0b10;
			}
		}else{
			mode = 0b11;
		}
		return op_code | (mode << 24) |  (reg << 20) | ind;
	}
	
	private int JMP_CONDITION(int op_code, String[] a) throws Exception {
		int mode = 0;
		int reg1 = 0;
		int reg2 = 0;
		int ind = 0;
		reg1 = Integer.parseInt(a[1].substring(1, a[1].length()));
		if(a.length == 3){
			if(a[2].charAt(0) == '#'){
				ind = Integer.parseInt(a[2].substring(1, a[2].length()));
				mode = 0b00;
			}else if(a[2].charAt(0) == ':'){
				Integer pos = labels.get(a[2]) + stack_size;
				if(pos != null){
					ind = pos;
				}
				mode = 0b00;
			}else if(a[2].charAt(0) == '$'){
				reg2 = Integer.parseInt(a[2].substring(1, a[2].length()));
				mode = 0b01;
			}else if(a[2].charAt(0) == '&'){
				ind = Integer.parseInt(a[2].substring(1, a[2].length()));
				mode = 0b10;
			}
		}else{
			mode = 0b11;
		}
		return op_code | (mode << 24) | (reg1 << 22) | (reg2 << 20) | ind;
	}
	
	private int PUSH_POP(int op_code, String a[]){
		int reg = 0;
		if(a[1].charAt(0) == '$'){
			reg = Integer.parseInt(a[1].substring(1, a[1].length()));
		}
		return op_code | (reg << 24);
	}
	
	private int WVR(int op_code, String[] a){
		int color = 0;
		int mode = 0;
		int pos = 0;
		int reg1 = 0;
		int reg2 = 0;
		if(a[1].charAt(0) == '#'){
			color = Integer.parseInt(a[1].substring(1, a[1].length()));
			if(a[2].charAt(0) == '#'){
				mode = 0b00;
				pos = Integer.parseInt(a[2].substring(1, a[2].length()));
			}
		}else if(a[1].charAt(0) == '$'){
			reg1 = Integer.parseInt(a[1].substring(1, a[1].length()));
			if(a[2].charAt(0) == '#'){
				mode = 0b01;
				pos = Integer.parseInt(a[2].substring(1, a[2].length()));
			}else if(a[2].charAt(0) == '$'){
				mode = 0b10;
				reg2 = Integer.parseInt(a[2].substring(1, a[2].length()));
			}
		}
		return op_code | (mode << 24) | (color << 16) | (reg1 << 22) | (reg2 << 20) | pos;
	}
	
	private int UVR(int op_code, String[] a){
		return op_code;
	}
	
	
}
