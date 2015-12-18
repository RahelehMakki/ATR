package Associate;

public class Main {
	static int numberOfIterations = 2;
	static double threshold = 0.7;
	static boolean realUser = false;
	static boolean activeMode = true;
	
	public static void main(String[] args) {
		TweetDebateAssociation tda = new TweetDebateAssociation();
		tda.initialize();
		tda.associate(numberOfIterations, threshold, activeMode, realUser);
	}

}
