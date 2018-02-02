package ca.nicho.vm;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.util.Scanner;

public class HSystem {

	public static boolean DEBUG_MODE = false;
	
	public static final int MEMORY_CAPACITY = 4096; //2^12
	
	public ByteBuffer SYSTEM_MEMORY = ByteBuffer.allocate(MEMORY_CAPACITY);
	public byte[] SYSTEM_VRAM = new byte[2 << 16];
	
	public byte COLOR = 0;
	
	/* Byte to represent multiple flags.
	 * 1: Overflow bit
	 * 2: 
	 * 3: 
	 * 4: 
	 */
	public byte SYSTEM_FLAGS;
	
	public short PC = 0;
	
	//DEBUG WILL REMOVE LATER
	public int size;
	
	// 16 bit registers
	public short[] REGISTER = new short[4];
	
	private Display display = new Display();
	
	public HSystem(File f){
		try{
			load(f);
		}catch(Exception e){
			e.printStackTrace();
		}		
	}
	
	public void load(File f) throws Exception {
		DataInputStream in = new DataInputStream(new FileInputStream(f));
		int size = in.readInt() / 4;
		short stack_size = in.readShort();
		this.size = size; //TODO REMOVE
		this.PC = stack_size;
		REGISTER[3] = stack_size;
		for(int i = 0; i < size; i++){
			int instr = in.readInt();
			SYSTEM_MEMORY.putInt(stack_size + i * 4, instr);
		}
		in.close();
	}
		
	public int next(){
		int instr = SYSTEM_MEMORY.getInt(PC);
		executeInstruction(instr);
		PC += 4; //4 Byte instructions
		return instr;
	}
	
	private void executeInstruction(int instruction){
		int op = instruction >>> 28;
		int rem = instruction & 0x0FFFFFFF;
		
		if(op == 0b0000) WMI(rem);
		else if(op == 0b0001) MOV(rem);
		else if(op == 0b0010) WMB(rem);
		else if(op == 0b0011) RMB(rem);
		else if(op == 0b0100) ARITH(rem);
		else if(op == 0b0101) HANDLE_JMP(rem);
		else if(op == 0b0110) HANDLE_PUSH_POP(rem);
		else if(op == 0b1000) WVR(rem);
		else if(op == 0b1001) UVR(rem);
		else{
			System.out.println("NO INSTRUCTION MATCH - " + Integer.toBinaryString(op) + " " + Integer.toHexString(op) + " " + PC);
		}

	}
	
	/**
	 * WRITE MEMORY INTEGER
	 * Function to represent OP 0b0000.
	 */
	@Deprecated
	private void WMI(int rem){
		int ind = (rem & 0x0FFF0000) >> 16;
		short val = (short)(rem & 0x0000FFFF); // Typecast keeps last 16 bits.
		SYSTEM_MEMORY.putShort(ind, val);
	}
	
	 /*
	 *  [0001][000][?:<1>][A:<2>][?:<6>][B:<16>] - MOVE INT into REG: A is reg, B is value
	 *  [0001][001][?:<1>][A:<2>][?:<10>][B:<12>] - MOVE INT from MEM to REG: A is reg, B is index
	 *  [0001][010][?:<1>][A:<2>][B:<2>][?:<21>] - MOVE INT from REG to REG: A is reg, B is reg
	 *  [0001][011][?:<1>][A:<12>][B:<12>] - MOVE INT from MEM to MEM: A is index, B is index
	 *  [0001][100][?:<1>][A:<2>][?:<10>][B:<12>] - MOVE INT from REG to MEM: A is reg, B is index
	 *  [0001][11][A:<12>][B:<14>] - MOVE INT into MEM: A is index, B is value (ONLY SUPPORTS 14 BIT VALUE!)
	 */
	
