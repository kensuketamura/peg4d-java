package org.peg4d;

import java.util.HashMap;

import org.peg4d.ParsingContextMemo.ObjectMemo;

interface Matcher {
	boolean simpleMatch(ParsingContext context);
}

public abstract class ParsingExpression implements Matcher {
	public final static int CyclicRule       = 1;
	public final static int HasNonTerminal    = 1 << 1;
	public final static int HasString         = 1 << 2;
	public final static int HasCharacter      = 1 << 3;
	public final static int HasAny            = 1 << 4;
	public final static int HasRepetition     = 1 << 5;
	public final static int HasOptional       = 1 << 6;
	public final static int HasChoice         = 1 << 7;
	public final static int HasAnd            = 1 << 8;
	public final static int HasNot            = 1 << 9;
	
	public final static int HasConstructor    = 1 << 10;
	public final static int HasConnector      = 1 << 11;
	public final static int HasTagging        = 1 << 12;
	public final static int HasMessage        = 1 << 13;
	public final static int HasContext        = 1 << 14;
	public final static int HasReserved       = 1 << 15;
	public final static int hasReserved2       = 1 << 16;
	public final static int Mask = HasNonTerminal | HasString | HasCharacter | HasAny
	                             | HasRepetition | HasOptional | HasChoice | HasAnd | HasNot
	                             | HasConstructor | HasConnector | HasTagging | HasMessage 
	                             | HasReserved | hasReserved2 | HasContext;
	public final static int HasLazyNonTerminal = Mask;

	public final static int LeftObjectOperation    = 1 << 17;
	public final static int PossibleDifferentRight = 1 << 18;
	
	public final static int NoMemo            = 1 << 20;
	
	public final static int HasSyntaxError    = 1 << 26;
	public final static int HasTypeError      = 1 << 27;

	int        flag       = 0;
	int        uniqueId   = 0;
	ParsingObject po      = null;
	ParsingExpression flowNext  = null;
	int        minlen = -1;
	Matcher matcher;
		
	protected ParsingExpression(int flag) {
		this.flag = flag;
		this.matcher = this;
	}
	
	public final boolean isOptimized() {
		return (this.matcher != this);
	}

	abstract ParsingExpression dup();
	protected abstract void visit(ExpressionVisitor visitor);

	public final boolean fastMatch1(ParsingContext c) {
//		int pos = (int)context.getPosition();
//		boolean b = this.matcher.simpleMatch(context);
//		assert(context.isFailure() == !b);
//		System.out.println("["+pos+"] return: " + b + " by " + this);
//		return b;
		return this.matcher.simpleMatch(c);
	}
	
	public final static short Reject        = 0;
	public final static short Accept        = 1;
	public final static short WeakAccept    = 1;
	public final static short CheckNextFlow = 2;
	
	short acceptByte(int ch) {
		return CheckNextFlow;
	}
	
//	protected final short checkNextFlow(short r, int ch, ParsingExpression stopped) {
//		if(r == CheckNextFlow) {
//			if(this.flowNext == null) {
//				return Reject;
//			}
//			if(stopped != this.flowNext ) {
//				return this.flowNext.acceptByte(ch, stopped);
//			}
//			return CheckNextFlow;
//		}
//		return r;
//	}

	public ParsingExpression getExpression() {
		return this;
	}

	public final boolean is(int uflag) {
		return ((this.flag & uflag) == uflag);
	}

	public void set(int uflag) {
		this.flag = this.flag | uflag;
	}

	protected void derived(ParsingExpression e) {
		this.flag |= (e.flag & ParsingExpression.Mask);
	}
	
	public final boolean isUnique() {
		return this.uniqueId > 0;
	}
	
	public int size() {
		return 0;
	}
	
	public ParsingExpression get(int index) {
		return null;
	}

	private final static GrammarFormatter DefaultFormatter = new GrammarFormatter();
	
	@Override public String toString() {
		StringBuilder sb = new StringBuilder();
		DefaultFormatter.format(this, sb);
		return sb.toString();
	}

	public final String format(String name, GrammarFormatter fmt) {
		StringBuilder sb = new StringBuilder();
		fmt.formatRule(name, this, sb);
		return sb.toString();
	}
	
	public final String format(String name) {
		return this.format(name, new GrammarFormatter());
	}
	
	final void report(ReportLevel level, String msg) {
		if(this.po != null) {
			Main._PrintLine(po.formatSourceMessage(level.toString(), msg));
		}
		else {
			System.out.println("" + level.toString() + ": " + msg);
		}
	}
	
	public static ParsingExpression makeFlow(ParsingExpression e, ParsingExpression tail) {
		e.flowNext = tail;
		if(e instanceof ParsingChoice) {
			for(int i = 0; i < e.size(); i++) {
				makeFlow(e.get(i), tail);
			}
			return e;
		}
		if(e instanceof ParsingSequence || e instanceof PConstructor) {
			for(int i = e.size() - 1; i >=0; i--) {
				tail = makeFlow(e.get(i), tail);
			}
			return e;
		}
		if(e instanceof ParsingUnary) {
			tail = makeFlow(((ParsingUnary) e).inner, tail);
		}
		return e;
	}

	private static boolean checkRecursion(String uName, UList<String> stack) {
		for(int i = 0; i < stack.size() - 1; i++) {
			if(uName.equals(stack.ArrayValues[i])) {
				return true;
			}
		}
		return false;
	}

	static int checkLeftRecursion(ParsingExpression e, String uName, int minlen, UList<String> stack, ParsingExpression stopped) {
		if(e == null || e == stopped) {
			return minlen;
		}
		if(e instanceof PNonTerminal) {
			PNonTerminal ne = (PNonTerminal) e;
			ne.checkReference();
			ParsingRule r = ne.getRule();
			String n = ne.getUniqueName();
			if(n.equals(uName) && minlen == 0 && !e.is(HasSyntaxError)) {
				e.set(HasSyntaxError);
				System.out.println(uName + " @@ " + stack);
				e.report(ReportLevel.error, "left recursion: " + r);
			}
		}
		if(e.minlen == -1) {
			if(e instanceof PNonTerminal) {
				PNonTerminal ne = (PNonTerminal) e;
				ne.checkReference();
				ParsingRule r = ne.getRule();
				String n = ne.getUniqueName();
				if(r.minlen != -1) {
					e.minlen = r.minlen;
				}
				else {
					if(!checkRecursion(n, stack)) {
						int pos = stack.size();
						stack.add(n);
						int nc = checkLeftRecursion(ne.calling, uName, minlen, stack, null);
						e.minlen = nc - minlen;
						stack.clear(pos);
					}
					else {
//						System.out.println(uName + " @@ " + stack);
						e.minlen = 1; // assuming no left recursion
					}
				}
			}
			else if(e instanceof ParsingList) {
				if(e instanceof ParsingChoice) {
//					if(uName.equals("Constructor_")) {
//						System.out.println("choice: " + e + " flowNext=" + e.flowNext);
//					}
					int lmin = Integer.MAX_VALUE;
					for(int i = 0; i < e.size(); i++) {
						int nc = checkLeftRecursion(e.get(i), uName, minlen, stack, e.flowNext);
						if(nc < lmin) {
							lmin = nc;
						}
					}
					e.minlen = lmin - minlen;
				}
				else {
					int nc = minlen;
					e.minlen = 0;
					for(int i = 0; i < e.size(); i++) {
						ParsingExpression eN = e.get(i);
						nc = checkLeftRecursion(eN, uName, nc, stack, eN.flowNext);
					}
					e.minlen = nc - minlen;
				}
//				if(e.minlen == 0) {
//					System.out.println("debug: e.minlen=0 " + e);
//				}
			}
			else if(e instanceof ParsingUnary) {
				int lmin = checkLeftRecursion(((ParsingUnary) e).inner, uName, minlen, stack, e.flowNext); // skip count
				if(e instanceof ParsingOption || e instanceof ParsingRepetition || e instanceof ParsingNot || e instanceof ParsingAnd ) {
					e.minlen = 0;
				}
				else {
					e.minlen = lmin - minlen;
				}
			}
			else {
				e.minlen = 0;
			}
		}
//		if(e.minlen == -1) {
//			System.out.println("remaining: " + e);
//		}
		assert(e.minlen != -1);
		minlen += e.minlen;
		return checkLeftRecursion(e.flowNext, uName, minlen, stack, stopped);
	}

