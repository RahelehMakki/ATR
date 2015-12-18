package Associate;

import java.util.HashMap;
import java.util.LinkedHashMap;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.index.TermFreqVector;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;

public class DiscriminativeKeytermsExtraction {

	Directory dir;
	IndexSearcher searcher;
	
	public LinkedHashMap<String,LinkedHashMap<String,Double>> calcTfIdf(Directory dir) {
		this.dir = dir;
		LinkedHashMap<String,LinkedHashMap<String,Double>> res = new LinkedHashMap<String, LinkedHashMap<String,Double>>();
		try {
			searcher = new IndexSearcher(dir);
		    IndexReader indexReader = searcher.getIndexReader();
		    
		    //Create Empty HashMaps for each Topic/Debate
		    int numDocs = indexReader.numDocs();
		    for (int i = 0; i < numDocs; i++) {
		    	Document doc = indexReader.document(i);
		    	String docId = doc.get("Id");			    	
		    	
		    	LinkedHashMap<String,Double> tfIdfVec = new LinkedHashMap<String, Double>();
		    	String topicStr = doc.get("Id");
		    	
		    	res.put(topicStr, tfIdfVec);	
		    }
		    
		    //Calculate Idf for all terms
		    HashMap<String,Double> idfMap = new HashMap<String, Double>();
		    TermEnum terms = indexReader.terms();
    		while (terms.next()) {
		    	String vocabTerm = terms.term().text().toLowerCase();
		    	//double idf = Math.log(((numDocs + 1)* 1.0)/(terms.docFreq()));
		    	double idf = Math.log((numDocs* 1.0)/(terms.docFreq()));
		    	idfMap.put(vocabTerm, idf);	
		    }
		    
		    for (int i = 0; i < numDocs; i++) {
		    	Document doc = indexReader.document(i);
		    	LinkedHashMap<String,Double> tfIdfMap = res.get(doc.get("Id"));
		    	
		    	TermFreqVector vector = indexReader.getTermFreqVector(i,"text");
		    	if (vector!=null) {
			    	String[] termVec = vector.getTerms();
			    	int[] freqs = vector.getTermFrequencies();
			    	
			    	int totalFreq = 0;
			    	for (int j=0;j<freqs.length;j++) {
			    		totalFreq = totalFreq + freqs[j];
			    	}
			    	for (int j=0;j<freqs.length;j++) {
			    		
			    		double val = idfMap.get(termVec[j]) * ((freqs[j]*1.0)/totalFreq);
			    		tfIdfMap.put(termVec[j], val);
			    		
			    	}
			    	res.put(doc.get("Id"), tfIdfMap);
			    	
		    	}

		    }
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		return res;
	}
}