	/**
	 * MOVE
	 * Function to represent 0b0001s
	 */
	private void MOV(int rem){
		int mode = (rem & 0x0E000000) >> 25;
		int reg1 = (rem & 0x00C00000) >> 22;
		int reg2 = (rem & 0x00300000) >> 20;
		short val = (short)(rem & 0x0000FFFF);
		int ind1 = rem & 0x00000FFF;
		int ind2 = (rem & 0x00FFF000) >> 12;
		int pre_mode = mode & 0b100;
		int pre_ind = (rem & 0x03FFC000) >> 14;
		short pre_val = (short)(rem & 0x00003FFF);
		if(pre_mode == 0){
			if(mode == 0b000) REGISTER[reg1] = val;
			else if(mode == 0b001) REGISTER[reg1] = SYSTEM_MEMORY.getShort(ind1);
			else if(mode == 0b010) REGISTER[reg1] = REGISTER[reg2];
			else if(mode == 0b011) SYSTEM_MEMORY.putShort(ind1, SYSTEM_MEMORY.getShort(ind2));
		}else{
			if(mode == 0b100) SYSTEM_MEMORY.putShort(ind1, REGISTER[reg1]);
			else SYSTEM_MEMORY.putShort(pre_ind, pre_val);
		}
	}
	
	/**
	 * READ MEMORY INTEGER
	 * Function to represent OP 0b0001.
	 * Stores result in REGISTER_A.
	 */
	@Deprecated
	private void RMI(int rem){
		int ind = (rem & 0x0FFF0000) >> 16;
		REGISTER[0] = SYSTEM_MEMORY.getShort(ind);
	}
	
	/**
	 * WRITE MEMORY INTEGER
	 * Function to represent OP 0b0010.
	 */
	private void WMB(int rem){
		int ind = (rem & 0x0FFF0000) >> 16;
		byte val = (byte)(rem & 0x000000FF); // Typecast keeps last 8 bits.
		SYSTEM_MEMORY.put(ind, val);
	}
	
	/**
	 * READ MEMORY INTEGER
	 * Function to represent OP 0b0011.
	 * Stores result in REGISTER_A.
	 */
	private void RMB(int rem){
		int ind = (rem & 0x0FFF0000) >> 16;
		REGISTER[0] = SYSTEM_MEMORY.get(ind);
	}
	
	//BEGIN ARITHMETIC LOGIC
	public void ARITH(int rem){
		int mode = (rem & 0x0F000000) >> 24;
		if(mode == 0b0000) ADD_MEM_MEM(rem);
		else if(mode == 0b0001) ADD_REG_LIT(rem);
		else if(mode == 0b0010) ADD_REG_MEM(rem);
		else if(mode == 0b0011) ADD_REG_REG(rem);
		else if(mode == 0b0100) SUB_MEM_MEM(rem);
		else if(mode == 0b0101) SUB_REG_LIT(rem);
		else if(mode == 0b0110) SUB_REG_MEM(rem);
		else if(mode == 0b0111) SUB_REG_REG(rem);
		else if(mode == 0b1000) MUL_MEM_MEM(rem);
		else if(mode == 0b1001) MUL_REG_LIT(rem);
		else if(mode == 0b1010) MUL_REG_MEM(rem);
		else if(mode == 0b1011) MUL_REG_REG(rem);
		else if(mode == 0b1100) DIV_MEM_MEM(rem);
		else if(mode == 0b1101) DIV_REG_LIT(rem);
		else if(mode == 0b1110) DIV_REG_MEM(rem);
		else if(mode == 0b1111) DIV_REG_REG(rem);
	}
	
	/**
	 * ADD MEMORY to MEMORY
	 * Function to represent OP 0b0100 MODE 0b0000.
	 * Stores addition in REGISTER_A.
	 */
	public void ADD_MEM_MEM(int rem){
		int i1 = (rem & 0x00FFF000) >> 12;
		int i2 = (rem & 0x00000FFF);
		REGISTER[0] = (short)(SYSTEM_MEMORY.getShort(i1) + SYSTEM_MEMORY.getShort(i2));
		System.out.println(REGISTER[0] + " " + SYSTEM_MEMORY.getShort(i1) + " " + SYSTEM_MEMORY.getShort(i2));
	}
	
	/**
	 * ADD REGISTER to LITERAL
	 * Function to represent OP 0b0100 MODE 0b0001.
	 * Stores addition in specified REGISTER.
	 */
	public void ADD_REG_LIT(int rem){
		int reg = (rem & 0x00C00000) >> 22;
		short value = (short)(rem & 0x0000FFFF);
		REGISTER[reg] += value;
	}
	
	/**
	 * ADD REGISTER to LITERAL
	 * Function to represent OP 0b0100 MODE 0b0010.
	 * Stores addition in specified REGISTER.
	 */
	public void ADD_REG_MEM(int rem){
		int reg = (rem & 0x00C00000) >> 22;
		int ind = (rem & 0x00000FFF);
		REGISTER[reg] += (short)SYSTEM_MEMORY.getShort(ind);
	}
	
