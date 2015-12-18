package Data;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class TweetData {
	private String id_str;
	 private Map<String , Object> otherProperties = new HashMap<String , Object>();

	    @JsonCreator
	    public TweetData(@JsonProperty("id_str") String id_str) 
	    {
	        this.id_str = id_str;
	       	        
	    }
	 
	   public String getIdStr() {
	        return id_str;
	    }
	 
	    public void setIdStr(String idStr) {
	        this.id_str = idStr;
	    }
	 
	   	    
	    public Object get(String name) {
	        return otherProperties.get(name);
	    }
	 
	    @JsonAnyGetter
	    public Map<String , Object> any() {
	        return otherProperties;
	    }
	 
	    @JsonAnySetter
	    public void set(String name, Object value) {
	        otherProperties.put(name, value);
	    }
}