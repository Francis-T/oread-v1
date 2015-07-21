package net.oukranos.oreadv1.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.oukranos.oreadv1.interfaces.MethodEvaluatorIntf;
import net.oukranos.oreadv1.types.DataStore;
import net.oukranos.oreadv1.types.DataStoreObject;
import net.oukranos.oreadv1.types.Status;

public class ConditionEvaluator {
	/* Get an instance of the OreadLogger class to handle logging */
	private static final OreadLogger OLog = OreadLogger.getInstance();
	
	private static final String REGEX_TOKEN        	= "\\$*[\"_A-Za-z0-9\\.\\/\\:\\-]+\\(*\\)*";
	private static final String REGEX_CONDITIONAL   = "[!]*[><=][=]*";
	private static final String REGEX_WHITESPACE	= "\\s*";
	private static final String REGEX_TERMINATOR	= ";";

	private static final String REGEX_REDUNDANT_WS	= "\\s\\s+";
	private static final String REGEX_FUNCTION_STR 	= "^[_A-Za-z][A-Za-z0-9]+\\(\\)$";
	private static final String REGEX_STRING_STR 	= "^\".+\"$";
	private static final String REGEX_INTEGER_STR 	= "^[0-9]+$";
	private static final String REGEX_LONG_STR 		= "^[0-9]+l$";
	private static final String REGEX_DOUBLE_STR 	= "^[0-9]+\\.[0-9]+$";
	private static final String REGEX_FLOAT_STR 	= "^[0-9]+\\.[0-9]+f$";
	
	private DataStore 			_dataStore = null;
	private MethodEvaluatorIntf	_methodEval = null;
	
	public ConditionEvaluator() {
		return;
	}
	
	public Status setDataStore(DataStore dataStore) {
		_dataStore = dataStore;
		return Status.OK;
	}
	
	public Status setMethodEvaluator(MethodEvaluatorIntf evaluator) {
		_methodEval = evaluator;
		return Status.OK;
	}
	
	public boolean evaluate(String condition) {
		if (condition == null) {
			return false;
		}
		
		if (condition.isEmpty()) {
			return false;
		}
		
		boolean result = false;
		String condStrShort = condition.replaceAll(REGEX_REDUNDANT_WS, " ");
		Pattern condPattern = Pattern.compile(buildCondition());
		Matcher condMatcher = condPattern.matcher(condStrShort);
		
		while (condMatcher.find()) {
			int startIdx = condMatcher.start();
			int endIdx = condMatcher.end();

			String condExprStr = condStrShort.substring(startIdx, endIdx);
			
			Expression expr = new Expression();
			this.extractTokens(expr, condExprStr);
			this.extractOperator(expr, condExprStr);
			
			OLog.info("Evaluating: " + condExprStr);
			result = expr.evaluate();
			OLog.info("Result: " + result);
			if (result == false) {
				break;
			}
		}
		
		return result;
	}
	
	private String buildCondition() {
		StringBuilder pattern = new StringBuilder();
		
		pattern.append(REGEX_TOKEN);
		pattern.append(REGEX_WHITESPACE);
		pattern.append(REGEX_CONDITIONAL);
		pattern.append(REGEX_WHITESPACE);
		pattern.append(REGEX_TOKEN);
		pattern.append(REGEX_TERMINATOR);
		
		return pattern.toString();
	}
	
	private void extractTokens(Expression expr, String exprStr) {
		Pattern tokenPattern = Pattern.compile(REGEX_TOKEN);
		Matcher tokenMatcher = tokenPattern.matcher(exprStr);

		int tokStartIdx = 0;
		int tokEndIdx = 0;
		String tokenStr = null;
		
		/* Extract the first token */
		tokenMatcher.find();
		
		tokStartIdx = tokenMatcher.start();
		tokEndIdx = tokenMatcher.end();
		tokenStr = exprStr.substring(tokStartIdx, tokEndIdx);
		
		expr.setFirstToken(new Token().build(tokenStr));

		/* Extract the second token */
		tokenMatcher.find();
		
		tokStartIdx = tokenMatcher.start();
		tokEndIdx = tokenMatcher.end();
		tokenStr = exprStr.substring(tokStartIdx, tokEndIdx);
		
		expr.setSecondToken(new Token().build(tokenStr));
		
		return;
	}
	
