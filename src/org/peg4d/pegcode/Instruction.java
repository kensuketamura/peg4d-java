package org.peg4d.pegcode;

import java.util.ArrayList;
import java.util.List;

import org.peg4d.expression.ParsingExpression;

public abstract class Instruction {
	Opcode op;
	ParsingExpression expr;
	BasicBlock bb;
	public Instruction(ParsingExpression expr, BasicBlock bb) {
		this.expr = expr;
		this.bb = bb;
		this.bb.append(this);
	}
	
	protected abstract void stringfy(StringBuilder sb);
	
	public abstract String toString();
}

class EXIT extends Instruction {
	public EXIT(ParsingExpression expr, BasicBlock bb) {
		super(expr, bb);
		this.op = Opcode.EXIT;
	}

	@Override
	protected void stringfy(StringBuilder sb) {
		sb.append("  EXIT");
	}

	@Override
	public String toString() {
		return "EXIT";
	}
}

class CALL extends Instruction {
	String ruleName;
	int jumpIndex;
	public CALL(ParsingExpression expr, BasicBlock bb, String ruleName) {
		super(expr, bb);
		this.op = Opcode.CALL;
		this.ruleName = ruleName;
	}

	@Override
	protected void stringfy(StringBuilder sb) {
		sb.append("  CALL ");
		sb.append(this.ruleName);
	}

	@Override
	public String toString() {
		return "CALL " + this.ruleName + "(" + this.jumpIndex + ")";
	}
}

class RET extends Instruction {
	public RET(ParsingExpression expr, BasicBlock bb) {
		super(expr, bb);
		this.op = Opcode.RET;
	}

	@Override
	protected void stringfy(StringBuilder sb) {
		sb.append("  RET");
	}

	@Override
	public String toString() {
		return "RET";
	}
}

abstract class JumpInstruction extends Instruction {
	BasicBlock jump;
	public JumpInstruction(ParsingExpression expr, BasicBlock bb, BasicBlock jump) {
		super(expr, bb);
		this.jump = jump;
	}
	
	public BasicBlock getJumpPoint() {
		return jump;
	}
}

class JUMP extends JumpInstruction {
	public JUMP(ParsingExpression expr, BasicBlock bb, BasicBlock jump) {
		super(expr, bb, jump);
		this.op = Opcode.JUMP;
	}

	@Override
	protected void stringfy(StringBuilder sb) {
		sb.append("  JUMP ");
		sb.append("jump:" + this.jump.getBBName());
	}

	@Override
	public String toString() {
		return "JUMP " + this.jump.codeIndex;
	}
}

class CONDBRANCH extends JumpInstruction {
	int val;
	public CONDBRANCH(ParsingExpression expr, BasicBlock bb, BasicBlock jump, int val) {
		super(expr, bb, jump);
		this.op = Opcode.CONDBRANCH;
		this.val = val;
	}

	@Override
	protected void stringfy(StringBuilder sb) {
		sb.append("  CONDBRANCH ");
		sb.append(this.val + " ");
		sb.append("jump:" + this.jump.getBBName());
	}

	@Override
	public String toString() {
		return "CONDBRANCH " + this.jump.codeIndex;
	}
}

class REPCOND extends JumpInstruction {
	public REPCOND(ParsingExpression expr, BasicBlock bb, BasicBlock jump) {
		super(expr, bb, jump);
		this.op = Opcode.REPCOND;
	}

	@Override
	protected void stringfy(StringBuilder sb) {
		sb.append("  REPCOND ");
		sb.append("jump:" + this.jump.getBBName());
	}

	@Override
	public String toString() {
		return "REPCOND " + this.jump.codeIndex;
	}
}

abstract class MatchingInstruction extends Instruction {
	List<Integer> cdata;
	public MatchingInstruction(ParsingExpression expr, BasicBlock bb, int ...cdata) {
		super(expr, bb);
		this.cdata = new ArrayList<Integer>(); 
		for(int i = 0; i < cdata.length; i++) {
			this.cdata.add(cdata[i]);
		}
	}
	
	public int size() {
		return this.cdata.size();
	}
	
	public int getc(int index) {
		return this.cdata.get(index);
	}
	
	public void append(int c) {
		this.cdata.add(c);
	}
}

