package com.ohmyf3.font;

import java.util.ArrayList;
import java.util.List;

/**
 * A small SVG path parser and flattener.
 *
 * <p>Supports the subset used by the Lucide icon set: {@code M m L l H h V v C c S s Q q T t
 * A a Z z}, implicit line segments after a moveto, repeated coordinate sets after any
 * command, and the usual "sign acts as a separator" number syntax ({@code "4-4"} is two
 * numbers). Curves and elliptical arcs are flattened into polylines so the caller only has
 * to deal with line segments.
 *
 * <p>Coordinates stay in the icon's own space (Lucide uses a 24x24 viewBox).
 */
public final class SvgPath {
	private SvgPath() {
	}

	/** Parses path data into flattened polylines. */
	public static List<float[]> parse(String data) {
		Parser parser = new Parser(data);
		return parser.run();
	}

	/** A polyline approximation of a circle. */
	public static float[] circle(float cx, float cy, float radius) {
		int steps = 48;
		float[] points = new float[steps * 2 + 2];

		for (int i = 0; i <= steps; i++) {
			double angle = 2 * Math.PI * i / steps;
			points[i * 2] = cx + (float) Math.cos(angle) * radius;
			points[i * 2 + 1] = cy + (float) Math.sin(angle) * radius;
		}

		return points;
	}

	/** A polyline approximation of a straight segment. */
	public static float[] line(float x1, float y1, float x2, float y2) {
		return new float[]{x1, y1, x2, y2};
	}

	/** A polyline approximation of a (optionally rounded) rectangle. */
	public static float[] rect(float x, float y, float width, float height, float radius) {
		float r = Math.max(0, Math.min(radius, Math.min(width, height) * 0.5F));
		List<Float> points = new ArrayList<>();

		if (r <= 0) {
			points.add(x);
			points.add(y);
			points.add(x + width);
			points.add(y);
			points.add(x + width);
			points.add(y + height);
			points.add(x);
			points.add(y + height);
			points.add(x);
			points.add(y);
		} else {
			// Counter-clockwise from the top-left corner's end, one quarter arc per corner.
			corner(points, x + r, y + r, r, 180, 270);
			corner(points, x + width - r, y + r, r, 270, 360);
			corner(points, x + width - r, y + height - r, r, 0, 90);
			corner(points, x + r, y + height - r, r, 90, 180);
			points.add(points.get(0));
			points.add(points.get(1));
		}

		float[] result = new float[points.size()];

		for (int i = 0; i < result.length; i++) {
			result[i] = points.get(i);
		}

		return result;
	}

	private static void corner(List<Float> points, float cx, float cy, float radius, float fromDegrees,
			float toDegrees) {
		int steps = 8;

		for (int i = 0; i <= steps; i++) {
			double angle = Math.toRadians(fromDegrees + (toDegrees - fromDegrees) * i / steps);
			points.add(cx + (float) Math.cos(angle) * radius);
			points.add(cy + (float) Math.sin(angle) * radius);
		}
	}

	// ---------------------------------------------------------------- parser

	private static final class Parser {
		private final String data;
		private int index;

		private float currentX;
		private float currentY;
		private float startX;
		private float startY;
		private float lastControlX;
		private float lastControlY;
		private char lastCommand;

		private final List<float[]> polylines = new ArrayList<>();
		private List<Float> current;

		Parser(String data) {
			this.data = data;
		}

