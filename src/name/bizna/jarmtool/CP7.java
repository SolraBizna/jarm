package name.bizna.jarmtool;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.RandomAccessFile;

import name.bizna.jarm.AlignmentException;
import name.bizna.jarm.BusErrorException;
import name.bizna.jarm.CPU;
import name.bizna.jarm.EscapeCompleteException;
import name.bizna.jarm.EscapeRetryException;
import name.bizna.jarm.SaneCoprocessor;
import name.bizna.jarm.UndefinedException;
import name.bizna.jarm.VirtualMemorySpace;

public class CP7 extends SaneCoprocessor {
	
	public CP7(CPU cpu) { super(cpu); }
	
	private static final int MAXFDS = 16;
	
	private static abstract class FD {
		abstract public int fstat(VirtualMemorySpace vm, int p) throws EscapeRetryException;
		abstract public int read(VirtualMemorySpace vm, int p, int count);
		abstract public int write(VirtualMemorySpace vm, int p, int count);
		abstract public int close();
		abstract public boolean isatty();
		abstract public int lseek(int offset, int whence);
	};
	
	private static class IStreamFD extends FD {

		private final InputStream i;
		public IStreamFD(InputStream i) {
			this.i = i;
		}
		
		@Override
		public int fstat(VirtualMemorySpace vm, int p) {
			return -79; // EFTYE - inappropriate file type or format
		}

		@Override
		public int read(VirtualMemorySpace vm, int p, int count) {
			if(count <= 0) return 0;
			if(count > 1024) count = 1024;
			byte[] r = new byte[count];
			try {
				int red = i.read(r);
				int q = red;
				int n = 0;
				while(q > 0) {
					try {
						vm.writeByte(p, r[n]);
					}
					catch(EscapeRetryException e) {
						continue;
					}
					p++;
					n++;
					q--;
				}
				return red;
			}
			catch(EOFException e) {
				return 0;
			}
			catch(IOException e) {
				return -5; // EIO - I/O error
			}
			catch(BusErrorException e) {
				return -14; // EFAULT - Bad address
			}
		}

		@Override
		public int write(VirtualMemorySpace vm, int p, int count) {
			return -9; // EBADF - Bad file descriptor
		}

		@Override
		public int close() {
			return 0;
		}

		@Override
		public boolean isatty() {
			return true;
		}

		@Override
		public int lseek(int offset, int whence) {
			return -29; // ESPIPE - Illegal seek
		}
		
	};
	
	private static class OStreamFD extends FD {

		private final PrintStream o;
		public OStreamFD(PrintStream o) {
			this.o = o;
		}
		
		@Override
		public int fstat(VirtualMemorySpace vm, int p) {
			return -79; // EFTYE - inappropriate file type or format
		}

		@Override
		public int write(VirtualMemorySpace vm, int p, int count) {
			if(count <= 0) return 0;
			try {
				int wrote = 0;
				while(count > 0) {
					byte b;
					while(true) {
						try {
							b = vm.readByte(p);
							break;
						}
						catch(EscapeRetryException e) {}
					}
					count--;
					p++;
					o.write(b);
					++wrote;
				}
				return wrote;
			}
			catch(BusErrorException e) {
				return -14; // EFAULT - Bad address
			}
		}

		@Override
		public int read(VirtualMemorySpace vm, int p, int count) {
			return -9; // EBADF - Bad file descriptor
		}

		@Override
		public int close() {
			return 0;
		}

		@Override
		public boolean isatty() {
			return true;
		}

		@Override
		public int lseek(int offset, int whence) {
			return -29; // ESPIPE - Illegal seek
		}
		
	};
	
	private abstract class RAFFD extends FD {
		
		protected RandomAccessFile raf;
		protected RAFFD(RandomAccessFile raf) {
			this.raf = raf;
		}
		@Override
		public int fstat(VirtualMemorySpace vm, int p) throws EscapeRetryException {
			try {
				vm.writeInt(p, 0, true, cpu.isBigEndian()); // st_dev
				vm.writeInt(p+4, 0, true, cpu.isBigEndian()); // st_ino
				vm.writeInt(p+8, 0, true, cpu.isBigEndian()); // st_mode, ick
				vm.writeInt(p+12, 1, true, cpu.isBigEndian()); // st_nlink
				vm.writeInt(p+16, 0, true, cpu.isBigEndian()); // st_uid
				vm.writeInt(p+20, 0, true, cpu.isBigEndian()); // st_gid
				vm.writeInt(p+24, 0, true, cpu.isBigEndian()); // st_rdev
				vm.writeInt(p+28, (int)raf.length(), true, cpu.isBigEndian()); // st_size
				vm.writeInt(p+32, 256, true, cpu.isBigEndian()); // st_blksize
				vm.writeInt(p+36, ((int)raf.length()+511)/512, true, cpu.isBigEndian()); // st_blocks
				return 0;
			}
			catch(AlignmentException e) {
				return -14; // EFAULT - Bad address
			}
			catch(BusErrorException e) {
				return -14; // EFAULT - Bad address
			}
			catch(IOException e) {
				return -5; // EIO - IO error
			}
		}

