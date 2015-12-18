
package Associate;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.Map.Entry;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TotalHitCountCollector;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import Data.TweetData;
import Data.TweetList;
import Index.IndexingDocuments;
import PreProcess.TweetPreProcess;
import Util.DirectoryFileUtil;

public class TweetDebateAssociation {
	
	public class myIndexer {
		Analyzer analyzer = null;
		IndexSearcher searcher = null;
		IndexReader indexReader = null ;
	}
	

	public static HashMap<String,String> trueLabels;
	public static HashSet<String> classLabels = new HashSet<String>();
	public static HashMap<String,TweetData> allTweets = new HashMap<String,TweetData>();
	public static HashSet<String> leftInCloudTweets = new HashSet<String>();
	HashMap<String,HashSet<String>> tweetId2ItsRetweets = new HashMap<String, HashSet<String>>();
	HashMap<String,String> retweetedId2ItsOrigTweet = new HashMap<String, String>();
	public static HashMap<String,HashSet<String>> tweetId2ItsReplies = new HashMap<String, HashSet<String>>();
	HashMap<String,String> repliedTweetId2OrigTweet = new HashMap<String, String>();
	public static HashMap<String,keyTermWeights> discriminativeKeyterms2Classes = new HashMap<String, keyTermWeights>(); 
	public static LinkedHashSet<String> newlyAddedTweets = new LinkedHashSet<String>();
	public static LinkedHashMap<String,String> askedThroughActiveLearning = new LinkedHashMap<String, String>();
	public static HashMap<String,String> tweetId2PredictedLabel = new HashMap<String, String>();
	public static HashSet<String> retrievedTweets = new HashSet<String>();
	HashMap<String,Boolean> specificHashTags;
	
	public static double threshold = 0.5; 
	public static int askCounter = 0;
	public static boolean askFromUser = false;
	public static boolean activeMode = true;
	
	public int numberOfKeyTerms = 5;
	public int numberOfHashTags = 1;
	public int numberOfUserMentions = 2;
	public int numberOfURLs = 2;	
	

	public void associate(int iterLimit,double threshold, boolean activeMode, boolean askFromUser){
		TweetDebateAssociation.threshold = threshold;
		TweetDebateAssociation.askFromUser = askFromUser;
		TweetDebateAssociation.activeMode = activeMode;

		int iteration = 1;

		while (iteration<=iterLimit) {
			Directory dir = null;

			IndexingDocuments indexer = new IndexingDocuments();
			indexer.initializeLucene();
			
			if (iteration == 1) {
				dir = indexer.indexDocuments("./data/Debates/");
			}
			else {
				dir = indexer.indexDocuments("./data/RetrievedTweets/");
			}
			DiscriminativeKeytermsExtraction keyExt = new DiscriminativeKeytermsExtraction();
			LinkedHashMap<String,LinkedHashMap<String,Double>> tfidfMat = keyExt.calcTfIdf(dir);
			discriminativeKeyterms2Classes = addToKeyterms("keyterm",tfidfMat,(((classLabels.size()-1)*numberOfKeyTerms)/iteration),iteration);
			
			retrieveTweets(false,iteration);
			retrieveTweets(true,iteration);
			
			iteration++;
			if (iteration <= iterLimit) {
				LinkedHashMap<String,LinkedHashMap<String,Double>> hfidfMat = calculateFfIdfMat("hashTag");
				discriminativeKeyterms2Classes = addToKeyterms("hashTag",hfidfMat,(classLabels.size()-1)*numberOfHashTags,iteration);
						
				LinkedHashMap<String,LinkedHashMap<String,Double>> ufidfMat = calculateFfIdfMat("url");
				discriminativeKeyterms2Classes = addToKeyterms("url",ufidfMat,(classLabels.size()-1)*numberOfURLs,iteration);
				
				LinkedHashMap<String,LinkedHashMap<String,Double>> userfidfMat = calculateFfIdfMat("userMention");
				discriminativeKeyterms2Classes = addToKeyterms("userMention",userfidfMat,(classLabels.size()-1)*numberOfUserMentions,iteration);
			
				saveNewDocuments(false,true,true,true);
			}
		}

		if (activeMode == false) {
			System.out.println("Automatic Method Results:\n");
		}
		else {
			System.out.println("Considering Equal Scores: " + askCounter + " questions");
		}
		Test();
		System.out.println("===========================================================\n");

		if (activeMode) {
			newlyAddedTweets = new LinkedHashSet<String>();
			InstanceSelection twtSel = new InstanceSelection();
			specificHashTags = twtSel.findHashTagCandidates(tweetId2PredictedLabel,classLabels);
			//LinkedHashMap<String,String> newHashTags = twtSel.randomBasedActiveLearning(specificHashTags);
			
			saveNewDocuments(true,false,false,false);
			LinkedHashMap<String,String> newHashTags = twtSel.findNearDuplicates(specificHashTags);
			newHashTags = checkTheCredibilityofHashTag(newHashTags);
			applyFeedBack(newHashTags);
	
			System.out.println("Finding Near Duplicate: " + askCounter + " questions");
			Test();
			System.out.println("===========================================================\n");
			
			
			LinkedHashMap<String,String> newHashtags2 = twtSel.findHashTagCandidatesForPrecision(specificHashTags);		
			applyFeedBack(newHashtags2);
			//Test();
			
			LinkedHashMap<String,String> newHashtags3 = twtSel.findHashTagCandidatesForRecall(specificHashTags);		
			applyFeedBack(newHashtags3);
			Test();
			
			saveNewDocuments(true,false,false,false);
			IndexingDocuments indexer2 = new IndexingDocuments();
			indexer2.initializeLucene();
			
			Directory dir2 = indexer2.indexDocuments("./data/RetrievedTweets/");
	
			DiscriminativeKeytermsExtraction keyExt = new DiscriminativeKeytermsExtraction();
			LinkedHashMap<String,LinkedHashMap<String,Double>> tfidfMat = keyExt.calcTfIdf(dir2);
			discriminativeKeyterms2Classes = addKeytermsForEachDebate("keyterm",tfidfMat,1,0);
	
			retrieveTweets(true,iteration);
	
			System.out.println("Using HashTags: " + askCounter + " questions");
			Test();
			System.out.println("===========================================================\n");
	
			LinkedHashMap<String,String> newHashtags4 = twtSel.checkReplyChains(specificHashTags);
			newHashtags4 = checkTheCredibilityofHashTag(newHashtags4);
			applyFeedBack(newHashtags4);
			System.out.println("Leveraging Replies: " + askCounter + " questions");
			Test();
			System.out.println("===========================================================\n");
			
			System.out.println(" In total " + askCounter + " queries are asked from the Oracle/user.");
			saveNewDocuments(true,true,true,true);
		}
	}	