		List<float[]> run() {
			while (true) {
				skipSeparators();

				if (this.index >= this.data.length()) {
					break;
				}

				char command = this.data.charAt(this.index);

				if (Character.isLetter(command)) {
					this.index++;
				} else if (this.lastCommand == 0) {
					throw new IllegalArgumentException("path data must start with a command");
				} else {
					// Repeated coordinate set: reuse the previous command.
					command = this.lastCommand == 'M' ? 'L' : this.lastCommand == 'm' ? 'l' : this.lastCommand;
				}

				this.lastCommand = command;

				switch (command) {
					case 'M' -> moveTo(number(), number(), false);
					case 'm' -> moveTo(number(), number(), true);
					case 'L' -> lineTo(number(), number(), false);
					case 'l' -> lineTo(number(), number(), true);
					// h/v are single-axis: only the commanded axis is relative, the other one
					// must stay unterminated (treating both as relative doubles it).
					case 'H' -> lineTo(number(), this.currentY, false);
					case 'h' -> lineTo(this.currentX + number(), this.currentY, false);
					case 'V' -> lineTo(this.currentX, number(), false);
					case 'v' -> lineTo(this.currentX, this.currentY + number(), false);
					case 'C' -> {
						float x1 = number();
						float y1 = number();
						float x2 = number();
						float y2 = number();
						float x = number();
						float y = number();
						cubicTo(x1, y1, x2, y2, x, y);
					}
					case 'c' -> {
						float x1 = this.currentX + number();
						float y1 = this.currentY + number();
						float x2 = this.currentX + number();
						float y2 = this.currentY + number();
						float x = this.currentX + number();
						float y = this.currentY + number();
						cubicTo(x1, y1, x2, y2, x, y);
					}
					case 'S' -> {
						float x2 = number();
						float y2 = number();
						float x = number();
						float y = number();
						smoothCubicTo(x2, y2, x, y);
					}
					case 's' -> {
						float x2 = this.currentX + number();
						float y2 = this.currentY + number();
						float x = this.currentX + number();
						float y = this.currentY + number();
						smoothCubicTo(x2, y2, x, y);
					}
					case 'Q' -> {
						float qx = number();
						float qy = number();
						float x = number();
						float y = number();
						quadTo(qx, qy, x, y);
					}
					case 'q' -> {
						float qx = this.currentX + number();
						float qy = this.currentY + number();
						float x = this.currentX + number();
						float y = this.currentY + number();
						quadTo(qx, qy, x, y);
					}
					case 'T', 't' -> {
						float x = command == 'T' ? number() : this.currentX + number();
						float y = command == 'T' ? number() : this.currentY + number();
						// Reflect the previous control point for a smooth quadratic.
						float qx = 2 * this.currentX - this.lastControlX;
						float qy = 2 * this.currentY - this.lastControlY;
						quadTo(qx, qy, x, y);
					}
					case 'A', 'a' -> {
						float rx = number();
						float ry = number();
						float rotation = number();
						// Arc flags are single characters, not general numbers: "00-2.474"
						// is flag 0, flag 0, then -2.474.
						boolean largeArc = flag();
						boolean sweep = flag();
						float x = number();
						float y = number();

						if (command == 'a') {
							x += this.currentX;
							y += this.currentY;
						}

						arcTo(rx, ry, rotation, largeArc, sweep, x, y);
					}
					case 'Z', 'z' -> {
						this.currentX = this.startX;
						this.currentY = this.startY;
						append(this.currentX, this.currentY);
						finishPolyline();
					}
					default -> throw new IllegalArgumentException("unsupported path command: " + command);
				}
			}

			finishPolyline();
			return this.polylines;
		}

		private void moveTo(float x, float y, boolean relative) {
			if (relative) {
				x += this.currentX;
				y += this.currentY;
			}

			finishPolyline();
			this.currentX = x;
			this.currentY = y;
			this.startX = x;
			this.startY = y;
			this.current = new ArrayList<>();
			append(x, y);
		}

		private void lineTo(float x, float y, boolean relative) {
			if (relative) {
				x += this.currentX;
				y += this.currentY;
			}

			ensurePolyline();
			this.currentX = x;
			this.currentY = y;
			append(x, y);
		}

