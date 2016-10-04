package name.bizna.ocarmsim;

/**
 *
 * @author Jean-Rémy Buchs <jrb0001@692b8c32.de>
 */
public class Breakpoint {

	private final long address;
	private final long length;

	public Breakpoint(long address, long length) {
		this.address = address;
		this.length = length;
	}

	public long getAddress() {
		return address;
	}

	public long getLength() {
		return length;
	}

	@Override
	public int hashCode() {
		int hash = 3;
		hash = 59 * hash + (int) (this.address ^ (this.address >>> 32));
		hash = 59 * hash + (int) (this.length ^ (this.length >>> 32));
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final Breakpoint other = (Breakpoint) obj;
		if (this.address != other.address) {
			return false;
		}
		if (this.length != other.length) {
			return false;
		}
		return true;
	}
}