	private void extractOperator(Expression expr, String exprStr) {
		Pattern operatorPattern = Pattern.compile(REGEX_CONDITIONAL);
		Matcher operatorMatcher = operatorPattern.matcher(exprStr);
		
		int operatorCount = 0;
		if (operatorMatcher.find() != true) {
			return;
		}
		int operStartIdx = operatorMatcher.start();
		int operEndIdx = operatorMatcher.end();
		
		operatorCount++;
		
		System.out.println("Operator#" + operatorCount + ": " + 
				exprStr.substring(operStartIdx, operEndIdx));
		
		expr.setOperator(exprStr.substring(operStartIdx, operEndIdx));
		
		return;
	}
	
	private enum ExprOperator {
		UNKNOWN,
		EQUAL_TO, 
		NOT_EQUAL_TO, 
		GREATER_THAN, 
		GREATER_THAN_OR_EQUAL_TO,
		LESS_THAN,
		LESS_THAN_OR_EQUAL_TO
	}
	
	private class Expression {
		private static final String TYPE_INT 	= "Integer";
		private static final String TYPE_LONG 	= "Long";
		private static final String TYPE_DOUBLE = "Double";
		private static final String TYPE_FLOAT 	= "Float";
		private static final String TYPE_BYTE 	= "Byte";
		private static final String TYPE_SHORT 	= "Short";
		private static final String TYPE_CHAR 	= "Character";
		private static final String TYPE_STRING	= "String";
		
		private Token token1 = null;
		private Token token2 = null;
		private ExprOperator operator = ExprOperator.UNKNOWN;
		
		public boolean evaluate() {
			if ((token1 == null) || (token2 == null)) {
				OLog.err("One of the tokens are null");
				return false;
			}
			
			if ((token1.value == null) || (token2.value == null)) {
				OLog.err("One of the token's values are null");
				return false;
			}
			
			if ( !token1.type.equals(token2.type) ) {
				OLog.err("Type mismatch: " +
						token1.type + "to" + token2.type);
				return false;
			}
			
			OLog.info("  Token#1:  " + token1.toString());
			OLog.info("  Token#2:  " + token2.toString());
			OLog.info("  Operator: " + operator.toString());
			
			switch(operator) {
				case EQUAL_TO:
					return isEqualTo(token1.value, token2.value);
				case NOT_EQUAL_TO:
					return isNotEqualTo(token1.value, token2.value);
				case GREATER_THAN:
					return isGreaterThan(token1.value, token2.value);
				case GREATER_THAN_OR_EQUAL_TO:
					return isGreaterThanOrEqualTo(token1.value, token2.value);
				case LESS_THAN:
					return isLessThan(token1.value, token2.value);
				case LESS_THAN_OR_EQUAL_TO:
					return isLessThanOrEqualTo(token1.value, token2.value);
				default:
					break;
			}
			
			return false;
		}

		public void setFirstToken(Token t) {
			this.token1 = t;
			return;
		}

		public void setSecondToken(Token t) {
			this.token2 = t;
			return;
		}
		
		public void setOperator(String opStr) {
			if (opStr.equals("==")) {
				operator = ExprOperator.EQUAL_TO;
			} else if (opStr.equals("!=")) {
				operator = ExprOperator.NOT_EQUAL_TO;
			} else if (opStr.equals(">")) {
				operator = ExprOperator.GREATER_THAN;
			} else if (opStr.equals(">=")) {
				operator = ExprOperator.GREATER_THAN_OR_EQUAL_TO;
			} else if (opStr.equals("<")) {
				operator = ExprOperator.LESS_THAN;
			} else if (opStr.equals("<=")) {
				operator = ExprOperator.LESS_THAN_OR_EQUAL_TO;
			} else {
				operator = ExprOperator.UNKNOWN;
			}
			
			return;
		}
		