	static ParsingType typeCheck(ParsingExpression e, UList<String> stack, ParsingType leftType, ParsingExpression stopped) {
		if(e == null || e == stopped) {
			return leftType;
		}
		if(e instanceof ParsingConnector) {
			ParsingType rightType = typeCheck(((ParsingConnector) e).inner, stack, new ParsingType(), e.flowNext);
// FIXME:
//			if(!rightType.isObjectType() && !e.is(HasTypeError)) {
//				e.set(HasTypeError);
//				e.report(ReportLevel.warning, "nothing is connected: in " + e);
//			}
			leftType.set(((ParsingConnector) e).index, rightType, (ParsingConnector)e);
			return typeCheck(e.flowNext, stack, leftType, stopped);
		}
		if(e instanceof PConstructor) {
			boolean LeftJoin = ((PConstructor) e).leftJoin;
			if(LeftJoin) {
				if(!leftType.isObjectType() && !e.is(HasTypeError)) {
					e.set(HasTypeError);
					e.report(ReportLevel.warning, "type error: unspecific left in " + e);
				}
			}
			else {
				if(leftType.isObjectType() && !e.is(HasTypeError)) {
					e.set(HasTypeError);
					e.report(ReportLevel.warning, "type error: object transition of " + leftType + " before " + e);
				}
			}
			if(((PConstructor) e).type == null) {
				ParsingType t = leftType.isEmpty() ? leftType : new ParsingType();
				if(LeftJoin) {
					t.set(0, leftType);
				}
				t.setConstructor((PConstructor)e);
				((PConstructor) e).type = typeCheck(e.get(0), stack, t, e.flowNext);
				
			}
			if(LeftJoin) {
				leftType.addUnionType(((PConstructor) e).type.dup());
			}
			else {
				leftType = ((PConstructor) e).type.dup();
			}
		}
		if(e instanceof ParsingTagging) {
			leftType.addTagging(((ParsingTagging) e).tag);
		}
		if(e instanceof PNonTerminal) {
			ParsingRule r = ((PNonTerminal) e).getRule();
			if(r.type == null) {
				String n = ((PNonTerminal) e).getUniqueName();
				if(!checkRecursion(n, stack)) {
					int pos = stack.size();
					stack.add(n);
					ParsingType t = new ParsingType();
					r.type = t;
					r.type = typeCheck(((PNonTerminal) e).calling, stack, t, null);
					stack.clear(pos);
				}
				if(r.type == null) {
					e.report(ReportLevel.warning, "uninferred NonTerminal: " + n);				
				}
			}
			if(r.type != null) {
				if(r.type.isObjectType()) {
					leftType = r.type.dup();
				}
			}
		}
		if(e instanceof ParsingChoice) {
			if(e.size() > 1) {
				ParsingType rightType = typeCheck(e.get(0), stack, leftType.dup(), e.flowNext);
				if(leftType.hasTransition(rightType)) {
					for(int i = 1; i < e.size(); i++) {
						ParsingType unionType = typeCheck(e.get(i), stack, leftType.dup(), e.flowNext);
						rightType.addUnionType(unionType);
					}					
				}
				else {
					for(int i = 1; i < e.size(); i++) {
						ParsingType lleftType = rightType;
						lleftType.enableUnionTagging();
						rightType = typeCheck(e.get(i), stack, lleftType, e.flowNext);
						if(lleftType.hasTransition(rightType)) {
							if(!e.get(i).is(HasTypeError)) {
								e.get(i).set(HasTypeError);
								//e.report(ReportLevel.warning, "type error: mixed type: " + leftType + "/" + lleftType + "/" + rightType + " at " + e.get(i) + " in " + e);
							}
						}
					}
					rightType.disableUnionTagging();
					//System.out.println("CHOICE: " + e + "\n\t" + rightType);
				}
				leftType = rightType;
			}
		}
		if(e instanceof ParsingUnary) {
			leftType = typeCheck(((ParsingUnary) e).inner, stack, leftType, e.flowNext);
		}
		if(e instanceof ParsingOperation) {
			leftType = typeCheck(((ParsingOperation) e).inner, stack, leftType, e.flowNext);
		}
		if(e instanceof ParsingSequence) {
			if(e.size() > 0) {
				leftType = typeCheck(e.get(0), stack, leftType, e.flowNext);
			}
		}
		return typeCheck(e.flowNext, stack, leftType, stopped);
	}
	
	// factory
	
	private static boolean Conservative = false;
	private static boolean StringSpecialization = true;
	private static boolean CharacterChoice      = true;

	private static HashMap<String, Integer> idMap = new HashMap<String, Integer>();
	public final static int issueId(ParsingExpression e, String key) {
		key = e.getClass().getSimpleName() + "@" + key;
		Integer n = idMap.get(key);
		if(n == null) {
			n = idMap.size() + 1;
			idMap.put(key, n);
		}
		return n;
	}
	
	public final static ParsingEmpty newEmpty() {
		return new ParsingEmpty();
	}
	
	public final static ParsingByte newByte(int ch) {
		return new ParsingByte(ch & 0xff);
	}
	
	public final static ParsingExpression newByte(int ch, String token) {
		ParsingByte e = newByte(ch);
		e.errorToken = token;
		return e;
	}
	
	public final static ParsingExpression newAny(String text) {
		return new ParsingAny();
	}

	public final static ParsingExpression newSequence(UList<ParsingExpression> l) {
		if(l.size() == 1) {
			return l.ArrayValues[0];
		}
		return new ParsingSequence(0, l);
	}
	
