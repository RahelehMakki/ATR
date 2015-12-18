package Associate;

import Index.IndexingDocuments;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
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
import java.util.Random;
import java.util.Scanner;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.lucene.store.Directory;

import Data.TweetData;
import Data.TweetList;
import PreProcess.TweetPreProcess;
import Util.DirectoryFileUtil;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public class InstanceSelection {
	int specificHashTagThreshold = 3;
	HashMap<String,HashMap<String,Double>> hashTagSim = new HashMap<String, HashMap<String,Double>>();
	class MyComparator implements Comparator<Entry<String,Double>> {
		  public int compare(Entry<String,Double> e1, Entry<String,Double> e2) {
		    return (e2.getValue().compareTo(e1.getValue()));
		  }
		}
	class MyComparatorInt implements Comparator<Entry<String,Integer>> {
		  public int compare(Entry<String,Integer> e1, Entry<String,Integer> e2) {
		    return (e2.getValue() - (e1.getValue()));
		  }
		}
	public class MyComparatorInt2Int implements Comparator<Entry<Integer,Integer>> {
		  public int compare(Entry<Integer,Integer> e1, Entry<Integer,Integer> e2) {
		    return (e2.getValue() - (e1.getValue()));
		  }
		}	
	HashMap<String,String> tweetId2Label = new HashMap<String,String>();
	String path = "./data/Tweets/";
	
	HashMap<String,Integer> hashTagFreqs = new HashMap<String, Integer>();
	HashMap<String,HashMap<String,Integer>> hashTagCooccureMat = new HashMap<String, HashMap<String,Integer>>(); 
	HashMap<String,HashMap<String,Integer>> hashStr2Df = new HashMap<String, HashMap<String,Integer>>(); 
	
	public HashMap<String,Boolean> findHashTagCandidates(HashMap<String,String> tweetId2Label, HashSet<String> classLabels) {	
		this.tweetId2Label = tweetId2Label;
		
		DirectoryFileUtil dir = new DirectoryFileUtil();
		File[] myFiles = dir.returnLisOfFiles(path,"json");
		
		for (File f:myFiles) {
			extractHashTagInfo(path,f.getName());
		}
		return (sortBasedOndf());
	}
	
	public LinkedHashMap<String,String> randomBasedActiveLearning(HashMap<String, Boolean> specificHashTags) {

		LinkedHashMap<String,String> newHashTags = new LinkedHashMap<String, String>();
		
		Random rand = new Random();
		HashSet<Integer> selected = new HashSet<Integer>();
		//String[] myIter = (String[]) TweetDebateAssociation.trueLabels.keySet().toArray();
		String[] myIter = new String[TweetDebateAssociation.trueLabels.keySet().size()];
		int i = 0;
		for (String tId:TweetDebateAssociation.trueLabels.keySet()) {
			myIter[i] = tId;
			i++;
		}
		while (selected.size()<=100) {
			int randIdx = rand.nextInt(TweetDebateAssociation.trueLabels.size());
			if (!selected.contains(randIdx)) {
				selected.add(randIdx);
				String tId = myIter[randIdx];
				String label = TweetDebateAssociation.trueLabels.get(tId);
				TweetDebateAssociation.askedThroughActiveLearning.put(tId,label);
				TweetDebateAssociation.tweetId2PredictedLabel.put(tId, label);
				TweetDebateAssociation.newlyAddedTweets.add(tId);
				TweetDebateAssociation.retrievedTweets.add(tId);
				TweetDebateAssociation.leftInCloudTweets.remove(tId);
				TweetDebateAssociation.askCounter++;
				
				addDiscriminativeHashTag(tId, specificHashTags, newHashTags, label);
			}
		}
		return newHashTags;
	}

	private HashMap<String,Boolean> sortBasedOndf() 
	{
		HashMap<String,Boolean> res = new HashMap<String, Boolean>();
		HashMap<String,Integer> hashDf = new HashMap<String, Integer>();
		for (String hStr:hashStr2Df.keySet())
		{
			HashMap<String,Integer> labelVec = hashStr2Df.get(hStr);
			int labCounter = 0;
			for (String labStr:labelVec.keySet())
			{
				//System.out.println(hStr + " in  " + labStr + labelVec.get(labStr));
				if (labelVec.get(labStr)>0)
				{
					labCounter++;
				}
			}
			hashDf.put(hStr, labCounter);
		}
			
		List<Entry<String,Integer>> list = new LinkedList<Entry<String,Integer>>(hashDf.entrySet());
		Collections.sort(list, new MyComparatorInt());
	    
	    for (Iterator<Entry<String, Integer>> it = list.iterator(); it.hasNext();) {
	    	Entry<String, Integer> myEntry = it.next();
	    	int totalFreq = 0;
	    	for (String lStr:hashStr2Df.get(myEntry.getKey()).keySet()) {
	    		totalFreq = totalFreq + hashStr2Df.get(myEntry.getKey()).get(lStr);
	    	}
	    	if (myEntry.getValue()>specificHashTagThreshold) {
	    		res.put(myEntry.getKey(), false);

	    	}
	    	else {
	    		res.put(myEntry.getKey(), true);

	    	}
	    }
		return res;
	}

	private void extractHashTagInfo(String path2, String fileName) {
		try {
			File inFile = new File(path + fileName);
			FileInputStream fis = new FileInputStream(inFile);	    
		    
		    byte[] data = new byte[(int)inFile.length()];
		    fis.read(data);
		    fis.close();
		    
		    String text = "";
		    text = new String(data, "UTF-8");
		    
			ObjectMapper mapper = new ObjectMapper();
		    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
		
		    TweetList jsonData = mapper.readValue(text, TweetList.class);
		    TweetData[] datasets = jsonData.dataset;
		    
		    for (TweetData tweetEntity : datasets) {
		    	String tId = tweetEntity.getIdStr();
		    	
		    	if ((tweetId2Label.containsKey(tId)) && (!tweetId2Label.get(tId).equalsIgnoreCase("none"))) {
		    		String labStr = tweetId2Label.get(tId);
		    		
			    	TweetEntityExtraction eei = new TweetEntityExtraction(tweetEntity.get("entities").toString());
					HashMap<String,Integer> tags = eei.getHashTagFreqMap();
			        	
		        	for (String t1:tags.keySet()) {
		        		int freq = 1;
		        		if (hashTagFreqs.containsKey(t1)) {
		        			freq = hashTagFreqs.get(t1) + 1;
		        		}
		        		hashTagFreqs.put(t1, freq);
		        		
		        		HashMap<String,Integer> labelVec = new HashMap<String, Integer>();
		        		if (hashStr2Df.containsKey(t1)) {
		        			labelVec = hashStr2Df.get(t1);		        			
		        		}
		        		int freq2 = 1;
		        		if (labelVec.containsKey(labStr)) {
		        			freq2 = labelVec.get(labStr) + 1;
		        		}
		        		labelVec.put(labStr, freq2);
		        		hashStr2Df.put(t1, labelVec);
		        		
		        		HashMap<String,Integer> cooccurVec = new HashMap<String, Integer>();
	        			if (hashTagCooccureMat.containsKey(t1)) {
	        				cooccurVec = hashTagCooccureMat.get(t1);
	        			}
		        		
		        		for (String t2:tags.keySet()) {
			        		if (!t1.equals(t2)) {
			        			int val = 1;
			        			if (cooccurVec.containsKey(t2))
			        			{
			        				val = cooccurVec.get(t2) + 1;
			        			}
			        			cooccurVec.put(t2, val);
			        		}		        	
			        	}
		        		hashTagCooccureMat.put(t1, cooccurVec);	
			    	}
		    	}
		    }
		}
	    catch(Exception e) {
	    	e.printStackTrace();
	    }
	}

	public LinkedHashMap<String, String> findNearDuplicates(HashMap<String, Boolean> specificHashTags) {
		class Clusterer {
			public LinkedHashMap<Integer, LinkedHashSet<String>> findNearDuplicate(LinkedHashMap<String,TweetData> data, int clusterSize) {
				int numClusters = 0;
				LinkedHashMap<Integer,LinkedHashSet<String>> clusters = new LinkedHashMap<Integer, LinkedHashSet<String>>();
				
				TweetPreProcess ppt = new TweetPreProcess();
								
				LinkedHashSet<String> notClusteredTweets = new LinkedHashSet<String>();
				LinkedHashSet<String> clusteredTweets = new LinkedHashSet<String>();
				
				for (String twtId:data.keySet()) {
					notClusteredTweets.add(twtId);			
				}
				for (String tId:notClusteredTweets) {					
					if (!clusteredTweets.contains(tId)) {
						TweetData twtDat = data.get(tId);						
						String processedT1 = ppt.process(twtDat.get("text").toString(),false,false, true);
						
						numClusters++;
						LinkedHashSet<String> tweetsInCluster = new LinkedHashSet<String>();
						tweetsInCluster.add(tId);
						clusteredTweets.add(tId);
						
						for (String tId2:notClusteredTweets) {
							if ((!clusteredTweets.contains(tId2)) && (!tId.equals(tId2))) {
								
								TweetData twtDat2 = data.get(tId2);								
								String processedT2 = ppt.process(twtDat2.get("text").toString(),false,false, true);
								
								double score = nearDuplicateScore(processedT1, processedT2);
								if (score>0.9) {
									tweetsInCluster.add(tId2);
									clusteredTweets.add(tId2);
								}
						
							}
						}
						clusters.put(numClusters,tweetsInCluster);		
					}
				}
				
				int max = 0;
				
				for (int clsId:clusters.keySet()) {
					if (clusters.get(clsId).size()>max) {
						max = clusters.get(clsId).size();
					}
				}
				
				LinkedHashMap<Integer,LinkedHashSet<String>> res = new LinkedHashMap<Integer, LinkedHashSet<String>>();
				
				String path = "./data/NearDuplicates/";
				for (int clsId:clusters.keySet()) {
					if (clusters.get(clsId).size()>clusterSize) {
						try {
							FileWriter fout = new FileWriter(path + clsId);
							BufferedWriter bout = new BufferedWriter(fout);
							for (String twtId:clusters.get(clsId)) {
								bout.write(twtId + "\n" + data.get(twtId).get("text").toString() + "\n\n");
							}
							bout.close();
						}
						catch(Exception e) {
							e.printStackTrace();
						}
						res.put(clsId, clusters.get(clsId));
					}
				}
				//Write2File////
				try{
					FileWriter fout = new FileWriter("./data/listOfNearDuplicates");
					BufferedWriter bout = new BufferedWriter(fout);
					for (int classId:res.keySet()) {
						LinkedHashSet<String> tIds = res.get(classId);
						int cc = 0;
						for (String tId:tIds) {
							
							cc++;
							if (cc==tIds.size()) {
								bout.write("u'" + tId + "' ");	
							}
							else {
								bout.write("u'" + tId + "', ");
							}
						}
						bout.write("\n");
					}
					bout.close();
				}
				catch(Exception e) {
					e.printStackTrace();
				}
				return res;			
			}
			
			private double nearDuplicateScore(String t1,String t2) {
				double res = 0;
				int len = 0;
				int commonTerms = 0;
				
				String[] terms1 = t1.split(" ");
				HashSet<String> vocab1 = new HashSet<String>();
				
				for (String tStr1:terms1) {
					vocab1.add(tStr1);
				}
				
				String[] terms2 = t2.split(" ");
				HashSet<String> vocab2 = new HashSet<String>();
				for (String tStr2:terms2) {
					vocab2.add(tStr2);
				}
				
				if ((vocab1.size() != 0) && (vocab2.size() != 0)) {
					HashSet<String> vocab = vocab1;
					len = vocab1.size();
					
					if (vocab2.size() < vocab1.size()) {
						vocab = vocab2;
						len = vocab2.size();
					}
					
					for (String tStr:vocab) {
						 if ((vocab1.contains(tStr)) && (vocab2.contains(tStr))) {
							 commonTerms++;
						 }
					}
				}
				res = (commonTerms * 1.0)/len;
				return res;
			}

		}

		LinkedHashMap<String,String> newHashTags = new LinkedHashMap<String, String>();
		
		Clusterer myClusterer = new Clusterer();
		LinkedHashMap<String,TweetData> notClusteredTweets = new LinkedHashMap<String, TweetData>();
		for (String tId:TweetDebateAssociation.leftInCloudTweets) {
		//for (String tId:TweetDebateAssociation.allTweets.keySet()) {
			if (TweetDebateAssociation.trueLabels.containsKey(tId)) {
				notClusteredTweets.put(tId, TweetDebateAssociation.allTweets.get(tId));
			}
		}
		LinkedHashMap<Integer,LinkedHashSet<String>> candidateTweets = myClusterer.findNearDuplicate(notClusteredTweets,5);
//		LinkedHashMap<Integer,LinkedHashSet<String>> candidateTweets = loadClusters();
		//filterClusters(candidateTweets);
		
		LinkedHashMap<Integer,Integer> clusterfFreqMap = new LinkedHashMap<Integer, Integer>();
		for (int myIdx:candidateTweets.keySet()) {
			clusterfFreqMap.put(myIdx, candidateTweets.get(myIdx).size());
		}
		
		LinkedList<Entry<Integer,Integer>> list = new LinkedList<Entry<Integer,Integer>>(clusterfFreqMap.entrySet());
		Collections.sort(list, new MyComparatorInt2Int());
	    
		Iterator<Entry<Integer, Integer>> it = list.iterator();
		Scanner in = new Scanner(System.in);
		int counter = 0;
		
		while (it.hasNext()) {


			Entry<Integer,Integer> myEntry = it.next();
			int clsIdx = myEntry.getKey();		
		
			LinkedHashSet<String> clusteredTweets = candidateTweets.get(clsIdx);
			String label = ""; 
			Iterator<String> myIter = null;
			if (clusteredTweets.size()>0) {
				myIter = clusteredTweets.iterator();
			
				while(myIter.hasNext()) {
					String tweetId = myIter.next();
					if (TweetDebateAssociation.askedThroughActiveLearning.containsKey(tweetId)) {
						label = TweetDebateAssociation.askedThroughActiveLearning.get(tweetId);
						break;
					}
					else if (TweetDebateAssociation.trueLabels.containsKey(tweetId)) {
						label = TweetDebateAssociation.trueLabels.get(tweetId);
						TweetDebateAssociation.askedThroughActiveLearning.put(tweetId,label);
						TweetDebateAssociation.askCounter++;
						break;
					}
					else if (TweetDebateAssociation.askFromUser){
						label = TweetDebateAssociation.getTheLabelFromTheUser(tweetId);
						TweetDebateAssociation.askCounter++;
						break;
					}
				}
			}
			
			if (!label.equals("")) {
				for (String tId:clusteredTweets) {
					TweetDebateAssociation.tweetId2PredictedLabel.put(tId, label);
					TweetDebateAssociation.askedThroughActiveLearning.put(tId,label);
					TweetDebateAssociation.newlyAddedTweets.add(tId);
					TweetDebateAssociation.retrievedTweets.add(tId);
					TweetDebateAssociation.leftInCloudTweets.remove(tId);
					
					addDiscriminativeHashTag(tId, specificHashTags, newHashTags, label);
				}
			}
			counter++;
			if (counter>20) {
				break;
			}
		}
		return newHashTags;
	}
	
	private LinkedHashMap<Integer, LinkedHashSet<String>> loadClusters() {
		LinkedHashMap<Integer,LinkedHashSet<String>> res = new LinkedHashMap<Integer,LinkedHashSet<String>>();
		try {
			FileInputStream fin = new FileInputStream("./data/listOfDuplicates");
			DataInputStream din = new DataInputStream(fin);
			BufferedReader bin = new BufferedReader(new InputStreamReader(din));
			
			String text = "";
			int clusterID = 1;
			while ((text = bin.readLine())!= null) {
				LinkedHashSet<String> nextCluster = new LinkedHashSet<String>();
				String[] parts = text.split(", ");
				for (String pStr:parts) {
					String tId = pStr.substring(2,pStr.length()-1);
					if (TweetDebateAssociation.allTweets.containsKey(tId)) {
						nextCluster.add(tId);
					}
				}
				res.put(clusterID, nextCluster);
				clusterID++;
			}
			bin.close();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		return res;
	}

	private void filterClusters(LinkedHashMap<Integer, LinkedHashSet<String>> candidateTweets) {
		TweetPreProcess myProcessor = new TweetPreProcess();
		Iterator<Integer> myIter = candidateTweets.keySet().iterator();
		while (myIter.hasNext()) {
			int clusterIdx = myIter.next();
			LinkedHashSet<String> twtIds = candidateTweets.get(clusterIdx);
			for(String tId:twtIds) {
				String twtText = TweetDebateAssociation.allTweets.get(tId).get("text").toString();
				String processedTxt = myProcessor.process(twtText, true, true, false);
				if (!hasKeyTerm(processedTxt)) {
					myIter.remove();
					break;
				}
			}
			 
		}
	}

	private boolean hasKeyTerm(String twtText) {
		String[] parts = twtText.split(" ");
		for (String kStr:TweetDebateAssociation.discriminativeKeyterms2Classes.keySet()) {
			for (String pt:parts) {
				if (pt.equalsIgnoreCase(kStr)) {
					return true;
				}
			}
		}
		return false;
	}
	private void addDiscriminativeHashTag(String tId,
			HashMap<String, Boolean> specificHashTags, HashMap<String, String> newHashTags, String origTweetLab) {
		TweetEntityExtraction eei = new TweetEntityExtraction(TweetDebateAssociation.allTweets.get(tId).get("entities").toString());
		String hashTagStr = eei.getHashTagsStr();
		
		if (!hashTagStr.equals("")) {
			HashMap<String,Integer> hashTagMap = eei.getHashTagFreqMap();
			for (String hStr:hashTagMap.keySet()) {
				if ((!specificHashTags.containsKey(hStr)) || (specificHashTags.get(hStr) == true)) {
					newHashTags.put(hStr, origTweetLab);
				}		
			}
		}
		HashMap<String,Integer> urls = eei.getUrlFreqMap();
		for (String url:urls.keySet()) {
			newHashTags.put(url, origTweetLab);
		}
	}
	
	public LinkedHashMap<String,String> findHashTagCandidatesForPrecision(HashMap<String, Boolean> specificHashTags) {
		LinkedHashMap<String,LinkedHashMap<String,Integer>> hashTag2LabelsFreq = new LinkedHashMap<String, LinkedHashMap<String,Integer>>();
		hashTag2LabelsFreq = extractHashTag2DF(specificHashTags);
		
		LinkedHashSet<String> alreadyAddedToPrecision = new LinkedHashSet<String>();
		LinkedHashSet<String> candidateHashTagsForPrecision = new LinkedHashSet<String>();

		candidateHashTagsForPrecision = selectHashTagCandidatesForPrecision(5,hashTag2LabelsFreq,alreadyAddedToPrecision);
		
		LinkedHashMap<String,String> newHashTags = findLabelofHashTagsByActiveLearning(candidateHashTagsForPrecision,hashTag2LabelsFreq);
		return newHashTags;
	}
	public LinkedHashMap<String,String> findHashTagCandidatesForRecall(HashMap<String, Boolean> specificHashTags) {	
		LinkedHashMap<String,LinkedHashMap<String,Integer>> hashTag2LabelsFreq = new LinkedHashMap<String, LinkedHashMap<String,Integer>>();
		hashTag2LabelsFreq = extractHashTag2DF(specificHashTags);
		
		LinkedHashSet<String> alreadyAddedToRecall = new LinkedHashSet<String>();
		hashTag2LabelsFreq = extractHashTag2DF(specificHashTags);		
		LinkedHashSet<String> candidateHashTagsForRecall = new LinkedHashSet<String>();
		//val = 8
		candidateHashTagsForRecall = selectHashTagCandidatesForRecall("word",8, specificHashTags,alreadyAddedToRecall);
//		for (String str:candidateHashTagsForRecall){
//			System.out.println(str + " selected for recall");
//		}
		LinkedHashMap<String,String> newHashTags = findLabelofHashTagsByActiveLearning(candidateHashTagsForRecall,hashTag2LabelsFreq);
		
		return newHashTags;
	}

	private LinkedHashSet<String> selectHashTagCandidatesForRecall(String mode,int candidateNumber, 
			HashMap<String, Boolean> specificHashTags, LinkedHashSet<String> alreadyAddedToRecall) { 
		
		LinkedHashSet<String> res = new LinkedHashSet<String>();		
		LinkedHashMap<String,ArrayList<String>> hashTag2ItsUnlabeledTweets = new LinkedHashMap<String, ArrayList<String>>();

		LinkedHashSet<Double> actualValues = new LinkedHashSet<Double>();
		
		LinkedHashMap<String,Double> maxSimHashTag2ClassesMap = new LinkedHashMap<String, Double>();
		LinkedHashMap<String,Integer> hashTagFreqMap = sortHashTagsByFreq(hashTag2ItsUnlabeledTweets);
		LinkedHashMap<String,Integer> selectedHashTags2Freq = new LinkedHashMap<String, Integer>();
		
		for (String hStr:hashTagFreqMap.keySet()) {
			if ((!alreadyAddedToRecall.contains(hStr)) && (!hStr.equalsIgnoreCase("con"))){
				if (hashTagFreqMap.get(hStr)>2) {
					if ((!specificHashTags.containsKey(hStr))||((specificHashTags.containsKey(hStr)) && (specificHashTags.get(hStr) == true))) {
						ArrayList<String> tweetArr = hashTag2ItsUnlabeledTweets.get(hStr);
						writeToHashTagFiles(hStr,tweetArr);
						
						HashMap<String,Double> maxLabValMap;
						DirectoryFileUtil dirFU = new DirectoryFileUtil();
						dirFU.clearDir("./data/Cosine/");
						dirFU.copyDir("./data/RetrievedTweets/", "./data/Cosine/");
						dirFU.copyFile("./data/HashTagFiles/" + hStr,"./data/Cosine/"+ hStr);
							
						maxLabValMap = getMaxSimVal2Classes(hStr);
						
						double val = 0.0;
						for (String labStr:maxLabValMap.keySet()) {
							val = maxLabValMap.get(labStr); 
						}
						//val = val * (Math.log(tweetArr.size()));
						maxSimHashTag2ClassesMap.put(hStr, val);
						actualValues.add(val);
					}
				}
			}
		}

		LinkedList<Entry<String,Double>> list = new LinkedList<Entry<String,Double>>(maxSimHashTag2ClassesMap.entrySet());
		Collections.sort(list, new MyComparator());
	    
		int hCounter = 1;
		
	    for (Iterator<Entry<String, Double>> it = list.iterator(); it.hasNext();) {
	    	Entry<String, Double> myEntry = it.next();
	    	boolean add = true; 
			if (TweetDebateAssociation.discriminativeKeyterms2Classes.containsKey(myEntry.getKey())) {
				keyTermWeights ktw = TweetDebateAssociation.discriminativeKeyterms2Classes.get(myEntry.getKey());
				HashMap<String,Double> ktWeigts = ktw.weights;
				if (ktWeigts.size()==1) {
					add = false;
					for (String labStr:ktWeigts.keySet()) {
						ktWeigts.put(labStr, 1.5);
					}
					ktw.weights = ktWeigts;
					TweetDebateAssociation.discriminativeKeyterms2Classes.put(myEntry.getKey().toLowerCase(), ktw);
				}
			}
			if (add) {
	    		res.add(myEntry.getKey());
	    		alreadyAddedToRecall.add(myEntry.getKey());
	    	
		    	hCounter++;
		    	if (hCounter>candidateNumber) {
		    		break;
		    	}
			}
	    }
	    
	    LinkedHashSet<String> newRes = new LinkedHashSet<String>();
	    for (String hStr:res) {
	    	selectedHashTags2Freq.put(hStr, hashTagFreqMap.get(hStr)); 
	    }
	    LinkedList<Entry<String,Integer>> list2 = new LinkedList<Entry<String,Integer>>(selectedHashTags2Freq.entrySet());
		Collections.sort(list2, new MyComparatorInt());
	
	    for (Iterator<Entry<String, Integer>> it2 = list2.iterator(); it2.hasNext();) {
	    	Entry<String, Integer> myEntry = it2.next();
	    	newRes.add(myEntry.getKey());
	    	//System.out.println(myEntry.getKey() + "\t" + myEntry.getValue());
	    }
		return newRes;
	}
	private LinkedHashMap<String, Integer> sortHashTagsByFreq(LinkedHashMap<String, ArrayList<String>> hashTag2ItsUnlabeledTweets) {	
		LinkedHashMap<String,Integer> hashTags = new LinkedHashMap<String, Integer>();
		
		for (String tId:TweetDebateAssociation.leftInCloudTweets) {
			TweetData myTwt = TweetDebateAssociation.allTweets.get(tId);
			
			TweetEntityExtraction eei = new TweetEntityExtraction(myTwt.get("entities").toString());			
			LinkedHashMap<String,Integer> hashFreqMap = eei.getHashTagFreqMap();
			for(String hashStr:hashFreqMap.keySet()) {
				ArrayList<String> tweetSet = new ArrayList<String>();
				if (hashTag2ItsUnlabeledTweets.containsKey(hashStr)) {
					tweetSet = hashTag2ItsUnlabeledTweets.get(hashStr);
				}
				tweetSet.add(tId);
				hashTag2ItsUnlabeledTweets.put(hashStr, tweetSet);
				
				int val = 1;
				if (hashTags.containsKey(hashStr)) {
					val = hashTags.get(hashStr) + 1;
				}
				hashTags.put(hashStr, val);
			}
		}
		return hashTags;		
	}
	private LinkedHashMap<String, LinkedHashMap<String, Integer>> extractHashTag2DF(HashMap<String, Boolean> specificHashTags) {
		LinkedHashMap<String,LinkedHashMap<String,Integer>> res = new LinkedHashMap<String, LinkedHashMap<String,Integer>>();
		
		for (String tId:TweetDebateAssociation.tweetId2PredictedLabel.keySet()) {
			String labStr = TweetDebateAssociation.tweetId2PredictedLabel.get(tId);
			TweetData myTwt = TweetDebateAssociation.allTweets.get(tId);
			
			TweetEntityExtraction eei = new TweetEntityExtraction(myTwt.get("entities").toString());
			LinkedHashMap<String,Integer> hashFreqMap = eei.getHashTagFreqMap();
			
			for(String hashStr:hashFreqMap.keySet()) {
				if ((specificHashTags.containsKey(hashStr)) && (specificHashTags.get(hashStr)) == true) {
					LinkedHashMap<String,Integer> labelFreqVec = new LinkedHashMap<String, Integer>(); 
					if (res.containsKey(hashStr)) {
						labelFreqVec = res.get(hashStr);
					}
					int val = 1;
					if (labelFreqVec.containsKey(labStr)){
						val = labelFreqVec.get(labStr) + 1;
					}
					labelFreqVec.put(labStr, val);
					res.put(hashStr, labelFreqVec);
				}
			}
		}
		return res;
	}
	private LinkedHashMap<String, String> findLabelofHashTagsByActiveLearning(LinkedHashSet<String> selectedCandidates, 
			LinkedHashMap<String, LinkedHashMap<String, Integer>> hashTag2LabelsFreq) {
		LinkedHashMap<String,ArrayList<String>> hashTag2AllofItsTweets = loadHashTag2TweetMap(selectedCandidates);
		
		LinkedHashMap<String,String> res = new LinkedHashMap<String, String>();
		Random rand = new Random();
		
		for (String hStr:selectedCandidates) {
			LinkedHashMap<String,Integer> labelKNN = new LinkedHashMap<String, Integer>();			
			ArrayList<String> tweetArr = hashTag2AllofItsTweets.get(hStr);
			
			int tCounter = 0;
			int loopCounter = 0;
			LinkedHashSet<Integer> alreadySeenTweet = new LinkedHashSet<Integer>();
			while ((tCounter<3) && (loopCounter<tweetArr.size())) {	
				int randomIdx = 0; 
				while (alreadySeenTweet.contains(randomIdx)) {
					randomIdx = rand.nextInt(tweetArr.size()); 
				}
				alreadySeenTweet.add(randomIdx);
				String tId = tweetArr.get(randomIdx);
				loopCounter++;
				String labStr = "";

				if (TweetDebateAssociation.trueLabels.containsKey(tId)) {
					
					if (TweetDebateAssociation.askedThroughActiveLearning.containsKey(tId)) {
						labStr = TweetDebateAssociation.askedThroughActiveLearning.get(tId);
					}
					else if (TweetDebateAssociation.trueLabels.containsKey(tId)) {
						labStr = TweetDebateAssociation.trueLabels.get(tId);
						TweetDebateAssociation.askedThroughActiveLearning.put(tId,labStr);
						TweetDebateAssociation.askCounter++;
					}
					tCounter++;
					TweetDebateAssociation.tweetId2PredictedLabel.put(tId, labStr);
					TweetDebateAssociation.askedThroughActiveLearning.put(tId,labStr);
					TweetDebateAssociation.leftInCloudTweets.remove(tId);

				}
				else if (TweetDebateAssociation.askFromUser){
					labStr = TweetDebateAssociation.getTheLabelFromTheUser(tId);
					TweetDebateAssociation.askCounter++;
					tCounter++;
				}
					
				int val = 1;
				if (labelKNN.containsKey(labStr)) {
					val = labelKNN.get(labStr) + 1;
				}
				labelKNN.put(labStr, val);
			
						
			}
			String winningClass = "none";
			int max = 0;
			for (String labStr:labelKNN.keySet()) {
				if (labelKNN.get(labStr)>max) {
					max = labelKNN.get(labStr);
					winningClass = labStr;
				}
			}
			if (labelKNN.size()==1) {
				//System.out.println(" hashtag " + hStr + " belongs to " + winningClass);
				res.put(hStr, winningClass);
			}			
		}
		return res;
	}
	private LinkedHashSet<String> selectHashTagCandidatesForPrecision(int desiredNumber, 
			LinkedHashMap<String, LinkedHashMap<String, Integer>> hashTag2LabelsFreq, LinkedHashSet<String> alreadyAddedToPrecision) {
		LinkedHashMap<String,Integer> candidatesFreq = new LinkedHashMap<String, Integer>();
		LinkedHashMap<String,String> autoCorrectCandidates = new LinkedHashMap<String, String>();
		
		LinkedHashMap<String,Integer> candidateHashTags2 = new LinkedHashMap<String, Integer>();
		LinkedHashSet<String> hashTagsWithDFEqualTo1 = new LinkedHashSet<String>();
		
		//Active Learning with Hashtags DF>1
		for (String hStr:hashTag2LabelsFreq.keySet()) {			
			if (!alreadyAddedToPrecision.contains(hStr)) {
				int vecSize = hashTag2LabelsFreq.get(hStr).size();
				if (vecSize>1) {
					LinkedHashMap<String,Integer> labFreqVec = hashTag2LabelsFreq.get(hStr);
					int totalFreq = 0;
					String firstLab = "";
					int max = 0;
					
					for (String labStr:labFreqVec.keySet()) {
						if (labFreqVec.get(labStr) > max) {
							max = labFreqVec.get(labStr);
							firstLab = labStr;
						}
						totalFreq = totalFreq + labFreqVec.get(labStr);							
					}
					double entropyVal = calcEntropy(hashTag2LabelsFreq.get(hStr));
					
					if (entropyVal>0.7) {
						candidatesFreq.put(hStr, totalFreq);
					}
					else {
						autoCorrectCandidates.put(hStr, firstLab);
					}
				}
				if (vecSize == 1) {
					hashTagsWithDFEqualTo1.add(hStr);
				}
			}
		}
		
		LinkedHashSet<String> res = new LinkedHashSet<String>();
		
		//Active Learning with Hashtags DF == 1
		LinkedHashMap<String,ArrayList<String>> hashTag2AllofItsTweets = loadHashTag2TweetMap(hashTagsWithDFEqualTo1);
		for (String hStr:hashTag2AllofItsTweets.keySet()) {
			LinkedHashMap<String,Integer> predictedVecMap = hashTag2LabelsFreq.get(hStr);
			int frequency = 0;
			for (String lStr:predictedVecMap.keySet()) {
				frequency = predictedVecMap.get(lStr);
				candidateHashTags2.put(hStr,frequency);
			}
		}
		LinkedList<Entry<String,Integer>> list1 = new LinkedList<Entry<String,Integer>>(candidateHashTags2.entrySet());
		Collections.sort(list1, new MyComparatorInt());
	    
		Iterator<Entry<String, Integer>> it1 = list1.iterator();
		
		int counter1 = 1;
		while (it1.hasNext()) {
			Entry<String,Integer> myEnt = it1.next();
			String hStr = myEnt.getKey();
			
			if (!hStr.equalsIgnoreCase("con")) {
				ArrayList<String> tweetArr = hashTag2AllofItsTweets.get(hStr);
				
				writeRetrievedTweetsForSimilarityCalculation(tweetArr);
				writeToHashTagFiles(hStr,tweetArr);
				DirectoryFileUtil dirFU = new DirectoryFileUtil();
				dirFU.copyFile("./data/HashTagFiles/" + hStr, "./data/Cosine/" + hStr + "-tag");

				HashMap<String,Double> maxSimmap = getMaxSimVal2Classes(hStr + "-tag");
				
				String trueLabStr = "";
				for (String lStr:maxSimmap.keySet()) {
					trueLabStr = lStr;
				}
				
				String predictedLabStr = "";
				for (String lStr:hashTag2LabelsFreq.get(hStr).keySet()) {
					predictedLabStr = lStr;
				}					
				if ((!predictedLabStr.equalsIgnoreCase(trueLabStr)) && (!trueLabStr.equals(""))) {
					res.add(hStr);
					counter1++;					
				}
			}
			if (counter1==4) {
				break;
			}
		}
		
		LinkedList<Entry<String,Integer>> list = new LinkedList<Entry<String,Integer>>(candidatesFreq.entrySet());
		Collections.sort(list, new MyComparatorInt());
	    
		Iterator<Entry<String, Integer>> it = list.iterator();
		
		int counter = 1;
		while (it.hasNext()) {
			Entry<String,Integer> myEntry = it.next();
			res.add(myEntry.getKey());
			//System.out.println(myEntry.getKey());
			
			counter++;
			if (counter>desiredNumber) {
				break;
			}
		}
	    return res;		
    }
	private double calcEntropy(HashMap<String, Integer> replyListVotes) {
		int totalFreq = 0;
		for (String labStr:replyListVotes.keySet()) {
			totalFreq = totalFreq + replyListVotes.get(labStr);
		}
		
		double val = 0.0;
		for (String labStr:replyListVotes.keySet()) {
			double p = (replyListVotes.get(labStr)*1.0)/(totalFreq); 
			double tempVal1 = Math.log(p);
			double tempVal2 = Math.log(2);
			double tempVal = (tempVal1/tempVal2) * -1.0;
			val = val + (p*tempVal);
		}
		return val;
	}
	private LinkedHashMap<String, ArrayList<String>> loadHashTag2TweetMap(LinkedHashSet<String> candidateHashTags) {
		LinkedHashMap<String,ArrayList<String>> res = new LinkedHashMap<String, ArrayList<String>>();
		for (String tId:TweetDebateAssociation.allTweets.keySet()){
			TweetData tweetEntity = TweetDebateAssociation.allTweets.get(tId);
			TweetEntityExtraction eei = new TweetEntityExtraction(tweetEntity.get("entities").toString());
			LinkedHashMap<String,Integer> tags = eei.getHashTagFreqMap();
			
			for (String tagStr:tags.keySet()) {
				if (candidateHashTags.contains(tagStr)) {
					ArrayList<String> twtArr = new ArrayList<String>();
					if (res.containsKey(tagStr)) {
						twtArr = res.get(tagStr);						
					}
					twtArr.add(tId);
					res.put(tagStr,twtArr);					
				}
			}
		}
		return res;
	}
	private void writeRetrievedTweetsForSimilarityCalculation(ArrayList<String> unWantedTweets) {
		DirectoryFileUtil dirFU = new DirectoryFileUtil();
		dirFU.clearDir("./data/Cosine/");
		dirFU.makeNewDirectory("./data/Cosine/");
		
		String path = "./data/Cosine/";
		TweetPreProcess ppt = new TweetPreProcess();
		
		for (String tId:TweetDebateAssociation.retrievedTweets) {
			if (!unWantedTweets.contains(tId)) {
				String labStr = TweetDebateAssociation.tweetId2PredictedLabel.get(tId);
				
				if (!labStr.equalsIgnoreCase("none")) {
					try {
						
						FileWriter fout = new FileWriter(path + labStr, true);
						BufferedWriter bout = new BufferedWriter(fout);
						
						String twtTxt = TweetDebateAssociation.allTweets.get(tId).get("text").toString();
						
						bout.write(ppt.process(twtTxt, false, false, false));
						
						bout.close();
					}
					catch(Exception e) {
						e.printStackTrace();
						
					}
				}
			}
		}
	}
	private void writeToHashTagFiles(String hStr, ArrayList<String> tweetArr) {
		try {
			DirectoryFileUtil dirFU = new DirectoryFileUtil();
			dirFU.makeNewDirectory("./data/HashTagFiles/");
			FileWriter fout = new FileWriter("./data/HashTagFiles/" + hStr);
			BufferedWriter bout = new BufferedWriter(fout);
			
			TweetPreProcess ppt = new TweetPreProcess();
			for (String tId:tweetArr) {
				TweetData twt = TweetDebateAssociation.allTweets.get(tId);
				bout.write(ppt.process(twt.get("text").toString(),false, false, false) + "\n");
			}
			bout.close();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	private HashMap<String,Double> getMaxSimVal2Classes(String hStr) {
		HashMap<String,Double> allCosValues = new HashMap<String, Double>();
		
		double maxVal = 0.0;		
		String maxLab = "";
		
		IndexingDocuments myItwl = new IndexingDocuments();
		Directory myDir = null;
		
		try {
			myDir = myItwl.indexDocuments("./data/Cosine/");
		
			DiscriminativeKeytermsExtraction kext = new DiscriminativeKeytermsExtraction();	
			LinkedHashMap<String,LinkedHashMap<String,Double>> tfidfMat = kext.calcTfIdf(myDir);
			HashSet<String> vocab = new HashSet<String>();

			createProfiles(tfidfMat,vocab);
			
			DirectoryFileUtil dirFU = new DirectoryFileUtil();
			File []myFiles = dirFU.returnLisOfFiles("./data/RetrievedTweets/", "");
			
			for (File f:myFiles) {
				if (!f.getName().equalsIgnoreCase(hStr)) {
					if (tfidfMat.containsKey(f.getName())) {
						double cosVal = calcCosine(tfidfMat.get(f.getName()),tfidfMat.get(hStr),vocab);
						allCosValues.put(f.getName(), cosVal);
						
						if (cosVal>maxVal) {
							maxVal = cosVal;
							maxLab = f.getName();
						}
					}
				}
			}
			if (hStr.contains("-tag")) {
				hStr = hStr.split("-tag")[0];
				
			}
			hashTagSim.put(hStr, allCosValues);
			
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		HashMap<String,Double> res = new HashMap<String, Double>();
		res.put(maxLab, maxVal);
		return res;
	}
	
	private void createProfiles(LinkedHashMap<String, LinkedHashMap<String, Double>> tfidfMat, HashSet<String> vocab) {
		
		DirectoryFileUtil dirFU = new DirectoryFileUtil();
		dirFU.clearDir("./data/Profiles/");
		dirFU.makeNewDirectory("./data/Profiles/");
		try {
			int len = 50;
			for (String labStr:tfidfMat.keySet()) {
				FileWriter fout = new FileWriter("./data/Profiles/" + labStr);
				BufferedWriter bout = new BufferedWriter(fout);
				
				HashMap<String,Double> vec = tfidfMat.get(labStr);
				List<Entry<String,Double>> list = new LinkedList<Entry<String,Double>>(vec.entrySet());
				Collections.sort(list, new MyComparator());
			    
				int counter = 0;
				LinkedHashMap<String,Double> newVec = new LinkedHashMap<String, Double>();
			    for (Iterator<Entry<String, Double>> it = list.iterator(); it.hasNext();){
			    	Entry<String, Double> myEntry = it.next();
			    	
			    	vocab.add(myEntry.getKey());
			    	bout.write(myEntry.getKey() + "\t" + myEntry.getValue() + "\n");
			    	newVec.put(myEntry.getKey(), myEntry.getValue());
			    	
			    	counter++;
			    	if (counter>len) {
			    		break;
			    	}
			    }
			    bout.close();
			    tfidfMat.put(labStr, newVec);
			}
		}
		catch (Exception e){
			e.printStackTrace();
		}
	}
	private double calcCosine(HashMap<String, Double> vec1,HashMap<String, Double> vec2, HashSet<String> vocab) {
		
		double dotVal = 0.0;
		double len1 = 0.0;
		double len2 = 0.0;
		int commonWords = 0;
		
		for (String vocabStr:vocab) {
			double val1 = 0.0;
			if (vec1.containsKey(vocabStr)) {
				val1 = vec1.get(vocabStr);
			}
			len1 = len1 + (val1*val1);
			double val2 = 0.0;
			if (vec2.containsKey(vocabStr)) {
				val2 = vec2.get(vocabStr);
			}
			if ((val1>0.0) && (val2>0.0)) {
				commonWords++;				
			}
			len2 = len2 + (val2*val2);
			dotVal = dotVal + (val1*val2);
		}
		//double res = dotVal/((Math.sqrt(len1))*(Math.sqrt(len2)));
		return (commonWords*1.0)/(vocab.size());
	}
	public LinkedHashMap<String,String> checkReplyChains(HashMap<String, Boolean> specificHashTags) {
		HashMap<String,Double> reply2Ent = new HashMap<String, Double>();

		HashMap<String,Integer> tweetId2Freq = new HashMap<String, Integer>();
		LinkedHashMap<String,String> newHashTags = new LinkedHashMap<String, String>();
		
		for (String tId:TweetDebateAssociation.tweetId2ItsReplies.keySet()) {
			
			HashSet<String> replyList = TweetDebateAssociation.tweetId2ItsReplies.get(tId);			
			HashMap<String,Integer> replyListVotes =  new HashMap<String, Integer>();
			
			if (TweetDebateAssociation.trueLabels.containsKey(tId)) {
				for (String replyTId:replyList) {
					String labStr = "none";
					if (TweetDebateAssociation.tweetId2PredictedLabel.containsKey(replyTId)) {
						labStr = TweetDebateAssociation.tweetId2PredictedLabel.get(replyTId);
						int val = 1;
						if (replyListVotes.containsKey(labStr)) {
							val = replyListVotes.get(labStr) + 1;
						}
						replyListVotes.put(labStr, val);
					}
				}
				
				String maxLab = getMaxInHashMap(replyListVotes);
				if (!maxLab.equalsIgnoreCase("")) {					
					double entropyVal = calcEntropy(replyListVotes);
					
					entropyVal = entropyVal * Math.log(replyListVotes.size());
					reply2Ent.put(tId, entropyVal);					
				}
				else {
					if (!TweetDebateAssociation.tweetId2PredictedLabel.containsKey(tId)) {
						tweetId2Freq.put(tId, replyList.size());
					}
					if (TweetDebateAssociation.tweetId2PredictedLabel.containsKey(tId)) {
						for (String repId:replyList) {
							TweetDebateAssociation.tweetId2PredictedLabel.put(repId, TweetDebateAssociation.tweetId2PredictedLabel.get(tId));
						}
					}
				}
			}
//			else {
//				System.out.println("Ask About " + tId);
//			}
		}
		
		
		List<Entry<String,Double>> list1 = new LinkedList<Entry<String,Double>>(reply2Ent.entrySet());
		Collections.sort(list1, new MyComparator());
	    
		Iterator<Entry<String, Double>> it1 = list1.iterator();
		
		int counter1 = 1;
		while (it1.hasNext()) {
			Entry<String,Double> myEntry = it1.next();
			String tId = myEntry.getKey();
			
			String origTweetLab = "";
			if (TweetDebateAssociation.askedThroughActiveLearning.containsKey(tId)) {
				origTweetLab = TweetDebateAssociation.askedThroughActiveLearning.get(tId);
			}
			else if (TweetDebateAssociation.trueLabels.containsKey(tId)) {
				origTweetLab = TweetDebateAssociation.trueLabels.get(tId);
				TweetDebateAssociation.askedThroughActiveLearning.put(tId,origTweetLab);
				TweetDebateAssociation.askCounter++;
			}
			else if (TweetDebateAssociation.askFromUser){
				origTweetLab = TweetDebateAssociation.getTheLabelFromTheUser(tId);
				TweetDebateAssociation.askCounter++;
			}
			if (!origTweetLab.equalsIgnoreCase("")) {
				
				TweetDebateAssociation.tweetId2PredictedLabel.put(tId, origTweetLab);
				TweetDebateAssociation.leftInCloudTweets.remove(tId);
				
				addDiscriminativeHashTag(tId,specificHashTags,newHashTags,origTweetLab);
				for (String rId:TweetDebateAssociation.tweetId2ItsReplies.get(tId)) {					
					TweetDebateAssociation.tweetId2PredictedLabel.put(rId, origTweetLab);

					TweetDebateAssociation.retrievedTweets.add(rId);
					TweetDebateAssociation.leftInCloudTweets.remove(rId);
					addDiscriminativeHashTag(rId,specificHashTags,newHashTags,origTweetLab);
				}
			}
			counter1++;
			if (counter1>10) {
				break;
			}
		}
		
		
		List<Entry<String,Integer>> list = new LinkedList<Entry<String,Integer>>(tweetId2Freq.entrySet());
		Collections.sort(list, new MyComparatorInt());
	    
		Iterator<Entry<String, Integer>> it = list.iterator();
		int itCounter = 1;
		while (it.hasNext()) {
			Entry<String,Integer> myEnt = it.next();
			if (itCounter>32) {
				break;
			}
			String origTweetLab = "";
			if (TweetDebateAssociation.askedThroughActiveLearning.containsKey(myEnt.getKey())) {
				origTweetLab = TweetDebateAssociation.askedThroughActiveLearning.get(myEnt.getKey());
			}
			else if (TweetDebateAssociation.trueLabels.containsKey(myEnt.getKey())) {
				origTweetLab = TweetDebateAssociation.trueLabels.get(myEnt.getKey());
				TweetDebateAssociation.askedThroughActiveLearning.put(myEnt.getKey(),origTweetLab);
				TweetDebateAssociation.askCounter++;
			}
			else if (TweetDebateAssociation.askFromUser){
				origTweetLab = TweetDebateAssociation.getTheLabelFromTheUser(myEnt.getKey());
				TweetDebateAssociation.askCounter++;
			}

			if (!origTweetLab.equalsIgnoreCase(""))	{
				TweetDebateAssociation.tweetId2PredictedLabel.put(myEnt.getKey(), origTweetLab);

				TweetDebateAssociation.leftInCloudTweets.remove(myEnt.getKey());
				
				addDiscriminativeHashTag(myEnt.getKey(),specificHashTags,newHashTags,origTweetLab);
	
				for (String repId:TweetDebateAssociation.tweetId2ItsReplies.get(myEnt.getKey())) {
					TweetDebateAssociation.tweetId2PredictedLabel.put(repId, origTweetLab);
					addDiscriminativeHashTag(repId,specificHashTags,newHashTags,origTweetLab);	
				}
				itCounter++;
			}
		}
		return newHashTags;
	}

	private String getMaxInHashMap(HashMap<String, Integer> replyListVotes) {
		String res = "";
		if (replyListVotes.size()>0) {
			List<Entry<String,Integer>> list = new LinkedList<Entry<String,Integer>>(replyListVotes.entrySet());
			Collections.sort(list, new MyComparatorInt());
		    
			Iterator<Entry<String, Integer>> it = list.iterator();
			Entry<String, Integer> myEntry = it.next();
			int firstVal = myEntry.getValue();
			String firstLab = myEntry.getKey();
			
			res = firstLab;
			
			if (it.hasNext()) {
				Entry<String, Integer> myEntry2 = it.next();
				if (myEntry2.getValue()==firstVal) {
					res = "";
				}
			}
		}
		return res;		
	}

	public void writeHashTagSim(LinkedHashMap<String, String> newHashtags3,
			LinkedHashMap<String, String> newHashtags2) {
		DecimalFormat df = new DecimalFormat("####0.0000");
		try {
			
			FileWriter fout = new FileWriter("./data/HashTagDebateSim.json");
			BufferedWriter bout = new BufferedWriter(fout);
		
			bout.write("{\"HashTags\":[\n");
			for (String hStr:newHashtags3.keySet()) {
				bout.write("{\"tagStr\":\"" + hStr + "\",\"scores\":[\n");
				HashMap<String,Double> scoreList = hashTagSim.get(hStr);
				int counter2 = 1;
				for (String lStr:scoreList.keySet()) {
					bout.write("{\"debate\":\"" + lStr + "\",\"val\":" + df.format(scoreList.get(lStr)));
					if (counter2<scoreList.size()) {
						bout.write("},\n");
					}
					else {
						bout.write("}\n");
					}
					counter2++;
				}
				
				bout.write("]},\n");				
			}
			int counter = 1;
			for (String hStr:newHashtags2.keySet()) {
				if (hashTagSim.containsKey(hStr)) {
					bout.write("{\"tagStr\":\"" + hStr + "\",\"scores\":[\n");
					HashMap<String,Double> scoreList = hashTagSim.get(hStr);
					int counter2 = 1;
					for (String lStr:scoreList.keySet()) {
						bout.write("{\"debate\":\"" + lStr + "\",\"val\":" + df.format(scoreList.get(lStr)));
						if (counter2<scoreList.size()) {
							bout.write("},\n");
						}
						else {
							bout.write("}\n");
						}
						counter2++;
					}
					
					if (counter<newHashtags3.size()) {
						bout.write("]},\n");
					}
					else {
						bout.write("]}\n");
					}
					counter++;
				}
				else {
					counter++;
				}
			}
			bout.write("]}");
			bout.close();
		}
		catch(Exception e){
			e.printStackTrace();
		}
		
	}
}
