package name.bizna.ocarmsim.components;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.util.Arrays;
import javax.swing.JComponent;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.machine.Machine;
import li.cil.oc.api.prefab.AbstractValue;

public class SimFilesystem extends SimComponent {

	public static final int MAX_HANDLES = 16;
	public static final int MAX_READ_LENGTH = 2048;

	private static enum Whence {
		SET, CUR, END
	}

	/* oh jeeeez */
	private static class FileHandle {

		private File path;
		private RandomAccessFile raf;
		private boolean readable, writable;

		public FileHandle(File path, boolean readable, boolean writable, boolean seekToEnd) throws FileNotFoundException {
			this.path = path;
			this.raf = new RandomAccessFile(path, writable ? "rw" : "r");
			this.readable = readable;
			this.writable = writable;
			if (seekToEnd) {
				seek(Whence.END, 0);
			}
		}

		public long seek(Whence whence, long offset) {
			try {
				long base, max = path.length();
				switch (whence) {
					default:
					case SET:
						base = 0;
						break;
					case CUR:
						base = raf.getFilePointer();
						break;
					case END:
						base = max;
						break;
				}
				long target = base + offset;
				if (target >= max) {
					target = max;
				} else if (target < 0) {
					target = 0;
				}
				raf.seek(target);
				return raf.getFilePointer();
			} catch (IOException e) {
			}
			return -1;
		}

		public boolean write(byte[] buf, int offset, int length) {
			assert (writable);
			try {
				raf.write(buf, offset, length);
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}
			return true;
		}

		public int read(byte[] buf) {
			assert (readable);
			try {
				int red = raf.read(buf);
				if (red < 0) {
					return 0;
				} else {
					return red;
				}
			} catch (IOException e) {
				e.printStackTrace();
				return -1;
			}
		}
	}

	public static final String filesystemAddressFormatString = "%08x-cb0d-47da-b849-11e2cca99d5a";

	private static class HandleValue extends AbstractValue {

		private final String owner;
		private final int handle;

		HandleValue(String owner, int handle) {
			this.owner = owner;
			this.handle = handle;
		}

		static int getHandle(String me, Object o) {
			if (!(o instanceof HandleValue)) {
				return MAX_HANDLES;
			}
			HandleValue h = (HandleValue) o;
			if (h.owner.equals(me)) {
				return h.handle;
			} else {
				return MAX_HANDLES;
			}
		}
	};

	private FileHandle[] handles = new FileHandle[MAX_HANDLES];
	private File basePath;
	private boolean writable;
	private String label;

	public SimFilesystem(Machine machine, String address, File basePath, boolean writable) {
		super(machine, address);

		this.basePath = basePath;
		this.writable = writable;
		/* we generate some bits of a random UUID based on the hash code of the absolute path
		 * this way, we'll generate the same bits for the same filesystem every time
		 * AND the random parts are relatively high up, lexically speaking
		 */
		this.label = basePath.getName();
	}

	@Override
	public String name() {
		return "filesystem";
	}

	private File processOCPath(String path) {
		File ret = basePath;
		// try to do this similarly to the way OpenComputers has been observed to do it
		String[] components = path.split("/");
		int levelsDeep = 0;
		for (String component : components) {
			if (component.equals(".")) {
			} // do nothing
			else if (component.equals("..")) {
				if (levelsDeep != 0) {
					ret = ret.getParentFile();
					assert (ret != null); // should never happen... and yet....
				}
			} else {
				ret = new File(ret, component);
			}
		}
		return ret;
	}

	@Callback
	Object[] spaceUsed(Context ctx, Object[] args) {
		return new Object[]{basePath.getTotalSpace() - basePath.getFreeSpace()};
	}

	@Callback
	Object[] spaceTotal(Context ctx, Object[] args) {
		return new Object[]{basePath.getTotalSpace()};
	}