	public final static void addSequence(UList<ParsingExpression> l, ParsingExpression e) {
		if(e instanceof ParsingSequence) {
			for(int i = 0; i < e.size(); i++) {
				addSequence(l, e.get(i));
			}
		}
		else {
			if(l.size() > 0 && e instanceof ParsingNot) {
				ParsingExpression pe = l.ArrayValues[l.size()-1];
				if(pe instanceof ParsingNot) {
					((ParsingNot) pe).inner = appendAsChoice(((ParsingNot) pe).inner, ((ParsingNot) e).inner);
					Main.printVerbose("merging", pe);
					return;
				}
			}
			l.add(e);
		}
	}

	public final static ParsingExpression appendAsChoice(ParsingExpression e, ParsingExpression e2) {
		if(e == null) return e2;
		if(e2 == null) return e;
		UList<ParsingExpression> l = new UList<ParsingExpression>(new ParsingExpression[e.size()+e2.size()]);
		addChoice(l, e);
		addChoice(l, e2);
		return newChoice(l);
	}

	public static final ParsingExpression newString(String text) {
		byte[] utf8 = ParsingCharset.toUtf8(text);
		if(Conservative) {
			return new PString(0, text, utf8);	
		}
		if(utf8.length == 1) {
			return newByte(utf8[0]);
		}
		return newByteSequence(utf8, text);
	}

	public final static ParsingExpression newByteSequence(byte[] utf8, String token) {
		UList<ParsingExpression> l = new UList<ParsingExpression>(new ParsingExpression[utf8.length]);
		for(int i = 0; i < utf8.length; i++) {
			l.add(newByte(utf8[i], token));
		}
		return newSequence(l);
	}
	

	public final static ParsingExpression newUnicodeRange(int c, int c2, String token) {
		byte[] b = ParsingCharset.toUtf8(String.valueOf((char)c));
		byte[] b2 = ParsingCharset.toUtf8(String.valueOf((char)c2));
		if(equalsBase(b, b2)) {
			return newUnicodeRange(b, b2, token);
		}
		UList<ParsingExpression> l = new UList<ParsingExpression>(new ParsingExpression[b.length]);
		b2 = b;
		for(int pc = c + 1; pc <= c2; pc++) {
			byte[] b3 = ParsingCharset.toUtf8(String.valueOf((char)pc));
			if(equalsBase(b, b3)) {
				b2 = b3;
				continue;
			}
			l.add(newUnicodeRange(b, b2, token));
			b = b3;
			b2 = b3;
		}
		b2 = ParsingCharset.toUtf8(String.valueOf((char)c2));
		l.add(newUnicodeRange(b, b2, token));
		return newChoice(l);
	}
	
	private final static boolean equalsBase(byte[] b, byte[] b2) {
		if(b.length == b2.length) {
			switch(b.length) {
			case 3: return b[0] == b2[0] && b[1] == b2[1];
			case 4: return b[0] == b2[0] && b[1] == b2[1] && b[2] == b2[2];
			}
			return b[0] == b2[0];
		}
		return false;
	}

	private final static ParsingExpression newUnicodeRange(byte[] b, byte[] b2, String token) {
		if(b[b.length-1] == b2[b.length-1]) {
			return newByteSequence(b, token);
		}
		else {
			UList<ParsingExpression> l = new UList<ParsingExpression>(new ParsingExpression[b.length]);
			for(int i = 0; i < b.length-1; i++) {
				l.add(newByte(b[i], token));
			}
			l.add(newByteRange(b[b.length-1] & 0xff, b2[b2.length-1] & 0xff, token));
			return newSequence(l);
		}
	}

	public final static ParsingExpression newByteRange(int c, int c2, String token) {
		if(c == c2) {
			return newByte(c, token);
		}
		return new ParsingByteRange(c, c2);
//		UList<ParsingExpression> l = new UList<ParsingExpression>(new ParsingExpression[c2 - c + 1]);
//		while(c <= c2) {
//			l.add(newByteChar(c, token));
//			c++;
//		}
//		return newChoice(l);
	}
	
	public final static ParsingExpression newCharset(String t, String t2, String token) {
		int c = ParsingCharset.parseAscii(t);
		int c2 = ParsingCharset.parseAscii(t2);
		if(c != -1 && c2 != -1) {
			return newByteRange(c, c2, token);
		}
		c = ParsingCharset.parseUnicode(t);
		c2 = ParsingCharset.parseUnicode(t2);
		if(c < 128 && c2 < 128) {
			return newByteRange(c, c2, token);
		}
		else {
			return newUnicodeRange(c, c2, token);
		}
	}
	
	public final static ParsingExpression newCharacter(String text) {
		UList<ParsingExpression> l = new UList<ParsingExpression>(new ParsingExpression[text.length()]);
		CharacterReader r = new CharacterReader(text);
		char ch = r.readChar();
		while(ch != 0) {
			char next = r.readChar();
			if(next == '-') {
				int ch2 = r.readChar();
				if(ch > 0 && ch2 < 128) {
					l.add(newByteRange(ch, ch2, text));
				}
				ch = r.readChar();
			}
			else {
				if(ch > 0 && ch < 128) {
					l.add(newByte(ch, text));
				}
				ch = next; //r.readChar();
			}
		}
		return newChoice(l);
	}

//	@Deprecated
//	public final static ParsingExpression newCharacter(ParsingCharset u) {
//		if(u instanceof UnicodeRange) {
//			return newUnicodeRange(((UnicodeRange) u).beginChar, ((UnicodeRange) u).endChar, u.key());
//		}
//		ByteCharset bc = (ByteCharset)u;
//		ParsingExpression e = null;
//		int c = bc.size();
//		if(c > 1) {
//			e = new ParsingByteRange(0, u);
//		}
//		else if(c == 1) {
//			for(c = 0; c < 256; c++) {
//				if(bc.bitMap[c]) {
//					break;
//				}
//			}
//			e = newByte(c);
//		}
//		if(bc.unicodeRangeList != null) {
//			UList<ParsingExpression> l = new UList<ParsingExpression>(new ParsingExpression[2]);
//			if(e != null) {
//				l.add(e);
//			}
//			for(int i = 0; i < bc.unicodeRangeList.size(); i++) {
//				UnicodeRange ur = bc.unicodeRangeList.ArrayValues[i];
//				addChoice(l, newUnicodeRange(ur.beginChar, ur.endChar, ur.key()));
//			}
//			return newChoice(l);
//		}
//		return e;
//	}
	
	public final static ParsingExpression newOptional(ParsingExpression p) {
//		if(StringSpecialization) {
//			if(p instanceof PByteChar) {
//				return new POptionalByteChar(0, (PByteChar)p);
//			}
//			if(p instanceof PCharacter) {
//				return new POptionalCharacter(0, (PCharacter)p);
//			}
//			if(p instanceof PString) {
//				return new POptionalString(0, (PString)p);
//			}
//		}
		return new ParsingOption(p);
	}
	
