package Associate;

import java.util.HashMap;
import java.util.LinkedHashMap;

public class TweetEntityExtraction {

	String entityInfo = "";
	public TweetEntityExtraction(String entityInfo) 
	{
		this.entityInfo = entityInfo;
	}
	
	public LinkedHashMap<String,Integer> getusersMentioned()
	{
		LinkedHashMap<String,Integer> res = new LinkedHashMap<String,Integer>();
		String []parts = this.entityInfo.split("user_mentions=");
		String userMenStr = parts[1].split("], hashtags=")[0];

		String []people = userMenStr.split(", screen_name="); 
		for (int idx=1;idx<people.length;idx++)
		{	
			//String ppId = "@" + people[idx].split(", ")[0];
			String ppId = people[idx].split(", ")[0].toLowerCase();
			
			int val = 1;
			if (res.containsKey(ppId))
			{
				val = res.get(ppId) + 1;
			}
			res.put(ppId, val);
		}
		return res;
	}
	
	public String getHashTagsStr()
	{
		String res = "";
		
		String []parts = this.entityInfo.split(", hashtags=");
		
		if (parts.length>0)
		{
			res = parts[1].split("], urls=")[0].toLowerCase();
		}
		
		return res;
	}

	public LinkedHashMap<String, Integer> getUrlFreqMap() 
	{
		LinkedHashMap<String,Integer> res = new LinkedHashMap<String, Integer>();
		
		String []parts = this.entityInfo.split("urls=");
		String []urls = parts[1].split("}, in_reply_to_screen_name=")[0].split("}");
		
		for (String urlItem:urls)
		{
			if (!urlItem.equals(""))
			{
				String []urlParts = urlItem.split("expanded_url=");
				if (urlParts.length>1)
				{
					String urlStr = urlParts[1].split(", display_url=")[0].toLowerCase();
					int val = 1;
					if (res.containsKey(urlStr))
					{
						val = res.get(urlStr);
					}
					res.put(urlStr, val);
				}
			}
			
		}
		
		return res;
	}
	
	public String getOrigTId()
	{
		String res = "";
		String[] parts = this.entityInfo.split(", id=");
		res = parts[1].split(", favorite_count=")[0];
		
		return res;
	}

	public LinkedHashMap<String,Integer> getHashTagFreqMap() {
		LinkedHashMap<String,Integer> hashTagFreqs = new LinkedHashMap<String, Integer>();
		String hashTagsStr = getHashTagsStr();
		
		if (hashTagsStr!=null) {
			String []parts = hashTagsStr.split("},");
		
			for (String pp:parts) {
				String []hashParts = pp.split("text=");
				if (hashParts.length>1) {
					String hash = hashParts[1].split("}")[0].toLowerCase();
				
					if (hashTagFreqs.containsKey(hash)) {
						int val = hashTagFreqs.get(hash);
						val++;
						hashTagFreqs.put(hash, val);
					}
					else {
						hashTagFreqs.put(hash, 1);
					}
				}
			}	
		}
		return hashTagFreqs;
	}
	
}