		@Override
		public int close() {
			try {
				raf.close();
				return 0;
			}
			catch(IOException e) {
				return -5; // EIO - IO error
			}
		}

		@Override
		public boolean isatty() {
			return false;
		}

		@Override
		public int lseek(int offset, int whence) {
			try {
				switch(whence) {
				case 0: // SEEK_SET
					if(offset < 0) return -22; // EINVAL
					raf.seek(offset);
					break;
				case 1: // SEEK_CUR
					if(offset + raf.getFilePointer() < 0) return -22; // EINVAL
					raf.seek(offset + raf.getFilePointer());
					break;
				case 2: // SEEK_END
					if(offset + raf.length() < 0) return -22; // EINVAL
					raf.seek(offset + raf.length());
					break;
				default:
					return -22; // EINVAL
				}
				return (int)raf.getFilePointer();
			}
			catch(IOException e) {
				return -5; // EIO - IO error
			}
		}
		
	};
	
	private class ReadOnlyFD extends RAFFD {
		
		public ReadOnlyFD(RandomAccessFile raf) { super(raf); }

		@Override
		public int read(VirtualMemorySpace vm, int p, int count) {
			if(count <= 0) return 0;
			if(count > 1024) count = 1024;
			byte[] r = new byte[count];
			try {
				int red = raf.read(r);
				int q = red;
				int n = 0;
				while(q > 0) {
					try {
						vm.writeByte(p, r[n]);
					}
					catch(EscapeRetryException e) {
						continue;
					}
					p++;
					n++;
					q--;
				}
				return red;
			}
			catch(EOFException e) {
				return 0;
			}
			catch(IOException e) {
				return -5; // EIO - I/O error
			}
			catch(BusErrorException e) {
				return -14; // EFAULT - Bad address
			}
		}

		@Override
		public int write(VirtualMemorySpace vm, int p, int count) {
			return -22; // EINVAL
		}
		
	}
	
	private class ReadWriteFD extends ReadOnlyFD {

		public ReadWriteFD(RandomAccessFile raf) { super(raf); }
		
		@Override
		public int write(VirtualMemorySpace vm, int p, int count) {
			if(count <= 0) return 0;
			try {
				int wrote = 0;
				while(count > 0) {
					byte b;
					while(true) {
						try {
							b = vm.readByte(p);
							break;
						}
						catch(EscapeRetryException e) {}
					}
					count--;
					p++;
					raf.write(b);
					++wrote;
				}
				return wrote;
			}
			catch(BusErrorException e) {
				return -14; // EFAULT - Bad address
			}
			catch(IOException e) {
				return -5; // EIO - IO error
			}
		}
		
	}
	
	private class AppendingFD extends ReadOnlyFD {

		public AppendingFD(RandomAccessFile raf) { super(raf); }

		@Override
		public int write(VirtualMemorySpace vm, int p, int count) {
			int res = lseek(0, 2);
			if(res < 0) return res;
			else return super.write(vm, p, count);
		}
		
	}
	
	FD[] fds = new FD[16];

	@Override
	public void storeCoprocessorRegisterToMemory(boolean unconditional,
			int coproc, boolean D, int base, int CRd, int option, int iword)
			throws BusErrorException, AlignmentException, UndefinedException,
			EscapeRetryException, EscapeCompleteException {
		throw new UndefinedException();
	}

	@Override
	public void loadCoprocessorRegisterFromMemory(boolean unconditional,
			int coproc, boolean D, int base, int CRd, int option, int iword)
			throws BusErrorException, AlignmentException, UndefinedException,
			EscapeRetryException, EscapeCompleteException {
		throw new UndefinedException();
	}

	@Override
	public void moveCoreRegisterToCoprocessorRegister(boolean unconditional,
			int coproc, int opc1, int opc2, int CRn, int CRm, int Rt)
			throws BusErrorException, AlignmentException, UndefinedException,
			EscapeRetryException, EscapeCompleteException {
		if(CRn != 15 && CRm != 15) throw new UndefinedException();
		throw new ProgramExit(cpu.readRegister(Rt));
	}