	public final static ParsingExpression newMatch(ParsingExpression p) {
		return new ParsingMatch(p);
	}
		
	public final static ParsingExpression newRepetition(ParsingExpression p) {
//		if(p instanceof PCharacter) {
//			return new PZeroMoreCharacter(0, (PCharacter)p);
//		}
		return new ParsingRepetition(0, p);
	}

	public final static ParsingExpression newAnd(ParsingExpression p) {
		return new ParsingAnd(0, p);
	}
	
	public final static ParsingExpression newNot(ParsingExpression p) {
//		if(StringSpecialization) {
//			if(p instanceof PByteChar) {
//				return new PNotByteChar(0, (PByteChar)p);
//			}
//			if(p instanceof PString) {
//				return new PNotString(0, (PString)p);
//			}
//			if(p instanceof PCharacter) {
//				return new PNotCharacter(0, (PCharacter)p);
//			}
//		}
//		if(p instanceof ParsingOperation) {
//			p = ((ParsingOperation)p).inner;
//		}
		return new ParsingNot(0, p);
	}
		
	public final static ParsingExpression newChoice(UList<ParsingExpression> l) {
		if(l.size() == 1) {
			return l.ArrayValues[0];
		}
		return new ParsingChoice(0, l);
	}

	
	public final static void addChoice(UList<ParsingExpression> l, ParsingExpression e) {
		if(e instanceof ParsingChoice) {
			for(int i = 0; i < e.size(); i++) {
				addChoice(l, e.get(i));
			}
		}
		else {
			l.add(e);
		}
	}
	
	public final static ParsingExpression newConstructor(ParsingExpression p) {
		ParsingExpression e = new PConstructor(0, false, toSequenceList(p));
		return e;
	}

	public final static ParsingExpression newJoinConstructor(ParsingExpression p) {
		ParsingExpression e = new PConstructor(0, true, toSequenceList(p));
		return e;
	}
	
	public final static UList<ParsingExpression> toSequenceList(ParsingExpression e) {
		if(e instanceof ParsingSequence) {
			return ((ParsingSequence) e).list;
		}
		UList<ParsingExpression> l = new UList<ParsingExpression>(new ParsingExpression[1]);
		l.add(e);
		return l;
	}
		
	public final static ParsingExpression newConnector(ParsingExpression p, int index) {
		return new ParsingConnector(0, p, index);
	}

	public final static ParsingExpression newTagging(ParsingTag tag) {
		return new ParsingTagging(0, tag);
	}

	public final static ParsingExpression newValue(String msg) {
		return new ParsingValue(0, msg);
	}
	
	public final static ParsingExpression newDebug(ParsingExpression e) {
		return new ParsingDebug(e);
	}

	public final static ParsingExpression newFail(String message) {
		return new ParsingFail(0, message);
	}

	private static ParsingExpression catchExpression = null;

	public final static ParsingExpression newCatch() {
		if(catchExpression == null) {
			catchExpression = new ParsingCatch(0);
		}
		return catchExpression;
	}
	
	public final static ParsingExpression newFlag(String flagName) {
		return new ParsingIfFlag(0, flagName);
	}

	public final static ParsingExpression newEnableFlag(String flagName, ParsingExpression e) {
		return new ParsingWithFlag(flagName, e);
	}

	public final static ParsingExpression newDisableFlag(String flagName, ParsingExpression e) {
		return new ParsingWithoutFlag(flagName, e);
	}

	private static ParsingExpression indentExpression = null;

	public final static ParsingExpression newIndent(ParsingExpression e) {
		if(e == null) {
			if(indentExpression == null) {
				indentExpression = new ParsingIndent(0);
			}
			return indentExpression;
		}
		return new ParsingStackIndent(e);
	}

}

abstract class ParsingAtom extends ParsingExpression {
	ParsingAtom (int flag) {
		super(flag);
	}
}

abstract class ParsingUnary extends ParsingExpression {
	ParsingExpression inner;
	ParsingUnary(int flag, ParsingExpression e) {
		super(flag);
		this.inner = e;
	}
	@Override
	public final int size() {
		return 1;
	}
	@Override
	public final ParsingExpression get(int index) {
		return this.inner;
	}
	@Override
	short acceptByte(int ch) {
		return this.inner.acceptByte(ch);
	}
}

abstract class ParsingList extends ParsingExpression {
	UList<ParsingExpression> list;
	ParsingList(int flag, UList<ParsingExpression> list) {
		super(flag);
		this.list = list;
	}
	@Override
	public final int size() {
		return this.list.size();
	}
	@Override
	public final ParsingExpression get(int index) {
		return this.list.ArrayValues[index];
	}

	public final void set(int index, ParsingExpression e) {
		this.list.ArrayValues[index] = e;
	}

	@Override
	short acceptByte(int ch) {
		for(int i = 0; i < this.size(); i++) {
			short r = this.get(i).acceptByte(ch);
			if(r != CheckNextFlow) {
				return r;
			}
		}
		return CheckNextFlow;
	}

//	public final ParsingExpression trim() {
//		int size = this.size();
//		boolean hasNull = true;
//		while(hasNull) {
//			hasNull = false;
//			for(int i = 0; i < size-1; i++) {
//				if(this.get(i) == null && this.get(i+1) != null) {
//					this.swap(i,i+1);
//					hasNull = true;
//				}
//			}
//		}
//		for(int i = 0; i < this.size(); i++) {
//			if(this.get(i) == null) {
//				size = i;
//				break;
//			}
//		}
//		if(size == 0) {
//			return null;
//		}
//		if(size == 1) {
//			return this.get(0);
//		}
//		this.list.clear(size);
//		return this;
//	}
	
	public final void swap(int i, int j) {
		ParsingExpression e = this.list.ArrayValues[i];
		this.list.ArrayValues[i] = this.list.ArrayValues[j];
		this.list.ArrayValues[j] = e;
	}
}

class ParsingEmpty extends ParsingExpression {
	ParsingEmpty() {
		super(0);
		this.minlen = 0;
	}
	@Override ParsingExpression dup() { 
		return new ParsingEmpty();
	}
	@Override
	protected void visit(ExpressionVisitor visitor) {
		visitor.visitEmpty(this);
	}
	@Override
	public boolean simpleMatch(ParsingContext context) {
		return true;
	}
}

class ParsingFailure extends ParsingExpression {
	ParsingExpression dead;
	ParsingFailure(ParsingExpression dead) {
		super(0);
		this.dead = dead;
	}
	@Override
	ParsingExpression dup() {
		return new ParsingFailure(dead);
	}
	@Override
	short acceptByte(int ch) {
		return Reject;
	}
	@Override
	public boolean simpleMatch(ParsingContext context) {
		context.opFailure(dead);
		return false;
	}
	@Override
	protected void visit(ExpressionVisitor visitor) {
	}
}