	private HashMap<String, keyTermWeights> addKeytermsForEachDebate(String type, LinkedHashMap<String, LinkedHashMap<String, Double>> tfidfMat, int desiredNumber, int iterNumber) 
	{
		HashMap<String,keyTermWeights> res = discriminativeKeyterms2Classes;
		
		for (String labStr:tfidfMat.keySet()) {
			HashMap<String,Double> keyTermValueMap = tfidfMat.get(labStr);
			LinkedList<Entry<String,Double>> list = new LinkedList<Entry<String,Double>>(keyTermValueMap.entrySet());
		    Collections.sort(list, new MyComparator());
		    
		    int resCounter = 1;
		    for (Iterator<Entry<String, Double>> it = list.iterator(); it.hasNext();) {
		    	boolean added = false;
		        Map.Entry<String,Double> entry = (Map.Entry<String,Double>)it.next();
		        keyTermWeights ktw = new keyTermWeights();
		        
		        String keyStr = entry.getKey();
		        if ((!keyStr.contains("&")) && ((!specificHashTags.containsKey(keyStr)) ||(specificHashTags.get(keyStr)==true))) {
			        if (discriminativeKeyterms2Classes.containsKey(keyStr)) {
			        	ktw = discriminativeKeyterms2Classes.get(keyStr);
			        }
			        
			        if (!ktw.weights.containsKey(labStr)) {
			        	double val = 1.0;
			        	if (specificHashTags.containsKey(keyStr)) {
			        		val = 1.5;
			        	}
						added = ktw.addKeyTerm(labStr, val, false);
					}
					
					res.put(keyStr, ktw);
					if (added) {
						//System.out.println(iterNumber + ": " + keyStr + "\t"+ labStr);
						resCounter++;
					}
			        if (resCounter>desiredNumber) {
			        	break;
			        }
		        }
		    }
		}
		return res;
	}

	private void applyFeedBack(LinkedHashMap<String, String> newHashTagsRecall) {		
		
		for (String hStr:newHashTagsRecall.keySet())
		{
			String labStr = newHashTagsRecall.get(hStr);

			//add the founded hashtags to the list of discriminative keyterms
			keyTermWeights ktw = new keyTermWeights();
	       
	        ktw.addKeyTerm(labStr, 1.5, true);
	        discriminativeKeyterms2Classes.put(hStr.toLowerCase(), ktw);
	        
			ArrayList<TweetData> wrongRetrievedTweets = getAllTweetsContainingThisHashTag(hStr);
			for (TweetData twt:wrongRetrievedTweets)
			{
				retrievedTweets.add(twt.getIdStr());
				newlyAddedTweets.add(twt.getIdStr());

				tweetId2PredictedLabel.put(twt.getIdStr(), labStr);
				leftInCloudTweets.remove(twt.getIdStr());
			}
		}		
	}
	
	private ArrayList<TweetData> getAllTweetsContainingThisHashTag(String hStr) 
	{
		ArrayList<TweetData> res = new ArrayList<TweetData>();
		for (String twtId:allTweets.keySet())
		{
			TweetData tweetEntity = allTweets.get(twtId);
			
			TweetEntityExtraction eei = new TweetEntityExtraction(tweetEntity.get("entities").toString());
			LinkedHashMap<String,Integer> tags = eei.getHashTagFreqMap();
	        	
        	for (String t1:tags.keySet())
        	{
        		if (t1.equalsIgnoreCase(hStr))
        		{
        			res.add(tweetEntity);
        		}
        	}
		}
		return res;
	}

