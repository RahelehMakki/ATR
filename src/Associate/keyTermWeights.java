package Associate;

import java.util.HashMap;

public class keyTermWeights {
	
	HashMap<String,Double> weights = new HashMap<String,Double>();
	public boolean addKeyTerm(String labStr, double weight, boolean override) {
		boolean newValueAdded = false;
		if ((override == true) || (!weights.containsKey(labStr))) {
			weights.put(labStr, weight);
			newValueAdded = true;
		}
		return newValueAdded;
	}
}