	/**
	 * ADD REGISTER to REGISTER
	 * Function to represent OP 0b0100 MODE 0b0011.
	 * Stores addition in specified REGISTER.
	 */
	public void ADD_REG_REG(int rem){
		int reg1 = (rem & 0x00C00000) >> 22;
		int reg2 = (rem & 0x00300000) >> 20;
		REGISTER[reg1] = (short)(REGISTER[reg1] + REGISTER[reg2]);
	}
	
	/**
	 * SUB MEMORY to MEMORY
	 * Function to represent OP 0b0100 MODE 0b0100.
	 * Stores subtraction in REGISTER_A.
	 */
	public void SUB_MEM_MEM(int rem){
		int i1 = (rem & 0x00FFF000) >> 12;
		int i2 = (rem & 0x00000FFF);
		REGISTER[0] = (short)(SYSTEM_MEMORY.getShort(i1) - SYSTEM_MEMORY.getShort(i2));
	}
	
	/**
	 * SUB REGISTER to LITERAL
	 * Function to represent OP 0b0100 MODE 0b0101.
	 * Stores subtraction in specified REGISTER.
	 */
	public void SUB_REG_LIT(int rem){
		int reg = (rem & 0x00C00000) >> 22;
		short value = (short)(rem & 0x0000FFFF);
		REGISTER[reg] -= value;
	}
	
	/**
	 * SUB REGISTER to LITERAL
	 * Function to represent OP 0b0100 MODE 0b0110.
	 * Stores subtraction in specified REGISTER.
	 */
	public void SUB_REG_MEM(int rem){
		int reg = (rem & 0x00C00000) >> 22;
		int ind = (rem & 0x00000FFF);
		REGISTER[reg] -= (short)SYSTEM_MEMORY.getShort(ind);
	}
	
	/**
	 * SUB REGISTER from REGISTER
	 * Function to represent OP 0b0100 MODE 0b0111.
	 * Stores subtraction in specified REGISTER.
	 */
	public void SUB_REG_REG(int rem){
		int reg1 = (rem & 0x00C00000) >> 22;
		int reg2 = (rem & 0x00300000) >> 20;
		REGISTER[reg1] = (short)(REGISTER[reg1] - REGISTER[reg2]);
	}
	
	/**
	 * MUL MEMORY to MEMORY
	 * Function to represent OP 0b0100 MODE 0b1000.
	 * Stores product in REGISTER_A.
	 */
	public void MUL_MEM_MEM(int rem){
		int i1 = (rem & 0x00FFF000) >> 12;
		int i2 = (rem & 0x00000FFF);
		REGISTER[0] = (short)(SYSTEM_MEMORY.getShort(i1) * SYSTEM_MEMORY.getShort(i2));
	}
	
	/**
	 * MUL REGISTER to LITERAL
	 * Function to represent OP 0b0100 MODE 0b1001.
	 * Stores product in specified REGISTER.
	 */
	public void MUL_REG_LIT(int rem){
		int reg = (rem & 0x00C00000) >> 22;
		short value = (short)(rem & 0x0000FFFF);
		REGISTER[reg] *= value;
	}
	
	/**
	 * MUL REGISTER to LITERAL
	 * Function to represent OP 0b0100 MODE 0b1010.
	 * Stores product in specified REGISTER.
	 */
	public void MUL_REG_MEM(int rem){
		int reg = (rem & 0x00C00000) >> 22;
		int ind = (rem & 0x00000FFF);
		REGISTER[reg] *= (short)SYSTEM_MEMORY.getShort(ind);
	}
	
	/**
	 * MUL REGISTER from REGISTER
	 * Function to represent OP 0b0100 MODE 0b1011.
	 * Stores product in specified REGISTER (reg1).
	 */
	public void MUL_REG_REG(int rem){
		int reg1 = (rem & 0x00C00000) >> 22;
		int reg2 = (rem & 0x00300000) >> 20;
		REGISTER[reg1] = (short)(REGISTER[reg1] * REGISTER[reg2]);
	}
	
	/**
	 * DIV MEMORY and MEMORY
	 * Function to represent OP 0b0100 MODE 0b1100.
	 * Stores quotient in REGISTER_A.
	 */
	public void DIV_MEM_MEM(int rem){
		int i1 = (rem & 0x00FFF000) >> 12;
		int i2 = (rem & 0x00000FFF);
		REGISTER[0] = (short)(SYSTEM_MEMORY.getShort(i1) / SYSTEM_MEMORY.getShort(i2));
	}
	