		private void cubicTo(float x1, float y1, float x2, float y2, float x, float y) {
			ensurePolyline();
			float sx = this.currentX;
			float sy = this.currentY;
			float length = distance(sx, sy, x1, y1) + distance(x1, y1, x2, y2) + distance(x2, y2, x, y);
			int steps = Math.max(6, Math.min(48, Math.round(length)));

			for (int i = 1; i <= steps; i++) {
				float t = (float) i / steps;
				float inverse = 1 - t;
				float px = inverse * inverse * inverse * sx + 3 * inverse * inverse * t * x1
						+ 3 * inverse * t * t * x2 + t * t * t * x;
				float py = inverse * inverse * inverse * sy + 3 * inverse * inverse * t * y1
						+ 3 * inverse * t * t * y2 + t * t * t * y;
				append(px, py);
			}

			this.lastControlX = x2;
			this.lastControlY = y2;
			this.currentX = x;
			this.currentY = y;
		}

		private void smoothCubicTo(float x2, float y2, float x, float y) {
			float x1 = 2 * this.currentX - this.lastControlX;
			float y1 = 2 * this.currentY - this.lastControlY;
			cubicTo(x1, y1, x2, y2, x, y);
		}

		private void quadTo(float qx, float qy, float x, float y) {
			ensurePolyline();
			float sx = this.currentX;
			float sy = this.currentY;
			float length = distance(sx, sy, qx, qy) + distance(qx, qy, x, y);
			int steps = Math.max(6, Math.min(48, Math.round(length)));

			for (int i = 1; i <= steps; i++) {
				float t = (float) i / steps;
				float inverse = 1 - t;
				float px = inverse * inverse * sx + 2 * inverse * t * qx + t * t * x;
				float py = inverse * inverse * sy + 2 * inverse * t * qy + t * t * y;
				append(px, py);
			}

			this.lastControlX = qx;
			this.lastControlY = qy;
			this.currentX = x;
			this.currentY = y;
		}

		/** Endpoint parameterisation of an SVG elliptical arc, flattened to a polyline. */
		private void arcTo(float rx, float ry, float rotationDegrees, boolean largeArc, boolean sweep, float x,
				float y) {
			ensurePolyline();

			float x1 = this.currentX;
			float y1 = this.currentY;

			if (x1 == x && y1 == y) {
				return;
			}

			float rxAbs = Math.abs(rx);
			float ryAbs = Math.abs(ry);

			if (rxAbs == 0 || ryAbs == 0) {
				lineTo(x, y, false);
				return;
			}

			double phi = Math.toRadians(rotationDegrees % 360.0);
			double cosPhi = Math.cos(phi);
			double sinPhi = Math.sin(phi);

			double dx2 = (x1 - x) / 2.0;
			double dy2 = (y1 - y) / 2.0;
			double x1p = cosPhi * dx2 + sinPhi * dy2;
			double y1p = -sinPhi * dx2 + cosPhi * dy2;

			double lambda = (x1p * x1p) / (rxAbs * rxAbs) + (y1p * y1p) / (ryAbs * ryAbs);

			if (lambda > 1) {
				double scale = Math.sqrt(lambda);
				rxAbs *= (float) scale;
				ryAbs *= (float) scale;
			}

			double rxSquared = (double) rxAbs * rxAbs;
			double rySquared = (double) ryAbs * ryAbs;
			double numerator = rxSquared * rySquared - rxSquared * y1p * y1p - rySquared * x1p * x1p;
			double denominator = rxSquared * y1p * y1p + rySquared * x1p * x1p;
			double factor = Math.sqrt(Math.max(0, numerator / denominator));

			if (largeArc == sweep) {
				factor = -factor;
			}

			double cxp = factor * rxAbs * y1p / ryAbs;
			double cyp = -factor * ryAbs * x1p / rxAbs;

			double cx = cosPhi * cxp - sinPhi * cyp + (x1 + x) / 2.0;
			double cy = sinPhi * cxp + cosPhi * cyp + (y1 + y) / 2.0;

			double startAngle = angle(1, 0, (x1p - cxp) / rxAbs, (y1p - cyp) / ryAbs);
			double endAngle = angle((x1p - cxp) / rxAbs, (y1p - cyp) / ryAbs, (-x1p - cxp) / rxAbs,
					(-y1p - cyp) / ryAbs);
			double delta = endAngle;

			if (!sweep && delta > 0) {
				delta -= 2 * Math.PI;
			} else if (sweep && delta < 0) {
				delta += 2 * Math.PI;
			}

			int steps = Math.max(8, (int) Math.ceil(Math.abs(delta) * Math.max(rxAbs, ryAbs) / 2));

			for (int i = 1; i <= steps; i++) {
				double theta = startAngle + delta * i / steps;
				double px = cosPhi * rxAbs * Math.cos(theta) - sinPhi * ryAbs * Math.sin(theta) + cx;
				double py = sinPhi * rxAbs * Math.cos(theta) + cosPhi * ryAbs * Math.sin(theta) + cy;
				append((float) px, (float) py);
			}

			this.currentX = x;
			this.currentY = y;
		}