abstract class JumpMatchingInstruction extends MatchingInstruction {
	BasicBlock jump;
	public JumpMatchingInstruction(ParsingExpression expr, BasicBlock bb, BasicBlock jump, int ...cdata ) {
		super(expr, bb, cdata);
		this.jump = jump;
	}
	
	public BasicBlock getJumpPoint() {
		return jump;
	}
}

class CHARRANGE extends JumpMatchingInstruction {
	public CHARRANGE(ParsingExpression expr, BasicBlock bb, BasicBlock jump, int ...cdata) {
		super(expr, bb, jump, cdata);
		this.op = Opcode.CHARRANGE;
	}

	@Override
	protected void stringfy(StringBuilder sb) {
		sb.append("  CHARRANGE ");
		for(int i = 0; i < this.size(); i++) {
			sb.append(this.getc(i) + " ");
		}
		sb.append("jump:" + this.jump.getBBName());
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("CHARRANGE ");
		for(int i = 0; i < this.size(); i++) {
			sb.append(this.getc(i) + " ");
		}
		sb.append(this.jump.codeIndex);
		return sb.toString();
	}
}

class CHARSET extends JumpMatchingInstruction {
	public CHARSET(ParsingExpression expr, BasicBlock bb, BasicBlock jump, int ...cdata) {
		super(expr, bb, jump, cdata);
		this.op = Opcode.CHARSET;
	}

	@Override
	protected void stringfy(StringBuilder sb) {
		sb.append("  CHARSET ");
		for(int i = 0; i < this.size(); i++) {
			sb.append(this.getc(i) + " ");
		}
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("CHARSET ");
		for(int i = 0; i < this.size(); i++) {
			sb.append(this.getc(i) + " ");
		}
		sb.append(this.jump.codeIndex);
		return sb.toString();
	}
}

class STRING extends JumpMatchingInstruction {
	public STRING(ParsingExpression expr, BasicBlock bb,  BasicBlock jump, int ...cdata) {
		super(expr, bb, jump, cdata);
		this.op = Opcode.STRING;
	}

	@Override
	protected void stringfy(StringBuilder sb) {
		sb.append("  STRING ");
		for(int i = 0; i < this.size(); i++) {
			sb.append(this.getc(i) + " ");
		}
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("STRING ");
		for(int i = 0; i < this.size(); i++) {
			sb.append(this.getc(i) + " ");
		}
		sb.append(this.jump.codeIndex);
		return sb.toString();
	}
}

class ANY extends JumpMatchingInstruction {
	public ANY(ParsingExpression expr, BasicBlock bb, BasicBlock jump, int ...cdata) {
		super(expr, bb, jump, cdata);
		this.op = Opcode.ANY;
	}

	@Override
	protected void stringfy(StringBuilder sb) {
		sb.append("  ANY");
	}

	@Override
	public String toString() {
		return "ANY " + this.jump.codeIndex;
	}
}

abstract class StackOperateInstruction extends Instruction {
	public StackOperateInstruction(ParsingExpression expr, BasicBlock bb) {
		super(expr, bb);
	}
}

class PUSHp extends StackOperateInstruction {
	public PUSHp(ParsingExpression expr, BasicBlock bb) {
		super(expr, bb);
		this.op = Opcode.PUSHp;
	}

	@Override
	protected void stringfy(StringBuilder sb) {
		sb.append("  PUSHp");
	}

	@Override
	public String toString() {
		return "PUSHp";
	}
}

class PUSHo extends StackOperateInstruction {
	public PUSHo(ParsingExpression expr, BasicBlock bb) {
		super(expr, bb);
		this.op = Opcode.PUSHconnect;
	}

	@Override
	protected void stringfy(StringBuilder sb) {
		sb.append("  PUSHo");
	}

	@Override
	public String toString() {
		return "PUSHo";
	}
}

class POPp extends StackOperateInstruction {
	public POPp(ParsingExpression expr, BasicBlock bb) {
		super(expr, bb);
		this.op = Opcode.POPp;
	}

	@Override
	protected void stringfy(StringBuilder sb) {
		sb.append("  POPp");
	}

	@Override
	public String toString() {
		return "POPp";
	}
}

class POPo extends StackOperateInstruction {
	public POPo(ParsingExpression expr, BasicBlock bb) {
		super(expr, bb);
		this.op = Opcode.POPo;
	}