	/**
	 * DIV REGISTER and LITERAL
	 * Function to represent OP 0b0100 MODE 0b1101.
	 * Stores quotient in specified REGISTER.
	 */
	public void DIV_REG_LIT(int rem){
		int reg = (rem & 0x00C00000) >> 22;
		short value = (short)(rem & 0x0000FFFF);
		REGISTER[reg] /= value;
	}
	
	/**
	 * DIV REGISTER and LITERAL
	 * Function to represent OP 0b0100 MODE 0b1110.
	 * Stores quotient in specified REGISTER.
	 */
	public void DIV_REG_MEM(int rem){
		int reg = (rem & 0x00C00000) >> 22;
		int ind = (rem & 0x00000FFF);
		REGISTER[reg] /= (short)SYSTEM_MEMORY.getShort(ind);
	}
	
	/**
	 * DIV REGISTER and REGISTER
	 * Function to represent OP 0b0100 MODE 0b1111.
	 * Stores quotient in specified REGISTER (reg1).
	 */
	public void DIV_REG_REG(int rem){
		int reg1 = (rem & 0x00C00000) >> 22;
		int reg2 = (rem & 0x00300000) >> 20;
		REGISTER[reg1] = (short)(REGISTER[reg1] / REGISTER[reg2]);
	}
	
	//BEGIN JMP LOGIC
	
	public void HANDLE_JMP(int rem){
		int mode = (rem & 0x0C000000) >> 26;
		if(mode == 0b00) JMP(rem);
		else if(mode == 0b01) JMP_EQUAL(rem);
		else if(mode == 0b10) JMP_LESS_THAN(rem);
		else if(mode == 0b11) JMP_GREATER_THAN(rem);
	}
	
	/**
	 * JMP MEMORY
	 * Function to represent OP 0b0101 MODE 0b00 (however used by conditional jumps if condition passes)
	 * Sets PC to specified MEMORY
	 */
	public void JMP(int rem){
		int mode = (rem & 0x03000000) >> 24;
		int ind = rem & 0x00000FFF;
		int reg = (rem & 0x00300000) >> 20;
		if(mode == 0b00){
			int m = (rem & 0x00800000) >> 23;
			if(m == 0){
				PC = (short)(ind - 4);
			}else{
				REGISTER[3] -= 2; 
				SYSTEM_MEMORY.putShort(REGISTER[3], PC);
				PC = (short)(ind - 4);
			}
		}
		else if(mode == 0b01) PC = (short)(REGISTER[reg] - 4);
		else if(mode == 0b10) PC = (short)(SYSTEM_MEMORY.getShort(ind) - 4);
		else if(mode == 0b11){ 
			PC = (short)(SYSTEM_MEMORY.getShort(REGISTER[3]));
			REGISTER[3] += 2;
		}
	}
	
	
	/**
	 * JMP MEMORY if equal
	 * Function to represent OP 0b0101 MODE 0b01
	 * Sets PC to specified MEMORY
	 */
	public void JMP_EQUAL(int rem){
		int reg = (rem & 0x00C00000) >> 22;
		if(REGISTER[reg] == 0)
			JMP(rem);
	}
	
	/**
	 * JMP MEMORY if less than
	 * Function to represent OP 0b0101 MODE 0b10
	 * Sets PC to specified MEMORY
	 */
	public void JMP_LESS_THAN(int rem){
		int reg = (rem & 0x00C00000) >> 22;
		if(REGISTER[reg] < 0)
			JMP(rem);
	}
	
	/**
	 * JMP MEMORY if greater than
	 * Function to represent OP 0b0101 MODE 0b11
	 * Sets PC to specified MEMORY
	 */
	public void JMP_GREATER_THAN(int rem){
		int reg = (rem & 0x00C00000) >> 22;
		if(REGISTER[reg] > 0)
			JMP(rem);
	}
	
	public void HANDLE_PUSH_POP(int rem){
		int type = (rem & 0b0000_1000_0000_0000_0000_0000_0000_0000) >> 27;
		if(type == 0){
			PUSH_POP_INT(rem);
		}else if(type == 1){
			PUSH_POP_BYTE(rem);
		}
		
	}
	