	@Override
	public void moveCoprocessorRegisterToCoreRegister(boolean unconditional,
			int coproc, int opc1, int opc2, int CRn, int CRm, int Rt)
			throws BusErrorException, AlignmentException, UndefinedException,
			EscapeRetryException, EscapeCompleteException {
		throw new UndefinedException();
	}

	@Override
	public void coprocessorDataOperation(boolean unconditional, int coproc,
			int opc1, int opc2, int CRn, int CRm, int CRd)
			throws BusErrorException, AlignmentException, UndefinedException,
			EscapeRetryException, EscapeCompleteException {
		if(CRn != 0 || CRm != 0 || CRd != 0) throw new UndefinedException();
		switch(opc1) {
		case 0: {
			/* fstat */
			int fd = cpu.readGPR(0);
			int ret;
			if(fd < 0 || fd >= MAXFDS || fds[fd] == null) ret = -9; // EBADF - Bad file number
			else ret = fds[fd].fstat(cpu.getVirtualMemorySpace(), cpu.readGPR(1));
			if(ret >= 0) {
				cpu.writeGPR(0, ret);
				cpu.setConditionZ(false);
			}
			else {
				cpu.writeGPR(0, -1);
				cpu.writeGPR(1, -ret);
				cpu.setConditionZ(true);
			}
		} break;
		case 1: {
			/* open */
			int ret;
			try {
				for(ret = 0; ret < MAXFDS; ++ret) {
					if(fds[ret] == null) break;
				}
				if(ret >= MAXFDS) ret = -23; // ENFILE - Too many open files
				else {
					String path = readString(cpu.getVirtualMemorySpace(), cpu.readGPR(0));
					int flags = cpu.readGPR(1);
					// ignore mode parameter
					final boolean O_WRITE = (flags & 0x0003) > 0;
					final boolean O_APPEND = (flags & 0x0008) != 0;
					final boolean O_CREAT = (flags & 0x0200) != 0;
					final boolean O_TRUNC = (flags & 0x0400) != 0;
					final boolean O_EXCL = (flags & 0x0800) != 0;
					// ignored flags: O_SYNC, O_NONBLOCK, O_NOCTTY
					if(O_EXCL && !O_CREAT) ret = -22; // EINVAL
					else if(O_APPEND && !O_WRITE) ret = -22;
					else {
						File file = new File(path);
						if(O_EXCL || O_CREAT) {
							boolean created = file.createNewFile();
							if(!created && O_EXCL) ret = -17; // EEXIST - File exists
						}
						if(ret >= 0) {
							RandomAccessFile raf = new RandomAccessFile(file, O_WRITE ? "rw" : "r");
							if(O_TRUNC) raf.setLength(0);
							fds[ret] = O_APPEND ? new AppendingFD(raf) : O_WRITE ? new ReadWriteFD(raf) : new ReadOnlyFD(raf);
						}
					}
				}
			}
			catch(IOException e) {
				ret = -5; // EIO - IO error
			}
			catch(BusErrorException e) {
				ret = -14; // EFAULT - Bad address
			}
			if(ret >= 0) {
				cpu.writeGPR(0, ret);
				cpu.setConditionZ(false);
			}
			else {
				cpu.writeGPR(0, -1);
				cpu.writeGPR(1, -ret);
				cpu.setConditionZ(true);
			}
		} break;
		case 2: {
			/* read */
			int fd = cpu.readGPR(0);
			int ret;
			if(fd < 0 || fd >= MAXFDS || fds[fd] == null) ret = -9; // EBADF - Bad file number
			else ret = fds[fd].read(cpu.getVirtualMemorySpace(), cpu.readGPR(1), cpu.readGPR(2));
			if(ret >= 0) {
				cpu.writeGPR(0, ret);
				cpu.setConditionZ(false);
			}
			else {
				cpu.writeGPR(0, -1);
				cpu.writeGPR(1, -ret);
				cpu.setConditionZ(true);
			}
		} break;
		case 3: {
			/* write */
			int fd = cpu.readGPR(0);
			int ret;
			if(fd < 0 || fd >= MAXFDS || fds[fd] == null) ret = -9; // EBADF - Bad file number
			else ret = fds[fd].write(cpu.getVirtualMemorySpace(), cpu.readGPR(1), cpu.readGPR(2));
			if(ret >= 0) {
				cpu.writeGPR(0, ret);
				cpu.setConditionZ(false);
			}
			else {
				cpu.writeGPR(0, -1);
				cpu.writeGPR(1, -ret);
				cpu.setConditionZ(true);
			}
		} break;
		case 4: {
			/* close */
			int fd = cpu.readGPR(0);
			int ret;
			if(fd < 0 || fd >= MAXFDS || fds[fd] == null) ret = -9; // EBADF - Bad file number
			else ret = fds[fd].close();
			fds[fd] = null;
			if(ret >= 0) {
				cpu.writeGPR(0, ret);
				cpu.setConditionZ(false);
			}
			else {
				cpu.writeGPR(0, -1);
				cpu.writeGPR(1, -ret);
				cpu.setConditionZ(true);
			}
		} break;
		case 5: {
			/* gettimeofday */
			int ret = 0;
			try {
				long t = System.currentTimeMillis();
				int tv = cpu.readGPR(0);
				VirtualMemorySpace vm = cpu.getVirtualMemorySpace();
				vm.writeInt(tv, (int)(t / 1000), true, cpu.isBigEndian());
				vm.writeInt(tv+4, (int)(t * 1000 % 1000000), true, cpu.isBigEndian());
			}
			catch(BusErrorException b) {
				ret = -14; // EFAULT - Bad address
			}
			if(ret >= 0) {
				cpu.writeGPR(0, ret);
				cpu.setConditionZ(false);
			}
			else {
				cpu.writeGPR(0, -1);
				cpu.writeGPR(1, -ret);
				cpu.setConditionZ(true);
			}
		} break;
		case 6: {
			/* isatty */
			int fd = cpu.readGPR(0);
			int ret;
			if(fd < 0 || fd >= MAXFDS || fds[fd] == null) ret = -9; // EBADF - Bad file number
			else ret = fds[fd].isatty() ? 1 : -25; // ENOTTY
			if(ret >= 0) {
				cpu.writeGPR(0, ret);
				cpu.setConditionZ(false);
			}
			else {
				cpu.writeGPR(0, 0); // NOT -1
				cpu.writeGPR(1, -ret);
				cpu.setConditionZ(true);
			}
		} break;
		case 7: {
			/* write */
			int fd = cpu.readGPR(0);
			int ret;
			if(fd < 0 || fd >= MAXFDS || fds[fd] == null) ret = -9; // EBADF - Bad file number
			else ret = fds[fd].lseek(cpu.readGPR(1), cpu.readGPR(2));
			if(ret >= 0) {
				cpu.writeGPR(0, ret);
				cpu.setConditionZ(false);
			}
			else {
				cpu.writeGPR(0, -1);
				cpu.writeGPR(1, -ret);
				cpu.setConditionZ(true);
			}
		} break;
		case 8: {
			/* unlink */
			int ret;
			try {
				String str = readString(cpu.getVirtualMemorySpace(), cpu.readGPR(0));
				ret = (new File(str)).delete() ? 0 : -5; // EIO - IO error
			}
			catch(BusErrorException e) {
				ret = -14; // EFAULT - Bad address
			}
			if(ret >= 0) {
				cpu.writeGPR(0, ret);
				cpu.setConditionZ(false);
			}
			else {
				cpu.writeGPR(0, -1);
				cpu.writeGPR(1, -ret);
				cpu.setConditionZ(true);
			}
		} break;
		}
	}