	@Callback
	Object[] open(Context ctx, Object[] args) {
		int nullHandleIndex = -1;
		for (int n = 0; n < MAX_HANDLES; ++n) {
			if (handles[n] == null) {
				nullHandleIndex = n;
				break;
			}
		}
		if (nullHandleIndex < 0) {
			return new Object[]{null, "too many open files"};
		}
		String mode = args.length >= 2 ? SimComponent.toString(args[1]) : "r";
		boolean openForRead = mode.startsWith("r");
		boolean openForWrite = mode.startsWith("w") || mode.startsWith("a");
		if (!openForRead && !openForWrite) {
			return new Object[]{null, "mode must begin with one of r, w, or a"};
		}
		openForRead = openForRead || mode.contains("+");
		openForWrite = openForWrite || mode.contains("+");
		if (openForWrite && !writable) {
			return new Object[]{null, "filesystem is read-only"};
		}
		boolean truncate = mode.startsWith("w");
		boolean seekToEnd = mode.startsWith("a");
		File filePath = processOCPath(SimComponent.toString(args[0]));
		if (filePath.exists() && filePath.isDirectory()) {
			return new Object[]{null, "is a directory"};
		}
		if (truncate) {
			try {
				if (filePath.exists() && !filePath.delete()) {
					return new Object[]{null, "could not truncate file"};
				}
				if (!filePath.createNewFile()) {
					return new Object[]{null, "could not create file"};
				}
			} catch (IOException e) {
				return new Object[]{null, "could not create file"};
			}
		} else if (!filePath.exists()) {
			return new Object[]{null, "no such file"};
		}
		try {
			handles[nullHandleIndex] = new FileHandle(filePath, openForRead, openForWrite, seekToEnd);
		} catch (FileNotFoundException e) {
			return new Object[]{null, "no such file"};
		}
		return new Object[]{new HandleValue(address(), nullHandleIndex)};
	}

	@Callback
	public Object[] seek(Context ctx, Object[] args) {
		int handle = HandleValue.getHandle(address(), args[0]);
		if (handle >= MAX_HANDLES || handle < 0 || handles[handle] == null) {
			return new Object[]{null, "no such handle"};
		}
		String whenceStr = SimComponent.toString(args[1]);
		Whence whence;
		if (whenceStr.equals("set")) {
			whence = Whence.SET;
		} else if (whenceStr.equals("cur")) {
			whence = Whence.CUR;
		} else if (whenceStr.equals("end")) {
			whence = Whence.END;
		} else {
			return new Object[]{null, "unknown whence value"};
		}
		long offset = ((Number) args[2]).longValue();
		return new Object[]{handles[handle].seek(whence, offset)};
	}

	@Callback
	public Object[] read(Context ctx, Object[] args) {
		Number readLenN = (Number) args[1];
		int readLen;
		if (Double.isInfinite(readLenN.doubleValue()) || readLenN.doubleValue() > MAX_READ_LENGTH) {
			readLen = MAX_READ_LENGTH;
		} else {
			readLen = readLenN.intValue();
		}
		if (readLen <= 0) {
			return new Object[]{null, "read length out of range"};
		}
		int handle = HandleValue.getHandle(address(), args[0]);
		if (handle >= MAX_HANDLES || handle < 0 || handles[handle] == null) {
			return new Object[]{null, "no such handle"};
		}
		if (!handles[handle].readable) {
			return new Object[]{null, "file not opened for reading"};
		}
		byte[] buf = new byte[readLen];
		int red = handles[handle].read(buf);
		if (red < 0) {
			return new Object[]{null, "IO error"};
		} else if (red == 0) {
			return new Object[]{null};
		} else if (red < buf.length) {
			return new Object[]{Arrays.copyOf(buf, red)};
		} else {
			return new Object[]{buf};
		}
	}

