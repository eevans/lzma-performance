package org.wikimedia.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;

import org.tukaani.xz.XZInputStream;
import org.wikimedia.test.TestCompressor.Config;
import org.wikimedia.test.TestCompressor.Result;
import org.wikimedia.test.TestCompressor.Scheme;

public class TestDecompressor {

	// Usage: java TestDecompressor <lzma|gzip> <base-file> <multiples>
	public static void main(String[] args) throws FileNotFoundException, IOException {
		Config cfg = null;
		try {
			cfg = new Config(args);
		}
		catch (Exception e) {
			System.err.println(e.getMessage());
			System.err
					.printf("Usage: java %s <lzma|gzip> <input> <multiplier>%n", TestDecompressor.class.getSimpleName());
		}

		// Warm up
		for (int i = 0; i < TestCompressor.WARM_UP_PASSES; i++)
			doRun(cfg);

		Result[] results = doRun(cfg);

		for (Result r : results) {
			System.out.printf("Decompressed %s in %.8f ms (%d bytes)%n", r.label, r.elapsed, r.outLen);
//			System.out.printf("%s,%.8f%n", r.label, r.elapsed);
		}

	}

	private static InputStream inputStream(Scheme scheme, InputStream inner) throws IOException {
		switch (scheme) {
		case LZMA:
			return new XZInputStream(inner);
		case GZIP:
			return new GZIPInputStream(inner);
		default:
			throw new RuntimeException();
		}
	}

	private static Result[] doRun(Config cfg) throws FileNotFoundException, IOException {
		Result[] results = new Result[cfg.multiple];

		for (int i = 1; i <= cfg.multiple; i++) {
			File input = new File(String.format("%s.%d.%s", cfg.inFile.getAbsolutePath(), i, cfg.scheme));
			
			try (
					InputStream  inStream  = inputStream(cfg.scheme, new FileInputStream(input));
					OutputStream outStream = new FileOutputStream(new File("/dev/null"))
			) {
				byte[] buf = new byte[8192];
				int size, total = 0;
				long start = System.nanoTime();
				while ((size = inStream.read(buf)) != -1) {
					outStream.write(buf, 0, size);
					total += size;
				}
				long end = System.nanoTime();

				results[i - 1] = new Result(
						String.format("%s.x%d.%s", cfg.inFile.getName(), i, cfg.scheme),
						-1L,
						total,
						-1.0d,
						(double) (end - start) / 1e6);
			}

		}

		return results;
	}

}
