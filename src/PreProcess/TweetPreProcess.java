package PreProcess;


import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import Util.DirectoryFileUtil;

public class TweetPreProcess {
	
	private static HashSet<String> stopWords;
	
	public static void init() {
		DirectoryFileUtil dirFU = new DirectoryFileUtil();
		stopWords = dirFU.loadStopWords();
	}
	public HashSet<String> getStopWords() {
		return stopWords;
	}
	public String process(String inStr,boolean keepUserMention, boolean keepUrl, boolean keepStopWord) {
			
		String filteredText = "";
		String tmp = inStr.toLowerCase();
		String []prts = tmp.split("\\s+");
		
		for (String ptr:prts) {
			if ((keepUserMention) && (ptr.startsWith("@"))) {
				filteredText = filteredText + ptr + " ";
			}
				
			if ((keepUrl) && (ptr.contains("http"))) {
				filteredText = filteredText + ptr + " ";			
			}	
			
			if ((!ptr.startsWith("@")) && (!ptr.contains("http"))) {
				filteredText = filteredText + ptr + " ";
			}
		}
		
		filteredText = filteredText.replaceAll("[!,:\";.#/?@(><)]+", " ");		
		filteredText = filteredText.replaceAll("\\*", " ");
		filteredText = filteredText.replaceAll("'s?\\W", " ");
		filteredText = filteredText.replaceAll("[\\s]+", " ");
		filteredText = filteredText.trim();
		
		String []parts = filteredText.split(" ");
		String res = "";
		for (String pStr:parts)
		{
			Pattern pattern = Pattern.compile("\\w+");
			Matcher matcher = pattern.matcher(pStr);
			
			if (matcher.find()) {
				if (keepStopWord) {
					res = res + pStr + " ";
				}
				else if (!stopWords.contains(pStr)) {
					res = res + pStr + " "; 
				}
			}
		}
			return res.trim();
	}
}