	public void PUSH_POP_INT(int rem){
		int mode = (rem & 0x0C000000) >> 26;
		int reg = (rem & 0x03000000) >> 24;
		if(mode == 0){ //PUSH
			REGISTER[3] -= 2; 
			SYSTEM_MEMORY.putShort(REGISTER[3], REGISTER[reg]);
		}else if(mode == 1){ //POP
			REGISTER[reg] = SYSTEM_MEMORY.getShort(REGISTER[3]);
			REGISTER[3] += 2; 
		}
	}
	
	public void PUSH_POP_BYTE(int rem){
		int mode = (rem & 0x0C000000) >> 26;
		int reg = (rem & 0x03000000) >> 24;
		if(mode == 2){ //PUSH
			REGISTER[3] -= 1; 
			SYSTEM_MEMORY.put(REGISTER[3], (byte)(REGISTER[reg] & 0x00FF));
		}else if(mode == 3){ //POP
			REGISTER[reg] = SYSTEM_MEMORY.get(REGISTER[3]);
			REGISTER[3] += 1; 
		}
	}

	/**
	 * WRITE VRAM
	 * Function to represent OP 0b1000
	 * Sets index of VRAM to VALUE
	 */
	public void WVR(int rem){
		byte color = (byte)((rem & 0x00FF0000) >> 16);
		int mode = (rem & 0x0F000000) >> 24;
		int reg1 = (rem & 0x00C00000) >> 22;
		int reg2 = (rem & 0x00300000) >> 20;
		int pos = rem & 0x0000FFFF;
		if(mode == 0b0000) SYSTEM_VRAM[pos] = color;
		else if(mode == 0b0001) SYSTEM_VRAM[pos] = (byte)(REGISTER[reg1] & 0xFF);
		else if(mode == 0b0010) SYSTEM_VRAM[Short.toUnsignedInt(REGISTER[reg2])] = (byte)(REGISTER[reg1] & 0xFF);
	}
	
	/**
	 * Handles updating the display.
	 */
	public void UVR(int rem){
		display.drawVRAM(SYSTEM_VRAM);
		display.update();
	}
	
	public void printRegister(){
		for(short sh : REGISTER){
			System.out.print("[" + sh + "] ");
		}
		System.out.println();
	}
	
	public void verbose(String s){
		if(DEBUG_MODE){
			System.out.println(s);
		}
	}
	
	public void printMemory(int off, int amount){
		for(int i = off; i < off + amount; i ++){
			System.out.print(String.format("%02X ", SYSTEM_MEMORY.get(i)) + " ");
		}
		System.out.println();
	}
	
}

