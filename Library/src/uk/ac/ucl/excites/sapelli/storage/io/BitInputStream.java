package uk.ac.ucl.excites.sapelli.storage.io;


import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.Charset;

/**
 * A stream of bits that can be read. Provides read methods for various (primitive) types.<br/>
 * <br/>
 * Heavily modified/extended version of original work by Nayuki Minase:<br/>
 * 		- Source: <a href="https://github.com/nayuki/Huffman-Coding/blob/master/src/nayuki/huffmancoding/BitInputStream.java">https://github.com/nayuki/Huffman-Coding/blob/master/src/nayuki/huffmancoding/BitInputStream.java</a><br/>
 * 		- License: MIT License<br/>
 * 
 * @author mstevens
 */
public final class BitInputStream extends InputStream
{
	//STATIC
	private static final Charset DEFAULT_CHARSET = Charset.forName("UTF-8");
	private static final Charset UTF16BE = Charset.forName("UTF-16BE");

	//DYNAMIC
	private boolean closed;
	private InputStream input;			// Underlying byte stream to read from
	private int currentByte;			// Buffered bits stored as an int (either in the range 0x00 to 0xFF, or -1 if the end of stream is reached)
	private int numBitsRemaining;		// Always between 0 and 7, inclusive

	private boolean isEndOfStream;

	public BitInputStream(InputStream input)
	{
		if(input == null)
			throw new NullPointerException("Underlying InputStream cannot be null!");
		closed = false;
		this.input = input;
		numBitsRemaining = 0;
		isEndOfStream = false;
	}
	
	/**
	 * @return whether stream is at end (true) or not (false)
	 * @throws IOException if the stream is closed or another I/O error occurs
	 */
	public boolean atEnd() throws IOException
	{
		if(closed)
			throw new IOException("This stream is closed");
		if(isEndOfStream && numBitsRemaining == 0)
			return true;
		if(numBitsRemaining == 0)
		{
			currentByte = input.read();
			if(currentByte == -1)
			{
				isEndOfStream = true;
				return true;
			}
			else
				numBitsRemaining = 8;
		}
		return false;
	}

	/**
	 * Reads a bit from the stream. Returns a boolean (true for 1; false for 0) if a bit is available,
	 * or throws an EOFException if the end of stream is reached.
	 * 
	 * @return the bit that was read (true = 1; false = 0)
	 * @throws IOException if the stream is closed or another I/O error occurs
	 * @throws EOFException when the next bit cannot be read because the end of stream is reached
	 */
	public boolean readBit() throws IOException, EOFException
	{
		if(atEnd()) //also reads a new byte from underlying stream if needed! (will also check for closedness)
			throw new EOFException("End of stream reached");
		numBitsRemaining--;
		return ((currentByte >>> numBitsRemaining) & 1) == 1;
	}

	/**
	 * Reads exactly {@code numberOfBits} of bits from the input stream, and returns them as a boolean[]. 
	 * If not enough bits could be read an {@link EOFException} is thrown.
	 * 
	 * @param numberOfBits number of bits to be read
	 * @return boolean array (of length numberOfBits) with the bits that were read
	 * @throws IOException if the stream is closed or another I/O error occurs
	 * @throws EOFException could not read the requested amount of bits
	 */
	public boolean[] readBits(int numberOfBits) throws EOFException, IOException
	{
		boolean[] bits = new boolean[numberOfBits]; // all bits initialised to 0 (false)
		int n = read(bits);
		if(n < numberOfBits)
			throw new EOFException("Could not read enough bits (requested: " + numberOfBits + "; read: " + Math.max(n, 0) + "), because end of stream was reached");
		return bits;
	}
	
	/**
	 * Equivalent to {@code read(buffer, 0, buffer.length)}.
	 * 
	 * @see java.io.InputStream#read(byte[])
	 */
	public int read(boolean[] buffer) throws IOException
	{
		return read(buffer, 0, buffer.length);
	}
	