class ParsingByte extends ParsingExpression {
	int byteChar;
	String errorToken = null;
	ParsingByte(int ch) {
		super(0);
		this.byteChar = ch;
		this.minlen = 1;
	}
	@Override ParsingExpression dup() { 
		ParsingByte n = new ParsingByte(byteChar);
		n.errorToken = errorToken;
		return n;
	}
	@Override
	protected void visit(ExpressionVisitor visitor) {
		visitor.visitByte(this);
	}
	@Override
	short acceptByte(int ch) {
		return (byteChar == ch) ? Accept : Reject;
	}
	@Override
	public boolean simpleMatch(ParsingContext context) {
		if(context.source.byteAt(context.pos) == this.byteChar) {
			context.consume(1);
			return true;
		}
		context.opFailure(this.errorToken);
		return false;
	}
}

class ParsingAny extends ParsingExpression {
	ParsingAny() {
		super(ParsingExpression.HasAny | ParsingExpression.NoMemo);
		this.minlen = 1;
	}
	@Override ParsingExpression dup() { return new ParsingAny(); }
	@Override 
	short acceptByte(int ch) {
		return Accept;
	}
	@Override
	protected void visit(ExpressionVisitor visitor) {
		visitor.visitAny(this);
	}
	@Override
	public boolean simpleMatch(ParsingContext context) {
		if(context.source.charAt(context.pos) != -1) {
			int len = context.source.charLength(context.pos);
			context.consume(len);
			return true;
		}
		context.opFailure();
		return false;
	}
}

class PNonTerminal extends ParsingExpression {
	Grammar peg;
	String  ruleName;
	ParsingExpression    calling = null;
	PNonTerminal(Grammar base, int flag, String ruleName) {
		super(flag | ParsingExpression.HasNonTerminal | ParsingExpression.NoMemo);
		this.peg = base;
		this.ruleName = ruleName;
	}
	@Override
	ParsingExpression dup() {
		return new PNonTerminal(peg, flag, ruleName);
	}
	String getUniqueName() {
		return this.peg.uniqueRuleName(this.ruleName);
	}
	final ParsingRule getRule() {
		return this.peg.getRule(this.ruleName);
	}
	void checkReference() {
		if(this.calling == null) {
			ParsingRule r = this.getRule();
			if(r.minlen != -1) {
				this.minlen = r.minlen;
			}
			this.calling = r.expr;
			//System.out.println("NonTerminal: " + this + " ref: " + this.resolvedExpression);
			if(this.calling == null) {
				this.report(ReportLevel.error, "undefined rule: " + this.ruleName);
				this.calling = new ParsingIfFlag(0, this.ruleName);
				ParsingRule rule = new ParsingRule(this.peg, this.ruleName, null, this.calling);
				this.peg.setRule(this.ruleName, rule);
			}
		}
	}
	@Override
	protected void visit(ExpressionVisitor visitor) {
		visitor.visitNonTerminal(this);
	}
	@Override short acceptByte(int ch) {
		if(this.calling != null) {
			return this.calling.acceptByte(ch);
		}
		return WeakAccept;
	}
	@Override
	public boolean simpleMatch(ParsingContext context) {
//		System.out.println("calling " + this.ruleName);
		if(this.calling == null) {
			System.out.println("Null Reference remains: " + this.ruleName + " next=" + this.flowNext);
			//assert(this.calling != null);
			this.checkReference();
		}
		return this.calling.matcher.simpleMatch(context);
	}
}

class PString extends ParsingAtom {
	String text;
	byte[] utf8;
	PString(int flag, String text, byte[] utf8) {
		super(ParsingExpression.HasString | ParsingExpression.NoMemo | flag);
		this.text = text;
		this.utf8 = utf8;
		this.minlen = utf8.length;
	}
	PString(int flag, String text) {
		this(flag, text, ParsingCharset.toUtf8(text));
	}
	PString(int flag, int ch) {
		super(ParsingExpression.HasString | ParsingExpression.NoMemo | flag);
		utf8 = new byte[1];
		utf8[0] = (byte)ch;
		if(ch >= ' ' && ch < 127) {
			this.text = String.valueOf((char)ch);
		}
		else {
			this.text = String.format("0x%x", ch);
		}
	}
	@Override
	ParsingExpression dup() { 
		return new PString(flag, text, utf8); 
	}
	@Override
	protected void visit(ExpressionVisitor visitor) {
		visitor.visitString(this);
	}
	@Override short acceptByte(int ch) {
		if(this.utf8.length == 0) {
			return CheckNextFlow;
		}
		return ((this.utf8[0] & 0xff) == ch) ? Accept : Reject;
	}
	@Override
	public boolean simpleMatch(ParsingContext context) {
		if(context.source.match(context.pos, this.utf8)) {
			context.consume(this.utf8.length);
			return true;
		}
		else {
			context.opFailure();
			return false;
		}
	}
}

class ParsingByteRange extends ParsingExpression {
	int startByteChar;
	int endByteChar;
	ParsingByteRange(int startByteChar, int endByteChar) {
		super(0);
		this.startByteChar = startByteChar;
		this.endByteChar = endByteChar;
		this.minlen = 1;
	}
	@Override 
	ParsingExpression dup() { 
		return new ParsingByteRange(startByteChar, endByteChar);
	}
	void setCount(int[] count) {
		for(int c = startByteChar; c <= endByteChar; c++) {
			count[c]++;
		}
	}
	@Override
	protected void visit(ExpressionVisitor visitor) {
		visitor.visitByteRange(this);
	}
	@Override 
	short acceptByte(int ch) {
		return (startByteChar <= ch && ch <= endByteChar) ? Accept : Reject;
	}
	@Override
	public boolean simpleMatch(ParsingContext context) {
		int ch = context.source.byteAt(context.pos);
		if(startByteChar <= ch && ch <= endByteChar) {
			context.consume(1);
			return true;
		}
		context.opFailure();
		return false;
	}
}


class ParsingOption extends ParsingUnary {
	ParsingOption(ParsingExpression e) {
		super(ParsingExpression.HasOptional | ParsingExpression.NoMemo, e);
	}
	@Override ParsingExpression dup() { 
		return new ParsingOption(inner); 
	}
	@Override
	protected void visit(ExpressionVisitor visitor) {
		visitor.visitOptional(this);
	}
	@Override 
	short acceptByte(int ch) {
		short r = this.inner.acceptByte(ch);
		if(r == Accept) {
			return r;
		}
		return CheckNextFlow;
	}
	@Override
	public boolean simpleMatch(ParsingContext context) {
		long f = context.rememberFailure();
		ParsingObject left = context.left;
		if(!this.inner.matcher.simpleMatch(context)) {
			context.left = left;
			context.forgetFailure(f);
		}
		return true;
	}
}