		private static double angle(double ux, double uy, double vx, double vy) {
			double dot = ux * vx + uy * vy;
			double length = Math.sqrt(ux * ux + uy * uy) * Math.sqrt(vx * vx + vy * vy);

			if (length == 0) {
				return 0;
			}

			double value = Math.acos(Math.max(-1, Math.min(1, dot / length)));
			return ux * vy - uy * vx < 0 ? -value : value;
		}

		private void ensurePolyline() {
			if (this.current == null) {
				this.current = new ArrayList<>();
				append(this.currentX, this.currentY);
			}
		}

		private void append(float x, float y) {
			if (this.current == null) {
				this.current = new ArrayList<>();
			}

			this.current.add(x);
			this.current.add(y);
		}

		private void finishPolyline() {
			if (this.current != null && this.current.size() >= 4) {
				float[] points = new float[this.current.size()];

				for (int i = 0; i < points.length; i++) {
					points[i] = this.current.get(i);
				}

				this.polylines.add(points);
			}

			this.current = null;
		}

		private void skipSeparators() {
			while (this.index < this.data.length()) {
				char c = this.data.charAt(this.index);

				if (c == ',' || Character.isWhitespace(c)) {
					this.index++;
				} else {
					break;
				}
			}
		}

		/** Reads a single-character SVG arc flag ({@code 0} or {@code 1}). */
		private boolean flag() {
			skipSeparators();

			if (this.index < this.data.length()) {
				char c = this.data.charAt(this.index);

				if (c == '0' || c == '1') {
					this.index++;
					return c == '1';
				}
			}

			throw new IllegalArgumentException("expected an arc flag at index " + this.index + " in " + this.data);
		}

		private float number() {
			skipSeparators();
			int start = this.index;

			if (this.index < this.data.length() && (this.data.charAt(this.index) == '-' || this.data.charAt(this.index) == '+')) {
				this.index++;
			}

			boolean seenDot = false;

			while (this.index < this.data.length()) {
				char c = this.data.charAt(this.index);

				if (Character.isDigit(c)) {
					this.index++;
				} else if (c == '.' && !seenDot) {
					seenDot = true;
					this.index++;
				} else if ((c == 'e' || c == 'E') && this.index + 1 < this.data.length()) {
					this.index++;

					if (this.data.charAt(this.index) == '-' || this.data.charAt(this.index) == '+') {
						this.index++;
					}
				} else {
					break;
				}
			}

			if (start == this.index) {
				throw new IllegalArgumentException("expected a number at index " + start + " in " + this.data);
			}

			return Float.parseFloat(this.data.substring(start, this.index));
		}

		private static float distance(float x1, float y1, float x2, float y2) {
			float dx = x2 - x1;
			float dy = y2 - y1;
			return (float) Math.sqrt(dx * dx + dy * dy);
		}
	}
}