	/**
	 * Reads up to {@code bitCount} bytes from this stream and stores them in
     * the boolean array {@code buffer} starting at {@code bitOffset}.
     * Returns the number of bytes actually read or -1 if the end of the stream
     * had been reached before any bit could be read.
     * 
     * The bits are stored in the order or reading (i.e. the first bit that
     * was read from the stream goes in {@code buffer[bitOffset]}).
     *
     * @throws IndexOutOfBoundsException
     *   if {@code bitOffset < 0 || bitCount < 0 || bitOffset + bitCount > buffer.length}.
     * @throws IOException
     *             if the stream is closed or another IOException occurs.
	 * 
	 * @see java.io.InputStream#read(byte[], int, int)
	 */
	public int read(boolean[] buffer, int bitOffset, int bitCount) throws IOException
	{
		if(buffer == null)
			throw new NullPointerException("buffer cannot be null");
		if(bitOffset < 0 || bitCount < 0 || bitOffset + bitCount > buffer.length)
			throw new IndexOutOfBoundsException("bitOffset and/or bitCount cause out of bounds exception");
		int i = 0;
		for(; i < bitCount; i++)
		{
			try
			{
				buffer[i + bitOffset] = readBit();
			}
			catch(EOFException eof)
			{
				if(i == 0)
					return -1; //not a single bit could be read
				break;
			}
		}
		return i; //number of bytes read
	}
	
	/**
	 * Reads exactly n whole bytes from the input
	 * 
	 * @param numberOfBytes number of bytes to read
	 * @return array (of length numberOfBytes) with the bytes that were read
	 * @throws IOException if the stream is closed or another I/O error occurs
	 * @throws EOFException could not read enough bytes
	 */
	public byte[] readBytes(int numberOfBytes) throws IOException, EOFException
	{	
		byte[] bytes = new byte[numberOfBytes];
		int n = read(bytes); //throws IOException (e.g. when stream is closed)
		if(n < numberOfBytes)
			throw new EOFException("Could not read enough bits (requested: " + numberOfBytes + "; read: " + Math.max(n, 0) + "), because end of stream was reached");
		return bytes;
	}
	
	/* (non-Javadoc)
	 * @see java.io.InputStream#read(byte[])
	 */
	public int read(byte[] buffer) throws IOException
	{
		return read(buffer, 0, buffer.length);
	}
	
	/* (non-Javadoc)
	 * @see java.io.InputStream#read(byte[], int, int)
	 */
	public int read(byte[] buffer, int byteOffset, int byteCount) throws IOException 
	{
		if(buffer == null)
			throw new NullPointerException("buffer cannot be null");
		if(byteOffset < 0 || byteCount < 0 || byteOffset + byteCount > buffer.length)
			throw new IndexOutOfBoundsException("byteOffset and/or byteCount cause out of bounds exception");
		int i = 0;
		for(; i < byteCount; i++)
		{
			try
			{
				buffer[i + byteOffset] = readByte();
			}
			catch(EOFException eof)
			{
				if(i == 0)
					return -1; //not a single byte could be read
				break;
			}
		}
		return i; //number of bytes read
	}
	
	/**
	 * Reads the next byte of data from the input stream.
	 *  
	 * Has the same semantics as {@link java.io.InputStream#read()}:
	 * 	the value byte is returned as an {@code int} in the range 0 to 255;
	 * 	if no byte is available because the end of the stream has been reached, the value -1 is returned.
	 * 
	 * @see java.io.InputStream#read()
	 * 
	 * @return the next byte of data, or -1 if the end of the stream is reached
	 * @throws IOException if the stream is closed or another I/O error occurs
	 */
	public int read() throws IOException
	{
		try
		{
			return (int) readByte();
		}
		catch(EOFException eof)
		{
			return -1;
		}
	}
	
	/**
	 * Reads exactly one byte from the input and returns it.
	 * Throws an {@link EOFException} when less than 8 bits could be read.
	 * 
	 * @return the byte that was read
	 * @throws IOException if the stream is closed or another I/O error occurs
	 * @throws EOFException could not read exactly 8 bits
	 */
	public byte readByte() throws IOException, EOFException
	{
		return (byte) readInteger(Byte.SIZE, false);
		//Old version:
		/*byte b = 0;
		int i = 0;
		for(; i < 8; i++)
			if(readBit())
				b |= 1 << (7 - i); //MSB is read first
		return b;*/
	}
	
	/**
	 * Reads a 16 bit signed integer (short) from the input
	 * 
	 * @return the value that was read
	 * @throws IOException if an I/O error occurs
	 */
	public short readShort() throws IOException
	{
		return (short) readInteger(Short.SIZE, true);
	}
	
	/**
	 * Reads a 32 bit signed integer (int) from the input
	 * 
	 * @return the value that was read
	 * @throws IOException if an I/O error occurs
	 */
	public int readInt() throws IOException
	{
		return (int) readInteger(Integer.SIZE, true);
	}
	