/*
 * Instruction size: 32 bits (first 4 bits are OP codes)
 * 
 * Move data values
 *  [0001][000][A:<2>][?:<7>][B:<16>] - MOVE INT into REG: A is reg, B is value
 *  [0001][001][A:<2>][?:<11>][B:<12>] - MOVE INT from MEM to REG: A is reg, B is index
 *  [0001][010][A:<2>][?:<11>][B:<12>] - MOVE INT from REG to MEM: A is reg, B is index
 *  [0001][011][?:<1>][A:<12>][B:<12>] - MOVE INT from MEM to MEM: A is index, B is index
 *  [0001][100][A:<2>][B:<2>][?:<22>] - MOVE INT from REG to REG: A is reg, B is reg
 *  [0001][11][A:<12>][B:<14>] - MOVE INT into MEM: A is index, B is value (ONLY SUPPORTS 14 BIT VALUE!)
 *  
 * [0010][A:<12>][?:<8>][B:<8>] - WRITE BYTE to MEM: A is index, B is value
 * [0011][A:<12>][?:<16>] - READ BYTE from MEM: A is index
 * 
 * Arithmetic by mode
 * 	[0100][00][00][A:<12>][B:<12>] - ADD MEM to MEM: A is ind1, B is ind2
 * 	[0100][00][01][A:<2>][?:<6>][C:<16>] - ADD REG to LIT:  A is register, B is literal int
 *  [0100][00][10][A:<2>][?:<10>][C:<12>] - ADD REG to MEM: A is register, B is index
 * 	[0100][00][11][A:<2>][B:<2>][?:<20>] - ADD REG to REG: A is register, B is register
 *  
 * 	[0100][01][00][A:<12>][B:<12>] - SUB MEM and MEM: A is ind1, B is ind2
 * 	[0100][01][01][A:<2>][?:<6>][C:<16>] - SUB REG and LIT:  A is register, B is literal int
 *  [0100][01][10][A:<2>][?:<10>][C:<12>] - SUB REG and MEM: A is register, B is index
 * 	[0100][01][11][A:<2>][B:<2>][?:<20>] - SUB REG and REG: A is register, B is register
 *  
 *  [0100][10][00][A:<12>][B:<12>] - MULT MEM with MEM: A is ind1, B is ind2
 * 	[0100][10][01][A:<2>][?:<6>][C:<16>] - MULT REG with LIT:  A is register, B is literal int
 *  [0100][10][10][A:<2>][?:<10>][C:<12>] - MULT REG with MEM: A is register, B is index
 * 	[0100][10][11][A:<2>][B:<2>][?:<20>] - MULT REG with REG: A is register, B is register
 * 
 *  [0100][11][00][A:<12>][B:<12>] - DIV MEM to MEM: A is ind1, B is ind2
 * 	[0100][11][01][A:<2>][?:<6>][C:<16>] - DIV REG to LIT:  A is register, B is literal int
 *  [0100][11][10][A:<2>][?:<10>][C:<12>] - DIV REG to MEM: A is register, B is index
 * 	[0100][11][11][A:<2>][B:<2>][?:<20>] - DIV REG to REG: A is register, B is register
 * 
 * Jump by mode
 *  [0101][00][00][0][?:<11>][A:<12>] - JMP to LIT MEM: A is index
 *  [0101][00][00][1][?:<11>][A:<12>] - JMP to : JMP to label, while PUSHing PC to Stack. A is index. 
 *  [0101][00][01][?:<2>][A:<2>][?:<22>] - JMP to REG: A is reg
 *  [0101][00][10][?:<2>][A:<2>][?:<8>][B:<12>] - JMP to MEM (value): A is index
 *  [0101][00][11][0][?:<23>] - JMP to : JMP to stack value (RET) 
 *  [0101][01][00][A:<2>][?:<12>][B:<12>] - JMP to MEM if REG equals 0: A is REG, B is index
 *  [0101][01][01][A:<2>][B:<2>][?:<20>] - JMP to REG if REG equals 0: A is reg1, B is reg2
 *  [0101][01][10][A:<2>][?:<10>][B:<12>] - JMP to MEM (value) if REG equals 0: A is reg, B is index
 *  [0101][01][11][A:<2>][?:<22>] - JMP to : JMP to stack value (RET) if REG equals 0: A is reg
 *  [0101][10][00][A:<2>][?:<10>][B:<12>] - JMP to MEM if REG less than 0: 
 *  [0101][10][01][A:<2>][?:<24>] -JMP to REG if REG is less than 0: A is reg1, B is reg2
 *  [0101][10][10][A:<2>][A:<12>] - JMP to MEM (value) if REG is less than 0: A is reg, B is index
 *  [0101][10][11][A:<2>][?:<22>] - JMP to : JMP to stack value (RET) if REG is less than 0: A is reg
 *  [0101][11][00][A:<2>][?:<10>][B:<12>] - JMP to MEM if REG greater than 0: 
 *  [0101][11][01][A:<2>][?:<24>] - JMP to REG if REG is greater than 0: A is reg1, B is reg2
 *  [0101][11][10][A:<2>][?:<10>][B:<12>] - JMP to MEM (value) if REG is greater than 0: A is reg, B is index
 *  [0101][11][11][A:<2>][?:<22>] - JMP to : JMP to stack value (RET) if REG is greater than 0: A is reg
 * 
 * Push/Pop by mode
 *  [0110][00][A:<2>][?:<24>] PUSH from REG (INT)
 *  [0110][01][A:<2>][?:<24>] POP to REG (INT)
 *  [0110][10][A:<2>][?:<24>] PUSH from REG (BYTE)
 *  [0110][11][A:<2>][?:<24>] POP from REG (BYTE)
 *
 * Graphics
 * [1000][0000][A:<8>][B:<16>] WRITE to VRAM, A is RGB, B is VRAM index.
 * [1000][0001][A:<2>][?:<6>][B:<16>] WRITE to VRAM, A is reg (for RGB, lower byte), B is VRAM index.
 * [1000][0010][A:<2>][B:<2>][?:<20>] WRITE to VRAM, A is reg (for RGB, lower byte), B is reg (for VRAM index)
 *
 *
 *
 */