class ParsingRepetition extends ParsingUnary {
	ParsingRepetition(int flag, ParsingExpression e) {
		super(flag | ParsingExpression.HasRepetition, e);
	}
	@Override ParsingExpression dup() { 
		return new ParsingRepetition(flag, inner/*, atleast*/); 
	}
	@Override
	protected void visit(ExpressionVisitor visitor) {
		visitor.visitRepetition(this);
	}
	@Override short acceptByte(int ch) {
		short r = this.inner.acceptByte(ch);
		if(r == Accept) {
			return r;
		}
		return CheckNextFlow;
	}
	@Override
	public boolean simpleMatch(ParsingContext context) {
		long ppos = -1;
		long pos = context.getPosition();
		long f = context.rememberFailure();
		while(ppos < pos) {
			ParsingObject left = context.left;
			if(!this.inner.matcher.simpleMatch(context)) {
				context.left = left;
				break;
			}
			ppos = pos;
			pos = context.getPosition();
		}
		context.forgetFailure(f);
		return true;
	}
}

class ParsingAnd extends ParsingUnary {
	ParsingAnd(int flag, ParsingExpression e) {
		super(flag | ParsingExpression.HasAnd, e);
	}
	@Override ParsingExpression dup() { 
		return new ParsingAnd(flag, inner); 
	}
	@Override
	protected void visit(ExpressionVisitor visitor) {
		visitor.visitAnd(this);
	}
//	@Override
//	short acceptByte(int ch) {
//		short r = this.inner.acceptByte(ch);
//		if(r == Reject) {
//			return Reject;
//		}
//		return CheckNextFlow;
//	}
	@Override
	public boolean simpleMatch(ParsingContext context) {
		long pos = context.getPosition();
		this.inner.matcher.simpleMatch(context);
		context.rollback(pos);
		return !context.isFailure();
	}
}

class ParsingNot extends ParsingUnary {
	ParsingNot(int flag, ParsingExpression e) {
		super(ParsingExpression.HasNot | flag, e);
	}
	@Override ParsingExpression dup() { 
		return new ParsingNot(flag, inner); 
	}
	@Override
	protected void visit(ExpressionVisitor visitor) {
		visitor.visitNot(this);
	}
	@Override
	short acceptByte(int ch) {
		short r = this.inner.acceptByte(ch);
		if(r == Accept) {
			return Reject;
		}
		if(r == Reject) {
			return Accept;
		}
		return r;
	}
	@Override
	public boolean simpleMatch(ParsingContext context) {
		long pos = context.getPosition();
		long f   = context.rememberFailure();
		ParsingObject left = context.left;
		if(this.inner.matcher.simpleMatch(context)) {
			context.rollback(pos);
			context.opFailure(this);
			return false;
		}
		else {
			context.rollback(pos);
			context.forgetFailure(f);
			context.left = left;
			return true;
		}
	}
}

class ParsingSequence extends ParsingList {
	ParsingSequence(int flag, UList<ParsingExpression> l) {
		super(flag, l);
	}
	@Override
	ParsingExpression dup() {
		return new ParsingSequence(flag, list);
	}
	@Override
	protected void visit(ExpressionVisitor visitor) {
		visitor.visitSequence(this);
	}
	@Override
	public boolean simpleMatch(ParsingContext context) {
		long pos = context.getPosition();
		int mark = context.markObjectStack();
		for(int i = 0; i < this.size(); i++) {
			if(!(this.get(i).matcher.simpleMatch(context))) {
				context.abortLinkLog(mark);
				context.rollback(pos);
				return false;
			}
		}
		return true;
	}
}

class ParsingChoice extends ParsingList {
	ParsingExpression[] caseOf = null;
	ParsingChoice(int flag, UList<ParsingExpression> list) {
		super(flag | ParsingExpression.HasChoice, list);
	}
	@Override
	ParsingExpression dup() {
		return new ParsingChoice(flag, list);
	}
	@Override
	protected void visit(ExpressionVisitor visitor) {
		visitor.visitChoice(this);
	}
	@Override
	short acceptByte(int ch) {
		boolean checkNext = false;
		for(int i = 0; i < this.size(); i++) {
			short r = this.get(i).acceptByte(ch);
			if(r == Accept) {
				return r;
			}
			if(r == CheckNextFlow) {
				checkNext = true;
			}
		}
		return checkNext ? CheckNextFlow : Reject;
	}
	@Override
	public boolean simpleMatch(ParsingContext context) {
		long f = context.rememberFailure();
		ParsingObject left = context.left;
		for(int i = 0; i < this.size(); i++) {
			context.left = left;
			if(this.get(i).matcher.simpleMatch(context)) {
				context.forgetFailure(f);
				return true;
			}
		}
		assert(context.isFailure());
		return false;
	}
}

class ParsingConnector extends ParsingUnary {
	public int index;
	ParsingConnector(int flag, ParsingExpression e, int index) {
		super(flag | ParsingExpression.HasConnector | ParsingExpression.NoMemo, e);
		this.index = index;
	}
	@Override
	ParsingExpression dup() {
		return new ParsingConnector(flag, inner, index);
	}
	@Override
	protected void visit(ExpressionVisitor visitor) {
		visitor.visitConnector(this);
	}
	@Override
	public boolean simpleMatch(ParsingContext context) {
		ParsingObject left = context.left;
		if(!this.inner.matcher.simpleMatch(context)) {
			return false;
		}
		if(context.canTransCapture() && context.left != left) {
			context.logLink(left, this.index, context.left);
		}
		context.left = left;
		return true;
	}
}

class ParsingTagging extends ParsingExpression {
	ParsingTag tag;
	ParsingTagging(int flag, ParsingTag tag) {
		super(ParsingExpression.HasTagging | ParsingExpression.NoMemo | flag);
		this.tag = tag;
	}
	@Override
	ParsingExpression dup() {
		return new ParsingTagging(flag, tag);
	}
	@Override
	protected void visit(ExpressionVisitor visitor) {
		visitor.visitTagging(this);
	}
	@Override
	public boolean simpleMatch(ParsingContext context) {
		if(context.canTransCapture()) {
			context.left.setTag(this.tag);
		}
		return true;
	}
}

class ParsingValue extends ParsingExpression {
	String value;
	ParsingValue(int flag, String value) {
		super(flag | ParsingExpression.NoMemo | ParsingExpression.HasMessage);
		this.value = value;
	}
	@Override
	ParsingExpression dup() {
		return new ParsingValue(flag, value);
	}
	@Override
	protected void visit(ExpressionVisitor visitor) {
		visitor.visitValue(this);
	}
	@Override
	public boolean simpleMatch(ParsingContext context) {
		if(context.canTransCapture()) {
			context.left.setValue(this.value);
		}
		return true;
	}
}

