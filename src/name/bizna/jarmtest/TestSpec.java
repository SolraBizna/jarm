package name.bizna.jarmtest;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import name.bizna.jarm.CPU;

public class TestSpec {
	public static class InvalidSpecException extends Exception {
		public static final long serialVersionUID = 1;
	}
	
	private static enum Token {
		WHITESPACE("^[ \t]+"),
		IDENTIFIER("^[_a-zA-Z][_a-zA-Z0-9]*"),
		ARRAY_ACCESS("^\\[(\\-?(?:0x[0-9A-FA-f]+|[0-9]+))\\]"),
		NUMBER("\\-?(?:0x[0-9A-FA-f]+|[0-9]+)"),
		ASSIGNMENT("^:="),
		EQUALITY("^==");
		private final Pattern p;
		private Token(String p) {
			this.p = Pattern.compile(p);
		}
		public Pattern getPattern() {
			return p;
		}
	}
	
	private static class LineParser {
		private Matcher[] matchers;
		private boolean parsedOkay = true;
		private int curPos = 0, parsedPos = 0;
		private MatchResult lastResult = null;
		private Token lastToken = null;
		private String line;
		public LineParser(String line) {
			this.line = line;
			matchers = new Matcher[Token.values().length];
			for(int n = 0; n < matchers.length; ++n)
				matchers[n] = Token.values()[n].getPattern().matcher(line);
		}
		public boolean parseNextToken() {
			if(!parsedOkay) return false;
			lastResult = null;
			lastToken = null;
			if(curPos >= line.length()) return false;
			for(int i = 0; i < matchers.length; ++i) {
				Matcher m = matchers[i];
				m.region(curPos, line.length());
				if(m.lookingAt()) {
					lastResult = m.toMatchResult();
					lastToken = Token.values()[i];
					parsedPos = curPos;
					curPos = lastResult.end();
					if(lastToken == Token.WHITESPACE)
						return parseNextToken();
					else
						return true;
				}
			}
			parsedOkay = false;
			return false;
		}
		public int getParsedPos() {
			return parsedPos;
		}
		public Token getParsedToken() {
			return lastToken;
		}
		public MatchResult getParsedResult() {
			return lastResult;
		}
		public boolean parsedOkay() {
			return parsedOkay;
		}
		public int getPos() {
			return curPos;
		}
	}
	
	private static class Assignment {
		protected LValue left;
		protected RValue right;
		public Assignment(LValue left, RValue right) {
			this.left = left;
			this.right = right;
		}
		public void execute(CPU cpu) {
			left.setValue(cpu, right.getValue(cpu));
		}
		@Override
		public String toString() {
			return left.toString() + " := " + right.toString();
		}
	}
	private static abstract class Check {
		protected RValue left, right;
		protected Check(RValue left, RValue right) {
			this.left = left;
			this.right = right;
		}
		abstract public boolean check(CPU cpu);
		@Override
		abstract public String toString();
	}
	private static class EqualityCheck extends Check {
		public EqualityCheck(RValue left, RValue right) { super(left, right); }
		@Override
		public boolean check(CPU cpu) { return left.getValue(cpu) == right.getValue(cpu); }
		@Override
		public String toString() {
			return left.toString() + " == " + right.toString();
		}
	}

	private static final Pattern linePattern = Pattern.compile("^[^#]*");

	private List<Assignment> assignments = new ArrayList<Assignment>();
	private List<Check> checks = new ArrayList<Check>();
	
	public TestSpec(File sourceFile) throws IOException, InvalidSpecException {
		InputStream stream = new FileInputStream(sourceFile);
		Scanner scan = null;
		boolean hadParseErrors = false, sawQuitReasonCompareLeft = false;
		try {
			scan = new Scanner(stream);
			int lineno = 0;
			while(scan.hasNextLine()) {
				++lineno;
				String rawLine = scan.nextLine();
				String line;
				{
					Matcher lpm = linePattern.matcher(rawLine);
					if(lpm.lookingAt()) line = lpm.group(0);
					else line = rawLine;
				}
				LineParser p = new LineParser(line);
				boolean validLine = true;
				if(p.parseNextToken()) {
					switch(p.getParsedToken()) {
					case IDENTIFIER:
						String lvalueID = p.getParsedResult().group(0);
						if(p.parseNextToken()) {
							LValue lvalue;
							if(p.getParsedToken() == Token.ARRAY_ACCESS) {
								lvalue = LValue.make(lvalueID, p.getParsedResult().group(1));
								p.parseNextToken();
							}
							else {
								lvalue = LValue.make(lvalueID);
								if(lvalueID.equals("quitReason")) sawQuitReasonCompareLeft = true;
							}
							validLine = validLine && lvalue != null;
							if(validLine && p.parsedOkay()) switch(p.getParsedToken()) {
							case ASSIGNMENT:
							case EQUALITY:
								Token op = p.getParsedToken();
								if(p.parseNextToken()) {
									RValue rvalue = null;
									switch(p.getParsedToken()) {
									case IDENTIFIER:
										String rvalueID = p.getParsedResult().group(0);
										if(p.parseNextToken()) {
											if(p.getParsedToken() == Token.ARRAY_ACCESS) {
												rvalue = RValue.make(rvalueID, p.getParsedResult().group(1));
												validLine = validLine && !p.parseNextToken();
											}
											else validLine = false;
										}
										else rvalue = RValue.make(rvalueID);
										break;
									case NUMBER:
										rvalue = RValue.makeIntConstant(p.getParsedResult().group(0));
										validLine = validLine && !p.parseNextToken();
										break;
									default:
										validLine = false;
									}
									if(validLine) switch(op) {
									case ASSIGNMENT:
										assignments.add(new Assignment(lvalue, rvalue));
										break;
									case EQUALITY:
										checks.add(new EqualityCheck(lvalue, rvalue));
										break;
									default: throw new Error("2 != 2");
									}
								}
								else assert(!p.parsedOkay());
								break;
							default:
								validLine = false;
							}
						}
						else assert(!p.parsedOkay());
						break;
					default:
						validLine = false;
					}
					if(!p.parsedOkay() || !validLine || p.getParsedToken() != null) {
						System.err.println(sourceFile.getPath()+": parse error on line "+lineno);
						System.err.println(rawLine);
						int pos = p.parsedOkay() ? p.getParsedPos() : p.getPos();
						for(int n = 0; n < pos; ++n) System.err.print("-");
						System.err.println("^");
						hadParseErrors = true;
					}
				}
			}
		}
		finally { if(scan != null) scan.close(); }
		if(hadParseErrors) throw new InvalidSpecException();
		if(!sawQuitReasonCompareLeft) checks.add(new EqualityCheck(RValue.make("quitReason"), RValue.makeIntConstant("0")));
	}
	public void applyInitialStateAndReset(CPU cpu, boolean littleEndian) {
		// TODO: high vectors, thumb exceptions
		for(int n = 0; n < 13; ++n) {
			cpu.writeRegister(n, 0xDEADBEEF);
		}
		cpu.reset(false, !littleEndian, false);
		cpu.writeSP(0x80000000);
		for(Assignment a : assignments) {
			a.execute(cpu);
		}
	}
	public boolean checkFinalState(CPU cpu, List<String> failures) {
		boolean ret = true;
		for(Check c : checks) {
			if(!c.check(cpu)) {
				failures.add("failed "+c.toString()+"; value was "+c.left.getValue(cpu));
				ret = false;
			}
		}
		return ret;
	}
}