	@Override
	protected void stringfy(StringBuilder sb) {
		sb.append("  POPo");
	}

	@Override
	public String toString() {
		return "POPo";
	}
}

class STOREp extends StackOperateInstruction {
	public STOREp(ParsingExpression expr, BasicBlock bb) {
		super(expr, bb);
		this.op = Opcode.STOREp;
	}

	@Override
	protected void stringfy(StringBuilder sb) {
		sb.append("  STOREp");
	}

	@Override
	public String toString() {
		return "STOREp";
	}
}

class STOREo extends StackOperateInstruction {
	public STOREo(ParsingExpression expr, BasicBlock bb) {
		super(expr, bb);
		this.op = Opcode.STOREo;
	}

	@Override
	protected void stringfy(StringBuilder sb) {
		sb.append("  STOREo");
	}

	@Override
	public String toString() {
		return "STOREo";
	}
}

class STOREflag extends Instruction {
	int val;
	public STOREflag(ParsingExpression expr, BasicBlock bb, int val) {
		super(expr, bb);
		this.op = Opcode.STOREflag;
		this.val = val;
	}

	@Override
	protected void stringfy(StringBuilder sb) {
		sb.append("  STOREflag");
		sb.append(this.val);
	}

	@Override
	public String toString() {
		return "STOREflag " + this.val;
	}
}

class NEW extends Instruction {
	public NEW(ParsingExpression expr, BasicBlock bb) {
		super(expr, bb);
		this.op = Opcode.NEW;
	}

	@Override
	protected void stringfy(StringBuilder sb) {
		sb.append("  NEW");
	}

	@Override
	public String toString() {
		return "NEW";
	}
	
}

class NEWJOIN extends Instruction {
	int ndata;
	public NEWJOIN(ParsingExpression expr, BasicBlock bb, int ndata) {
		super(expr, bb);
		this.op = Opcode.NEWJOIN;
		this.ndata = ndata;
	}

	@Override
	protected void stringfy(StringBuilder sb) {
		sb.append("  NEWJOIN ");
		sb.append(this.ndata);
	}

	@Override
	public String toString() {
		return "NEWJOIN " + this.ndata;
	}	
}

class COMMIT extends Instruction {
	int ndata;
	public COMMIT(ParsingExpression expr, BasicBlock bb, int ndata) {
		super(expr, bb);
		this.op = Opcode.COMMIT;
		this.ndata = ndata;
	}

	@Override
	protected void stringfy(StringBuilder sb) {
		sb.append("  COMMIT ");
		sb.append(this.ndata);
	}

	@Override
	public String toString() {
		return "COMMIT " + this.ndata;
	}
}

class ABORT extends Instruction {
	public ABORT(ParsingExpression expr, BasicBlock bb) {
		super(expr, bb);
		this.op = Opcode.ABORT;
	}

	@Override
	protected void stringfy(StringBuilder sb) {
		sb.append("  ABORT");
	}

	@Override
	public String toString() {
		return "ABORT";
	}
}

class SETendp extends Instruction {
	public SETendp(ParsingExpression expr, BasicBlock bb) {
		super(expr, bb);
		this.op = Opcode.SETendp;
	}

	@Override
	protected void stringfy(StringBuilder sb) {
		sb.append("  SETendp");
	}

	@Override
	public String toString() {
		return "SETendp";
	}
}

class TAG extends Instruction {
	String cdata;
	public TAG(ParsingExpression expr, BasicBlock bb, String cdata) {
		super(expr, bb);
		this.op = Opcode.TAG;
		this.cdata = cdata;
	}

	@Override
	protected void stringfy(StringBuilder sb) {
		sb.append("  TAG ");
		sb.append(this.cdata);
	}

	@Override
	public String toString() {
		return "TAG " + this.cdata;
	}
}

class VALUE extends Instruction {
	String cdata;
	public VALUE(ParsingExpression expr, BasicBlock bb, String cdata) {
		super(expr, bb);
		this.op = Opcode.VALUE;
		this.cdata = cdata;
	}

	@Override
	protected void stringfy(StringBuilder sb) {
		sb.append("  VALUE ");
		sb.append(this.cdata);
	}

	@Override
	public String toString() {
		return "VALUE " + this.cdata;
	}
}