package Data;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TweetList {
	
	@JsonProperty("dataset")
	public TweetData[] dataset; 
}