		private boolean isEqualTo(Object obj1, Object obj2) {
			String obj1Class = obj1.getClass().getSimpleName();
			String obj2Class = obj2.getClass().getSimpleName();
			
			if (!obj1Class.equals(obj2Class)) {
				OLog.err("Incompatible Types: " +
						obj1Class + " to " + obj2Class );
				return false;
			}
			
			if (obj1Class.equals(TYPE_INT)) {
				return ((Integer)obj1 == (Integer)obj2);
			} else if (obj1Class.equals(TYPE_LONG)) {
				return ((Long)obj1 == (Long)obj2);
			} else if (obj1Class.equals(TYPE_DOUBLE)) {
				return ((Double)obj1 == (Double)obj2);
			} else if (obj1Class.equals(TYPE_FLOAT)) {
				return ((Float)obj1 == (Float)obj2);
			} else if (obj1Class.equals(TYPE_BYTE)) {
				return ((Byte)obj1 == (Byte)obj2);
			} else if (obj1Class.equals(TYPE_SHORT)) {
				return ((Short)obj1 == (Short)obj2);
			} else if (obj1Class.equals(TYPE_CHAR)) {
				return ((Character)obj1 == (Character)obj2);
			} else if (obj1Class.equals(TYPE_STRING)) {
				return (obj1.equals(obj2));
			}
			
			return false;
		}
		
		private boolean isNotEqualTo(Object obj1, Object obj2) {
			String obj1Class = obj1.getClass().getSimpleName();
			String obj2Class = obj2.getClass().getSimpleName();
			
			if (!obj1Class.equals(obj2Class)) {
				OLog.err("Incompatible Types: " +
						obj1Class + " to " + obj2Class );
				return false;
			}
			
			if (obj1Class.equals(TYPE_INT)) {
				return ((Integer)obj1 != (Integer)obj2);
			} else if (obj1Class.equals(TYPE_LONG)) {
				return ((Long)obj1 != (Long)obj2);
			} else if (obj1Class.equals(TYPE_DOUBLE)) {
				return ((Double)obj1 != (Double)obj2);
			} else if (obj1Class.equals(TYPE_FLOAT)) {
				return ((Float)obj1 != (Float)obj2);
			} else if (obj1Class.equals(TYPE_BYTE)) {
				return ((Byte)obj1 != (Byte)obj2);
			} else if (obj1Class.equals(TYPE_SHORT)) {
				return ((Short)obj1 != (Short)obj2);
			} else if (obj1Class.equals(TYPE_CHAR)) {
				return ((Character)obj1 != (Character)obj2);
			} else if (obj1Class.equals(TYPE_STRING)) {
				return (!obj1.equals(obj2));
			}
			
			return false;
		}
		
		private boolean isGreaterThan(Object obj1, Object obj2) {
			String obj1Class = obj1.getClass().getSimpleName();
			String obj2Class = obj2.getClass().getSimpleName();
			
			if (!obj1Class.equals(obj2Class)) {
				OLog.err("Incompatible Types: " +
						obj1Class + " to " + obj2Class );
				return false;
			}
			
			if (obj1Class.equals(TYPE_INT)) {
				return ((Integer)obj1 > (Integer)obj2);
			} else if (obj1Class.equals(TYPE_LONG)) {
				return ((Long)obj1 > (Long)obj2);
			} else if (obj1Class.equals(TYPE_DOUBLE)) {
				return ((Double)obj1 > (Double)obj2);
			} else if (obj1Class.equals(TYPE_FLOAT)) {
				return ((Float)obj1 > (Float)obj2);
			} else if (obj1Class.equals(TYPE_BYTE)) {
				return ((Byte)obj1 > (Byte)obj2);
			} else if (obj1Class.equals(TYPE_SHORT)) {
				return ((Short)obj1 > (Short)obj2);
			} else if (obj1Class.equals(TYPE_CHAR)) {
				return ((Character)obj1 > (Character)obj2);
			}
			
			return false;
		}
		