	private String readString(VirtualMemorySpace vm, int start) throws BusErrorException, EscapeRetryException {
		int len = 0;
		int p = start;
		while(vm.readByte(p++) != 0) ++len;
		byte[] sbyte = new byte[len];
		p = start;
		int n = 0;
		while(n < len) sbyte[n++] = vm.readByte(p++);
		return new String(sbyte);
	}

	@Override
	public void moveCoreRegistersToCoprocessorRegister(boolean unconditional,
			int coproc, int opc1, int CRm, int Rt, int Rt2)
			throws BusErrorException, AlignmentException, UndefinedException,
			EscapeRetryException, EscapeCompleteException {
		throw new UndefinedException();
	}

	@Override
	public void moveCoprocessorRegisterToCoreRegisters(boolean unconditional,
			int coproc, int opc1, int CRm, int Rt, int Rt2)
			throws BusErrorException, AlignmentException, UndefinedException,
			EscapeRetryException, EscapeCompleteException {
		throw new UndefinedException();
	}

	@Override
	public void reset() {
		fds[0] = new IStreamFD(System.in);
		fds[1] = new OStreamFD(System.out);
		fds[2] = new OStreamFD(System.err);
		for(int n = 3; n < MAXFDS; ++n) {
			fds[n] = null;
		}
	}

}