class PConstructor extends ParsingList {
	boolean leftJoin = false;
	int prefetchIndex = 0;
	ParsingType type;
	PConstructor(int flag, boolean leftJoin, UList<ParsingExpression> list) {
		super(flag | ParsingExpression.HasConstructor, list);
		this.leftJoin = leftJoin;
	}
	@Override
	ParsingExpression dup() {
		return new PConstructor(flag, leftJoin, list);
	}
	@Override
	protected void visit(ExpressionVisitor visitor) {
		visitor.visitConstructor(this);
	}
	@Override
	public boolean simpleMatch(ParsingContext context) {
		long startIndex = context.getPosition();
		ParsingObject left = context.left;
		if(context.isRecognitionMode()) {
			ParsingObject newone = context.newParsingObject(startIndex, this);
			context.left = newone;
			for(int i = 0; i < this.size(); i++) {
				if(!this.get(i).matcher.simpleMatch(context)) {
					context.rollback(startIndex);
					return false;
				}
			}
			context.left = newone;
			return true;
		}
		else {
			for(int i = 0; i < this.prefetchIndex; i++) {
				if(!this.get(i).matcher.simpleMatch(context)) {
					context.rollback(startIndex);
					return false;
				}
			}
			int mark = context.markObjectStack();
			ParsingObject newnode = context.newParsingObject(startIndex, this);
			context.left = newnode;
			if(this.leftJoin) {
				context.logLink(newnode, -1, left);
			}
			for(int i = this.prefetchIndex; i < this.size(); i++) {
				if(!this.get(i).matcher.simpleMatch(context)) {
					context.abortLinkLog(mark);
					context.rollback(startIndex);
					return false;
				}
			}
			context.commitLinkLog(newnode, startIndex, mark);
			if(context.stat != null) {
				context.stat.countObjectCreation();
			}
			context.left = newnode;
			return true;
		}
	}
}

// ------------------------------
// PEG4d Function, PEG4d Operator

abstract class ParsingFunction extends ParsingExpression {
	String funcName;
	ParsingFunction(String funcName, int flag) {
		super(flag);
		this.funcName = funcName;
	}
	@Override
	protected void visit(ExpressionVisitor visitor) {
		visitor.visitParsingFunction(this);
	}
	String getParameters() {
		return "";
	}
}

abstract class ParsingOperation extends ParsingUnary {
	String funcName;
	ParsingOperation(String funcName, ParsingExpression inner) {
		super(0, inner);
		this.funcName = funcName;
		this.inner = inner;
	}
	@Override
	protected void visit(ExpressionVisitor visitor) {
		visitor.visitParsingOperation(this);
	}
	@Override
	public ParsingExpression getExpression() {
		return this.inner;
	}
	public String getParameters() {
		return "";
	}
}

class ParsingIndent extends ParsingFunction {
	ParsingIndent(int flag) {
		super("indent", flag | ParsingExpression.HasContext);
	}
	@Override ParsingExpression dup() {
		return this;
	}
	@Override
	short acceptByte(int ch) {
		if (ch == '\t' || ch == ' ') {
			return Accept;
		}
		return CheckNextFlow;
	}
	@Override
	public boolean simpleMatch(ParsingContext context) {
		context.opIndent();
		return !context.isFailure();
	}
}

class ParsingFail extends ParsingFunction {
	String message;
	ParsingFail(int flag, String message) {
		super("fail", flag);
		this.message = message;
	}
	@Override ParsingExpression dup() {
		return new ParsingFail(flag, message);
	}
	@Override
	public boolean simpleMatch(ParsingContext context) {
		context.opFailure(this.message);
		return false;
	}
	@Override
	short acceptByte(int ch) {
		return Reject;
	}
}

class ParsingCatch extends ParsingFunction {
	ParsingCatch(int flag) {
		super("catch", flag);
	}
	@Override ParsingExpression dup() {
		return this;
	}
	@Override
	public boolean simpleMatch(ParsingContext context) {
		context.opCatch();
		return true;
	}
}


class ParsingExport extends ParsingUnary {
	ParsingExport(int flag, ParsingExpression e) {
		super(flag | ParsingExpression.NoMemo, e);
	}
	@Override
	ParsingExpression dup() {
		return new ParsingExport(flag, inner);
	}
	@Override
	protected void visit(ExpressionVisitor visitor) {
		visitor.visitExport(this);
	}
	@Override
	public boolean simpleMatch(ParsingContext context) {
		return true;
	}
}

class ParsingMemo extends ParsingOperation {
	static ParsingObject NonTransition = new ParsingObject(null, null, 0);

	boolean enableMemo = true;
	int memoId;
	int memoHit = 0;
	int memoMiss = 0;

	ParsingMemo(int memoId, ParsingExpression inner) {
		super("memo", inner);
		this.memoId = memoId;
	}

	@Override ParsingExpression dup() {
		return new ParsingMemo(0, inner);
	}

	@Override
	public boolean simpleMatch(ParsingContext context) {
		if(!this.enableMemo) {
			return this.inner.matcher.simpleMatch(context);
		}
		long pos = context.getPosition();
		ParsingObject left = context.left;
		ObjectMemo m = context.getMemo(this, pos);
		if(m != null) {
			this.memoHit += 1;
			context.setPosition(pos + m.consumed);
			if(m.generated != NonTransition) {
				context.left = m.generated;
			}
			return !(context.isFailure());
		}
		this.inner.matcher.simpleMatch(context);
		int length = (int)(context.getPosition() - pos);
		context.setMemo(pos, this, (context.left == left) ? NonTransition : context.left, length);
		this.memoMiss += 1;
		this.tryTracing();
		return !(context.isFailure());
	}

	private void tryTracing() {
		if(Main.TracingMemo) {
			if(this.memoMiss == 32) {
				if(this.memoHit < 2) {
					disabledMemo();
					return;
				}
			}
			if(this.memoMiss % 64 == 0) {
				if(this.memoHit == 0) {
					disabledMemo();
					return;
				}
				if(this.memoMiss / this.memoHit > 10) {
					disabledMemo();
					return;
				}
			}
		}		
	}
	
	private void disabledMemo() {
		//this.show();
		this.enableMemo = false;
//		this.base.DisabledMemo += 1;
//		int factor = this.base.EnabledMemo / 10;
//		if(factor != 0 && this.base.DisabledMemo % factor == 0) {
//			this.base.memoRemover.removeDisabled();
//		}
	}

	void show() {
		if(Main.VerboseMode) {
			double f = (double)this.memoHit / this.memoMiss;
			System.out.println(this.inner.getClass().getSimpleName() + " #h/m=" + this.memoHit + "," + this.memoMiss + ", f=" + f + " " + this.inner);
		}
	}
}

class ParsingMatch extends ParsingOperation {
	ParsingMatch(ParsingExpression inner) {
		super("match", inner);
	}
	@Override ParsingExpression dup() {
		return new ParsingMatch(inner);
	}
	@Override
	public boolean simpleMatch(ParsingContext context) {
		boolean oldMode = context.setRecognitionMode(true);
		ParsingObject left = context.left;
		if(this.inner.matcher.simpleMatch(context)) {
			context.setRecognitionMode(oldMode);
			context.left = left;
			return true;
		}
		context.setRecognitionMode(oldMode);
		return false;
	}
}