	@Callback
	public Object[] write(Context ctx, Object[] args) {
		if (!writable) {
			return new Object[]{null, "filesystem is read-only"};
		}
		int handle = HandleValue.getHandle(address(), args[0]);
		if (handle >= MAX_HANDLES || handle < 0 || handles[handle] == null) {
			return new Object[]{null, "no such handle"};
		}
		if (!handles[handle].writable) {
			return new Object[]{null, "file not opened for writing"};
		}
		byte[] buf;
		int writeOffset, writeLength;
		if (args[1] instanceof byte[]) {
			buf = (byte[]) args[1];
			writeOffset = 0;
			writeLength = buf.length;
		} else if (args[1] instanceof String) {
			try {
				ByteBuffer bytes = Charset.forName("UTF-8").newEncoder().encode(CharBuffer.wrap(SimComponent.toString(args[1])));
				if (bytes.hasArray()) {
					buf = bytes.array();
					writeOffset = bytes.arrayOffset();
					writeLength = bytes.limit();
				} else {
					buf = new byte[bytes.limit()];
					bytes.get(buf);
					writeOffset = 0;
					writeLength = buf.length;
				}
			} catch (CharacterCodingException e) {
				return new Object[]{null, "CharacterCodingException"};
			}
		} else {
			return new Object[]{null, "invalid buffer"};
		}
		return new Object[]{handles[handle].write(buf, writeOffset, writeLength)};
	}

	@Callback
	public Object[] close(Context ctx, Object[] args) {
		int handle = HandleValue.getHandle(address(), args[0]);
		if (handle >= MAX_HANDLES || handle < 0 || handles[handle] == null) {
			return new Object[]{null, "no such handle"};
		}
		handles[handle] = null;
		return new Object[]{Boolean.TRUE};
	}

	@Callback
	public Object[] makeDirectory(Context ctx, Object[] args) {
		if (!writable) {
			return new Object[]{null, "filesystem is read-only"};
		}
		File filePath = processOCPath(SimComponent.toString(args[0]));
		return new Object[]{filePath.mkdirs()};
	}

	@Callback
	public Object[] exists(Context ctx, Object[] args) {
		File filePath = processOCPath(SimComponent.toString(args[0]));
		return new Object[]{filePath.exists()};
	}

	@Callback
	public Object[] isDirectory(Context ctx, Object[] args) {
		File filePath = processOCPath(SimComponent.toString(args[0]));
		return new Object[]{filePath.isDirectory()};
	}

	@Callback
	public Object[] rename(Context ctx, Object[] args) {
		if (!writable) {
			return new Object[]{null, "filesystem is read-only"};
		}
		File fromPath = processOCPath(SimComponent.toString(args[0]));
		File toPath = processOCPath(SimComponent.toString(args[1]));
		return new Object[]{fromPath.renameTo(toPath)};
	}

	@Callback
	public Object[] list(Context ctx, Object[] args) {
		File filePath = processOCPath(SimComponent.toString(args[0]));
		String[] files = filePath.list();
		return new Object[]{files};
	}

	@Callback
	public Object[] isReadOnly(Context ctx, Object[] args) {
		return new Object[]{!writable};
	}

	@Callback
	public Object[] lastModified(Context ctx, Object[] args) {
		File filePath = processOCPath(SimComponent.toString(args[0]));
		return new Object[]{filePath.lastModified()};
	}

	@Callback
	public Object[] remove(Context ctx, Object[] args) {
		if (!writable) {
			return new Object[]{null, "filesystem is read-only"};
		}
		File filePath = processOCPath(SimComponent.toString(args[0]));
		return new Object[]{filePath.delete()};
	}

	@Callback
	public Object[] size(Context ctx, Object[] args) {
		File filePath = processOCPath(SimComponent.toString(args[0]));
		return new Object[]{filePath.length()};
	}

	@Callback
	public Object[] getLabel(Context ctx, Object[] args) {
		return new Object[]{label};
	}

	@Callback
	public Object[] setLabel(Context ctx, Object[] args) {
		label = SimComponent.toString(args[0]);
		return new Object[]{label};
	}

	@Override
	public JComponent getUIComponent() {
		return null;
	}
	
	@Override
	public void reset() {
		Arrays.fill(handles, null);
	}
}