		private boolean isGreaterThanOrEqualTo(Object obj1, Object obj2) {
			String obj1Class = obj1.getClass().getSimpleName();
			String obj2Class = obj2.getClass().getSimpleName();
			
			if (!obj1Class.equals(obj2Class)) {
				OLog.err("Incompatible Types: " +
						obj1Class + " to " + obj2Class );
				return false;
			}
			
			if (obj1Class.equals(TYPE_INT)) {
				return ((Integer)obj1 >= (Integer)obj2);
			} else if (obj1Class.equals(TYPE_LONG)) {
				return ((Long)obj1 >= (Long)obj2);
			} else if (obj1Class.equals(TYPE_DOUBLE)) {
				return ((Double)obj1 >= (Double)obj2);
			} else if (obj1Class.equals(TYPE_FLOAT)) {
				return ((Float)obj1 >= (Float)obj2);
			} else if (obj1Class.equals(TYPE_BYTE)) {
				return ((Byte)obj1 >= (Byte)obj2);
			} else if (obj1Class.equals(TYPE_SHORT)) {
				return ((Short)obj1 >= (Short)obj2);
			} else if (obj1Class.equals(TYPE_CHAR)) {
				return ((Character)obj1 >= (Character)obj2);
			}
			
			return false;
		}
		
		private boolean isLessThan(Object obj1, Object obj2) {
			String obj1Class = obj1.getClass().getSimpleName();
			String obj2Class = obj2.getClass().getSimpleName();
			
			if (!obj1Class.equals(obj2Class)) {
				OLog.err("Incompatible Types: " +
						obj1Class + " to " + obj2Class );
				return false;
			}
			
			if (obj1Class.equals(TYPE_INT)) {
				return ((Integer)obj1 < (Integer)obj2);
			} else if (obj1Class.equals(TYPE_LONG)) {
				return ((Long)obj1 < (Long)obj2);
			} else if (obj1Class.equals(TYPE_DOUBLE)) {
				return ((Double)obj1 < (Double)obj2);
			} else if (obj1Class.equals(TYPE_FLOAT)) {
				return ((Float)obj1 < (Float)obj2);
			} else if (obj1Class.equals(TYPE_BYTE)) {
				return ((Byte)obj1 < (Byte)obj2);
			} else if (obj1Class.equals(TYPE_SHORT)) {
				return ((Short)obj1 < (Short)obj2);
			} else if (obj1Class.equals(TYPE_CHAR)) {
				return ((Character)obj1 < (Character)obj2);
			}
			
			return false;
		}
		
		private boolean isLessThanOrEqualTo(Object obj1, Object obj2) {
			String obj1Class = obj1.getClass().getSimpleName();
			String obj2Class = obj2.getClass().getSimpleName();
			
			if (!obj1Class.equals(obj2Class)) {
				OLog.err("Incompatible Types: " +
						obj1Class + " to " + obj2Class );
				return false;
			}
			
			if (obj1Class.equals(TYPE_INT)) {
				return ((Integer)obj1 <= (Integer)obj2);
			} else if (obj1Class.equals(TYPE_LONG)) {
				return ((Long)obj1 <= (Long)obj2);
			} else if (obj1Class.equals(TYPE_DOUBLE)) {
				return ((Double)obj1 <= (Double)obj2);
			} else if (obj1Class.equals(TYPE_FLOAT)) {
				return ((Float)obj1 <= (Float)obj2);
			} else if (obj1Class.equals(TYPE_BYTE)) {
				return ((Byte)obj1 <= (Byte)obj2);
			} else if (obj1Class.equals(TYPE_SHORT)) {
				return ((Short)obj1 <= (Short)obj2);
			} else if (obj1Class.equals(TYPE_CHAR)) {
				return ((Character)obj1 <= (Character)obj2);
			}
			
			return false;
		}
	}
	