	/**
	 * Reads a 64 bit signed integer (long) from the input
	 * 
	 * @return the value that was read
	 * @throws IOException if an I/O error occurs
	 */
	public long readLong() throws IOException
	{
		return readInteger(Long.SIZE, true);
	}
	
	/**
 	 * Reads an integer value of specified number of bits (max 64) and specified "signedness" from the input and returns it as a long.
 	 * 
 	 * Signed values are assumed to be stored using Two's complement format.
	 * Currently only supports big-endian byte order (and MSB 0 bit numbering), meaning the more significant bits (and bytes) are read first.
	 * 
	 * @param numberOfBits the number of bits (> 0; <= 64 if signed; <= 63 if unsigned)
	 * @param signed the "signedness" (true = signed; false = unsigned)
	 * @return the value that was read
	 * @throws IOException if the stream is closed or another I/O error occurs
	 * @see <a href="http://en.wikipedia.org/wiki/Integer_(computer_science)">http://en.wikipedia.org/wiki/Integer_(computer_science)</a>
	 * @see <a href="http://en.wikipedia.org/wiki/Two's_complement">http://en.wikipedia.org/wiki/Two's_complement</a>
	 * @see <a href="http://en.wikipedia.org/wiki/Endianness">http://en.wikipedia.org/wiki/Endianness</a>
	 * @see <a href="http://en.wikipedia.org/wiki/Bit_numbering">http://en.wikipedia.org/wiki/Bit_numbering</a>
	 * 
	 * TODO add support for little-endian byte order (and perhaps LSB 0 bit numbering)
	 */
	public long readInteger(int numberOfBits, boolean signed/*, ByteOrder order*/) throws IOException
	{
		if(numberOfBits > 64)
			throw new IllegalArgumentException("Cannot store more than 64 bits in a variable of type long; use readBigInteger() instead.");
		if(numberOfBits == 64 && !signed)
			throw new IllegalArgumentException("Cannot safely store unsigned values of more than 63 bits in a variable of type long; use readBigInteger() instead.");
		return readBigInteger(numberOfBits, signed).longValue();
	}
	
	/**
 	 * Reads an integer value of specified number of bits and specified "signedness" from the input and returns it as a BigInteger.
 	 * 
 	 * Signed values are assumed to be stored using Two's complement format.
	 * Currently only supports big-endian byte order (and MSB 0 bit numbering), meaning the more significant bits (and bytes) are read first.
	 * 
	 * @param numberOfBits  the number of bits to be read (> 0)
	 * @param signed  the "signedness" (true = signed; false = unsigned)
	 * 
	 * @return the value that was read
	 * @throws IOException if the stream is closed or another I/O error occurs
	 * @throws EOFException when not enough bits could be read
	 * 
	 * @see java.math.BigInteger
	 * @see <a href="http://en.wikipedia.org/wiki/Integer_(computer_science)">http://en.wikipedia.org/wiki/Integer_(computer_science)</a>
	 * @see <a href="http://en.wikipedia.org/wiki/Two's_complement">http://en.wikipedia.org/wiki/Two's_complement</a>
	 * @see <a href="http://en.wikipedia.org/wiki/Endianness">http://en.wikipedia.org/wiki/Endianness</a>
	 * @see <a href="http://en.wikipedia.org/wiki/Bit_numbering">http://en.wikipedia.org/wiki/Bit_numbering</a>
	 * 
	 * TODO add support for little-endian byte order (and perhaps LSB 0 bit numbering)
	 */
	public BigInteger readBigInteger(int numberOfBits, boolean signed/*, ByteOrder order*/) throws IOException
	{
		if(numberOfBits < 0)
			throw new IllegalArgumentException("numberOfBits (" + numberOfBits + ") cannot be negative!");
		// Determine min/max values:
		BigInteger minValue = signed ? 	BigInteger.valueOf(2).pow(numberOfBits - 1).negate() :
										BigInteger.ZERO;
		BigInteger maxValue = signed ? 	BigInteger.valueOf(2).pow(numberOfBits - 1).subtract(BigInteger.ONE) :
										BigInteger.valueOf(2).pow(numberOfBits).subtract(BigInteger.ONE);
		/* Read the value bit by bit...
		 *	The most significant bit is read first ("MSB 0" bit numbering).
		 *	But because  BigInteger uses "LSB 0" bit numbering internally we will always set its (numberOfBits - 1 - i)-th bit
		 *	(i.e. in the BigInteger the most significant bit is at address numburOfBits-1). */
		BigInteger value = BigInteger.ZERO;
		for(int i = 0; i < numberOfBits; i++)
			if(readBit()) //throws IOException and EOFException 
				value = value.setBit(numberOfBits - 1 - i);
			//Alternative for the 2 lines above:
			//	value = value.add(BigInteger.valueOf(bits[i] ? (1l << i) : 0l));
		//Overflowing values become negative:
		if(value.compareTo(maxValue) > 0) //is value bigger than maxValue?
			value = minValue.add(value.subtract(maxValue).subtract(BigInteger.ONE));
		return value;
	}
	