class ParsingStackIndent extends ParsingOperation {
	ParsingStackIndent(ParsingExpression e) {
		super("indent", e);
	}
	@Override ParsingExpression dup() {
		return new ParsingStackIndent(inner);
	}
	@Override
	public boolean simpleMatch(ParsingContext context) {
		context.opPushIndent();
		this.inner.matcher.simpleMatch(context);
		context.opPopIndent();
		return !(context.isFailure());
	}
}

class ParsingIfFlag extends ParsingFunction {
	String flagName;
	ParsingIfFlag(int flag, String flagName) {
		super("if", flag | ParsingExpression.HasContext);
		this.flagName = flagName;
	}
	@Override ParsingExpression dup() {
		return new ParsingIfFlag(flag, flagName);
	}
	@Override
	public String getParameters() {
		return " " + this.flagName;
	}
	@Override
	public boolean simpleMatch(ParsingContext context) {
		context.opCheckFlag(this.flagName);
		return !(context.isFailure());
	}
}

class ParsingWithFlag extends ParsingOperation {
	String flagName;
	ParsingWithFlag(String flagName, ParsingExpression inner) {
		super("with", inner);
		this.flagName = flagName;
	}
	@Override
	public String getParameters() {
		return " " + this.flagName;
	}
	@Override ParsingExpression dup() {
		return new ParsingWithFlag(flagName, inner);
	}
	@Override
	public boolean simpleMatch(ParsingContext context) {
		context.opEnableFlag(this.flagName);
		this.inner.matcher.simpleMatch(context);
		context.opPopFlag(this.flagName);
		return !(context.isFailure());
	}
}

class ParsingWithoutFlag extends ParsingOperation {
	String flagName;
	ParsingWithoutFlag(String flagName, ParsingExpression inner) {
		super("without", inner);
		this.flagName = flagName;
	}
	@Override
	public String getParameters() {
		return " " + this.flagName;
	}
	@Override ParsingExpression dup() {
		return new ParsingWithoutFlag(flagName, inner);
	}
	@Override
	public boolean simpleMatch(ParsingContext context) {
		context.opDisableFlag(this.flagName);
		this.inner.matcher.simpleMatch(context);
		context.opPopFlag(this.flagName);
		return !(context.isFailure());
	}
}

class ParsingDebug extends ParsingOperation {
	protected ParsingDebug(ParsingExpression inner) {
		super("debug", inner);
	}
	@Override ParsingExpression dup() {
		return new ParsingDebug(inner);
	}
	@Override
	public boolean simpleMatch(ParsingContext context) {
		context.opRememberPosition();
		context.opRememberFailurePosition();
		context.opStoreObject();
		this.inner.matcher.simpleMatch(context);
		context.opDebug(this.inner);
		return !(context.isFailure());
	}
}

class ParsingApply extends ParsingOperation {
	ParsingApply(ParsingExpression inner) {
		super("|apply", inner);
	}
	@Override ParsingExpression dup() {
		return new ParsingApply(inner);
	}
	@Override
	public boolean simpleMatch(ParsingContext context) {
//		ParsingContext s = new ParsingContext(context.left);
//		
//		this.inner.matcher.simpleMatch(s);
//		context.opRememberPosition();
//		context.opRememberFailurePosition();
//		context.opStoreObject();
//		this.inner.matcher.simpleMatch(context);
//		context.opDebug(this.inner);
		return !(context.isFailure());

	}
}


// --------------------------------------------------------------------------

//class POptionalString extends POptional {
//byte[] utf8;
//POptionalString(int flag, PString e) {
//	super(e);
//	this.utf8 = e.utf8;
//}
//@Override ParsingExpression dup() { 
//	return new POptionalString(flag, (PString)inner); 
//}
//@Override
//public boolean simpleMatch(ParsingContext context) {
//	if(context.source.match(context.pos, this.utf8)) {
//		context.consume(this.utf8.length);
//	}
//}
//}
//
//class POptionalByteChar extends POptional {
//int byteChar;
//POptionalByteChar(int flag, ParsingByte e) {
//	super(e);
//	this.byteChar = e.byteChar;
//}
//@Override ParsingExpression dup() { 
//	return new POptionalByteChar(flag, (ParsingByte)inner); 
//}
//@Override
//public boolean simpleMatch(ParsingContext context) {
//	context.opMatchOptionalByteChar(this.byteChar);
//}
//}
//
//class POptionalCharacter extends POptional {
//ParsingCharset charset;
//POptionalCharacter(int flag, PCharacter e) {
//	super(e);
//	this.charset = e.charset;
//}
//@Override ParsingExpression dup() { 
//	return new POptionalCharacter(flag, (PCharacter)inner); 
//}
//@Override
//public boolean simpleMatch(ParsingContext context) {
//	context.opMatchOptionalCharset(this.charset);
//}
//}
//class PZeroMoreCharacter extends PRepetition {
//	ParsingCharset charset;
//	PZeroMoreCharacter(int flag, PCharacter e) {
//		super(flag, e);
//		this.charset = e.charset;
//	}
//	@Override ParsingExpression dup() { 
//		return new PZeroMoreCharacter(flag, (PCharacter)inner); 
//	}
//	@Override
//	public boolean simpleMatch(ParsingContext context) {
//		long pos = context.getPosition();
//		int consumed = 0;
//		do {
//			consumed = this.charset.consume(context.source, pos);
//			pos += consumed;
//		}
//		while(consumed > 0);
//		context.setPosition(pos);
//	}
//}

//class PNotString extends PNot {
//byte[] utf8;
//PNotString(int flag, PString e) {
//	super(flag | ParsingExpression.NoMemo, e);
//	this.utf8 = e.utf8;
//}
//@Override ParsingExpression dup() { 
//	return new PNotString(flag, (PString)inner); 
//}
//@Override
//public boolean simpleMatch(ParsingContext context) {
//	context.opMatchTextNot(utf8);
//}
//}
//
//class PNotByteChar extends PNot {
//int byteChar;
//PNotByteChar(int flag, ParsingByte e) {
//	super(flag, e);
//	this.byteChar = e.byteChar;
//}
//@Override ParsingExpression dup() { 
//	return new PNotByteChar(flag, (ParsingByte)inner); 
//}
//@Override
//public boolean simpleMatch(ParsingContext context) {
//	context.opMatchByteCharNot(this.byteChar);
//}
//}	
//
//class PNotCharacter extends PNot {
//ParsingCharset charset;
//PNotCharacter(int flag, PCharacter e) {
//	super(flag | ParsingExpression.NoMemo, e);
//	this.charset = e.charset;
//}
//@Override ParsingExpression dup() { 
//	return new PNotCharacter(flag, (PCharacter)inner); 
//}
//@Override
//public boolean simpleMatch(ParsingContext context) {
//	context.opMatchCharsetNot(this.charset);
//}
//}