	private class Token {
		public String id = "";
		public String type = "";
		public Object value = null;
		
		public String toString() {
			return (this.id + "(" + this.type + "): " + this.value);
		}
		
		private Token build(String tokenStr) {
			this.id = tokenStr;
			/* Check if the token has an equivalent in the data store */
			if (this.id.charAt(0) == '$') {
				OLog.info("Found data store object");
				String dataStoreId = this.id.substring(1);
				if (_dataStore != null) {
					DataStoreObject d = _dataStore.retrieve(dataStoreId);
					if (d != null) {
						this.type = d.getType();
						this.value = d.getObject();
					}
				} else {	
					this.type = "string";	//TODO
					this.value = "spoopy";	//TODO
				}
			} else if (this.id.matches(REGEX_FUNCTION_STR)) {
				OLog.info("Found function object");
				
				if (_methodEval != null) {
					DataStoreObject d = _methodEval.evaluate(this.id);
					if (d != null) {
						this.type = d.getType();
						this.value = d.getObject();
					}
				} else {	
					this.type = "string";	//TODO
					this.value = "spoopy";	//TODO
				}
			} else if (this.id.matches(REGEX_STRING_STR)) {
				OLog.info("Found possible string");
				int startIdx = 1;
				int endIdx = this.id.length() - 1;
				String strValue = this.id.substring(startIdx, endIdx);
				
				this.type = "string";
				this.value = strValue;
			} else if(this.id.matches(REGEX_INTEGER_STR)) {
				OLog.info("Found possible integer");
				Integer intValue = null;
				try {
					intValue = Integer.parseInt(this.id);
				} catch(NumberFormatException e) {
					intValue = null;
				}

				this.type = "integer";
				this.value = intValue;
			} else if(this.id.matches(REGEX_LONG_STR)) {
				OLog.info("Found possible long");
				String longStr = this.id.replace("l", "");
				Long longValue = null;
				
				try {
					longValue = Long.parseLong(longStr);
				} catch(NumberFormatException e) {
					longValue = null;
				}
				this.type = "long";
				this.value = longValue;
			} else if(this.id.matches(REGEX_FLOAT_STR)) {
				OLog.info("Found possible float");
				String floatStr = this.id.replace("f", "");
				Float floatValue = null;
				try {
					floatValue = Float.parseFloat(floatStr);
				} catch(NumberFormatException e) {
					floatValue = null;
				}

				this.type = "float";
				this.value = floatValue;
			} else if(this.id.matches(REGEX_DOUBLE_STR)) {
				OLog.info("Found possible double");
				Double doubleValue = null;
				
				try {
					doubleValue = Double.parseDouble(this.id);
				} catch(NumberFormatException e) {
					doubleValue = null;
				}
				this.type = "double";
				this.value = doubleValue;
			}
			
			return this;
		}
	}
	
	/**
	 * @param args
	 */
//	public static void main(String[] args) {
//		
//		DataStore ds = new DataStore();
//		ds.add("currentVersion", "string", "spoopy");
//		ds.add("timeSinceLastReading", "long", 1242134555l);
//		
//		ConditionEvaluator condEval = new ConditionEvaluator();
//		condEval.setDataStore(ds);
////		condEval.setMethodEvaluator(new MethodEvaluatorTest()); //TODO 
//		
//		condEval.evaluate("$timeSinceLastReading <= 1242134555l; " +
//				"getCurrentVersion() == \"spoopy\";");
//	}
}
