package ru.ifmo.ctddev.dyadyushkin.walk;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public class Walk implements FileVisitor<Path> {
	private final static int fnv_primal = 0x01000193;
	private PrintWriter out;

	private static int fnv(InputStream in) throws IOException {
		int hash = 0x811c9dc5;
		int len;
		byte[] buffer = new byte[4096];

		while((len = in.read(buffer)) >= 0) {
			for (int i = 0; i < len; i++) {
				hash *= fnv_primal;
				hash ^= buffer[i];
			}
		}
		in.close();
		return hash;
	}

	private void setOutput(PrintWriter out) {
		this.out = out;
	}

	private void writeResult(int hash, String path) {
		out.printf("%08x %s\n", hash, path);
	}

	@Override
	public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
		writeResult(0, dir.toString());
		return FileVisitResult.TERMINATE;
	}

	@Override
	public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
		int hash = 0;

		try {
			InputStream in = new BufferedInputStream(new FileInputStream(path.toString()));
			hash = fnv(in);
		}
		catch (Exception e) {
			System.err.printf("Error during processing file: %s\n", path);
		}

		writeResult(hash, path.toString());
		return FileVisitResult.CONTINUE;
	}

	@Override
	public FileVisitResult visitFileFailed(Path file, IOException exc) {
		return FileVisitResult.CONTINUE;
	}

	@Override
	public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
		return FileVisitResult.CONTINUE;
	}

	protected void walk(String line) {
		try {
			Path path = Paths.get(line);
			if (Files.exists(path)) {
				try {
					Files.walkFileTree(path, this);
					return;
				}
				catch (IOException e) {
					System.err.printf("Error during processing path: %s\n", path);
					System.err.println(e);
				}
			}
		}
		catch (InvalidPathException e) {
			System.err.printf("Invalid path: %s\n", line);
		}
		writeResult(0, line);
	}

    public static void handler(String[] args, Walk walker) {
	    if (args.length != 2) {
	        System.err.println("Too many or too little number of arguments were specified.");
	        return;
        }
        String inputPath  = args[0];
	    String outputPath = args[1];
		try (BufferedReader in = new BufferedReader(new FileReader(inputPath))) {
			try (PrintWriter out = new PrintWriter(outputPath, "utf-8")) {
				String s = "";

				walker.setOutput(out);
				try {
					while ((s = in.readLine()) != null) {
						if (s.length() == 0) {
							continue;
						}
						walker.walk(s);
					}
				}
				catch (IOException e) {
					System.err.printf("I/O error during on: %s\n", s);
				}
				out.close();
			}
			catch (FileNotFoundException e) {
				System.err.printf("Output file cannot be created: %s\n", outputPath);
			}
			catch (IOException e) {
				System.err.printf("I/O error on file: %s\n", outputPath);
			}
		}
		catch (FileNotFoundException e) {
			System.err.printf("Input file not found: %s\n", inputPath);
		}
		catch (IOException e) {
			System.err.printf("I/O error on file: %s\n", inputPath);
		}
    }

    public static void main(String... args) {
		long start = System.currentTimeMillis();
		Walk w = new Walk();
		handler(args, w);
		long stop = System.currentTimeMillis();
		System.out.println(stop - start);
	}
}
