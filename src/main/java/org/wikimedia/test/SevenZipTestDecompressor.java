package org.wikimedia.test;

import static org.wikimedia.test.SevenZipTestCompressor.debug;
import static org.wikimedia.test.TestCompressor.WARM_UP_PASSES;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.wikimedia.test.SevenZipTestCompressor.Config;

import SevenZip.Compression.LZMA.Decoder;

public class SevenZipTestDecompressor {

	public static void main(String[] args) throws FileNotFoundException, IOException {
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

		for (Run run : doRuns(config)) {
			System.out.printf("Decompressed %s in %.8f ms%n", run.name, run.elapsed);
		}
	}

	private static Run[] doRuns(Config config) throws FileNotFoundException, IOException {
		Run[] runs = new Run[config.multiple];
		File[] inputs = getInputFiles(config.input, config.multiple);

		if (config.debug) {
			for (File i : inputs)
				debug("Input file %s, %d bytes", i.getName(), i.length());
		}

		int runIndex = 0;
		for (File input : inputs) {
			try (
					BufferedInputStream inStream = new BufferedInputStream(new FileInputStream(input));
					BufferedOutputStream outStream = new BufferedOutputStream(new FileOutputStream("/dev/null"))
			) {
				int propertiesSize = 5;
				byte[] properties = new byte[propertiesSize];
				if (inStream.read(properties, 0, propertiesSize) != propertiesSize)
					throw new RuntimeException("input .lzma file is too short");
				Decoder decoder = new Decoder();
				
				long start, end;
				
				start = System.nanoTime();

				if (!decoder.SetDecoderProperties(properties))
					throw new RuntimeException("Incorrect stream properties");
				long outSize = 0;
				for (int i = 0; i < 8; i++)
				{
					int v = inStream.read();
					if (v < 0)
						throw new RuntimeException("Can't read stream size");
					outSize |= ((long)v) << (8 * i);
				}
				if (!decoder.Code(inStream, outStream, outSize))
					throw new RuntimeException("Error in data stream");
				
				end = System.nanoTime();

				runs[runIndex++] = new Run(input.getName(), (double)(end-start)/1e6);
			}
		}

		return runs;
	}

	private static File[] getInputFiles(String path, int num) {
		File[] inputs = new File[num];
		File base = new File(path);
		File parent = base.getParentFile();
		inputs[0] = new File(parent, String.format("%s.7zip", base.getName()));
		for (int i = 1; i < num; i++)
			inputs[i] = new File(parent, String.format("%s_x%d.7zip", base.getName(), i + 1));
		return inputs;
	}

	private static class Run {
		private String name;
		private double elapsed;
		
		private Run(String name, double elapsed) {
			this.name = name;
			this.elapsed = elapsed;
		}
	}

}
