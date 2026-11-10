package com.ohmyf3.custom;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;

/**
 * A small evaluator for the custom F3 items' value expressions.
 *
 * <p>Minecraft runs on a JRE, so there is no compiler to hand a real Java snippet to: compiling at
 * runtime would need a JDK the player does not have. This instead supports a Java-like expression
 * subset — literals, arithmetic, comparisons, {@code &&}/{@code ||}, ternary, parentheses and a set
 * of game/time functions — which covers what a debug line usually wants (clock time, position,
 * derived numbers) and fails with a readable message instead of a stack trace.
 *
 * <p>Evaluation is pure and total: a malformed expression returns its error text, never throws.
 */
public final class Expression {
	/** Functions a custom item can call. Game state is read lazily, so no level is needed. */
	private static final double ERROR = Double.NaN;

	private final String source;
	private int pos;

	private Expression(String source) {
		this.source = source;
	}

	/** Evaluates {@code source}; returns the value as text, or a short error description. */
	public static String evaluate(String source) {
		if (source == null || source.isBlank()) {
			return "";
		}

		try {
			Expression parser = new Expression(source);
			Object value = parser.parseTernary();
			parser.skipWhitespace();

			if (parser.pos < source.length()) {
				return "<语法错误:" + parser.pos + ">";
			}

			return stringify(value);
		} catch (RuntimeException exception) {
			return "<" + exception.getMessage() + ">";
		}
	}

	private static String stringify(Object value) {
		if (value instanceof Double number) {
			if (number == Math.rint(number) && !Double.isInfinite(number)) {
				return String.valueOf(number.longValue());
			}

			return String.format(Locale.ROOT, "%.2f", number);
		}

		return String.valueOf(value);
	}

	// ---------------------------------------------------------------- grammar

	private Object parseTernary() {
		Object condition = parseOr();

		skipWhitespace();

		if (match('?')) {
			Object whenTrue = parseTernary();
			expect(':');
			Object whenFalse = parseTernary();
			return truthy(condition) ? whenTrue : whenFalse;
		}

		return condition;
	}

	private Object parseOr() {
		Object left = parseAnd();

		while (true) {
			skipWhitespace();

			if (!match("||")) {
				return left;
			}

			Object right = parseAnd();
			left = truthy(left) || truthy(right);
		}
	}

	private Object parseAnd() {
		Object left = parseComparison();

		while (true) {
			skipWhitespace();

			if (!match("&&")) {
				return left;
			}

			Object right = parseComparison();
			left = truthy(left) && truthy(right);
		}
	}

	private Object parseComparison() {
		Object left = parseAdditive();

		while (true) {
			skipWhitespace();
			String operator = matchAny("==", "!=", "<=", ">=", "<", ">");

			if (operator == null) {
				return left;
			}

			Object right = parseAdditive();
			left = compare(operator, left, right);
		}
	}

	private Object compare(String operator, Object left, Object right) {
		if (left instanceof String || right instanceof String) {
			String a = String.valueOf(left);
			String b = String.valueOf(right);
			return switch (operator) {
				case "==" -> a.equals(b);
				case "!=" -> !a.equals(b);
				default -> false;
			};
		}

		double a = number(left);
		double b = number(right);

		return switch (operator) {
			case "==" -> a == b;
			case "!=" -> a != b;
			case "<" -> a < b;
			case "<=" -> a <= b;
			case ">" -> a > b;
			default -> a >= b;
		};
	}

	private Object parseAdditive() {
		Object left = parseMultiplicative();

		while (true) {
			skipWhitespace();

			if (match('+')) {
				Object right = parseMultiplicative();

				// "+" concatenates as soon as one side is text, like Java's string concat.
				if (left instanceof String || right instanceof String) {
					left = stringify(left) + stringify(right);
				} else {
					left = number(left) + number(right);
				}
			} else if (match('-')) {
				left = number(left) - number(parseMultiplicative());
			} else {
				return left;
			}
		}
	}

	private Object parseMultiplicative() {
		Object left = parseUnary();

		while (true) {
			skipWhitespace();

			if (match('*')) {
				left = number(left) * number(parseUnary());
			} else if (match('/')) {
				left = number(left) / number(parseUnary());
			} else if (match('%')) {
				left = number(left) % number(parseUnary());
			} else {
				return left;
			}
		}
	}

	private Object parseUnary() {
		skipWhitespace();

		if (match('!')) {
			return !truthy(parseUnary());
		}

		if (match('-')) {
			return -number(parseUnary());
		}

		return parsePrimary();
	}

	private Object parsePrimary() {
		skipWhitespace();

		if (pos >= source.length()) {
			throw new IllegalStateException("表达式不完整");
		}

		char c = source.charAt(pos);

		if (c == '(') {
			pos++;
			Object value = parseTernary();
			skipWhitespace();
			expect(')');
			return value;
		}

		if (c == '"' || c == '\'') {
			return parseString(c);
		}

		if (Character.isDigit(c) || (c == '.' && pos + 1 < source.length())) {
			return parseNumber();
		}

		if (Character.isLetter(c) || c == '_') {
			String name = parseIdentifier();
			skipWhitespace();

			if (match('(')) {
				return call(name, parseArguments());
			}

			return identifier(name);
		}

		throw new IllegalStateException("无法解析:" + c);
	}

	private List<Object> parseArguments() {
		List<Object> arguments = new java.util.ArrayList<>();
		skipWhitespace();

		if (match(')')) {
			return arguments;
		}

		do {
			arguments.add(parseTernary());
			skipWhitespace();
		} while (match(','));

		expect(')');
		return arguments;
	}

