package name.bizna.ocarmsim;

public class SimKeyboard extends SimComponent {
	
	public static final String keyboardAddress = "5cf4656c-5df9-4d35-a1a0-9908de2ade9e";

	@Override
	public String name() {
		return "keyboard";
	}

	@Override
	public String address() {
		return keyboardAddress;
	}

}