	private LinkedHashMap<String, String> checkTheCredibilityofHashTag(
			LinkedHashMap<String, String> newHashTags) 
	{
		myIndexer myindexer = new myIndexer();
		
		LinkedHashMap<String,String> res = new LinkedHashMap<String, String>();
		getTheIndexForAllRetrievedTweets(tweetId2PredictedLabel, myindexer);
		
		for (String hStr:newHashTags.keySet())
		{
			try
			{
				LinkedHashMap<String,Integer> lab2Freq = new LinkedHashMap<String, Integer>();
				
				String querystr = hStr;
				Query q = new QueryParser(Version.LUCENE_34, "text", myindexer.analyzer).parse(querystr);
	
				TotalHitCountCollector collector = new TotalHitCountCollector();
				myindexer.searcher.search(q, collector);
				TopDocs docs = myindexer.searcher.search(q, Math.max(1, collector.getTotalHits()));
				for (int i=0;i<docs.totalHits;i++)
				{
					String tId = myindexer.indexReader.document(i).get("Id");
					
					String labStr = tweetId2PredictedLabel.get(tId);
					int val = 1;
					if (lab2Freq.containsKey(labStr))
					{
						val = lab2Freq.get(labStr) + 1;
					}
					lab2Freq.put(labStr, val);
				}
				if (lab2Freq.size()<2)
				{
					res.put(hStr, newHashTags.get(hStr));
				}
			}
			catch(Exception e)
			{
				e.printStackTrace();
			}
		}
		return res;
	}
	
