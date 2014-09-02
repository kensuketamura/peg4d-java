package org.peg4d.vm;

public enum MachineInstruction {
	EXIT,
	JUMP,
	CALL,
	RET,
	IFSUCC,
	IFFAIL,
	PUSH_POS,
	POP_POS,
	POP_POS_BACK,
	PUSH_BUFPOS,
	POP_BUFPOS,
	POP_BUFPOS_BACK,
	PUSH_FPOS,
	POP_FPOS,
	POP_FPOS_FORGET,
	
	PUSH_LEFT,
	POP_LEFT,
	POP_LEFT_IFFAIL,
	POP_LEFT_NOT,
	POP_LEFT_CONNECT,
	TMATCH, 
	AMATCH, 
	UMATCH, 
	NEW, TAG, REPLACE,
	INDENT,
}