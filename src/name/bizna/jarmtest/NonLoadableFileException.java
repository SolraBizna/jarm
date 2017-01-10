package name.bizna.jarmtest;

public class NonLoadableFileException extends Exception {
	public static final long serialVersionUID = 1;
	private String way;
	private String identifier;
	public NonLoadableFileException(String way, String identifier) {
		this.way = way;
		this.identifier = identifier;
	}
	public String getWay() {
		return way;
	}
	public String getIdentifier() {
		return identifier;
	}
}