	private void getTheIndexForAllRetrievedTweets(
			HashMap<String, String> tweetId2PredictedLabel2, myIndexer myindexer) {
		
		Directory dir;
		IndexWriter writer;

		try {
			
			myindexer.analyzer = new WhitespaceAnalyzer(Version.LUCENE_34);		
			dir = new RAMDirectory();
			IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_34, myindexer.analyzer);
			writer = new IndexWriter(dir, config);
			TweetPreProcess ppt = new TweetPreProcess();
			
			for (String tId:tweetId2PredictedLabel.keySet()) {
				Document doc = new Document();
				
				doc.add(new Field("Id", tId, Field.Store.YES, Field.Index.NO));
				doc.add(new Field("text", ppt.process(allTweets.get(tId).get("text").toString(),true, false, true), 
						Field.Store.YES, Field.Index.ANALYZED, Field.TermVector.YES));
		    
				writer.addDocument(doc);
			}
			writer.close();
			myindexer.searcher = new IndexSearcher(dir);
		    myindexer.indexReader = myindexer.searcher.getIndexReader();
		}
		
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	private void Test() {
		try {
						
			HashMap<String,Integer> TruePositives = new HashMap<String, Integer>();
			HashMap<String,Integer> FalsePositives = new HashMap<String, Integer>();
			HashMap<String,Integer> FalseNegatives = new HashMap<String, Integer>();
			
			int correctCounter = 0;
			for (String label:classLabels) {
				TruePositives.put(label.toLowerCase(), 0);
				FalsePositives.put(label.toLowerCase(), 0);
				FalseNegatives.put(label.toLowerCase(), 0);
			}
			
			//Output the rest for clustering
			FileWriter fout = new FileWriter("./data/remainInCloud.txt");
			BufferedWriter bout = new BufferedWriter(fout);
			
			FileWriter fResult = new FileWriter("./data/RetrievedTweets.txt");
			BufferedWriter bResult = new BufferedWriter(fResult);
			
			for (String tId:trueLabels.keySet()) {
				
				String predictedLabel = "none";
				if (tweetId2PredictedLabel.containsKey(tId)) {
					predictedLabel = tweetId2PredictedLabel.get(tId);
					bResult.write(tId + "\t" + predictedLabel + "\n");
				}	
				if (predictedLabel.equalsIgnoreCase("none")) {
					bout.write(tId + "\t" + "None" + "\n");
				}
				
				if (trueLabels.get(tId).equalsIgnoreCase(predictedLabel)) {
					TruePositives.put(predictedLabel.toLowerCase(), TruePositives.get(predictedLabel.toLowerCase()) + 1);
					correctCounter++;

				}
				else {
					FalsePositives.put(predictedLabel.toLowerCase(), FalsePositives.get(predictedLabel.toLowerCase()) + 1);
					String lStr = trueLabels.get(tId).toLowerCase();

					FalseNegatives.put(lStr, (FalseNegatives.get(lStr) + 1));					
				}
			}
			bout.close();
			bResult.close();
			
			double macroPr = 0.0;
			double macroRe = 0.0;
			double totalAccuracy = 0.0;
			
			for (String label:classLabels) {
				int makhraj  = (TruePositives.get(label) + FalsePositives.get(label));
				double pr = 0.0;
				if (makhraj !=0) {
					pr = (TruePositives.get(label) * 1.0)/makhraj;
				}
				macroPr = macroPr + pr;
				
				double re = 0.0;
				makhraj = (TruePositives.get(label) + FalseNegatives.get(label));
				if (makhraj!=0) {
					re = (TruePositives.get(label) * 1.0)/makhraj;
				}				
				macroRe = macroRe + re;

				System.out.println(label + "\tTP= " + TruePositives.get(label) + "\tFP= " + 
						FalsePositives.get(label) + "\tFN= " + FalseNegatives.get(label) + "\tpr= " + pr 
						+ "\tre= " + re);
			}
			totalAccuracy = (correctCounter * 1.0) / trueLabels.size();
			macroPr = macroPr / classLabels.size();
			macroRe = macroRe / classLabels.size();
			
			System.out.println(correctCounter + "\t" + trueLabels.size() + "\t" + tweetId2PredictedLabel.size());
			System.out.println("accuracy = " + totalAccuracy + "\tmacroPr= " + macroPr + "\tmacroRe= " + macroRe);
			
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private LinkedHashMap<String, LinkedHashMap<String, Double>> calculateFfIdfMat(String feature) {
		LinkedHashMap<String,LinkedHashMap<String,Integer>> label2Feature = new LinkedHashMap<String, LinkedHashMap<String,Integer>>();
		
		
		for (String tId:newlyAddedTweets) {
			
			TweetData tweetEntity = allTweets.get(tId);
			TweetEntityExtraction eei = new TweetEntityExtraction(tweetEntity.get("entities").toString());
			LinkedHashMap<String,Integer> featureFreqMap = new LinkedHashMap<String, Integer>();
			
			if (feature.equalsIgnoreCase("hashTag")) {				
				featureFreqMap = eei.getHashTagFreqMap();
			}
			if (feature.equalsIgnoreCase("url")) {
				featureFreqMap = eei.getUrlFreqMap();
			}
			if (feature.equalsIgnoreCase("userMention")) {
				featureFreqMap = eei.getusersMentioned();
			}
	    	
	    	String label = tweetId2PredictedLabel.get(tId);
	    	
	    	LinkedHashMap<String,Integer> hashTagFreq = new LinkedHashMap<String, Integer>();
	    	if (label2Feature.containsKey(label)) {
	    		hashTagFreq = label2Feature.get(label);
	    	}
	    	
	    	for (String tag:featureFreqMap.keySet()) {
	    		int val = 1;
	    		if (hashTagFreq.containsKey(tag)) {
	    			
	    			val = hashTagFreq.get(tag) + 1;
	    		}
	    		hashTagFreq.put(tag, val);
	    		
	    	}
	    	label2Feature.put(label, hashTagFreq);
		}
		
		LinkedHashMap<String,LinkedHashMap<String,Double>> label2Fidf = calcFfidf(label2Feature);
		return label2Fidf;
	}
	
	private LinkedHashMap<String, LinkedHashMap<String, Double>> calcFfidf(LinkedHashMap<String, LinkedHashMap<String, Integer>> label2Feature) {
		LinkedHashMap<String, LinkedHashMap<String, Double>> res = new LinkedHashMap<String, LinkedHashMap<String,Double>>();
		
		//How many times a hashtag appear in different docuements 
		HashMap<String,Integer> Idf = new HashMap<String, Integer>();
		for (String label:label2Feature.keySet()) {
			LinkedHashMap<String,Integer> hashtagFreqs = label2Feature.get(label);
			LinkedHashMap<String,Double> hfIdf = new LinkedHashMap<String, Double>();
			
			for (String hashStr:hashtagFreqs.keySet()) {
				
				if (Idf.containsKey(hashStr)) {
					Idf.put(hashStr, (Idf.get(hashStr) + 1));					
				}
				else {
					Idf.put(hashStr, 1);
				}
				hfIdf.put(hashStr,0.0);
			}
			res.put(label, hfIdf);
		}
		
		for (String label:label2Feature.keySet()) {
			HashMap<String,Integer> hashtagFreqs = label2Feature.get(label);
			int totalFreq = 0;
			for (String hStr:hashtagFreqs.keySet()) {
				totalFreq = totalFreq + hashtagFreqs.get(hStr);
			}
			
			LinkedHashMap<String,Double> hfIdfVals = res.get(label);
			
			for (String hashStr:hashtagFreqs.keySet()) {
				
				double idfVal = Math.log((label2Feature.keySet().size()* 1.0)/(Idf.get(hashStr)));
				double val = ((hashtagFreqs.get(hashStr) * 1.0)/totalFreq) * idfVal;				
				//double val = hashtagFreqs.get(hashStr) / Math.pow((Idf.get(hashStr) * 1.0),1);
				if (hashtagFreqs.get(hashStr)>1) {
					hfIdfVals.put(hashStr, val);
				}				
			}
			res.put(label, hfIdfVals);
		}
		return res;
	}
	
	public void retrieveTweets(boolean consideringAllTweets, int iterNumber) {

		HashMap<String,String> equalScoreTweets = new HashMap<String, String>();
		HashMap<String,Integer> numberTweetsRetrievedBecauseofKeyterm = new HashMap<String, Integer>();
		
		newlyAddedTweets = new LinkedHashSet<String>();    	
		TweetPreProcess ppt = new TweetPreProcess();
		LinkedHashSet<String> newLeftInCloudSet = new LinkedHashSet<String>();
		
		HashSet<String> consideringTweets = new HashSet<String>();
		if (consideringAllTweets) {
			for (String tId:allTweets.keySet()) {
				consideringTweets.add(tId);
			}			 
		}
		else {
			consideringTweets = leftInCloudTweets;
		}
		for (String tId:consideringTweets) {

			if (!askedThroughActiveLearning.containsKey(tId)) {
				TweetData twt = allTweets.get(tId);
				
				TweetEntityExtraction eei = new TweetEntityExtraction(twt.get("entities").toString());
				HashMap<String,Integer> hashFreqMap = eei.getHashTagFreqMap();
		    	
				String tweetTxt = twt.get("text").toString().toLowerCase();
				String processedTxt = ppt.process(tweetTxt,true,false, true);
	    	
				String[] termsInTweet = processedTxt.split(" ");
				        	
				HashMap<String,Double> scores = new HashMap<String, Double>();
				HashMap<String,HashSet<String>> matchedKeyTerms = new HashMap<String, HashSet<String>>();
				
				for (String pw:discriminativeKeyterms2Classes.keySet()) {
					String []keyTermParts = pw.split(" ");
					boolean match = findComplexTerminMap(keyTermParts,termsInTweet);
				
					if (match) {
						
						HashMap<String,Double> whts = discriminativeKeyterms2Classes.get(pw).weights;
				        for (String labelStr:whts.keySet()) {
				        	updateBecauseOfThisKeyTerm(pw,labelStr,matchedKeyTerms);
				        	
				        	double newVal = 0.0;
				        	if (scores.containsKey(labelStr)) {
				        		newVal = scores.get(labelStr);
				        	}
				        	double addedValue = whts.get(labelStr); 

				        	newVal = newVal + addedValue;
				        	scores.put(labelStr, newVal);
				        	
				        	int freq = 1;
				        	if (numberTweetsRetrievedBecauseofKeyterm.containsKey(pw)) {
				        		freq = numberTweetsRetrievedBecauseofKeyterm.get(pw) + 1;
				        	}
			        		numberTweetsRetrievedBecauseofKeyterm.put(pw, freq);
			        	}
			    	}
				}
	        	
	        	HashSet<String> predictedLabels = identifyWinningClass(scores,1,twt.getIdStr());
	        	if (predictedLabels.size()==1) {
	        		String predictedLabel = predictedLabels.iterator().next();
			        if (scores.get(predictedLabel)>threshold) {
			        	tweetId2PredictedLabel.put(twt.getIdStr(),predictedLabel);
			        	retrievedTweets.add(twt.getIdStr());
			        	newlyAddedTweets.add(twt.getIdStr());
			        }
			        else {
			        	newLeftInCloudSet.add(twt.getIdStr());
	        			tweetId2PredictedLabel.remove(twt.getIdStr());
	        			retrievedTweets.remove(twt.getIdStr());
	        			newlyAddedTweets.remove(twt.getIdStr());

			        }
				}
	        	else {
	        		if ((activeMode) && (iterNumber == 1)) { 
		        		if (scores.size()>0) {
							String labStr = "";
							if (askedThroughActiveLearning.containsKey(tId)) {
								labStr = askedThroughActiveLearning.get(tId);
							}
							else if (trueLabels.containsKey(tId)) {
								labStr = trueLabels.get(tId);
								askedThroughActiveLearning.put(tId,labStr);	
								askCounter++;
							}
							else if (askFromUser){
								//System.out.println("Ask About " + tId);
								labStr = getTheLabelFromTheUser(tId);
								askCounter++;
							}
							if (!labStr.equalsIgnoreCase("")) {
								equalScoreTweets.put(tId,labStr);
								
								tweetId2PredictedLabel.put(tId,labStr);		
					        	retrievedTweets.add(tId);
					        	newlyAddedTweets.add(tId);
					        	
					        	updateEveryThingRelatedToThisTweet(twt,labStr,matchedKeyTerms,predictedLabels,numberTweetsRetrievedBecauseofKeyterm);	
		        			}	
		        		}
		        		else {
		        			newLeftInCloudSet.add(tId);
		        		}
	        		}
	        		else {
	        			if (predictedLabels.size()==2) {
	        				Iterator<String> myIter = predictedLabels.iterator();
	        				String firstLabStr = myIter.next();
	        				String secondLabStr = myIter.next();
	        				if (firstLabStr.equalsIgnoreCase("none")) {
	        					tweetId2PredictedLabel.put(twt.getIdStr(),secondLabStr);
					        	retrievedTweets.add(twt.getIdStr());
					        	newlyAddedTweets.add(twt.getIdStr());
	        				}
	        				else if (secondLabStr.equalsIgnoreCase("none")) {
	        					tweetId2PredictedLabel.put(twt.getIdStr(),firstLabStr);
					        	retrievedTweets.add(twt.getIdStr());
					        	newlyAddedTweets.add(twt.getIdStr());
	        				}
	        				else {
	        					newLeftInCloudSet.add(twt.getIdStr());
	        				}
	        			}
	        			else {
		        			newLeftInCloudSet.add(twt.getIdStr());
		        			tweetId2PredictedLabel.remove(twt.getIdStr());
		        			retrievedTweets.remove(twt.getIdStr());
		        			newlyAddedTweets.remove(twt.getIdStr());
	        			}
	        		}
	        	}
			}		
		}
	
		for (String eqTId:equalScoreTweets.keySet()) {
			askedThroughActiveLearning.put(eqTId,equalScoreTweets.get(eqTId));
		}
		
		LinkedHashSet<String> newLeftInCloudSet2 = new LinkedHashSet<String>();
		for (String tId:newLeftInCloudSet) {
			String label = "";
			TweetData tweetEntity = allTweets.get(tId);
			TweetEntityExtraction eei = new TweetEntityExtraction(tweetEntity.get("entities").toString());
			HashMap<String,Integer> urlFreqMap = eei.getUrlFreqMap();
			for (String urlStr:urlFreqMap.keySet()) {
				if (discriminativeKeyterms2Classes.containsKey(urlStr)) {
					Set<String> labels = discriminativeKeyterms2Classes.get(urlStr).weights.keySet();
					
					if (labels.size() == 1) {
						for (String labStr:labels) {
							label = labStr;
						}
					}
				}
			}
			if (!label.equals("")) {
				tweetId2PredictedLabel.put(tId,label);
	        	retrievedTweets.add(tId);
	        	newlyAddedTweets.add(tId);				
			}
			else {
				newLeftInCloudSet2.add(tId);
			}
		}
		leftInCloudTweets = newLeftInCloudSet2;	
	}
	
	public static String getTheLabelFromTheUser(String tId) {
		Scanner in = new Scanner(System.in);

		System.out.println("Please Label this tweet: " + allTweets.get(tId).get("text").toString());
		System.out.print("Valid Labels are: ");
		for (String lStr:classLabels) {
			System.out.print(lStr + " , ");
		}
		System.out.println("\n");
		String userInput = in.nextLine();
		while (!classLabels.contains(userInput)) {
			System.out.println("Please Enter Again");
			userInput = in.nextLine();
		}
		trueLabels.put(tId, userInput);
		askedThroughActiveLearning.put(tId, userInput);
		leftInCloudTweets.remove(tId);
		tweetId2PredictedLabel.put(tId, userInput);
		return userInput;
	}

	
	private void updateEveryThingRelatedToThisTweet(TweetData twt,
			String trueLabel, HashMap<String, HashSet<String>> matchedKeyTerms, HashSet<String> predictedLabels, 
			HashMap<String, Integer> numberTweetsRetrievedBecauseofKeyterm) {
		
		TweetEntityExtraction eet = new TweetEntityExtraction(twt.get("entities").toString());
    	HashMap<String,Integer> urlList = eet.getUrlFreqMap();
    	for (String urlStr:urlList.keySet()) {
    		keyTermWeights ktw = new keyTermWeights();
		       
	        ktw.addKeyTerm(trueLabel, 1.5, true);
	        discriminativeKeyterms2Classes.put(urlStr.toLowerCase(), ktw);
    	}
    	
    	for (String lStr:predictedLabels) {
    		if (!lStr.equalsIgnoreCase(trueLabel)) {
    			HashSet<String> keyTerms = matchedKeyTerms.get(lStr);
    			for (String keyStr:keyTerms) {
    				keyTermWeights ktw = discriminativeKeyterms2Classes.get(keyStr);
    				HashMap<String,Double> weights = ktw.weights;
    				
    				int freq = numberTweetsRetrievedBecauseofKeyterm.get(keyStr);
    				
    				int labFreq = 0;
    				for (String tId:tweetId2PredictedLabel.keySet()) {
    					if (tweetId2PredictedLabel.get(tId).equalsIgnoreCase(lStr)) {
    						labFreq++;
    					}
    				}
    				double ratio = (freq*1.0)/labFreq;
    				//double newVal = weights.get(lStr) - 0.1;
    				
    				double newVal = 1 - (0.4 * ratio);
    				
    				weights.put(lStr, newVal);
    				ktw.weights = weights;
    				discriminativeKeyterms2Classes.put(keyStr.toLowerCase(), ktw);    				
    			}
    		}
    	}		
	}
	
	private HashSet<String> identifyWinningClass(HashMap<String, Double> input, int number, String tId) {
		if (input.size()==0) {
			return new HashSet<String>();
		}
	    
		List<Entry<String,Double>> list = new LinkedList<Entry<String,Double>>(input.entrySet());
	    Collections.sort(list, new MyComparator());

	    HashSet<String> result = new HashSet<String>();
	    int resCounter = 1;
	    
	    Iterator<Entry<String, Double>> it = list.iterator();
	    double lastVal = 0.0;
	    
	    while (it.hasNext()) {
	        Map.Entry<String,Double> entry = (Map.Entry<String,Double>)it.next();
	        
	        result.add(entry.getKey());
	        lastVal = entry.getValue();
	        
	        resCounter++;
	        if (resCounter>number) {
	        	break;
	        }
	    }
	   
	   	while (it.hasNext()) {
	    	Map.Entry<String,Double> nextEntry = (Map.Entry<String,Double>)it.next();
	    	double secondVal = nextEntry.getValue();
	    	if (secondVal == lastVal) {
	    		result.add(nextEntry.getKey());
	    	}
	    }
	   	return result;
	}
	
	private void updateBecauseOfThisKeyTerm(String keyTerm,
			String labelStr, HashMap<String, HashSet<String>> matchedKeyTerms) {
		HashSet<String> keyTerms = new HashSet<String>();
		
		if (matchedKeyTerms.containsKey(labelStr)) {
			keyTerms = matchedKeyTerms.get(labelStr);
		}
		keyTerms.add(keyTerm);
		matchedKeyTerms.put(labelStr, keyTerms);
	}
	
	private boolean findComplexTerminMap(String[] keyTermParts,
			String[] termsInTweet) {
		boolean match = true;
		for (int i=0;i<keyTermParts.length;i++) {
			String partStr = keyTermParts[i];
			boolean findThisPart = false;
			
			for (String tStr:termsInTweet) {
				if (fuzzyMatching(partStr,tStr)) {
					findThisPart = true;
				}
			}
			if (findThisPart==false) {
				match  = false;
			}
		}
		return match;
	}
	
	private boolean fuzzyMatching(String str1, String str2) {
		str1 = str1.toLowerCase();
		str2 = str2.toLowerCase();
		
		if (str1.equalsIgnoreCase(str2)) {
			return true;
		}
		else {
			String str1Editted = str1.replaceAll("[^\\s\\w]+", "");
			String str2Editted = str2.replaceAll("[^\\s\\w]+", "");
			if (str1Editted.equalsIgnoreCase(str2Editted)) {
				return true;
			}
			else {
				return false;
			}
		}
	}

	private HashMap<String, keyTermWeights> addToKeyterms(
			String type, LinkedHashMap<String, LinkedHashMap<String, Double>> tfidfMat,
			int desiredNumber, int iterNumber) {
		
		LinkedHashMap<String,Double> keyTermLableValueMap = new LinkedHashMap<String, Double>();
		for (String labStr:tfidfMat.keySet()) {
			if ((!labStr.equalsIgnoreCase("none")) && (tfidfMat.containsKey(labStr))) {
				LinkedHashMap<String,Double> keyTerm2Val = tfidfMat.get(labStr);
				
				for (String keyStr:keyTerm2Val.keySet()) {
					String myKey = keyStr + "," + labStr;					
					keyTermLableValueMap.put(myKey, keyTerm2Val.get(keyStr));
				}
			}
		}
		    
		LinkedList<Entry<String,Double>> list = new LinkedList<Entry<String,Double>>(keyTermLableValueMap.entrySet());
	    Collections.sort(list, new MyComparator());
	    
	    HashMap<String,keyTermWeights> res = discriminativeKeyterms2Classes;
		
	    int resCounter = 0;
	    for (Iterator<Entry<String, Double>> it = list.iterator(); it.hasNext();) {
	    	boolean added = false;
	        Map.Entry<String,Double> entry = (Map.Entry<String,Double>)it.next();
	        keyTermWeights ktw = new keyTermWeights();
	        
	        String keyStr = entry.getKey().split(",")[0];
	        String labStr = entry.getKey().split(",")[1];
	       
	        
	        if (discriminativeKeyterms2Classes.containsKey(keyStr)) {
	        	ktw = discriminativeKeyterms2Classes.get(keyStr);
	        }
	        
	        if (!ktw.weights.containsKey(labStr)) {
				double val = 1.0/iterNumber;
				if (type.equalsIgnoreCase("hashTag")) {
					val = 3.0/iterNumber;
					added = ktw.addKeyTerm(labStr, val, true);
				}
				else {
					added = ktw.addKeyTerm(labStr, val, false);
				}				
			}
			res.put(keyStr.toLowerCase(), ktw);

			if (added) {
				resCounter++;
			}
	        if (resCounter>desiredNumber) {
	        	break;
	        }
	    }	    
		return res;
	}
	
	
	public void initialize() 
	{	
		DirectoryFileUtil dirFU = new DirectoryFileUtil();
		
		dirFU.checkDir("./data/Debates/",true);
		dirFU.checkDir("./data/Tweets/",true);
		dirFU.checkDir("./data/stop.txt",true);
		dirFU.checkDir("./data/TrueLabels.txt",true);
		
		dirFU.clearDir("./data/NearDuplicates/");
		
		trueLabels = dirFU.loadHashMap("./data/TrueLabels.txt");
		for (String tId:trueLabels.keySet()) {	
			classLabels.add(trueLabels.get(tId).toLowerCase());
		}
		allTweets = loadAllTweets();
		TweetPreProcess.init();
		
		dirFU.makeNewDirectory("./data/RetrievedTweets/");		
		dirFU.makeNewDirectory("./data/NearDuplicates/");
	}
	
	private LinkedHashMap<String, TweetData> loadAllTweets() 
	{
		LinkedHashMap<String,TweetData> res = new LinkedHashMap<String, TweetData>();
		try {
			
			File dir = new File("./data/Tweets/");			
			File[] listOfFiles = dir.listFiles(new FilenameFilter() { 
	            public boolean accept(File dir, String filename)
	                 { return filename.endsWith(""); }
			} );
			if (listOfFiles==null) {
				return null;
			}
			for (File f:listOfFiles) {
			
				//System.out.println("Loading file " + f.getName());
				File inFile = new File("./data/Tweets/" + f.getName());
				FileInputStream fis = new FileInputStream(inFile);	    
			    
			    byte[] data = new byte[(int)inFile.length()];
			    fis.read(data);
			    fis.close();
			    
			    String text = "";
			    text = new String(data, "UTF-8");
			    
				ObjectMapper mapper = new ObjectMapper();
			    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
						    
			    TweetData[] datasets = mapper.readValue(text, TweetList.class).dataset;
			    
			    for (TweetData random:datasets) {
			    	if ((random.get("retweeted_status")==null)) {
			        	res.put(random.getIdStr(), random);
			        	leftInCloudTweets.add(random.getIdStr());
			    	}
			    	
			    	//if it is a retweet, add it to the list of retweeted messages
			    	else {
			    		TweetEntityExtraction etei = new TweetEntityExtraction(random.get("retweeted_status").toString());			    		
			    		String origTId = etei.getOrigTId();
			    		HashSet<String> retweetList = new HashSet<String>();
			    		if (tweetId2ItsRetweets.containsKey(origTId)) {
			    			retweetList = tweetId2ItsRetweets.get(origTId);
			    		}
			    		retweetList.add(random.getIdStr());
			    		tweetId2ItsRetweets.put(origTId,retweetList);
			    		retweetedId2ItsOrigTweet.put(random.getIdStr(), origTId);
			    	}
			    	
			    	if ((random.get("in_reply_to_status_id")!=null)) {
			    		String origTweet = random.get("in_reply_to_status_id").toString();
			    		repliedTweetId2OrigTweet.put(random.getIdStr(), origTweet);
			    		HashSet<String> replyTweetsList = new HashSet<String>();
			    		if (tweetId2ItsReplies.containsKey(origTweet)) {
			    			replyTweetsList = tweetId2ItsReplies.get(origTweet);
			    		}
			    		replyTweetsList.add(random.getIdStr());
			    		tweetId2ItsReplies.put(origTweet, replyTweetsList);
			    	}
			    }
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		HashMap<String,HashSet<String>> newTweetId2ItsReplies = new HashMap<String, HashSet<String>>();

		HashSet<String> alreadyAdded = new HashSet<String>();
		for (String tId:tweetId2ItsReplies.keySet()) {
			if (res.containsKey(tId)) {
				if (!alreadyAdded.contains(tId)) {
					HashSet<String> replyList = new HashSet<String>();
					recursiveFunc(replyList,tId);
					newTweetId2ItsReplies.put(tId, replyList);
					for (String ttId:replyList) {
						alreadyAdded.add(ttId);
					}
				}
			}
		}
		tweetId2ItsReplies = new HashMap<String, HashSet<String>>();
		for (String tId:newTweetId2ItsReplies.keySet()) {
			if (!alreadyAdded.contains(tId)) {
				tweetId2ItsReplies.put(tId, newTweetId2ItsReplies.get(tId));
			}
		}
		
		System.out.println(res.size() + " Tweets are loaded");
		System.out.println(classLabels.size() + " classes are loaded");
		return res;
	}

	private void recursiveFunc(HashSet<String> replyList, String tId) {
		for (String rId:tweetId2ItsReplies.get(tId)) {
			replyList.add(rId);
			if (tweetId2ItsReplies.containsKey(rId)) {
				recursiveFunc(replyList, rId);
			}
		}
	}
	private void saveNewDocuments(boolean isFinal, boolean keepUserMention, boolean keepUrl, boolean KeepStopWords) {
		DirectoryFileUtil dirFU = new DirectoryFileUtil();
		dirFU.clearDir("./data/RetrievedTweets/");
		
		TweetPreProcess pt = new TweetPreProcess();
				
		try {
			FileWriter fout2 = new FileWriter("./data/predictedLabels.txt");
			BufferedWriter bout2 = new BufferedWriter(fout2);
			
			for (String labelVal:classLabels) {
				//System.out.println("Saving tweets for class " + labelVal);
				if (!labelVal.equalsIgnoreCase("none")) {
					
					FileWriter fout = new FileWriter("./data/RetrievedTweets/" + labelVal);
					BufferedWriter bout = new BufferedWriter(fout);
					
					HashSet<String> shouldBeAdded = newlyAddedTweets;
					
					if (isFinal == true) {
						shouldBeAdded = retrievedTweets;
					}
					
					for (String tId:shouldBeAdded) {
						//System.out.println("label for " + tId + " is " + tweetId2PredictedLabel.get(tId) + "\t" + labelVal);
						if (tweetId2PredictedLabel.get(tId).equalsIgnoreCase(labelVal)) {
							//System.out.println("inside");

							TweetData twt = allTweets.get(tId);
							String tweetTxt = twt.get("text").toString().toLowerCase();
							String processedTxt = pt.process(tweetTxt,keepUserMention,keepUrl,KeepStopWords);
							
							if (isFinal == true) {
								//bout.write(tId + "\n");
								bout.write(tweetTxt + "\n\n");
								//bout.write(processedTxt + "\n");
							}
							else {
								//bout.write(processedTxt + "\n\n");
								bout.write(processedTxt + "\n");
							}
							bout2.write(tId + "\t" + labelVal + "\n");
						}			
					}
					bout.close();
				}
			}
			bout2.close();
			
			FileWriter fout4 = new FileWriter("./data/discriminativeKeyterms2Classes.txt"); 
			BufferedWriter bout4 = new BufferedWriter(fout4);
			
			for (String kStr:discriminativeKeyterms2Classes.keySet()) {
				HashMap<String,Double> kws = discriminativeKeyterms2Classes.get(kStr).weights;
				for (String wStr:kws.keySet()) {
					bout4.write(kStr + "\t" + wStr + "\t" + kws.get(wStr) + "\n");
				}
			}
			bout4.close();			
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	class MyComparator implements Comparator<Entry<String,Double>> {
		  public int compare(Entry<String,Double> e1, Entry<String,Double> e2) {
		    return (e2.getValue().compareTo(e1.getValue()));
		  }
		}
}