	// ---------------------------------------------------------------- values

	private Object identifier(String name) {
		return switch (name.toLowerCase(Locale.ROOT)) {
			case "x" -> {
				Minecraft minecraft = Minecraft.getInstance();
				yield minecraft.player == null ? 0.0 : minecraft.player.getX();
			}
			case "y" -> {
				Minecraft minecraft = Minecraft.getInstance();
				yield minecraft.player == null ? 0.0 : minecraft.player.getY();
			}
			case "z" -> {
				Minecraft minecraft = Minecraft.getInstance();
				yield minecraft.player == null ? 0.0 : minecraft.player.getZ();
			}
			case "fps" -> (double) Minecraft.getInstance().getFps();
			case "true" -> true;
			case "false" -> false;
			default -> throw new IllegalStateException("未知标识符:" + name);
		};
	}

	private Object call(String name, List<Object> arguments) {
		String function = name.toLowerCase(Locale.ROOT);

		return switch (function) {
			case "time" -> LocalDateTime.now().format(DateTimeFormatter.ofPattern(arguments.isEmpty() ? "HH:mm:ss"
					: String.valueOf(arguments.get(0))));
			case "date" -> LocalDateTime.now().format(DateTimeFormatter.ofPattern(arguments.isEmpty() ? "yyyy-MM-dd"
					: String.valueOf(arguments.get(0))));
			case "fps" -> (double) Minecraft.getInstance().getFps();
			case "day" -> {
				Minecraft minecraft = Minecraft.getInstance();
				yield minecraft.level == null ? 0.0
						: (double) (minecraft.level.getLevelData().getGameTime() / 24000L % 24L);
			}
			case "biome" -> {
				Minecraft minecraft = Minecraft.getInstance();

				if (minecraft.player == null || minecraft.level == null) {
					yield "?";
				}

				yield minecraft.level.getBiome(minecraft.player.blockPosition()).unwrapKey()
						.map(key -> key.identifier().getPath()).orElse("?");
			}
			case "dim" -> {
				Minecraft minecraft = Minecraft.getInstance();
				yield minecraft.level == null ? "?" : minecraft.level.dimension().identifier().getPath();
			}
			case "round" -> (double) Math.round(number(argument(arguments, 0)));
			case "floor" -> Math.floor(number(argument(arguments, 0)));
			case "ceil" -> Math.ceil(number(argument(arguments, 0)));
			case "abs" -> Math.abs(number(argument(arguments, 0)));
			case "rand" -> Math.random() * (arguments.isEmpty() ? 1.0 : number(arguments.get(0)));
			default -> throw new IllegalStateException("未知函数:" + name);
		};
	}

	private static Object argument(List<Object> arguments, int index) {
		if (index >= arguments.size()) {
			throw new IllegalStateException("参数不足");
		}

		return arguments.get(index);
	}

	private static double number(Object value) {
		if (value instanceof Double d) {
			return d;
		}

		if (value instanceof Boolean b) {
			return b ? 1.0 : 0.0;
		}

		try {
			return Double.parseDouble(String.valueOf(value));
		} catch (NumberFormatException exception) {
			throw new IllegalStateException("不是数字:" + value);
		}
	}

	private static boolean truthy(Object value) {
		if (value instanceof Boolean b) {
			return b;
		}

		if (value instanceof Double d) {
			return d != 0.0 && !Double.isNaN(d);
		}

		return value != null && !String.valueOf(value).isEmpty();
	}

	// ---------------------------------------------------------------- lexing

	private String parseIdentifier() {
		int start = pos;

		while (pos < source.length()) {
			char c = source.charAt(pos);

			if (!Character.isLetterOrDigit(c) && c != '_') {
				break;
			}

			pos++;
		}

		return source.substring(start, pos);
	}

	private Double parseNumber() {
		int start = pos;

		while (pos < source.length() && (Character.isDigit(source.charAt(pos)) || source.charAt(pos) == '.')) {
			pos++;
		}

		return Double.parseDouble(source.substring(start, pos));
	}

	private String parseString(char quote) {
		pos++;
		StringBuilder builder = new StringBuilder();

		while (pos < source.length()) {
			char c = source.charAt(pos++);

			if (c == quote) {
				return builder.toString();
			}

			if (c == '\\' && pos < source.length()) {
				char escaped = source.charAt(pos++);

				builder.append(switch (escaped) {
					case 'n' -> '\n';
					case 't' -> '\t';
					default -> escaped;
				});
			} else {
				builder.append(c);
			}
		}

		throw new IllegalStateException("字符串未闭合");
	}

	private void skipWhitespace() {
		while (pos < source.length() && Character.isWhitespace(source.charAt(pos))) {
			pos++;
		}
	}

	private boolean match(char c) {
		skipWhitespace();

		if (pos < source.length() && source.charAt(pos) == c) {
			pos++;
			return true;
		}

		return false;
	}

	private boolean match(String token) {
		skipWhitespace();

		if (source.startsWith(token, pos)) {
			pos += token.length();
			return true;
		}

		return false;
	}

	private String matchAny(String... tokens) {
		for (String token : tokens) {
			if (match(token)) {
				return token;
			}
		}

		return null;
	}

	private void expect(char c) {
		skipWhitespace();

		if (pos >= source.length() || source.charAt(pos) != c) {
			throw new IllegalStateException("缺少:" + c);
		}

		pos++;
	}
}
