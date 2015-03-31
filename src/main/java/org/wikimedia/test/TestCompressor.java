package org.wikimedia.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.UnsupportedOptionsException;
import org.tukaani.xz.XZOutputStream;

public class TestCompressor {

	public static final int WARM_UP_PASSES = 25;

	// Hack compression options here
	// See: http://tukaani.org/xz/xz-javadoc/org/tukaani/xz/LZMA2Options.html
	private static LZMA2Options getLZMA2Options() throws UnsupportedOptionsException {
		LZMA2Options options = new LZMA2Options(1);
		options.setNiceLen(8);
		options.setDictSize(2 * 1024 * 1024);
		return options;
	}

	// Usage: java TestCompressor <lzma|gzip> <input-file> <multiplier>
	public static void main(String... args) throws IOException {
		Config cfg = null;
		try {
			cfg = new Config(args);
		}
		catch (Exception e) {
			System.err.println(e.getMessage());
			System.err
					.printf("Usage: java %s <lzma|gzip> <input> <multiplier>%n", TestCompressor.class.getSimpleName());
			System.exit(1);
		}

		// Warm up
		for (int i = 0; i < WARM_UP_PASSES; i++)
			doRun(cfg);

		Result[] results = doRun(cfg);

		for (Result r : results) {
			System.out.printf(
					"Compressed %s (%d bytes) to %d bytes (%.4f) in %.8f ms%n",
					r.label,
					r.inLen,
					r.outLen,
					r.ratio,
					r.elapsed);
//			System.out.printf(
//					"%s,%d,%d,%.4f,%.8f%n",
//					r.label,
//					r.inLen,
//					r.outLen,
//					r.ratio,
//					r.elapsed);
		}

	}

	private static OutputStream outputStream(Scheme scheme, OutputStream inner) throws IOException {
		switch (scheme) {
		case LZMA:
			return new XZOutputStream(inner, getLZMA2Options());
		case GZIP:
			return new GZIPOutputStream(inner);
		default:
			throw new RuntimeException();
		}
	}

	private static Result[] doRun(Config cfg) throws IOException {
		Result[] results = new Result[cfg.multiple];

		for (int i = 1; i <= cfg.multiple; i++) {
			File outFile = new File(String.format("%s.%d.%s", cfg.inFile.getAbsolutePath(), i, cfg.scheme));

			try (OutputStream outStream = outputStream(cfg.scheme, new FileOutputStream(outFile))) {
				long start = System.nanoTime(), end;
				for (int j = 0; j < i; j++) {
					try (FileInputStream inStream = new FileInputStream(cfg.inFile)) {
						byte[] buf = new byte[8192];
						int size;
						while ((size = inStream.read(buf)) != -1) {
							outStream.write(buf, 0, size);
						}
					}
				}
				end = System.nanoTime();
				outStream.flush();
				long inLen = cfg.inFile.length() * i, outLen = outFile.length();

				results[i - 1] = new Result(
						String.format("%s.x%d", cfg.inFile.getName(), i),
						inLen,
						outLen,
						(double) outLen / inLen,
						(double) (end - start) / 1e6);
			}
		}

		return results;
	}

	static enum Scheme {
		LZMA, GZIP;
	}

	static class Config {
		Scheme scheme;
		File inFile;
		int multiple;

		Config(String[] args) {
			if (args.length != 3)
				throw new IllegalArgumentException("wrong number of arguments");

			try {
				this.scheme = Scheme.valueOf(args[0].toUpperCase());
			}
			catch (IllegalArgumentException e) {
				throw new IllegalArgumentException(String.format("unknown compression scheme: %s", args[0]));
			}

			this.inFile = new File(args[1]);

			if (!this.inFile.exists())
				throw new IllegalArgumentException(String.format("%s: no such file", args[1]));

			try {
				this.multiple = Integer.parseInt(args[2]);
			}
			catch (NumberFormatException e) {
				throw new IllegalArgumentException(String.format("%s is not a number", args[2]));
			}
		}

	}

	static class Result {
		final String label;
		final long inLen;
		final long outLen;
		final double ratio;
		final double elapsed;
		
		Result(String label, long inLen, long outLen, double ratio, double elapsed) {
			this.label = label;
			this.inLen = inLen;
			this.outLen = outLen;
			this.ratio = ratio;
			this.elapsed = elapsed;
		}

	}

}