	/**
	 * Reads a 32bit floating point value (a float) from the input
	 * 
	 * @return
	 * @throws IOException if an I/O error occurs
	 */
	public float readFloat() throws IOException
	{
		return Float.intBitsToFloat((int) readInteger(Float.SIZE, true));
	}
	
	/**
	 * Reads a 64bit floating point value (a double) from the input
	 * 
	 * @return
	 * @throws IOException if an I/O error occurs
	 */
	public double readDouble() throws IOException
	{
		return Double.longBitsToDouble(readInteger(Double.SIZE, true));
	}
	
	/**
	 * Reads a String composed of the specified number of bytes from the input,
	 * using the default character set (UTF-8).
	 * 
	 * @param numberOfBytes
	 * @return
	 * @throws IOException if an I/O error occurs
	 * @see <a href="http://en.wikipedia.org/wiki/UTF-8">http://en.wikipedia.org/wiki/UTF-8</a>
	 */
	public String readString(int numberOfBytes) throws IOException
	{
		return readString(numberOfBytes, DEFAULT_CHARSET);
	}
	
	/**
	 * Reads a String composed of the specified number of bytes from the input,
	 * using the provided character set.
	 * 
	 * @param numberOfBytes
	 * @param charset
	 * @return
	 * @throws IOException if an I/O error occurs
	 */
	public String readString(int numberOfBytes, Charset charset) throws IOException
	{
		return new String(readBytes(numberOfBytes), charset);
	}
	
	/**
	 * Reads a single (16 bit) char from the input.
	 * Always uses UTF-16BE encoding (for now).
	 *
	 * @param value char to write
	 * @throws IOException
	 * @see <a href="http://en.wikipedia.org/wiki/UTF-16">http://en.wikipedia.org/wiki/UTF-16</a>
	 */
	public char readChar() throws IOException
	{
		//TODO support other character encodings?
		return (new String(readBytes(2), UTF16BE)).charAt(0);
	}
	
	/**
	 * Closes this stream and the underlying InputStream.
	 * 
	 * @throws IOException if an I/O error occurs
	 */
	public void close() throws IOException
	{
		input.close();
		closed = true;
	}
	
	/**
	 * The (estimated) number of bits left available for reading.
	 * Calls atEnd().
	 * 
	 * @return
	 */
	public int bitsAvailable() throws IOException
	{
		try
		{
			if(atEnd())
				return 0;
			else
				return numBitsRemaining + (input.available() * 8);
		}
		catch(IOException e)
		{
			return numBitsRemaining;
		}
	}
	
	/* (non-Javadoc)
	 * @see java.io.InputStream#available()
	 */
	public int available() throws IOException
	{
		if(closed)
			throw new IOException("This stream is closed");
		return (numBitsRemaining == 8 ? 1 : 0) + input.available();
	}
	
	/* (non-Javadoc)
	 * @see java.io.InputStream#markSupported()
	 */
	public boolean markSupported()
	{
		return false;
	}
	
	/* (non-Javadoc)
	 * @see java.io.InputStream#mark(int)
	 */
	public void	mark(int readlimit)
	{
		//does nothing (mark/reset behaviour is not supported)
		//throw new IOException("Mark/reset not supported");
	}

	/* (non-Javadoc)
	 * @see java.io.InputStream#reset()
	 */
	public void reset() throws IOException
	{
		if(closed)
			throw new IOException("This stream is closed");
		//does nothing (mark/reset behaviour is not supported)
		//throw new IOException("Mark/reset not supported");
	}
	
	/* (non-Javadoc)
	 * @see java.io.InputStream#skip(long)
	 */
	public long skip(long n) throws IOException
	{
		if(n > 0)
		{
			long i = 0;
			for(; i < n; i++)
			{
				try
				{
					readByte();
				}
				catch(EOFException eof)
				{
					break;
				}
			}
			return i;
		}
		return 0;
	}
	
}