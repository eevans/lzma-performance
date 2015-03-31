package org.wikimedia.test;

import static org.wikimedia.test.TestCompressor.WARM_UP_PASSES;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import SevenZip.Compression.LZMA.Encoder;

public class SevenZipTestCompressor {

	// Hack encoder options here
	private static void configureEncoder(Encoder encoder) {
		encoder.SetMatchFinder(0); // one of 0, 1, 2 for bt2, bt4, bt4b respectively
	}

	public static void main(String... args) throws IOException {
		Config config = new Config();
		CmdLineParser parser = new CmdLineParser(config);
		parser.getProperties().withUsageWidth(80);

		try {
			parser.parseArgument(args);
		}
		catch (CmdLineException e) {
			System.err.println(e.getMessage());
			System.err.printf("java %s -i INPUT -n MULTIPLE%n", SevenZipTestCompressor.class.getSimpleName());
			System.err.println();
			parser.printUsage(System.err);
			System.err.println();
			System.exit(1);
		}

		if (config.debug)
			debug("Doing %d warm-up passes", WARM_UP_PASSES);

		for (int i = 0; i < WARM_UP_PASSES; i++)
			doRuns(config);

		for (Run run : doRuns(config))
			System.out.printf(
					"Compressed %s %d bytes to %d bytes (%.4f) in %.8f ms%n",
					run.name,
					run.length,
					run.outLength,
					run.getRatio(),
					run.elapsed);

	}

	private static Run[] doRuns(Config config) throws IOException {
		Run[] runs = new Run[config.multiple];
		File[] inputs = getInputFiles(config.input, config.multiple);

		if (config.debug) {
			for (File i : inputs)
				debug("Input file %s, %d bytes", i.getName(), i.length());
		}

		int runsIndex = 0;
		for (File input : inputs) {
			File output = new File(input.getParent(), String.format("%s.7zip", input.getName()));
			try (BufferedInputStream inStream = new BufferedInputStream(new FileInputStream(input));
					BufferedOutputStream outStream = new BufferedOutputStream(new FileOutputStream(output))) {
				Encoder encoder = new Encoder();
				configureEncoder(encoder);
				long start, end;

				start = System.nanoTime();

				encoder.SetEndMarkerMode(true);
				encoder.WriteCoderProperties(outStream);

				for (int i = 0; i < 8; i++)
					outStream.write((int) (-1 >>> (8 * i)) & 0xFF);

				encoder.Code(inStream, outStream, -1, -1, null);

				end = System.nanoTime();

				outStream.flush();

				double elapsed = (double) (end - start) / 1e6;
				runs[runsIndex++] = new Run(input.getName(), input.length(), output.length(), elapsed);
			}
		}

		return runs;
	}

	static void debug(String msg, Object... args) {
		System.err.printf("[D]: " + msg + "%n", args);
	}

	/** Fill dst w/ src, times times */
	private static void fill(File src, File dst, int times) throws IOException {
		try (FileOutputStream out = new FileOutputStream(dst)) {
			for (int i = 0; i < times; i++) {
				try (FileInputStream in = new FileInputStream(src)) {
					byte[] buf = new byte[8192];
					int size;
					while ((size = in.read(buf)) != -1)
						out.write(buf, 0, size);
				}
			}
		}
	}

	private static File[] getInputFiles(String base, int num) throws IOException {
		File[] inputs = new File[num];
		inputs[0] = new File(base);
		for (int i = 1; i < num; i++) {
			String name = String.format("%s_x%d", inputs[0].getName(), i + 1);
			inputs[i] = new File(inputs[0].getParent(), name);
			fill(inputs[0], inputs[i], i + 1);
			inputs[i].deleteOnExit();
		}
		return inputs;
	}

	private static class Run {
		private String name;
		private long length;
		private long outLength;
		private double elapsed;

		private Run(String name, long length, long outLength, double elapsed) {
			this.name = name;
			this.length = length;
			this.outLength = outLength;
			this.elapsed = elapsed;
		}

		private double getRatio() {
			return (double) outLength / length;
		}

	}

	static class Config {
		@Option(name = "-i", aliases = { "--input-file" }, usage = "Input file", metaVar = "INPUT", required = true)
		String input;
		@Option(name = "-n", aliases = { "--multiple" }, usage = "Multiple", metaVar = "MULTIPLE", required = true)
		int multiple;
		@Option(name = "-D", aliases = { "--debug" }, usage = "Enable debug output")
		boolean debug = false;
	}

}
