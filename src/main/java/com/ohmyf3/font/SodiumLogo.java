package com.ohmyf3.font;

import java.util.Base64;

/**
 * Sodium's logo mark.
 *
 * <p>Sodium does not draw its logo in code: its video settings screen blits
 * {@code sodium-icon.png} with a tinted blit (see {@code VideoSettingsScreen#renderIconWithSpacing}
 * in the Sodium sources). So this is a monochrome 48x48 coverage mask traced from that icon
 * (white mark, cropped square) and embedded here, which keeps the shape pixel-accurate and
 * avoids shipping another mod's asset.
 *
 * <p>Icons are drawn at whatever size the current device scale needs, so the mask is sampled
 * bilinearly rather than scaled with nearest neighbour.
 */
public final class SodiumLogo {
	/** Edge length of the embedded mask. */
	public static final int SIZE = 48;

	private static final String MASK_BASE64 = "AAAAAAAAAAAAAAAAAAAAAAAAAAADAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAIBACgAAgAA"
			+ "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQIAov9PAAUAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
			+ "AAAAAAAAAAAAAAAAAAABAwCh//j/WQAFAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAIDAKT/+v/8/1wA"
			+ "BQAAAAAAAAECAgEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQEArP/6///+/f9bAAUAAAAAAAEAAAAAAAAAAAAAAAAAAAAA"
			+ "AAAAAAAAAAAAAAABBgWn//r//////fv/YgACAAABAwCWrQIDAgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEAI7/+f////////78"
			+ "/1wCBAEEAJT//64AAQEAAAAAAAAAAAAAAAAAAAAAAAAAAAIALPP6/v/////////9/fgWAAYAjf/6+v+vBQMBAAAAAAAAAAAA"
			+ "AAAAAAAAAAAAAgQ08P79/////////////P60BQBx//v///r/oAEEAAAAAAAAAAAAAAAAAAAAAAAABADH//v/////////////"
			+ "//v/rjb6/P7////6/0sABAAAAAAAAAAAAAAAAAAAAAAEAIf6+//////////////////9//j//v/////+/vAxAQIAAAAAAAAA"
			+ "AAAAAAAAAAMARv///////////////////////f/+////////+/+zAAYAAAAAAAAAAAAAAAAAAgQX1Pr8//////////7/////"
			+ "//////7///////////v+eQACAAAAAAAAAAAAAAAAAwBr//v////////////8//////////////////////7+7RwAAQAAAAAA"
			+ "AAAAAAACACn6/f7////////+/fT////////////////////////6/5IEBQAAAAAAAAAAAAADA6f/+/////////v/qgzO/v3/"
			+ "/////////////////////vgcAgIAAAAAAAAAAAIAMP/9/////////v/wKgA5//z+/////////////////////P+cAAMAAAAA"
			+ "AAAAAAMAsP/8///////++ucwAAYAdv/7/////////////v///////vztLAEDAAAAAAAAAwAx7/3+///////9/0EABAAFAIb/"
			+ "+v/////////9//////////v/kgMEAAAAAAAABQFw//r///////z+pwkFAQABAwnS//z///////7/9v7+//////7+/A0AAQAA"
			+ "AAABAAfi/v3//////v7pAAAAAAAAAgAn4v79/////P+rRv/9///////9/XgBBAAAAAABABv7//7//////P9lAQQAAAAAAAMA"
			+ "Jfj8///8/s0DAL3+/P/////6/6QABgAAAAAEAIz//P/////8/9sNAwEAAAAAAAAFAID/+v39+j0AAD3//v/////9/9kfAgIA"
			+ "AAAFAKj/+//////8/3YAAwAAAAAAAAABBACZ//r/WAAFAA3s//7/////+/9EAAMAAAAFAKP/+////////xABAQAAAAAAAAAA"
			+ "AQQAr/9cAAQABAGD//v/////+/1AAAMAAAAFAKT/+////////x8AAQAAAAAAAAAAAAIBMVEABQAABQB0//r/////+/9BAAMA"
			+ "AAAFAKT/+////////xwAAQAAAAAAAAAAAAABAAACAAAABQB4//r/////+/9BAAMAAAAFAKP/+////////x0AAQAAAAAAAAAA"
			+ "AAAAAgQAAAAABQB4//r/////+/5BAAMAAAAFAKf/+////////xcAAQAAAAAAAAAAAAAAAAAAAAAABQBx//r/////+/9BAAMA"
			+ "AAADAFv7/v/////8/5QABAAAAAAAAAAAAAAAAAAAAAABAQrL//3////9/9gbAQIAAAABAAj7//7////7/8IGBQAAAAAAAAAA"
			+ "AAAAAAAAAAABAiP////////6/6UABQAAAAABAArZ/v3/////+vtGAAUAAAAAAAAAAAAAAAAAAAAGAKH+/P/////9/YkBBAAA"
			+ "AAAABABw//r//////f/PEgADAAAAAAAAAAAAAAAAAAUATfL7/v/////+/xYAAQAAAAAAAgIo6vz9//////z/1BkABQEAAAAA"
			+ "AAAAAAABBQAs6//+//////v/pwMDAAAAAAAAAAMAkf/8///////7/9UXAAAFBQMAAAADBQIAAE3n//3//////vrxOQEEAAAA"
			+ "AAAAAAICF/f8/v//////+//egxcAAAABAgIAAAAgl////f///////P+zAAMAAAAAAAAAAAAEA2z/+v////////3///N3dEcA"
			+ "AABCdZX///7+///////+/fIcAgIAAAAAAAAAAAAAAQbI//r////////9/f////7Y19f7/////P7////////6/24AAwAAAAAA"
			+ "AAAAAAAAAQEPw//7//////////76+v//////+vv//////////vr/egACAAAAAAAAAAAAAAAAAAIACsf/+v////////////79"
			+ "/f3+/////////////P9zAAYAAAAAAAAAAAAAAAAAAAADAAq///r+//////////////////////////z7/3YABQAAAAAAAAAA"
			+ "AAAAAAAAAAAAAgALvv///P7////////////////////++///cAAFAAAAAAAAAAAAAAAAAAAAAAAAAAIABXnx///7+///////"
			+ "///////++v3//81HAAQAAAAAAAAAAAAAAAAAAAAAAAAAAAABAQAtjOr///3+/fz8/Pz8/f3////hWAcAAwAAAAAAAAAAAAAA"
			+ "AAAAAAAAAAAAAAAAAAQAABWGqf/////////////zimMAAAACAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACAgAAACkner/D"
			+ "wcOzTighAAAAAwEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEFAQAAAAAAAAAAAAAABQQAAAAAAAAAAAAAAAAAAAAA";

	private static byte[] mask;

	private SodiumLogo() {
	}

	/** Coverage at normalised coordinates {@code u}, {@code v} in {@code [0, 1)}, 0-255. */
	public static int coverage(float u, float v) {
		byte[] data = mask();

		float x = Math.max(0, Math.min(SIZE - 1.001F, u * SIZE - 0.5F));
		float y = Math.max(0, Math.min(SIZE - 1.001F, v * SIZE - 0.5F));

		int x0 = (int) x;
		int y0 = (int) y;
		int x1 = Math.min(SIZE - 1, x0 + 1);
		int y1 = Math.min(SIZE - 1, y0 + 1);
		float fx = x - x0;
		float fy = y - y0;

		float top = data[y0 * SIZE + x0] * (1 - fx) + data[y0 * SIZE + x1] * fx;
		float bottom = data[y1 * SIZE + x0] * (1 - fx) + data[y1 * SIZE + x1] * fx;

		return Math.round(top * (1 - fy) + bottom * fy);
	}

	private static byte[] mask() {
		if (mask == null) {
			mask = Base64.getDecoder().decode(MASK_BASE64);
		}

		return mask;
	}
}
